package com.magick.gsm2sip.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.coroutines.flow.Flow

/** Direction of the bridged call, from the gateway's point of view. */
enum class CallDirection {
    /** SIP INVITE in -> GSM call out (X-GSM-Forward). */
    SIP_TO_GSM,

    /** GSM call in -> bridged to SIP. */
    GSM_TO_SIP,
}

/**
 * One bridged call, capturing both legs and their timing so the history screen
 * can show "SIP leg" and "GSM leg" with timestamps.
 */
@Entity(tableName = "call_history")
data class CallRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val direction: CallDirection,
    val sipRemote: String,
    val gsmNumber: String,
    val startedAtMillis: Long,
    val gsmConnectedAtMillis: Long? = null,
    val endedAtMillis: Long? = null,
    val audioBridged: Boolean = false,
    val result: String = "",         // e.g. "completed", "gsm_failed", "sip_rejected"
) {
    val durationSeconds: Long?
        get() = endedAtMillis?.let { (it - startedAtMillis) / 1000 }
}

@Dao
interface CallHistoryDao {
    @Insert
    suspend fun insert(record: CallRecord): Long

    @Query("UPDATE call_history SET gsmConnectedAtMillis = :ts WHERE id = :id")
    suspend fun markGsmConnected(id: Long, ts: Long)

    @Query("UPDATE call_history SET endedAtMillis = :ts, audioBridged = :bridged, result = :result WHERE id = :id")
    suspend fun markEnded(id: Long, ts: Long, bridged: Boolean, result: String)

    @Query("SELECT * FROM call_history ORDER BY startedAtMillis DESC LIMIT 200")
    fun recent(): Flow<List<CallRecord>>

    @Query("DELETE FROM call_history")
    suspend fun clear()
}

class Converters {
    @TypeConverter
    fun directionToString(d: CallDirection): String = d.name

    @TypeConverter
    fun stringToDirection(s: String): CallDirection = CallDirection.valueOf(s)
}

@Database(entities = [CallRecord::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class GatewayDatabase : RoomDatabase() {
    abstract fun callHistoryDao(): CallHistoryDao
}
