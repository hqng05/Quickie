package tech.qhuyy.quickie.database

import java.sql.Connection

interface Repository<T, ID> {
    val tableName: String

    suspend fun createTable()

    suspend fun findById(id: ID): T?

    suspend fun findAll(): List<T>

    suspend fun save(entity: T)

    suspend fun delete(id: ID)
}

abstract class AbstractRepository<T, ID>(
    protected val db: DatabaseManager,
    override val tableName: String
) : Repository<T, ID> {

    protected suspend fun <R> withConnection(block: suspend (Connection) -> R): R =
        db.withConnection(block)

    protected suspend fun tableExists(): Boolean =
        db.tableExists(tableName)
}
