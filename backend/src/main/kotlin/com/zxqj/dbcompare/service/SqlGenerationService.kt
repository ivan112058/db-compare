package com.zxqj.dbcompare.service

import com.zxqj.dbcompare.model.TableDiff
import com.zxqj.dbcompare.model.structure.Column
import com.zxqj.dbcompare.model.structure.IndexInfo
import com.zxqj.dbcompare.model.structure.DiffStatus

class SqlGenerationService {

    fun generateFullUpgradeScript(diffs: List<TableDiff>): String = buildString {
        appendLine("SET NAMES utf8mb4;")
        appendLine("SET FOREIGN_KEY_CHECKS = 0;")
        appendLine()

        diffs.filter { it.sourceDDL != null }.forEach { diff ->
            val sql = generateUpgradeSql(diff)
            if (sql.isNotBlank()) appendLine(sql)
        }
        diffs.filter { it.sourceDDL == null }.forEach { diff ->
            val sql = generateUpgradeSql(diff)
            if (sql.isNotBlank()) appendLine(sql)
        }

        appendLine("SET FOREIGN_KEY_CHECKS = 1;")
    }

    fun generateFullRollbackScript(diffs: List<TableDiff>): String = buildString {
        appendLine("SET NAMES utf8mb4;")
        appendLine("SET FOREIGN_KEY_CHECKS = 0;")
        appendLine()

        diffs.filter { it.targetDDL != null }.forEach { diff ->
            val sql = generateRollbackSql(diff)
            if (sql.isNotBlank()) appendLine(sql)
        }
        diffs.filter { it.targetDDL == null }.forEach { diff ->
            val sql = generateRollbackSql(diff)
            if (sql.isNotBlank()) appendLine(sql)
        }

        appendLine("SET FOREIGN_KEY_CHECKS = 1;")
    }

    fun generateUpgradeSql(diff: TableDiff): String = buildString {
        appendLine("-- Table: ${diff.tableName}")

        when {
            diff.sourceDDL != null -> {
                appendLine("-- Change: Drop table ${diff.tableName}")
                appendLine("DROP TABLE IF EXISTS `${diff.tableName}`;")
                appendLine()
                return@buildString
            }
            diff.targetDDL != null -> {
                appendLine("-- Change: Create table ${diff.tableName}")
                appendLine(makeCreateTableSafe(diff.targetDDL!!) + ";")
                appendLine()
                if (diff.dataDiff?.added?.isNotEmpty() == true) {
                    appendLine("-- Change: Insert new rows")
                    var addedRows = diff.dataDiff!!.added!!
                    if (diff.treeConfig != null) addedRows = sortTreeData(addedRows, diff.treeConfig!!)
                    addedRows.forEach { row ->
                        appendLine(buildInsertStatement(diff.tableName, row, diff.primaryKeys, diff.treeConfig) + ";")
                    }
                }
                return@buildString
            }
        }

        diff.structDiff?.let { struct ->
            struct.indexes.filter { it.status == DiffStatus.REMOVED }.forEach { item ->
                val idx = item.source!!
                appendLine("-- Change: Drop index ${idx.name}")
                appendLine(buildSafeDropIndexSql(diff.tableName, idx.name!!))
            }
            struct.columns.filter { it.status == DiffStatus.REMOVED }.forEach { item ->
                val col = item.source!!
                appendLine("-- Change: Drop column ${col.name}")
                appendLine(buildSafeDropColumnSql(diff.tableName, col.name!!))
            }
            struct.columns.filter { it.status == DiffStatus.ADDED }.forEach { item ->
                val col = item.target!!
                appendLine("-- Change: Add column ${col.name}")
                appendLine(buildSafeAddColumnSql(diff.tableName, col))
            }
            struct.columns.filter { it.status == DiffStatus.MODIFIED }.forEach { item ->
                val col = item.target!!
                appendLine("-- Change: Modify column ${col.name}")
                appendLine("ALTER TABLE `${diff.tableName}` MODIFY COLUMN ${formatColumnDefinition(col)};")
            }
            struct.indexes.filter { it.status == DiffStatus.ADDED }.forEach { item ->
                val idx = item.target!!
                appendLine("-- Change: Add index ${idx.name}")
                appendLine(buildSafeAddIndexSql(diff.tableName, idx))
            }
            struct.indexes.filter { it.status == DiffStatus.MODIFIED }.forEach { item ->
                val oldIdx = item.source!!
                val newIdx = item.target!!
                appendLine("-- Change: Modify index ${oldIdx.name}")
                appendLine(buildSafeDropIndexSql(diff.tableName, oldIdx.name!!))
                appendLine(buildSafeAddIndexSql(diff.tableName, newIdx))
            }
        }

        diff.dataDiff?.let { data ->
            if (!data.removed.isNullOrEmpty()) {
                appendLine("-- Change: Delete removed rows")
                data.removed!!.forEach { row ->
                    val pkClause = buildWhereClause(row, diff.primaryKeys)
                    if (pkClause.isNotEmpty()) appendLine("DELETE FROM `${diff.tableName}` WHERE $pkClause;")
                }
            }
            if (!data.added.isNullOrEmpty()) {
                appendLine("-- Change: Insert new rows")
                var addedRows = data.added!!
                if (diff.treeConfig != null) addedRows = sortTreeData(addedRows, diff.treeConfig!!)
                addedRows.forEach { row ->
                    appendLine(buildInsertStatement(diff.tableName, row, diff.primaryKeys, diff.treeConfig) + ";")
                }
            }
            if (!data.modified.isNullOrEmpty()) {
                appendLine("-- Change: Update rows")
                data.modified!!.forEach { row ->
                    val pkClause = buildWhereClause(row, diff.primaryKeys)
                    val updates = buildUpdateSetForRollback(row)
                    if (pkClause.isNotEmpty() && updates.isNotEmpty()) {
                        appendLine("UPDATE `${diff.tableName}` SET $updates WHERE $pkClause;")
                    }
                }
            }
        }
    }

    fun generateRollbackSql(diff: TableDiff): String = buildString {
        appendLine("-- Rollback for Table: ${diff.tableName}")

        when {
            diff.sourceDDL != null -> {
                appendLine("-- Change: Restore table ${diff.tableName} (Rollback Drop)")
                appendLine(makeCreateTableSafe(diff.sourceDDL!!) + ";")
                appendLine()
                if (diff.dataDiff?.removed?.isNotEmpty() == true) {
                    appendLine("-- Change: Restore deleted rows (Rollback Delete)")
                    diff.dataDiff?.removed?.forEach { row ->
                        appendLine(buildInsertStatement(diff.tableName, row, diff.primaryKeys, diff.treeConfig) + ";")
                    }
                }
                return@buildString
            }
            diff.targetDDL != null -> {
                appendLine("-- Change: Drop table ${diff.tableName} (Rollback Create)")
                appendLine("DROP TABLE IF EXISTS `${diff.tableName}`;")
                appendLine()
                return@buildString
            }
        }

        diff.structDiff?.let { struct ->
            struct.indexes.filter { it.status == DiffStatus.ADDED }.forEach { item ->
                val idx = item.target!!
                appendLine("-- Change: Drop index ${idx.name} (Rollback Add)")
                appendLine(buildSafeDropIndexSql(diff.tableName, idx.name!!))
            }
            struct.columns.filter { it.status == DiffStatus.ADDED }.forEach { item ->
                val col = item.target!!
                appendLine("-- Change: Drop column ${col.name} (Rollback Add)")
                appendLine(buildSafeDropColumnSql(diff.tableName, col.name!!))
            }
            struct.columns.filter { it.status == DiffStatus.REMOVED }.forEach { item ->
                val col = item.source!!
                appendLine("-- Change: Restore column ${col.name} (Rollback Drop)")
                appendLine(buildSafeAddColumnSql(diff.tableName, col))
            }
            struct.columns.filter { it.status == DiffStatus.MODIFIED }.forEach { item ->
                val col = item.source!!
                appendLine("-- Change: Restore column ${col.name} type (Rollback Modify)")
                appendLine("ALTER TABLE `${diff.tableName}` MODIFY COLUMN ${formatColumnDefinition(col)};")
            }
            struct.indexes.filter { it.status == DiffStatus.REMOVED }.forEach { item ->
                val idx = item.source!!
                appendLine("-- Change: Restore index ${idx.name} (Rollback Drop)")
                appendLine(buildSafeAddIndexSql(diff.tableName, idx))
            }
            struct.indexes.filter { it.status == DiffStatus.MODIFIED }.forEach { item ->
                val oldIdx = item.target!!
                val newIdx = item.source!!
                appendLine("-- Change: Restore index ${newIdx.name} (Rollback Modify)")
                appendLine(buildSafeDropIndexSql(diff.tableName, oldIdx.name!!))
                appendLine(buildSafeAddIndexSql(diff.tableName, newIdx))
            }
        }

        diff.dataDiff?.let { data ->
            if (!data.added.isNullOrEmpty()) {
                appendLine("-- Change: Remove inserted rows (Rollback Insert)")
                data.added!!.forEach { row ->
                    val pkClause = buildWhereClause(row, diff.primaryKeys)
                    if (pkClause.isNotEmpty()) appendLine("DELETE FROM `${diff.tableName}` WHERE $pkClause;")
                }
            }
            if (!data.removed.isNullOrEmpty()) {
                appendLine("-- Change: Restore deleted rows (Rollback Delete)")
                data.removed!!.forEach { row ->
                    appendLine(buildInsertStatement(diff.tableName, row, diff.primaryKeys, diff.treeConfig) + ";")
                }
            }
            if (!data.modified.isNullOrEmpty()) {
                appendLine("-- Change: Revert row updates (Rollback Update)")
                data.modified!!.forEach { row ->
                    val pkClause = buildWhereClause(row, diff.primaryKeys)
                    val updates = buildUpdateSet(row)
                    if (pkClause.isNotEmpty() && updates.isNotEmpty()) {
                        appendLine("UPDATE `${diff.tableName}` SET $updates WHERE $pkClause;")
                    }
                }
            }
        }
    }

    private fun formatColumnDefinition(col: Column): String {
        val type = col.typeName ?: "VARCHAR"
        val sizeStr = when (type.uppercase()) {
            "VARCHAR", "CHAR", "BINARY", "VARBINARY" -> "(${col.columnSize})"
            "DECIMAL", "NUMERIC" -> "(${col.columnSize},${col.decimalDigits})"
            "DATETIME", "TIMESTAMP" -> if (col.columnSize > 19) "(${col.columnSize - 20})" else ""
            "BIGINT" -> if (col.columnSize != 19) "(${col.columnSize})" else ""
            "INT", "INTEGER" -> if (col.columnSize != 10) "(${col.columnSize})" else ""
            else -> ""
        }
        val nullStr = if (col.isNullable) "NULL" else "NOT NULL"
        val defStr = if (col.defaultValue != null) {
            when {
                "VARCHAR".equals(type, ignoreCase = true) -> """DEFAULT "${col.defaultValue}""""
                "BIT".equals(type, ignoreCase = true) -> {
                    val defVal = col.defaultValue ?: ""
                    if (defVal.matches(Regex("^b'[01]+'$", RegexOption.IGNORE_CASE))) "DEFAULT ${defVal.replace("'", "''")}"
                    else "DEFAULT $defVal"
                }
                else -> "DEFAULT ${col.defaultValue}"
            }
        } else ""
        val autoStr = if (col.isAutoIncrement) "AUTO_INCREMENT" else ""

        return "`${col.name}` $type$sizeStr $nullStr $defStr $autoStr"
    }

    private fun buildWhereClause(row: Map<String, Any?>, pks: List<String>?): String {
        if (pks.isNullOrEmpty()) return ""
        return pks.joinToString(" AND ") { pk ->
            val valObj = row[pk] ?: row.entries.find { it.key.equals(pk, ignoreCase = true) }?.value
            if (valObj == null) "`$pk` IS NULL" else "`$pk` = ${formatValue(valObj)}"
        }
    }

    private fun buildInsertStatement(
        tableName: String, row: Map<String, Any?>, pks: List<String>?,
        treeConfig: TableDiff.TreeConfig? = null
    ): String {
        val validEntries = row.entries.filter {
            it.key != "_source" && (treeConfig == null || !it.key.startsWith("__parent_"))
        }

        if (treeConfig != null) {
            val parentBusinessKeys = treeConfig.parentBusinessKeys
            val isRoot = parentBusinessKeys.any { pk -> row["__parent_$pk"] == null }

            if (!isRoot) {
                val cols = validEntries.joinToString(", ") { "`${it.key}`" } + ", `${treeConfig.parentIdColumn}`"
                val selectVals = validEntries.joinToString(", ") { formatValue(it.value) } + ", `${treeConfig.idColumn}`"
                val lookupWhere = parentBusinessKeys.joinToString(" AND ") { pk ->
                    "`$pk` = ${formatValue(row["__parent_$pk"])}"
                }

                var whereClause = lookupWhere
                if (!pks.isNullOrEmpty()) {
                    val pkWhere = buildWhereClause(row, pks)
                    if (pkWhere.isNotEmpty()) {
                        whereClause += " AND NOT EXISTS (SELECT 1 FROM `$tableName` WHERE $pkWhere)"
                    }
                }
                return "INSERT INTO `$tableName` ($cols) SELECT $selectVals FROM `$tableName` WHERE $whereClause"
            }
        }

        val cols = validEntries.joinToString(", ") { "`${it.key}`" }
        val vals = validEntries.joinToString(", ") { formatValue(it.value) }

        if (!pks.isNullOrEmpty()) {
            val whereClause = buildWhereClause(row, pks)
            if (whereClause.isNotEmpty()) {
                return "INSERT INTO `$tableName` ($cols) SELECT $vals FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `$tableName` WHERE $whereClause)"
            }
        }
        return "INSERT IGNORE INTO `$tableName` ($cols) VALUES ($vals)"
    }

    private fun makeCreateTableSafe(ddl: String): String =
        ddl.replaceFirst("CREATE TABLE", "CREATE TABLE IF NOT EXISTS", ignoreCase = true)

    private fun buildSafeAddColumnSql(tableName: String, col: Column): String {
        val colDef = formatColumnDefinition(col)
        return """
            SET @dbname = DATABASE();
            SET @tablename = '$tableName';
            SET @columnname = '${col.name}';
            SET @preparedStatement = (SELECT IF(
              (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE
                (table_name = @tablename) AND (table_schema = @dbname) AND (column_name = @columnname)
              ) > 0, 'SELECT 1', 'ALTER TABLE `$tableName` ADD COLUMN $colDef'));
            PREPARE stmt FROM @preparedStatement;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        """.trimIndent()
    }

    private fun buildSafeDropColumnSql(tableName: String, colName: String): String = """
        SET @dbname = DATABASE();
        SET @tablename = '$tableName';
        SET @columnname = '$colName';
        SET @preparedStatement = (SELECT IF(
          (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE
            (table_name = @tablename) AND (table_schema = @dbname) AND (column_name = @columnname)
          ) > 0, 'ALTER TABLE `$tableName` DROP COLUMN `$colName`', 'SELECT 1'));
        PREPARE stmt FROM @preparedStatement;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    """.trimIndent()

    private fun buildSafeAddIndexSql(tableName: String, index: IndexInfo): String {
        val indexName = index.name
        val cols = index.columns?.joinToString(", ") { "`$it`" } ?: ""
        val unique = if (index.type == "UNIQUE") "UNIQUE " else ""

        if ("PRIMARY".equals(indexName, ignoreCase = true)) {
            return """
                SET @dbname = DATABASE();
                SET @tablename = '$tableName';
                SET @preparedStatement = (SELECT IF(
                  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS WHERE
                    (table_name = @tablename) AND (table_schema = @dbname) AND (index_name = 'PRIMARY')
                  ) > 0, 'SELECT 1', 'ALTER TABLE `$tableName` ADD PRIMARY KEY ($cols)'));
                PREPARE stmt FROM @preparedStatement;
                EXECUTE stmt;
                DEALLOCATE PREPARE stmt;
            """.trimIndent()
        }

        return """
            SET @dbname = DATABASE();
            SET @tablename = '$tableName';
            SET @indexname = '$indexName';
            SET @preparedStatement = (SELECT IF(
              (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS WHERE
                (table_name = @tablename) AND (table_schema = @dbname) AND (index_name = @indexname)
              ) > 0, 'SELECT 1', 'ALTER TABLE `$tableName` ADD ${unique}INDEX `$indexName` ($cols)'));
            PREPARE stmt FROM @preparedStatement;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        """.trimIndent()
    }

    private fun buildSafeDropIndexSql(tableName: String, indexName: String): String {
        if ("PRIMARY".equals(indexName, ignoreCase = true)) {
            return """
                SET @dbname = DATABASE();
                SET @tablename = '$tableName';
                SET @preparedStatement = (SELECT IF(
                  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS WHERE
                    (table_name = @tablename) AND (table_schema = @dbname) AND (index_name = 'PRIMARY')
                  ) > 0, 'ALTER TABLE `$tableName` DROP PRIMARY KEY', 'SELECT 1'));
                PREPARE stmt FROM @preparedStatement;
                EXECUTE stmt;
                DEALLOCATE PREPARE stmt;
            """.trimIndent()
        }

        return """
            SET @dbname = DATABASE();
            SET @tablename = '$tableName';
            SET @indexname = '$indexName';
            SET @preparedStatement = (SELECT IF(
              (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS WHERE
                (table_name = @tablename) AND (table_schema = @dbname) AND (index_name = @indexname)
              ) > 0, 'ALTER TABLE `$tableName` DROP INDEX `$indexName`', 'SELECT 1'));
            PREPARE stmt FROM @preparedStatement;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        """.trimIndent()
    }

    private fun buildUpdateSet(row: Map<String, Any?>): String {
        @Suppress("UNCHECKED_CAST")
        val sourceRow = row["_source"] as? Map<String, Any?> ?: return ""
        return sourceRow.entries
            .filter { (k, sourceVal) -> k != "_source" && sourceVal.toString() != row[k].toString() }
            .joinToString(", ") { (k, sourceVal) -> "`$k` = ${formatValue(sourceVal)}" }
    }

    private fun buildUpdateSetForRollback(row: Map<String, Any?>): String {
        @Suppress("UNCHECKED_CAST")
        val sourceRow = row["_source"] as? Map<String, Any?> ?: return ""
        return sourceRow.entries
            .filter { (k, sourceVal) -> k != "_source" && sourceVal.toString() != row[k].toString() }
            .joinToString(", ") { (k, _) -> "`$k` = ${formatValue(row[k])}" }
    }

    private fun sortTreeData(
        data: List<Map<String, Any?>>, treeConfig: TableDiff.TreeConfig
    ): List<Map<String, Any?>> {
        if (data.isEmpty()) return data

        val parentBusinessKeys = treeConfig.parentBusinessKeys

        class Node(val row: Map<String, Any?>) {
            val children = ArrayList<Node>()
        }

        val nodeMap = HashMap<String, Node>()

        fun getKey(row: Map<String, Any?>, keys: List<String>, prefix: String = ""): String =
            keys.joinToString("||") { pk ->
                val col = if (prefix.isNotEmpty()) "${prefix}$pk" else pk
                row[col]?.toString() ?: "NULL"
            }

        data.forEach { row -> nodeMap[getKey(row, parentBusinessKeys)] = Node(row) }

        val roots = ArrayList<Node>()
        nodeMap.values.forEach { node ->
            val parentKey = getKey(node.row, parentBusinessKeys, "__parent_")
            val isParentNull = parentBusinessKeys.any { pk -> node.row["__parent_$pk"] == null }
            if (isParentNull) {
                roots.add(node)
            } else {
                val parentNode = nodeMap[parentKey]
                if (parentNode != null) parentNode.children.add(node) else roots.add(node)
            }
        }

        val sortedList = ArrayList<Map<String, Any?>>()
        fun dfs(node: Node) {
            sortedList.add(node.row)
            node.children.forEach { dfs(it) }
        }
        roots.forEach { dfs(it) }

        if (sortedList.size < data.size) {
            val processedSet = sortedList.map { getKey(it, parentBusinessKeys) }.toSet()
            data.forEach { row -> if (!processedSet.contains(getKey(row, parentBusinessKeys))) sortedList.add(row) }
        }

        return sortedList
    }

    private fun formatValue(v: Any?): String = when {
        v == null -> "NULL"
        v is Number -> v.toString()
        v is Boolean -> if (v) "1" else "0"
        else -> "'${v.toString().replace("'", "''").replace("\\", "\\\\")}'"
    }
}
