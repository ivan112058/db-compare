package com.zxqj.dbcompare.service

import com.zxqj.dbcompare.model.TableDiff
import com.zxqj.dbcompare.model.structure.Column
import com.zxqj.dbcompare.model.structure.IndexInfo
import com.zxqj.dbcompare.model.structure.DiffStatus
import org.springframework.stereotype.Service
import java.util.stream.Collectors

@Service
class SqlGenerationService {

    fun generateFullUpgradeScript(diffs: List<TableDiff>): String {
        val sb = StringBuilder()

        sb.append("SET NAMES utf8mb4;").append("\n")
        sb.append("SET FOREIGN_KEY_CHECKS = 0;").append("\n").append("\n")
        
        // 1. Process Drop Tables first (Source exists, Target missing)
        diffs.filter { it.sourceDDL != null }.forEach { diff ->
            val sql = generateUpgradeSql(diff)
            if (sql.isNotBlank()) sb.append(sql).append("\n")
        }
        
        // 2. Process other changes (Creates and Modifies)
        diffs.filter { it.sourceDDL == null }.forEach { diff ->
             val sql = generateUpgradeSql(diff)
             if (sql.isNotBlank()) sb.append(sql).append("\n")
        }

        sb.append("SET FOREIGN_KEY_CHECKS = 1;")

        return sb.toString()
    }

    fun generateFullRollbackScript(diffs: List<TableDiff>): String {
        val sb = StringBuilder()

        sb.append("SET NAMES utf8mb4;").append("\n")
        sb.append("SET FOREIGN_KEY_CHECKS = 0;").append("\n").append("\n")

        // 1. Process Drop Tables first (Target exists, Source missing -> Created in Upgrade -> Drop in Rollback)
        diffs.filter { it.targetDDL != null }.forEach { diff ->
            val sql = generateRollbackSql(diff)
            if (sql.isNotBlank()) sb.append(sql).append("\n")
        }
        
        // 2. Process other changes (Creates and Modifies)
        diffs.filter { it.targetDDL == null }.forEach { diff ->
             val sql = generateRollbackSql(diff)
             if (sql.isNotBlank()) sb.append(sql).append("\n")
        }

        sb.append("SET FOREIGN_KEY_CHECKS = 1;")

        return sb.toString()
    }

    fun generateUpgradeSql(diff: TableDiff): String {
        val sb = StringBuilder()
        sb.append("-- Table: ${diff.tableName}\n")

        // 1. Missing Table
        if (diff.sourceDDL != null) {
            // Exists in Source, Missing in Target -> DROP TABLE (Upgrade Source to match Target)
            sb.append("-- Change: Drop table ${diff.tableName}\n")
            sb.append("DROP TABLE IF EXISTS `${diff.tableName}`;\n\n")
            return sb.toString() 
        } else if (diff.targetDDL != null) {
            // Missing in Source, Exists in Target -> CREATE TABLE (Upgrade Source to match Target)
            sb.append("-- Change: Create table ${diff.tableName}\n")
            sb.append(makeCreateTableSafe(diff.targetDDL!!)).append(";\n\n")
            // Since we created from Target DDL, we also need to insert data if it exists in Target
            if (diff.dataDiff?.added?.isNotEmpty() == true) {
                sb.append("-- Change: Insert new rows\n")
                diff.dataDiff?.added?.forEach { row ->
                    sb.append(buildInsertStatement(diff.tableName, row, diff.primaryKeys)).append(";\n")
                }
            }
            return sb.toString()
        }

        // 2. Structure Diff
        diff.structDiff?.let { struct ->
            // Order: Drop Indexes -> Drop Columns -> Add/Modify Columns -> Add/Modify Indexes
            
            // REMOVED Indexes in Source -> DROP INDEX
            struct.indexes.filter { it.status == DiffStatus.REMOVED }.forEach { item ->
                val idx = item.source!!
                sb.append("-- Change: Drop index ${idx.name}\n")
                sb.append(buildSafeDropIndexSql(diff.tableName, idx.name!!)).append("\n")
            }

            // REMOVED Columns in Source (Source has, Target doesn't) -> DROP COLUMN
            struct.columns.filter { it.status == DiffStatus.REMOVED }.forEach { item ->
                val col = item.source!!
                sb.append("-- Change: Drop column ${col.name}\n")
                sb.append(buildSafeDropColumnSql(diff.tableName, col.name!!)).append("\n")
            }

            // ADDED Columns in Target (Target has, Source doesn't) -> ADD COLUMN
            struct.columns.filter { it.status == DiffStatus.ADDED }.forEach { item ->
                 val col = item.target!!
                 sb.append("-- Change: Add column ${col.name}\n")
                 sb.append(buildSafeAddColumnSql(diff.tableName, col)).append("\n")
            }

            // MODIFIED Columns (Make Source match Target)
            struct.columns.filter { it.status == DiffStatus.MODIFIED }.forEach { item ->
                val col = item.target!!
                sb.append("-- Change: Modify column ${col.name}\n")
                sb.append("ALTER TABLE `${diff.tableName}` MODIFY COLUMN ${formatColumnDefinition(col)};\n")
            }

            // ADDED Indexes in Target -> ADD INDEX
            struct.indexes.filter { it.status == DiffStatus.ADDED }.forEach { item ->
                val idx = item.target!!
                sb.append("-- Change: Add index ${idx.name}\n")
                sb.append(buildSafeAddIndexSql(diff.tableName, idx)).append("\n")
            }

            // MODIFIED Indexes -> DROP then ADD
            struct.indexes.filter { it.status == DiffStatus.MODIFIED }.forEach { item ->
                val oldIdx = item.source!!
                val newIdx = item.target!!
                sb.append("-- Change: Modify index ${oldIdx.name}\n")
                sb.append(buildSafeDropIndexSql(diff.tableName, oldIdx.name!!)).append("\n")
                sb.append(buildSafeAddIndexSql(diff.tableName, newIdx)).append("\n")
            }
        }

        // 3. Data Diff
        diff.dataDiff?.let { data ->
            // Order: Delete -> Insert -> Update

            // Removed in Source (Source has, Target doesn't) -> DELETE from Source
            if (!data.removed.isNullOrEmpty()) {
                sb.append("-- Change: Delete removed rows\n")
                data.removed!!.forEach { row ->
                    val pkClause = buildWhereClause(row, diff.primaryKeys)
                    if (pkClause.isNotEmpty()) {
                        sb.append("DELETE FROM `${diff.tableName}` WHERE $pkClause;\n")
                    }
                }
            }

            // Added in Target -> INSERT into Source
            if (!data.added.isNullOrEmpty()) {
                sb.append("-- Change: Insert new rows\n")
                data.added!!.forEach { row ->
                    sb.append(buildInsertStatement(diff.tableName, row, diff.primaryKeys)).append(";\n")
                }
            }

            // Modified -> UPDATE Source to match Target
            if (!data.modified.isNullOrEmpty()) {
                sb.append("-- Change: Update rows\n")
                data.modified!!.forEach { row ->
                    val pkClause = buildWhereClause(row, diff.primaryKeys)
                    val updates = buildUpdateSetForRollback(row) // Use Target values (which are in 'row' directly)
                    if (pkClause.isNotEmpty() && updates.isNotEmpty()) {
                        sb.append("UPDATE `${diff.tableName}` SET $updates WHERE $pkClause;\n")
                    }
                }
            }
        }
        
        return sb.toString()
    }

    fun generateRollbackSql(diff: TableDiff): String {
         val sb = StringBuilder()
         sb.append("-- Rollback for Table: ${diff.tableName}\n")

         if (diff.sourceDDL != null) {
             // We Dropped it in Upgrade. Rollback = CREATE.
             sb.append("-- Change: Restore table ${diff.tableName} (Rollback Drop)\n")
             sb.append(makeCreateTableSafe(diff.sourceDDL!!)).append(";\n\n")
             // Since we restore from Source DDL, we also need to restore data if it was in Source
             if (diff.dataDiff?.removed?.isNotEmpty() == true) {
                 sb.append("-- Change: Restore deleted rows (Rollback Delete)\n")
                 diff.dataDiff?.removed?.forEach { row ->
                     sb.append(buildInsertStatement(diff.tableName, row, diff.primaryKeys)).append(";\n")
                 }
             }
             return sb.toString()
         } else if (diff.targetDDL != null) {
             // We Created it in Upgrade. Rollback = DROP.
             sb.append("-- Change: Drop table ${diff.tableName} (Rollback Create)\n")
             sb.append("DROP TABLE IF EXISTS `${diff.tableName}`;\n\n")
             return sb.toString()
         }

         // Structure
         diff.structDiff?.let { struct ->
             // Order: Drop Indexes (Upgrade Added) -> Drop Columns (Upgrade Added) -> Restore Columns -> Restore Indexes
             
             // ADDED Indexes (In Target). Upgrade added it. Rollback drops it.
             struct.indexes.filter { it.status == DiffStatus.ADDED }.forEach { item ->
                 val idx = item.target!!
                 sb.append("-- Change: Drop index ${idx.name} (Rollback Add)\n")
                 sb.append(buildSafeDropIndexSql(diff.tableName, idx.name!!)).append("\n")
             }

             // ADDED Columns (In Target). Upgrade added it. Rollback drops it.
             struct.columns.filter { it.status == DiffStatus.ADDED }.forEach { item ->
                 val col = item.target!!
                 sb.append("-- Change: Drop column ${col.name} (Rollback Add)\n")
                 sb.append(buildSafeDropColumnSql(diff.tableName, col.name!!)).append("\n")
             }

             // REMOVED Columns (In Source). Upgrade dropped it. Rollback adds it back.
             struct.columns.filter { it.status == DiffStatus.REMOVED }.forEach { item ->
                 val col = item.source!!
                 sb.append("-- Change: Restore column ${col.name} (Rollback Drop)\n")
                 sb.append(buildSafeAddColumnSql(diff.tableName, col)).append("\n")
             }

             // MODIFIED Columns. Rollback makes it like Source.
             struct.columns.filter { it.status == DiffStatus.MODIFIED }.forEach { item ->
                 val col = item.source!!
                 sb.append("-- Change: Restore column ${col.name} type (Rollback Modify)\n")
                 sb.append("ALTER TABLE `${diff.tableName}` MODIFY COLUMN ${formatColumnDefinition(col)};\n")
             }

             // REMOVED Indexes (In Source). Upgrade dropped it. Rollback adds it back.
             struct.indexes.filter { it.status == DiffStatus.REMOVED }.forEach { item ->
                 val idx = item.source!!
                 sb.append("-- Change: Restore index ${idx.name} (Rollback Drop)\n")
                 sb.append(buildSafeAddIndexSql(diff.tableName, idx)).append("\n")
             }

             // MODIFIED Indexes. Rollback makes it like Source.
             struct.indexes.filter { it.status == DiffStatus.MODIFIED }.forEach { item ->
                 val oldIdx = item.target!!
                 val newIdx = item.source!!
                 sb.append("-- Change: Restore index ${newIdx.name} (Rollback Modify)\n")
                 sb.append(buildSafeDropIndexSql(diff.tableName, oldIdx.name!!)).append("\n")
                 sb.append(buildSafeAddIndexSql(diff.tableName, newIdx)).append("\n")
             }
         }

         // Data
         diff.dataDiff?.let { data ->
             // Order: Delete (Upgrade Inserted) -> Insert (Upgrade Deleted) -> Update
             
             // ADDED (In Target). Upgrade inserted them. Rollback deletes them.
             if (!data.added.isNullOrEmpty()) {
                 sb.append("-- Change: Remove inserted rows (Rollback Insert)\n")
                 data.added!!.forEach { row ->
                     val pkClause = buildWhereClause(row, diff.primaryKeys)
                     if (pkClause.isNotEmpty()) {
                         sb.append("DELETE FROM `${diff.tableName}` WHERE $pkClause;\n")
                     }
                 }
             }

             // REMOVED (In Source). Upgrade deleted them. Rollback inserts them back.
             if (!data.removed.isNullOrEmpty()) {
                 sb.append("-- Change: Restore deleted rows (Rollback Delete)\n")
                 data.removed!!.forEach { row ->
                     sb.append(buildInsertStatement(diff.tableName, row, diff.primaryKeys)).append(";\n")
                 }
             }

             // MODIFIED. Rollback updates back to Source values.
             if (!data.modified.isNullOrEmpty()) {
                 sb.append("-- Change: Revert row updates (Rollback Update)\n")
                 data.modified!!.forEach { row ->
                     val pkClause = buildWhereClause(row, diff.primaryKeys)
                     val updates = buildUpdateSet(row) // Use Source values (which are in _source)
                     if (pkClause.isNotEmpty() && updates.isNotEmpty()) {
                         sb.append("UPDATE `${diff.tableName}` SET $updates WHERE $pkClause;\n")
                     }
                 }
             }
         }
         
         return sb.toString()
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
        // Handle CURRENT_TIMESTAMP
        val defStr = if (col.defaultValue != null) {
            if ("VARCHAR".equals(type.uppercase())) {
                "DEFAULT \"${col.defaultValue}\""
            } else {
                "DEFAULT ${col.defaultValue}"
            }
        } else "" 
        val autoStr = if (col.isAutoIncrement) "AUTO_INCREMENT" else ""

        return "`${col.name}` $type$sizeStr $nullStr $defStr $autoStr"
    }

    private fun buildWhereClause(row: Map<String, Any?>, pks: List<String>?): String {
        if (pks.isNullOrEmpty()) return "" 
        return pks.joinToString(" AND ") { pk ->
            val valObj = row[pk] ?: row.entries.find { it.key.equals(pk, ignoreCase = true) }?.value
            if (valObj == null) "`$pk` IS NULL"
            else "`$pk` = ${formatValue(valObj)}"
        }
    }

    private fun buildInsertStatement(tableName: String, row: Map<String, Any?>, pks: List<String>?): String {
        val validEntries = row.entries.filter { it.key != "_source" }
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

    private fun makeCreateTableSafe(ddl: String): String {
        // Simple heuristic: insert IF NOT EXISTS after CREATE TABLE
        return ddl.replaceFirst("CREATE TABLE", "CREATE TABLE IF NOT EXISTS", true)
    }

    private fun buildSafeAddColumnSql(tableName: String, col: Column): String {
        // Construct a safe ADD COLUMN using stored procedure logic in a block
        // Note: Using PREPARE statement pattern as it's more portable in scripts than defining stored procs
        val colDef = formatColumnDefinition(col)
        val colName = col.name
        
        return """
            SET @dbname = DATABASE();
            SET @tablename = '$tableName';
            SET @columnname = '$colName';
            SET @preparedStatement = (SELECT IF(
              (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE
                (table_name = @tablename)
                AND (table_schema = @dbname)
                AND (column_name = @columnname)
              ) > 0,
              'SELECT 1',
              'ALTER TABLE `$tableName` ADD COLUMN $colDef'
            ));
            PREPARE stmt FROM @preparedStatement;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        """.trimIndent()
    }

    private fun buildSafeDropColumnSql(tableName: String, colName: String): String {
        return """
            SET @dbname = DATABASE();
            SET @tablename = '$tableName';
            SET @columnname = '$colName';
            SET @preparedStatement = (SELECT IF(
              (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE
                (table_name = @tablename)
                AND (table_schema = @dbname)
                AND (column_name = @columnname)
              ) > 0,
              'ALTER TABLE `$tableName` DROP COLUMN `$colName`',
              'SELECT 1'
            ));
            PREPARE stmt FROM @preparedStatement;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        """.trimIndent()
    }

    private fun buildSafeAddIndexSql(tableName: String, index: IndexInfo): String {
        val indexName = index.name
        val cols = index.columns?.joinToString(", ") { "`$it`" } ?: ""
        val unique = if (index.type == "UNIQUE") "UNIQUE " else ""
        
        // Handle Primary Key
        if ("PRIMARY".equals(indexName, ignoreCase = true)) {
             return """
                SET @dbname = DATABASE();
                SET @tablename = '$tableName';
                SET @preparedStatement = (SELECT IF(
                  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS WHERE
                    (table_name = @tablename)
                    AND (table_schema = @dbname)
                    AND (index_name = 'PRIMARY')
                  ) > 0,
                  'SELECT 1',
                  'ALTER TABLE `$tableName` ADD PRIMARY KEY ($cols)'
                ));
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
                (table_name = @tablename)
                AND (table_schema = @dbname)
                AND (index_name = @indexname)
              ) > 0,
              'SELECT 1',
              'ALTER TABLE `$tableName` ADD ${unique}INDEX `$indexName` ($cols)'
            ));
            PREPARE stmt FROM @preparedStatement;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        """.trimIndent()
    }

    private fun buildSafeDropIndexSql(tableName: String, indexName: String): String {
        // Handle Primary Key
        if ("PRIMARY".equals(indexName, ignoreCase = true)) {
             return """
                SET @dbname = DATABASE();
                SET @tablename = '$tableName';
                SET @preparedStatement = (SELECT IF(
                  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS WHERE
                    (table_name = @tablename)
                    AND (table_schema = @dbname)
                    AND (index_name = 'PRIMARY')
                  ) > 0,
                  'ALTER TABLE `$tableName` DROP PRIMARY KEY',
                  'SELECT 1'
                ));
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
                (table_name = @tablename)
                AND (table_schema = @dbname)
                AND (index_name = @indexname)
              ) > 0,
              'ALTER TABLE `$tableName` DROP INDEX `$indexName`',
              'SELECT 1'
            ));
            PREPARE stmt FROM @preparedStatement;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        """.trimIndent()
    }

    private fun buildUpdateSet(row: Map<String, Any?>): String {
        @Suppress("UNCHECKED_CAST")
        val sourceRow = row["_source"] as? Map<String, Any?> ?: return ""
        val updates = ArrayList<String>()
        
        for ((k, sourceVal) in sourceRow) {
            val targetVal = row[k]
            if (sourceVal.toString() != targetVal.toString() && k != "_source") {
                updates.add("`$k` = ${formatValue(sourceVal)}")
            }
        }
        return updates.joinToString(", ")
    }

    private fun buildUpdateSetForRollback(row: Map<String, Any?>): String {
        @Suppress("UNCHECKED_CAST")
        val sourceRow = row["_source"] as? Map<String, Any?> ?: return ""
        val updates = ArrayList<String>()

        for ((k, sourceVal) in sourceRow) {
            val targetVal = row[k]
            if (sourceVal.toString() != targetVal.toString() && k != "_source") {
                updates.add("`$k` = ${formatValue(targetVal)}")
            }
        }
        return updates.joinToString(", ")
    }

    private fun formatValue(v: Any?): String {
        if (v == null) return "NULL"
        if (v is Number) return v.toString()
        if (v is Boolean) return if (v) "1" else "0"
        val s = v.toString().replace("'", "''").replace("\\", "\\\\")
        return "'$s'"
    }
}
