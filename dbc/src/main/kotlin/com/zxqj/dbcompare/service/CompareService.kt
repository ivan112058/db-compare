package com.zxqj.dbcompare.service

import com.mysql.cj.jdbc.exceptions.CommunicationsException
import com.zxqj.dbcompare.model.CompareRequest
import com.zxqj.dbcompare.model.TableDiff
import com.zxqj.dbcompare.model.structure.Column
import com.zxqj.dbcompare.model.structure.TableStructure
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.SQLException

class CompareService(private val dbService: DatabaseService) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Throws(SQLException::class)
    fun compare(request: CompareRequest): List<TableDiff> {
        val sourceConnCheck = try {
            dbService.connect(request.source!!)
        } catch (e: CommunicationsException) {
            throw SQLException("connect SOURCE database failed: ${e.message?.substringBefore('\n')}", e)
        } catch (e: Exception) {
            throw SQLException("connect SOURCE database failed: ${e.message}", e)
        }

        return sourceConnCheck.use { sourceConn ->
            val targetConnCheck = try {
                dbService.connect(request.target!!)
            } catch (e: Exception) {
                throw SQLException("connect TARGET database failed: ${e.message}", e)
            }

            targetConnCheck.use { targetConn ->
                val sourceTables = dbService.getTableNames(sourceConn, request.source.database).toHashSet()
                val targetTables = dbService.getTableNames(targetConn, request.target.database).toHashSet()
                val allTables = (sourceTables + targetTables).toSortedSet()

                allTables.mapNotNull { tableName ->
                    processTable(tableName, sourceConn, targetConn, sourceTables, targetTables, request)
                }
            }
        }
    }

    private fun processTable(
        tableName: String,
        sourceConn: Connection,
        targetConn: Connection,
        sourceTables: Set<String>,
        targetTables: Set<String>,
        request: CompareRequest
    ): TableDiff? {
        if (request.excludeTables?.contains(tableName) == true) return null

        val inSource = tableName in sourceTables
        val inTarget = tableName in targetTables
        val diff = TableDiff(tableName)

        if (inSource && !inTarget) {
            try {
                diff.sourceDDL = dbService.getCreateTableSql(sourceConn, tableName)
            } catch (e: Exception) {
                log.error("Failed to get source DDL for $tableName: ${e.message}")
            }
        }
        if (!inSource && inTarget) {
            try {
                diff.targetDDL = dbService.getCreateTableSql(targetConn, tableName)
            } catch (e: Exception) {
                log.error("Failed to get target DDL for $tableName: ${e.message}")
            }
        }

        val (sourceStruct, targetStruct) = resolveStructures(tableName, inSource, inTarget, sourceConn, targetConn)

        diff.structDiff = when {
            sourceStruct != null && targetStruct != null -> sourceStruct.compareStructure(targetStruct)
            sourceStruct != null -> sourceStruct.compareStructure(TableStructure(tableName))
            else -> TableStructure(tableName).compareStructure(targetStruct!!)
        }

        val dataResult = compareData(
            tableName, inSource, inTarget, sourceStruct, targetStruct,
            sourceConn, targetConn, request
        )

        diff.rowCount = dataResult.rowCount
        diff.dataDiff = dataResult.dataDiff
        diff.primaryKeys = dataResult.primaryKeys
        diff.treeConfig = dataResult.treeConfig

        val hasStructDiff = diff.structDiff?.isEmpty() == false
        val hasDataDiff = diff.dataDiff?.let {
            !it.added.isNullOrEmpty() || !it.removed.isNullOrEmpty() || !it.modified.isNullOrEmpty()
        } == true
        val isTableMissing = !inSource || !inTarget

        return if (hasStructDiff || hasDataDiff || isTableMissing) diff else null
    }

    private fun resolveStructures(
        tableName: String, inSource: Boolean, inTarget: Boolean,
        sourceConn: Connection, targetConn: Connection
    ): Pair<TableStructure?, TableStructure?> =
        (if (inSource) dbService.getTableStructure(sourceConn, tableName) else null) to
                (if (inTarget) dbService.getTableStructure(targetConn, tableName) else null)

    private data class DataComparisonResult(
        val rowCount: TableDiff.RowCount,
        val dataDiff: TableDiff.DataDiff?,
        val primaryKeys: List<String>?,
        val treeConfig: TableDiff.TreeConfig? = null
    )

    private fun compareData(
        tableName: String, inSource: Boolean, inTarget: Boolean,
        sourceStruct: TableStructure?, targetStruct: TableStructure?,
        sourceConn: Connection, targetConn: Connection, request: CompareRequest
    ): DataComparisonResult {
        var sourceCount = 0
        var targetCount = 0
        var dataDiff: TableDiff.DataDiff? = null
        var primaryKeys: List<String>? = null

        val treeConfig = getTreeConfig(tableName, request.treeTableConfig)
        if (treeConfig != null) {
            val specPKs = getPrimaryKeys(tableName, request.specifiedPrimaryKeys, null)
            if (specPKs.isNullOrEmpty()) {
                throw RuntimeException("Tree table '$tableName' must have specifiedPrimaryKeys configured.")
            }
        }

        val skipDataCompare = request.ignoreDataTables?.contains(tableName) == true
        var customQuery: String? = null

        if (treeConfig != null) {
            val specPKs = getPrimaryKeys(tableName, request.specifiedPrimaryKeys, null)
            if (!specPKs.isNullOrEmpty()) {
                val parentSelects = specPKs.joinToString(", ") { pk -> "p.`$pk` AS `__parent_$pk`" }
                customQuery =
                    "SELECT m.*, $parentSelects FROM `$tableName` m LEFT JOIN `$tableName` p ON m.`${treeConfig.parentIdColumn}` = p.`${treeConfig.idColumn}`"
            }
        }

        if (inSource && inTarget) {
            if (skipDataCompare) {
                sourceCount = 0; targetCount = 0
            } else {
                var rawSourceData = dbService.getTableData(sourceConn, tableName, customQuery)
                var rawTargetData = dbService.getTableData(targetConn, tableName, customQuery)

                if (treeConfig != null) {
                    val specPKs = getPrimaryKeys(tableName, request.specifiedPrimaryKeys, sourceStruct)
                        ?: throw RuntimeException("Tree table '$tableName' must have specifiedPrimaryKeys configured.")
                    rawSourceData = processTreeData(rawSourceData, treeConfig, specPKs)
                    rawTargetData = processTreeData(rawTargetData, treeConfig, specPKs)
                }

                sourceCount = rawSourceData.size
                targetCount = rawTargetData.size
                primaryKeys = getPrimaryKeys(tableName, request.specifiedPrimaryKeys, sourceStruct)

                val (alignedSource, alignedTarget) = alignAndNormalizeData(
                    rawSourceData, rawTargetData, sourceStruct, targetStruct,
                    request.ignoreFields, tableName, primaryKeys,
                    request.excludeDataRows, request.includeDataRows
                )
                dataDiff = calculateDataDiff(alignedSource, alignedTarget, primaryKeys, tableName)
            }
        } else {
            if (inSource) {
                sourceCount = dbService.getRowCount(sourceConn, tableName)
                if (!skipDataCompare) {
                    var rawData = dbService.getTableData(sourceConn, tableName, customQuery)
                    primaryKeys = getPrimaryKeys(tableName, request.specifiedPrimaryKeys, sourceStruct)
                    if (treeConfig != null) rawData = processTreeData(rawData, treeConfig, primaryKeys!!)
                    val normalizedData = normalizeData(rawData, request.ignoreFields, tableName, primaryKeys)
                    dataDiff = TableDiff.DataDiff(removed = normalizedData, added = emptyList(), modified = emptyList())
                }
            }
            if (inTarget) {
                targetCount = dbService.getRowCount(targetConn, tableName)
                if (!skipDataCompare) {
                    var rawData = dbService.getTableData(targetConn, tableName, customQuery)
                    primaryKeys = getPrimaryKeys(tableName, request.specifiedPrimaryKeys, targetStruct)
                    if (treeConfig != null) rawData = processTreeData(rawData, treeConfig, primaryKeys!!)
                    val normalizedData = normalizeData(rawData, request.ignoreFields, tableName, primaryKeys)
                    dataDiff = TableDiff.DataDiff(added = normalizedData, removed = emptyList(), modified = emptyList())
                }
            }
        }

        val finalTreeConfig = if (treeConfig != null && !primaryKeys.isNullOrEmpty()) {
            TableDiff.TreeConfig(treeConfig.idColumn, treeConfig.parentIdColumn, primaryKeys)
        } else null

        return DataComparisonResult(
            rowCount = TableDiff.RowCount(sourceCount, targetCount),
            dataDiff = dataDiff,
            primaryKeys = primaryKeys,
            treeConfig = finalTreeConfig
        )
    }

    private fun applyRowFilters(
        data: List<Map<String, Any?>>, tableName: String,
        excludeDataRows: List<String>?, includeDataRows: List<String>?
    ): List<Map<String, Any?>> {
        if (data.isEmpty()) return data

        val includeRules = parseRowRules(includeDataRows, tableName)
        if (includeRules.isNotEmpty()) {
            return data.filter { row -> includeRules.any { rule -> matchRule(row, rule) } }
        }

        val excludeRules = parseRowRules(excludeDataRows, tableName)
        if (excludeRules.isNotEmpty()) {
            return data.filterNot { row -> excludeRules.any { rule -> matchRule(row, rule) } }
        }

        return data
    }

    private fun parseRowRules(rules: List<String>?, tableName: String): List<Map<String, String>> {
        if (rules.isNullOrEmpty()) return emptyList()
        return rules.mapNotNull { rule ->
            val trimmedRule = rule.trim()
            if (trimmedRule.startsWith("$tableName(") && trimmedRule.endsWith(")")) {
                val content = trimmedRule.substring(tableName.length + 1, trimmedRule.length - 1)
                val parts = content.split("=", limit = 2)
                if (parts.size == 2) {
                    val cols = parts[0].split("#").map { it.trim() }
                    val vals = parts[1].split("#").map { it.trim() }
                    if (cols.size == vals.size) cols.zip(vals).toMap() else null
                } else null
            } else null
        }
    }

    private fun matchRule(row: Map<String, Any?>, rule: Map<String, String>): Boolean =
        rule.all { (col, expectedVal) ->
            val actualVal = row[col] ?: row.entries.find { it.key.equals(col, ignoreCase = true) }?.value
            actualVal?.toString() == expectedVal
        }

    private fun alignAndNormalizeData(
        sourceData: List<Map<String, Any?>>, targetData: List<Map<String, Any?>>,
        sourceStruct: TableStructure?, targetStruct: TableStructure?,
        ignoreFields: List<String>?, tableName: String, primaryKeys: List<String>?,
        excludeDataRows: List<String>? = null, includeDataRows: List<String>? = null
    ): Pair<List<Map<String, Any?>>, List<Map<String, Any?>>> {
        val filteredSource = applyRowFilters(sourceData, tableName, excludeDataRows, includeDataRows)
        val filteredTarget = applyRowFilters(targetData, tableName, excludeDataRows, includeDataRows)

        val normalizedSource = normalizeData(filteredSource, ignoreFields, tableName, primaryKeys)
        val normalizedTarget = normalizeData(filteredTarget, ignoreFields, tableName, primaryKeys)

        if (normalizedSource.isEmpty() || normalizedTarget.isEmpty()) return normalizedSource to normalizedTarget
        if (sourceStruct == null || targetStruct == null) return normalizedSource to normalizedTarget

        val sourceCols = normalizedSource.first().keys
        val targetCols = normalizedTarget.first().keys
        val sourceExtras = sourceCols - targetCols
        val targetExtras = targetCols - sourceCols

        val sourceToRemove = findRemovableColumns(normalizedSource, sourceStruct, sourceExtras)
        val targetToRemove = findRemovableColumns(normalizedTarget, targetStruct, targetExtras)

        return removeColumns(normalizedSource, sourceToRemove) to removeColumns(normalizedTarget, targetToRemove)
    }

    private fun findRemovableColumns(
        data: List<Map<String, Any?>>, struct: TableStructure, extraCols: Set<String>
    ): Set<String> = extraCols.filterTo(HashSet()) { colName ->
        val colDef = findColumn(struct, colName)
        colDef != null && colDef.isNullable && data.all { it[colName] == null }
    }

    private fun removeColumns(data: List<Map<String, Any?>>, columnsToRemove: Set<String>): List<Map<String, Any?>> =
        if (columnsToRemove.isEmpty()) data else data.map { row -> row - columnsToRemove }

    private fun calculateDataDiff(
        sourceData: List<Map<String, Any?>>, targetData: List<Map<String, Any?>>,
        primaryKeys: List<String>?, tableName: String
    ): TableDiff.DataDiff = if (!primaryKeys.isNullOrEmpty()) {
        try {
            computeDiffWithPK(sourceData, targetData, primaryKeys)
        } catch (e: Exception) {
            log.error("Primary key comparison failed for table $tableName: ${e.message}")
            computeDiffSimple(sourceData, targetData)
        }
    } else {
        computeDiffSimple(sourceData, targetData)
    }

    private fun computeDiffWithPK(
        sourceData: List<Map<String, Any?>>, targetData: List<Map<String, Any?>>,
        primaryKeys: List<String>
    ): TableDiff.DataDiff {
        val sourceMap = buildDataMap(sourceData, primaryKeys)
        val targetMap = buildDataMap(targetData, primaryKeys)

        val added = ArrayList<Map<String, Any?>>()
        val removed = ArrayList<Map<String, Any?>>()
        val modified = ArrayList<Map<String, Any?>>()

        for ((key, sourceRow) in sourceMap) {
            val targetRow = targetMap[key]
            if (targetRow != null) {
                if (sourceRow != targetRow) {
                    modified.add(LinkedHashMap(targetRow).apply { put("_source", sourceRow) })
                }
                targetMap.remove(key)
            } else {
                removed.add(sourceRow)
            }
        }
        added.addAll(targetMap.values)

        return TableDiff.DataDiff(added = added, removed = removed, modified = modified)
    }

    private fun computeDiffSimple(
        sourceData: List<Map<String, Any?>>, targetData: List<Map<String, Any?>>
    ): TableDiff.DataDiff {
        val sourceSet = sourceData.toSet()
        val targetSet = targetData.toSet()
        return TableDiff.DataDiff(
            added = (targetSet - sourceSet).toList(),
            removed = (sourceSet - targetSet).toList(),
            modified = emptyList()
        )
    }

    private fun normalizeData(
        data: List<Map<String, Any?>>, ignoreFields: List<String>?,
        tableName: String, protectedFields: List<String>?
    ): List<Map<String, Any?>> {
        if (ignoreFields.isNullOrEmpty() || data.isEmpty()) return data

        val fieldsToRemove = ignoreFields.asSequence()
            .mapNotNull { field ->
                if (field.contains(".")) {
                    val parts = field.split(".", limit = 2)
                    if (parts.size == 2 && parts[0].equals(tableName, ignoreCase = true)) parts[1] else null
                } else field
            }
            .filter { field -> protectedFields?.none { it.equals(field, ignoreCase = true) } ?: true }
            .toSet()

        if (fieldsToRemove.isEmpty()) return data
        return data.map { row -> row - fieldsToRemove }
    }

    private fun getPrimaryKeys(
        tableName: String, specifiedPrimaryKeys: List<String>?, structure: TableStructure?
    ): List<String>? {
        specifiedPrimaryKeys?.forEach { spec ->
            val trimmedSpec = spec.trim()
            if (trimmedSpec.startsWith("$tableName(") && trimmedSpec.endsWith(")")) {
                val colsPart = trimmedSpec.substring(tableName.length + 1, trimmedSpec.length - 1)
                return colsPart.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            }
        }
        return structure?.primaryKey?.columns
    }

    private fun buildDataMap(
        data: List<Map<String, Any?>>, primaryKeys: List<String>
    ): MutableMap<String, Map<String, Any?>> {
        if (data.isEmpty()) return LinkedHashMap()

        val firstRow = data[0]
        val rowKeys = firstRow.keys
        val pkMapping = primaryKeys.associateWith { pk ->
            if (firstRow.containsKey(pk)) pk
            else rowKeys.find { it.equals(pk, ignoreCase = true) } ?: pk
        }

        val map = LinkedHashMap<String, Map<String, Any?>>(data.size)
        for (row in data) {
            val key = primaryKeys.joinToString("||") { pk ->
                val actualKey = pkMapping[pk]!!
                if (!row.containsKey(actualKey)) throw RuntimeException("Primary key column '$pk' not found in data (mapped to '$actualKey')")
                row[actualKey].toString()
            }
            if (map.put(key, row) != null) throw RuntimeException("Duplicate primary key found: $key")
        }
        return map
    }

    private fun findColumn(struct: TableStructure, colName: String): Column? =
        struct.columns[colName] ?: struct.columns.entries.find { it.key.equals(colName, ignoreCase = true) }?.value

    private fun getTreeConfig(tableName: String, treeTableConfig: List<String>?): TableDiff.TreeConfig? {
        if (treeTableConfig.isNullOrEmpty()) return null
        return treeTableConfig.firstNotNullOfOrNull { config ->
            val trimmedConfig = config.trim()
            if (trimmedConfig.startsWith("$tableName(") && trimmedConfig.endsWith(")")) {
                val content = trimmedConfig.substring(tableName.length + 1, trimmedConfig.length - 1)
                val parts = content.split(",").map { it.trim() }
                if (parts.size == 2) TableDiff.TreeConfig(parts[0], parts[1], emptyList()) else null
            } else null
        }
    }

    private fun processTreeData(
        data: List<Map<String, Any?>>, treeConfig: TableDiff.TreeConfig, specifiedPrimaryKeys: List<String>
    ): List<Map<String, Any?>> = data.map { row ->
        LinkedHashMap(row).apply { remove(treeConfig.parentIdColumn) }
    }
}
