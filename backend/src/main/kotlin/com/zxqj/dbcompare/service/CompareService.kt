package com.zxqj.dbcompare.service

import com.zxqj.dbcompare.model.CompareRequest
import com.zxqj.dbcompare.model.TableDiff
import com.zxqj.dbcompare.model.structure.Column
import com.zxqj.dbcompare.model.structure.TableStructure
import org.springframework.stereotype.Service
import java.sql.Connection
import java.sql.SQLException

@Service
class CompareService(private val dbService: DatabaseService) {

    @Throws(SQLException::class)
    fun compare(request: CompareRequest): Map<String, Any> {
        val sourceConfig = request.source ?: throw IllegalArgumentException("Source config is required")
        val targetConfig = request.target ?: throw IllegalArgumentException("Target config is required")

        return dbService.connect(sourceConfig).use { sourceConn ->
            dbService.connect(targetConfig).use { targetConn ->
                
                val sourceTables = dbService.getTableNames(sourceConn, sourceConfig.database).toHashSet()
                val targetTables = dbService.getTableNames(targetConn, targetConfig.database).toHashSet()
                val allTables = (sourceTables + targetTables).toSortedSet()

                val tableDiffs = allTables.mapNotNull { tableName ->
                    processTable(tableName, sourceConn, targetConn, sourceTables, targetTables, request)
                }

                mapOf("tables" to tableDiffs)
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
        if (request.excludeTables?.contains(tableName) ?: false) {
            return null
        }

        val inSource = tableName in sourceTables
        val inTarget = tableName in targetTables
        val diff = TableDiff(tableName)

        if (inSource && !inTarget) {
            try {
                diff.sourceDDL = dbService.getCreateTableSql(sourceConn, tableName)
            } catch (e: Exception) {
                System.err.println("Failed to get source DDL for $tableName: ${e.message}")
            }
        }
        if (!inSource && inTarget) {
             try {
                diff.targetDDL = dbService.getCreateTableSql(targetConn, tableName)
            } catch (e: Exception) {
                System.err.println("Failed to get target DDL for $tableName: ${e.message}")
            }
        }

        // 1. Structure Comparison
        val (sourceStruct, targetStruct) = resolveStructures(
            tableName, inSource, inTarget, sourceConn, targetConn
        )

        diff.structDiff = if (sourceStruct != null && targetStruct != null) {
            sourceStruct.compareStructure(targetStruct)
        } else if (sourceStruct != null) {
            sourceStruct.compareStructure(TableStructure(tableName))
        } else {
            TableStructure(tableName).compareStructure(targetStruct!!)
        }

        // 2. Data & Row Count Comparison
        val dataResult = compareData(
            tableName, inSource, inTarget, 
            sourceStruct, targetStruct, 
            sourceConn, targetConn, request
        )
        
        diff.rowCount = dataResult.rowCount
        diff.dataDiff = dataResult.dataDiff
        diff.primaryKeys = dataResult.primaryKeys

        // 3. Filter Result
        val hasStructDiff = diff.structDiff?.isEmpty() == false
        val hasDataDiff = diff.dataDiff?.let {
            !it.added.isNullOrEmpty() || !it.removed.isNullOrEmpty() || !it.modified.isNullOrEmpty()
        } == true
        // val hasCountDiff = diff.rowCount?.let { it.source != it.target } == true
        val isTableMissing = !inSource || !inTarget

        // User requested to ignore row count diffs if that's the only diff
        return if (hasStructDiff || hasDataDiff || isTableMissing) {
            diff
        } else {
            null
        }
    }

    private fun resolveStructures(
        tableName: String,
        inSource: Boolean,
        inTarget: Boolean,
        sourceConn: Connection,
        targetConn: Connection
    ): Pair<TableStructure?, TableStructure?> {
        val sourceStruct = if (inSource) dbService.getTableStructure(sourceConn, tableName) else null
        val targetStruct = if (inTarget) dbService.getTableStructure(targetConn, tableName) else null
        return sourceStruct to targetStruct
    }

    private data class DataComparisonResult(
        val rowCount: TableDiff.RowCount,
        val dataDiff: TableDiff.DataDiff?,
        val primaryKeys: List<String>?
    )

    private fun compareData(
        tableName: String,
        inSource: Boolean,
        inTarget: Boolean,
        sourceStruct: TableStructure?,
        targetStruct: TableStructure?,
        sourceConn: Connection,
        targetConn: Connection,
        request: CompareRequest
    ): DataComparisonResult {
        var sourceCount = 0
        var targetCount = 0
        var dataDiff: TableDiff.DataDiff? = null
        var primaryKeys: List<String>? = null

        val skipDataCompare = request.ignoreDataTables?.contains(tableName) ?: false
        val customQuery = getTableQuery(tableName, request.specifiedDataQueries)

        if (inSource && inTarget) {
            if (skipDataCompare) {
                // If skipping data compare, also skip row count fetching as per requirement
                sourceCount = 0
                targetCount = 0
            } else {
                val rawSourceData = dbService.getTableData(sourceConn, tableName, customQuery)
                val rawTargetData = dbService.getTableData(targetConn, tableName, customQuery)
                sourceCount = rawSourceData.size
                targetCount = rawTargetData.size

                primaryKeys = getPrimaryKeys(tableName, request.specifiedPrimaryKeys, sourceStruct)
                
                val (alignedSource, alignedTarget) = alignAndNormalizeData(
                    rawSourceData, rawTargetData, 
                    sourceStruct, targetStruct, 
                    request.ignoreFields, tableName, primaryKeys,
                    request.excludeDataRows,
                    request.includeDataRows
                )

                dataDiff = calculateDataDiff(alignedSource, alignedTarget, primaryKeys, tableName)
            }
        } else {
            // Missing table case
            if (inSource) {
                sourceCount = dbService.getRowCount(sourceConn, tableName)
                if (!skipDataCompare) {
                    val rawData = dbService.getTableData(sourceConn, tableName, customQuery)
                    primaryKeys = getPrimaryKeys(tableName, request.specifiedPrimaryKeys, sourceStruct)
                    val normalizedData = normalizeData(rawData, request.ignoreFields, tableName, primaryKeys)
                    
                    dataDiff = TableDiff.DataDiff().apply {
                        removed = normalizedData
                        added = emptyList()
                        modified = emptyList()
                    }
                }
            }
            if (inTarget) {
                targetCount = dbService.getRowCount(targetConn, tableName)
                if (!skipDataCompare) {
                    val rawData = dbService.getTableData(targetConn, tableName, customQuery)
                    primaryKeys = getPrimaryKeys(tableName, request.specifiedPrimaryKeys, targetStruct)
                    val normalizedData = normalizeData(rawData, request.ignoreFields, tableName, primaryKeys)
                    
                    dataDiff = TableDiff.DataDiff().apply {
                        added = normalizedData
                        removed = emptyList()
                        modified = emptyList()
                    }
                }
            }
        }

        return DataComparisonResult(
            rowCount = TableDiff.RowCount(sourceCount, targetCount),
            dataDiff = dataDiff,
            primaryKeys = primaryKeys
        )
    }

    private fun applyRowFilters(
        data: List<Map<String, Any?>>,
        tableName: String,
        excludeDataRows: List<String>?,
        includeDataRows: List<String>?
    ): List<Map<String, Any?>> {
        if (data.isEmpty()) return data

        // 1. Check Include Rules
        val includeRules = parseRowRules(includeDataRows, tableName)
        if (includeRules.isNotEmpty()) {
            return data.filter { row ->
                includeRules.any { rule -> matchRule(row, rule) }
            }
        }

        // 2. Check Exclude Rules
        val excludeRules = parseRowRules(excludeDataRows, tableName)
        if (excludeRules.isNotEmpty()) {
            return data.filterNot { row ->
                excludeRules.any { rule -> matchRule(row, rule) }
            }
        }

        return data
    }

    private fun parseRowRules(rules: List<String>?, tableName: String): List<Map<String, String>> {
        if (rules.isNullOrEmpty()) return emptyList()

        return rules.mapNotNull { rule ->
            // Parse rule: table_name(col=value) or table_name(col1#col2=value1#value2)
            val trimmedRule = rule.trim()
            if (trimmedRule.startsWith("$tableName(") && trimmedRule.endsWith(")")) {
                val content = trimmedRule.substring(tableName.length + 1, trimmedRule.length - 1)
                val parts = content.split("=", limit = 2)
                if (parts.size == 2) {
                    val cols = parts[0].split("#").map { it.trim() }
                    val vals = parts[1].split("#").map { it.trim() }
                    if (cols.size == vals.size) {
                        cols.zip(vals).toMap()
                    } else null
                } else null
            } else null
        }
    }

    private fun matchRule(row: Map<String, Any?>, rule: Map<String, String>): Boolean {
        return rule.all { (col, expectedVal) ->
            // Determine if row matches criteria.
            // Case-insensitive check for column name?
            val actualVal = row[col] ?: row.entries.find { it.key.equals(col, ignoreCase = true) }?.value
            actualVal?.toString() == expectedVal
        }
    }

    private fun alignAndNormalizeData(
        sourceData: List<Map<String, Any?>>,
        targetData: List<Map<String, Any?>>,
        sourceStruct: TableStructure?,
        targetStruct: TableStructure?,
        ignoreFields: List<String>?,
        tableName: String,
        primaryKeys: List<String>?,
        excludeDataRows: List<String>? = null,
        includeDataRows: List<String>? = null
    ): Pair<List<Map<String, Any?>>, List<Map<String, Any?>>> {
        // 0. Filter Rows (Include/Exclude)
        val filteredSource = applyRowFilters(sourceData, tableName, excludeDataRows, includeDataRows)
        val filteredTarget = applyRowFilters(targetData, tableName, excludeDataRows, includeDataRows)

        // 1. Normalize (remove ignored fields)
        val normalizedSource = normalizeData(filteredSource, ignoreFields, tableName, primaryKeys)
        val normalizedTarget = normalizeData(filteredTarget, ignoreFields, tableName, primaryKeys)

        if (normalizedSource.isEmpty() || normalizedTarget.isEmpty()) {
            return normalizedSource to normalizedTarget
        }
        if (sourceStruct == null || targetStruct == null) {
            return normalizedSource to normalizedTarget
        }

        // 2. Align Columns (remove extra null columns)
        val sourceCols = normalizedSource.first().keys
        val targetCols = normalizedTarget.first().keys

        val sourceExtras = sourceCols - targetCols
        val targetExtras = targetCols - sourceCols

        val sourceToRemove = findRemovableColumns(normalizedSource, sourceStruct, sourceExtras)
        val targetToRemove = findRemovableColumns(normalizedTarget, targetStruct, targetExtras)

        val finalSource = removeColumns(normalizedSource, sourceToRemove)
        val finalTarget = removeColumns(normalizedTarget, targetToRemove)

        return finalSource to finalTarget
    }

    private fun findRemovableColumns(
        data: List<Map<String, Any?>>,
        struct: TableStructure,
        extraCols: Set<String>
    ): Set<String> {
        return extraCols.filterTo(HashSet()) { colName ->
            val colDef = findColumn(struct, colName)
            colDef != null && colDef.isNullable && isAllNull(data, colName)
        }
    }

    private fun removeColumns(
        data: List<Map<String, Any?>>,
        columnsToRemove: Set<String>
    ): List<Map<String, Any?>> {
        if (columnsToRemove.isEmpty()) return data
        return data.map { row ->
            row - columnsToRemove
        }
    }

    private fun calculateDataDiff(
        sourceData: List<Map<String, Any?>>,
        targetData: List<Map<String, Any?>>,
        primaryKeys: List<String>?,
        tableName: String
    ): TableDiff.DataDiff {
        return if (!primaryKeys.isNullOrEmpty()) {
            try {
                computeDiffWithPK(sourceData, targetData, primaryKeys)
            } catch (e: Exception) {
                System.err.println("Primary key comparison failed for table $tableName: ${e.message}")
                computeDiffSimple(sourceData, targetData)
            }
        } else {
            computeDiffSimple(sourceData, targetData)
        }
    }

    private fun computeDiffWithPK(
        sourceData: List<Map<String, Any?>>,
        targetData: List<Map<String, Any?>>,
        primaryKeys: List<String>
    ): TableDiff.DataDiff {
        val sourceMap = buildDataMap(sourceData, primaryKeys)
        val targetMap = buildDataMap(targetData, primaryKeys) // Mutable map to track processed

        val added = ArrayList<Map<String, Any?>>()
        val removed = ArrayList<Map<String, Any?>>()
        val modified = ArrayList<Map<String, Any?>>()

        // Find Removed and Modified
        for ((key, sourceRow) in sourceMap) {
            val targetRow = targetMap[key]
            if (targetRow != null) {
                if (sourceRow != targetRow) {
                    val modifiedRow = LinkedHashMap(targetRow)
                    modifiedRow["_source"] = sourceRow
                    modified.add(modifiedRow)
                }
                targetMap.remove(key) // Mark as processed
            } else {
                removed.add(sourceRow)
            }
        }

        // Remaining in Target are Added
        added.addAll(targetMap.values)

        return TableDiff.DataDiff().apply {
            this.added = added
            this.removed = removed
            this.modified = modified
        }
    }

    private fun computeDiffSimple(
        sourceData: List<Map<String, Any?>>,
        targetData: List<Map<String, Any?>>
    ): TableDiff.DataDiff {
        val sourceSet = sourceData.toSet()
        val targetSet = targetData.toSet()

        val added = (targetSet - sourceSet).toList()
        val removed = (sourceSet - targetSet).toList()

        return TableDiff.DataDiff().apply {
            this.added = added
            this.removed = removed
            this.modified = emptyList()
        }
    }

    private fun normalizeData(
        data: List<Map<String, Any?>>,
        ignoreFields: List<String>?,
        tableName: String,
        protectedFields: List<String>?
    ): List<Map<String, Any?>> {
        if (ignoreFields.isNullOrEmpty() || data.isEmpty()) return data

        val fieldsToRemove = ignoreFields.asSequence()
            .mapNotNull { field ->
                if (field.contains(".")) {
                    val parts = field.split(".", limit = 2)
                    if (parts.size == 2 && parts[0].equals(tableName, ignoreCase = true)) {
                        parts[1]
                    } else null
                } else field
            }
            .filter { field ->
                protectedFields?.none { it.equals(field, ignoreCase = true) } ?: true
            }
            .toSet()

        if (fieldsToRemove.isEmpty()) return data

        return data.map { row ->
            row - fieldsToRemove
        }
    }

    private fun getPrimaryKeys(
        tableName: String,
        specifiedPrimaryKeys: List<String>?,
        structure: TableStructure?
    ): List<String>? {
        // 1. Check User Config
        specifiedPrimaryKeys?.forEach { spec ->
            val trimmedSpec = spec.trim()
            if (trimmedSpec.startsWith("$tableName(") && trimmedSpec.endsWith(")")) {
                val colsPart = trimmedSpec.substring(tableName.length + 1, trimmedSpec.length - 1)
                return colsPart.split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
            }
        }
        // 2. Check Database PK
        return structure?.primaryKey?.columns
    }

    private fun buildDataMap(
        data: List<Map<String, Any?>>,
        primaryKeys: List<String>
    ): MutableMap<String, Map<String, Any?>> {
        if (data.isEmpty()) return LinkedHashMap()

        // Build PK field name mapping (case-insensitive check)
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
                if (!row.containsKey(actualKey)) {
                    throw RuntimeException("Primary key column '$pk' not found in data (mapped to '$actualKey')")
                }
                row[actualKey].toString()
            }
            
            if (map.put(key, row) != null) {
                throw RuntimeException("Duplicate primary key found: $key")
            }
        }
        return map
    }

    private fun findColumn(struct: TableStructure, colName: String): Column? {
        return struct.columns[colName] 
            ?: struct.columns.entries.find { it.key.equals(colName, ignoreCase = true) }?.value
    }

    private fun isAllNull(data: List<Map<String, Any?>>, colName: String): Boolean {
        return data.all { it[colName] == null }
    }

    private fun getTableQuery(tableName: String, specifiedDataQueries: List<String>?): String? {
        return specifiedDataQueries?.firstNotNullOfOrNull {
            val parts = it.split("=", limit = 2)
            if (parts.size == 2 && parts[0].trim() == tableName) {
                parts[1].trim()
            } else null
        }
    }
}
