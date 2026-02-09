package com.zxqj.dbcompare.service

import com.zxqj.dbcompare.model.DbConfig
import com.zxqj.dbcompare.model.structure.Column
import com.zxqj.dbcompare.model.structure.Index
import com.zxqj.dbcompare.model.structure.PrimaryKey
import com.zxqj.dbcompare.model.structure.TableStructure
import org.springframework.stereotype.Service
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.*

@Service
class DatabaseService {

    @Throws(SQLException::class)
    fun connect(config: DbConfig): Connection {
        return DriverManager.getConnection(config.jdbcUrl, config.username, config.password)
    }

    @Throws(SQLException::class)
    fun getTableNames(conn: Connection, databaseName: String?): List<String> {
        val tables = ArrayList<String>()
        val sql = "SELECT table_name FROM information_schema.tables WHERE table_schema = ? ORDER BY table_name"
        
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, databaseName)
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    tables.add(rs.getString("table_name"))
                }
            }
        }
        return tables
    }

    @Throws(SQLException::class)
    fun getTableStructure(conn: Connection, tableName: String): TableStructure {
        val structure = TableStructure(tableName)
        val meta = conn.metaData
        val catalog = conn.catalog
        val schema: String? = null

        // 1. Columns
        meta.getColumns(catalog, schema, tableName, null).use { rs ->
            while (rs.next()) {
                val name = rs.getString("COLUMN_NAME")
                val typeName = rs.getString("TYPE_NAME")
                val size = rs.getInt("COLUMN_SIZE")
                val digits = rs.getInt("DECIMAL_DIGITS")
                val isNullable = "YES" == rs.getString("IS_NULLABLE")
                var def = rs.getString("COLUMN_DEF")
                var isAuto = false
                try {
                    isAuto = "YES" == rs.getString("IS_AUTOINCREMENT")
                } catch (e: SQLException) {
                    // Ignore if column not found
                }

                val col = Column(name, typeName, size, digits, isNullable, isAuto, def)
                structure.addColumn(col)
            }
        }

        // 2. Primary Key
        var pkName: String? = null
        val pkColMap = TreeMap<Int, String>()

        meta.getPrimaryKeys(catalog, schema, tableName).use { rs ->
            while (rs.next()) {
                pkName = rs.getString("PK_NAME")
                val colName = rs.getString("COLUMN_NAME")
                val seq = rs.getShort("KEY_SEQ").toInt()
                pkColMap[seq] = colName
            }
        }
        if (pkColMap.isNotEmpty()) {
            structure.primaryKey = PrimaryKey(pkName, ArrayList(pkColMap.values))
        }

        // 3. Indexes
        val indexCols = HashMap<String, TreeMap<Int, String>>()
        val indexUnique = HashMap<String, Boolean>()

        meta.getIndexInfo(catalog, schema, tableName, false, false).use { rs ->
            while (rs.next()) {
                val indexName = rs.getString("INDEX_NAME") ?: continue
                
                val nonUnique = rs.getBoolean("NON_UNIQUE")
                val colName = rs.getString("COLUMN_NAME")
                val seq = rs.getShort("ORDINAL_POSITION").toInt()

                indexUnique[indexName] = !nonUnique

                indexCols.computeIfAbsent(indexName) { TreeMap() }[seq] = colName
            }
        }

        for ((name, value) in indexCols) {
            // Skip PK if it's the same name. MySQL PK is usually "PRIMARY"
            if (structure.primaryKey != null && "PRIMARY" == name) {
                continue
            }
            val cols = ArrayList(value.values)
            val unique = indexUnique[name] ?: false
            structure.addIndex(Index(name, unique, cols))
        }

        return structure
    }

    @Throws(SQLException::class)
    fun getRowCount(conn: Connection, tableName: String): Int {
        val sql = "SELECT COUNT(*) FROM $tableName"
        conn.createStatement().use { stmt ->
            stmt.executeQuery(sql).use { rs ->
                if (rs.next()) {
                    return rs.getInt(1)
                }
            }
        }
        return 0
    }

    @Throws(SQLException::class)
    fun getTableData(conn: Connection, tableName: String): List<Map<String, Any?>> {
        val data = ArrayList<Map<String, Any?>>()
        val sql = "SELECT * FROM $tableName" // Should ideally order by Primary Key
        
        conn.createStatement().use { stmt ->
            stmt.executeQuery(sql).use { rs ->
                val metaData = rs.metaData
                val columnCount = metaData.columnCount

                while (rs.next()) {
                    val row = LinkedHashMap<String, Any?>()
                    for (i in 1..columnCount) {
                        row[metaData.getColumnName(i)] = rs.getObject(i)
                    }
                    data.add(row)
                }
            }
        }
        return data
    }

    @Throws(SQLException::class)
    fun getCreateTableSql(conn: Connection, tableName: String): String {
        val sql = "SHOW CREATE TABLE $tableName"
        conn.createStatement().use { stmt ->
            stmt.executeQuery(sql).use { rs ->
                if (rs.next()) {
                    return rs.getString(2) // The second column contains the Create Table SQL
                }
            }
        }
        return ""
    }
}
