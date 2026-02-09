package com.zxqj.dbcompare.model.structure

data class DiffItem<T>(
    var name: String? = null,
    var source: T? = null,
    var target: T? = null,
    var status: DiffStatus? = null
)
