package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReceiptDao {
  @Query("SELECT * FROM receipts ORDER BY createdAt DESC")
  fun getAllReceipts(): Flow<List<ReceiptEntity>>

  @Query("SELECT * FROM receipts WHERE id = :id LIMIT 1")
  suspend fun getReceiptById(id: Long): ReceiptEntity?

  @Query("SELECT * FROM receipts WHERE recipientName LIKE '%' || :query || '%' OR recipientBank LIKE '%' || :query || '%' ORDER BY createdAt DESC")
  fun searchReceipts(query: String): Flow<List<ReceiptEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertReceipt(receipt: ReceiptEntity): Long

  @Update
  suspend fun updateReceipt(receipt: ReceiptEntity)

  @Delete
  suspend fun deleteReceipt(receipt: ReceiptEntity)

  @Query("DELETE FROM receipts WHERE id = :id")
  suspend fun deleteReceiptById(id: Long)

  @Query("SELECT COUNT(*) FROM receipts")
  fun getReceiptCount(): Flow<Int>

  @Query("SELECT COALESCE(SUM(amount), 0) FROM receipts")
  fun getTotalAmountSum(): Flow<Long>
}
