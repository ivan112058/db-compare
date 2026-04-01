package com.zxqj.dbcompare.model.structure

import java.util.*

class TableStructure(val tableName: String) {
    val columns: MutableMap<String, Column> = LinkedHashMap()
    var primaryKey: PrimaryKey? = null
    val indexes: MutableMap<String, Index> = LinkedHashMap()

    fun addColumn(column: Column) {
        column.name?.let { columns[it] = column }
    }

    fun addIndex(index: Index) {
        index.name?.let { indexes[it] = index }
    }

    fun compareStructure(other: TableStructure): StructureDiff {
        val diff = StructureDiff()

        // 1. Compare Columns
        val allCols = TreeSet(this.columns.keys)
        allCols.addAll(other.columns.keys)

        for (colName in allCols) {
            val thisCol = this.columns[colName]
            val otherCol = other.columns[colName]
            val status = when {
                thisCol == null -> DiffStatus.ADDED
                otherCol == null -> DiffStatus.REMOVED
                thisCol != otherCol -> DiffStatus.MODIFIED
                else -> DiffStatus.EQUAL
            }
            diff.columns.add(DiffItem(colName, thisCol, otherCol, status))
        }

        // 2. PK Comparison
        if (this.primaryKey != null || other.primaryKey != null) {
            val thisPk = this.primaryKey
            val otherPk = other.primaryKey

            val thisPkInfo = thisPk?.let { IndexInfo("PRIMARY", it.name, it.columns) }
            val otherPkInfo = otherPk?.let { IndexInfo("PRIMARY", it.name, it.columns) }

            val status = when {
                thisPkInfo == null -> DiffStatus.ADDED
                otherPkInfo == null -> DiffStatus.REMOVED
                thisPk != otherPk -> DiffStatus.MODIFIED
                else -> DiffStatus.EQUAL
            }
            diff.indexes.add(DiffItem("PRIMARY KEY", thisPkInfo, otherPkInfo, status))
        }

        // 3. Indexes Comparison
        val allIndexes = TreeSet(this.indexes.keys)
        allIndexes.addAll(other.indexes.keys)

        for (idxName in allIndexes) {
            val thisIdx = this.indexes[idxName]
            val otherIdx = other.indexes[idxName]
            
            val thisIdxInfo = thisIdx?.let { 
                IndexInfo(if (it.isUnique) "UNIQUE" else "INDEX", it.name, it.columns) 
            }
            val otherIdxInfo = otherIdx?.let { 
                IndexInfo(if (it.isUnique) "UNIQUE" else "INDEX", it.name, it.columns) 
            }

            val status = when {
                thisIdx == null -> DiffStatus.ADDED
                otherIdx == null -> DiffStatus.REMOVED
                thisIdx != otherIdx -> DiffStatus.MODIFIED
                else -> DiffStatus.EQUAL
            }
            diff.indexes.add(DiffItem(idxName, thisIdxInfo, otherIdxInfo, status))
        }

        return diff
    }
}
