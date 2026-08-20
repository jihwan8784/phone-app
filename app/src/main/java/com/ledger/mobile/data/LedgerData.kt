package com.ledger.mobile.data

import android.content.Context
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val passwordHash: String
)

@Entity(
    tableName = "transactions",
    foreignKeys = [ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.CASCADE)]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val date: String,
    val description: String,
    val amount: Long,
    val type: String
)

@Dao
interface LedgerDao {
    @Query("SELECT * FROM accounts ORDER BY id")
    fun accounts(): Flow<List<AccountEntity>>

    @Insert
    suspend fun insertAccount(account: AccountEntity): Long

    @Query("DELETE FROM accounts WHERE id = :id AND passwordHash = :passwordHash")
    suspend fun deleteAccount(id: Long, passwordHash: String): Int

    @Query("SELECT * FROM transactions WHERE accountId = :accountId ORDER BY date DESC, id DESC")
    fun transactions(accountId: Long): Flow<List<TransactionEntity>>

    @Insert
    suspend fun insertTransaction(transaction: TransactionEntity)
}

@Database(entities = [AccountEntity::class, TransactionEntity::class], version = 1, exportSchema = false)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun dao(): LedgerDao

    companion object {
        fun create(context: Context): LedgerDatabase = Room.databaseBuilder(
            context,
            LedgerDatabase::class.java,
            "ledger.db"
        ).build()
    }
}
