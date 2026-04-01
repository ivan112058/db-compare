package com.zxqj.dbcompare.service

import com.zxqj.dbcompare.model.DbConfig
import com.zxqj.dbcompare.model.structure.Column
import com.zxqj.dbcompare.model.structure.Index
import com.zxqj.dbcompare.model.structure.PrimaryKey
import com.zxqj.dbcompare.model.structure.TableStructure
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.TreeMap

class DatabaseService {

    @Throws(SQLException::class)
    fun connect(config: DbConfig): Connection =
        DriverManager.getConnection(config.jdbcUrl, config.username, config.password)

    @Throws(SQLException::class)
    fun getTableNames(conn: Connection, databaseName: String?): List<String> {
        val sql = "SELECT table_name FROM information_schema.tables WHERE table_schema = ? ORDER BY table_name"
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, databaseName)
            stmt.executeQuery().use { rs ->
                return buildList {
                    while (rs.next()) add(rs.getString("table_name"))
                }
            }
        }
    }

    @Throws(SQLException::class)
    fun getTableStructure(conn: Connection, tableName: String): TableStructure {
        val structure = TableStructure(tableName)
        val meta = conn.metaData
        val catalog = conn.catalog

        // Columns
        meta.getColumns(catalog, null, tableName, null).use { rs ->
            while (rs.next()) {
                val col = Column(
                    name = rs.getString("COLUMN_NAME"),
                    typeName = rs.getString("TYPE_NAME"),
                    columnSize = rs.getInt("COLUMN_SIZE"),
                    decimalDigits = rs.getInt("DECIMAL_DIGITS"),
                    isNullable = "YES" == rs.getString("IS_NULLABLE"),
                    isAutoIncrement = try { "YES" == rs.getString("IS_AUTOINCREMENT") } catch (_: SQLException) { false },
                    defaultValue = rs.getString("COLUMN_DEF")
                )
                structure.addColumn(col)
            }
        }

        // Primary Key
        val pkColMap = TreeMap<Int, String>()
        var pkName: String? = null
        meta.getPrimaryKeys(catalog, null, tableName).use { rs ->
            while (rs.next()) {
                pkName = rs.getString("PK_NAME")
                pkColMap[rs.getShort("KEY_SEQ").toInt()] = rs.getString("COLUMN_NAME")
            }
        }
        if (pkColMap.isNotEmpty()) {
            structure.primaryKey = PrimaryKey(pkName, pkColMap.values.toList())
        }

        // Indexes
        val indexCols = HashMap<String, TreeMap<Int, String>>()
        val indexUnique = HashMap<String, Boolean>()

        meta.getIndexInfo(catalog, null, tableName, false, false).use { rs ->
            while (rs.next()) {
                val indexName = rs.getString("INDEX_NAME") ?: continue
                indexUnique[indexName] = !rs.getBoolean("NON_UNIQUE")
                indexCols.getOrPut(indexName) { TreeMap() }[rs.getShort("ORDINAL_POSITION").toInt()] = rs.getString("COLUMN_NAME")
            }
        }

        for ((name, value) in indexCols) {
            if (structure.primaryKey != null && "PRIMARY" == name) continue
            structure.addIndex(Index(name, indexUnique[name] ?: false, value.values.toList()))
        }

        return structure
    }

    @Throws(SQLException::class)
    fun getRowCount(conn: Connection, tableName: String): Int {
        conn.createStatement().use { stmt ->
            stmt.executeQuery("SELECT COUNT(*) FROM $tableName").use { rs ->
                return if (rs.next()) rs.getInt(1) else 0
            }
        }
    }

    @Throws(SQLException::class)
    fun getTableData(conn: Connection, tableName: String, customQuery: String? = null): List<Map<String, Any?>> {
        val sql = customQuery ?: "SELECT * FROM $tableName"
        conn.createStatement().use { stmt ->
            stmt.executeQuery(sql).use { rs ->
                val meta = rs.metaData
                val columnCount = meta.columnCount
                return buildList {
                    while (rs.next()) {
                        val row = LinkedHashMap<String, Any?>()
                        for (i in 1..columnCount) {
                            row[meta.getColumnLabel(i)] = rs.getObject(i)
                        }
                        add(row)
                    }
                }
            }
        }
    }

    @Throws(SQLException::class)
    fun getCreateTableSql(conn: Connection, tableName: String): String {
        conn.createStatement().use { stmt ->
            stmt.executeQuery("SHOW CREATE TABLE $tableName").use { rs ->
                return if (rs.next()) rs.getString(2) else ""
            }
        }
    }
}
