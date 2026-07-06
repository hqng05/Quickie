package tech.qhuyy.quickie.enums

enum class DatabaseType {
    MYSQL, SQLITE;

    companion object {
        fun fromString(value: String?): DatabaseType {
            return when (value?.uppercase()) {
                "SQLITE" -> SQLITE
                "MYSQL" -> MYSQL
                else -> SQLITE
            }
        }
    }
}