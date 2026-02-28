package ke.afyaclaim.pos.data.local

import androidx.room.TypeConverter
import ke.afyaclaim.pos.domain.model.SyncState

class Converters {
    @TypeConverter
    fun syncStateToString(state: SyncState): String = state.wire

    @TypeConverter
    fun stringToSyncState(value: String): SyncState = SyncState.fromWire(value)
}
