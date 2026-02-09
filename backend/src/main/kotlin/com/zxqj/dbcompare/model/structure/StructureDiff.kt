package com.zxqj.dbcompare.model.structure

class StructureDiff {
    var columns: MutableList<DiffItem<Column>> = ArrayList()
    var indexes: MutableList<DiffItem<IndexInfo>> = ArrayList()

    fun isEmpty(): Boolean {
        val hasColumnDiff = columns.any { it.status != DiffStatus.EQUAL }
        val hasIndexDiff = indexes.any { it.status != DiffStatus.EQUAL }
        return !hasColumnDiff && !hasIndexDiff
    }
}
