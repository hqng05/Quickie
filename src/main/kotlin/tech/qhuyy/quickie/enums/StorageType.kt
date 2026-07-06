package tech.qhuyy.quickie.enums

enum class StorageType {
    MYSQL, SQLITE;

    companion object {
        fun fromString(value: String?): StorageType {
            return when (value?.uppercase()) {
                "SQLITE" -> SQLITE
                "MYSQL" -> MYSQL
                else -> SQLITE
            }
        }
    }
}