package com.zxqj.dbcompare.model.structure

data class DiffItem<T>(
    val name: String? = null,
    val source: T? = null,
    val target: T? = null,
    val status: DiffStatus? = null
)
