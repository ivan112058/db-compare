package com.zxqj.dbcompare.model.structure

enum class DiffStatus {
    ADDED,    // Target only
    REMOVED,  // Source only
    MODIFIED, // Both but different
    EQUAL     // Both and same
}
