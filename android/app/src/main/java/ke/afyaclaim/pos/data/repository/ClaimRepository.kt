package ke.afyaclaim.pos.data.repository

import ke.afyaclaim.pos.data.local.AfyaDatabase
import ke.afyaclaim.pos.domain.model.ClaimCard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClaimRepository @Inject constructor(db: AfyaDatabase) {
    private val claimDao = db.claimDao()

    fun claims(): Flow<List<ClaimCard>> = claimDao.observeAll().map { rows -> rows.map { it.toDomain() } }
}
