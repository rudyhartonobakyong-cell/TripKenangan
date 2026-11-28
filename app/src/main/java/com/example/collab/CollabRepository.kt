package com.example.collab

import android.content.Context
import android.util.Log
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom

data class FirestoreConnectionTestResult(
    val isSuccess: Boolean,
    val errorCode: String,
    val projectId: String,
    val databaseId: String,
    val message: String
)

class CollabRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
    private val configuredDatabaseId: String = DEFAULT_DATABASE_ID
) {
    constructor(context: Context) : this(
        db = getConfiguredFirestore(context),
        auth = Firebase.auth,
        configuredDatabaseId = resolveConfiguredDatabaseId(context)
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Pengguna harus masuk dengan Google sebelum mengakses Cloud Firestore.")
    }

    fun getActiveProjectId(): String {
        return try {
            FirebaseApp.getInstance().options.projectId.orEmpty().ifBlank { "(tidak diketahui)" }
        } catch (_: Exception) {
            "(tidak diketahui)"
        }
    }

    fun getActiveDatabaseId(): String = configuredDatabaseId

    suspend fun testFirestoreConnection(): FirestoreConnectionTestResult {
        val projectId = getActiveProjectId()
        val databaseId = configuredDatabaseId
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return FirestoreConnectionTestResult(
                isSuccess = false,
                errorCode = "UNAUTHENTICATED",
                projectId = projectId,
                databaseId = databaseId,
                message = formatFirestoreErrorForUi(e, "Belum login.")
            )
        }

        val path = "connection_tests/$uid"
        val docRef = db.collection("connection_tests").document(uid)
        val startMillis = System.currentTimeMillis()
        var currentStep = "READ_SERVER"
        var currentOp = OperationType.GET

        return try {
            val initialSnap = docRef.get(Source.SERVER).await()
            val existedBefore = initialSnap.exists()

            currentStep = "WRITE_SERVER"
            currentOp = OperationType.WRITE
            docRef.set(
                mapOf(
                    "uid" to uid,
                    "platform" to "android",
                    "databaseId" to databaseId,
                    "testedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            currentStep = "VERIFY_READ_SERVER"
            currentOp = OperationType.GET
            val verifySnap = docRef.get(Source.SERVER).await()
            val elapsedMs = System.currentTimeMillis() - startMillis

            val successMsg = "Koneksi berhasil ($elapsedMs ms).\n" +
                "Baca awal: ${if (existedBefore) "dokumen ditemukan" else "dokumen baru"}.\n" +
                "Tulis & baca ulang server ($path): ${if (verifySnap.exists()) "BERHASIL" else "TIDAK DITEMUKAN"}."
            Log.i(
                "FirestoreConnectionTest",
                "SUCCESS project=$projectId db=$databaseId path=$path elapsed=${elapsedMs}ms"
            )
            FirestoreConnectionTestResult(
                isSuccess = true,
                errorCode = "OK",
                projectId = projectId,
                databaseId = databaseId,
                message = successMsg
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, currentOp, path)
            val firestoreCode = extractFirestoreException(e)?.code?.name
                ?: e::class.java.simpleName
            val detail = formatFirestoreErrorForUi(e, "Koneksi ke Firestore gagal.")
            Log.e(
                "FirestoreConnectionTest",
                "FAILED step=$currentStep code=$firestoreCode project=$projectId db=$databaseId path=$path: $detail",
                e
            )
            FirestoreConnectionTestResult(
                isSuccess = false,
                errorCode = firestoreCode,
                projectId = projectId,
                databaseId = databaseId,
                message = "Gagal pada tahap $currentStep:\n$detail"
            )
        }
    }

    fun observeUserTrips(userId: String): Flow<List<SharedTrip>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "UID pengguna berubah." }
        val path = "trips"
        emitAll(
            db.collection(path)
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        SharedTrip::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt ?: it.createdAt }
                }
                .catch { error ->
                    val firestoreError = extractFirestoreException(error)
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeTripMembers(tripId: String): Flow<List<TripMember>> = flow {
        val uid = requireUserId()
        val path = "trips/$tripId/members"
        emitAll(
            db.collection("trips").document(tripId).collection("members")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        TripMember::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedWith(
                        compareBy<TripMember> { if (it.role == "owner") 0 else 1 }
                            .thenBy { it.displayName.lowercase() }
                    )
                }
                .catch { error ->
                    val firestoreError = extractFirestoreException(error)
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeTripVisits(tripId: String): Flow<List<SharedVisit>> = flow {
        val uid = requireUserId()
        val path = "trips/$tripId/visits"
        emitAll(
            db.collection("trips").document(tripId).collection("visits")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        SharedVisit::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedWith(
                        compareBy<SharedVisit> { it.visitDateEpochDay }
                            .thenBy { it.visitHour }
                            .thenBy { it.visitMinute }
                    )
                }
                .catch { error ->
                    val firestoreError = extractFirestoreException(error)
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeTripComments(tripId: String): Flow<List<TripDateComment>> = flow {
        val uid = requireUserId()
        val path = "trips/$tripId/comments"
        emitAll(
            db.collection("trips").document(tripId).collection("comments")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        TripDateComment::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.createdAt?.toDate()?.time ?: Long.MAX_VALUE }
                }
                .catch { error ->
                    val firestoreError = extractFirestoreException(error)
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createSharedTrip(
        title: String,
        displayName: String,
        photoUrl: String
    ): Result<SharedTrip> {
        val cleanTitle = title.trim().take(80)
        if (cleanTitle.isEmpty()) {
            return Result.failure(IllegalArgumentException("Nama trip bersama wajib diisi."))
        }
        val cleanName = displayName.trim().ifEmpty { "Pengguna" }.take(80)
        val cleanPhoto = photoUrl.trim().take(500)
        val tripRef = db.collection("trips").document()
        val tripId = tripRef.id
        var currentOp = OperationType.GET
        var currentPath = "trip_invites"

        return try {
            val uid = requireUserId()
            val inviteCode = generateUniqueInviteCode()
            currentOp = OperationType.CREATE
            currentPath = "trips/$tripId"
            val inviteRef = db.collection("trip_invites").document(inviteCode)
            val memberRef = tripRef.collection("members").document(uid)

            val batch = db.batch()
            batch.set(
                tripRef,
                mapOf(
                    "title" to cleanTitle,
                    "ownerId" to uid,
                    "inviteCode" to inviteCode,
                    "memberUids" to listOf(uid),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                inviteRef,
                mapOf(
                    "tripId" to tripId,
                    "tripTitle" to cleanTitle,
                    "ownerId" to uid,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                memberRef,
                mapOf(
                    "uid" to uid,
                    "displayName" to cleanName,
                    "photoUrl" to cleanPhoto,
                    "role" to "owner",
                    "inviteCodeUsed" to "",
                    "joinedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()

            Result.success(
                SharedTrip(
                    id = tripId,
                    title = cleanTitle,
                    ownerId = uid,
                    inviteCode = inviteCode,
                    memberUids = listOf(uid)
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, currentOp, currentPath)
            Result.failure(e)
        }
    }

    suspend fun joinTripByCode(
        rawInput: String,
        displayName: String,
        photoUrl: String,
        alreadyJoinedTripIds: Set<String>
    ): Result<String> {
        val code = extractInviteCode(rawInput)
        if (code == null) {
            return Result.failure(IllegalArgumentException("Kode undangan harus 6 karakter huruf atau angka."))
        }
        val cleanName = displayName.trim().ifEmpty { "Anggota" }.take(80)
        val cleanPhoto = photoUrl.trim().take(500)

        return try {
            val uid = requireUserId()
            val inviteSnap = db.collection("trip_invites").document(code).get().await()
            if (!inviteSnap.exists()) {
                return Result.failure(IllegalArgumentException("Kode undangan \"$code\" tidak ditemukan."))
            }
            val tripId = inviteSnap.getString("tripId").orEmpty()
            if (tripId.isBlank()) {
                return Result.failure(IllegalStateException("Data undangan tidak valid."))
            }
            if (tripId in alreadyJoinedTripIds) {
                return Result.success(tripId)
            }

            val tripRef = db.collection("trips").document(tripId)
            val memberRef = tripRef.collection("members").document(uid)

            val batch = db.batch()
            batch.set(
                memberRef,
                mapOf(
                    "uid" to uid,
                    "displayName" to cleanName,
                    "photoUrl" to cleanPhoto,
                    "role" to "member",
                    "inviteCodeUsed" to code,
                    "joinedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                tripRef,
                mapOf(
                    "memberUids" to FieldValue.arrayUnion(uid),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(tripId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "trip_invites/$code")
            Result.failure(e)
        }
    }

    suspend fun addSharedVisit(
        tripId: String,
        locationName: String,
        tripTitle: String,
        visitDateEpochDay: Long,
        visitHour: Int,
        visitMinute: Int,
        planNotes: String,
        authorName: String
    ): Result<String> {
        val cleanLocation = locationName.trim().take(160)
        if (cleanLocation.isEmpty()) {
            return Result.failure(IllegalArgumentException("Nama lokasi wajib diisi."))
        }
        val path = "trips/$tripId/visits"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("trips").document(tripId).collection("visits").document()
            docRef.set(
                mapOf(
                    "tripId" to tripId,
                    "locationName" to cleanLocation,
                    "tripTitle" to tripTitle.trim().take(80),
                    "visitDateEpochDay" to visitDateEpochDay.coerceIn(0L, 100_000L),
                    "visitHour" to visitHour.coerceIn(0, 23).toLong(),
                    "visitMinute" to visitMinute.coerceIn(0, 59).toLong(),
                    "planNotes" to planNotes.trim().take(500),
                    "memoryNotes" to "",
                    "completedManually" to false,
                    "createdByUid" to uid,
                    "createdByName" to authorName.trim().ifEmpty { "Anggota" }.take(80),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateSharedVisit(
        tripId: String,
        visitId: String,
        locationName: String,
        tripTitle: String,
        visitDateEpochDay: Long,
        visitHour: Int,
        visitMinute: Int,
        planNotes: String,
        memoryNotes: String,
        completedManually: Boolean
    ): Result<Unit> {
        val cleanLocation = locationName.trim().take(160)
        if (cleanLocation.isEmpty()) {
            return Result.failure(IllegalArgumentException("Nama lokasi wajib diisi."))
        }
        val path = "trips/$tripId/visits/$visitId"
        return try {
            requireUserId()
            require(visitId.isNotBlank())
            db.collection("trips").document(tripId)
                .collection("visits").document(visitId)
                .update(
                    mapOf(
                        "locationName" to cleanLocation,
                        "tripTitle" to tripTitle.trim().take(80),
                        "visitDateEpochDay" to visitDateEpochDay.coerceIn(0L, 100_000L),
                        "visitHour" to visitHour.coerceIn(0, 23).toLong(),
                        "visitMinute" to visitMinute.coerceIn(0, 59).toLong(),
                        "planNotes" to planNotes.trim().take(500),
                        "memoryNotes" to memoryNotes.trim().take(1000),
                        "completedManually" to completedManually,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteSharedVisit(tripId: String, visitId: String): Result<Unit> {
        val path = "trips/$tripId/visits/$visitId"
        return try {
            requireUserId()
            require(visitId.isNotBlank())
            db.collection("trips").document(tripId)
                .collection("visits").document(visitId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun addDateComment(
        tripId: String,
        dateEpochDay: Long,
        authorName: String,
        authorPhotoUrl: String,
        text: String
    ): Result<Unit> {
        val cleanText = text.trim().take(500)
        if (cleanText.isEmpty()) {
            return Result.failure(IllegalArgumentException("Komentar tidak boleh kosong."))
        }
        val path = "trips/$tripId/comments"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("trips").document(tripId).collection("comments").document()
            docRef.set(
                mapOf(
                    "tripId" to tripId,
                    "dateEpochDay" to dateEpochDay.coerceIn(0L, 100_000L),
                    "authorUid" to uid,
                    "authorName" to authorName.trim().ifEmpty { "Anggota" }.take(80),
                    "authorPhotoUrl" to authorPhotoUrl.trim().take(500),
                    "text" to cleanText,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteDateComment(tripId: String, commentId: String): Result<Unit> {
        val path = "trips/$tripId/comments/$commentId"
        return try {
            requireUserId()
            db.collection("trips").document(tripId)
                .collection("comments").document(commentId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteSharedTrip(tripId: String): Result<Unit> {
        val path = "trips/$tripId"
        return try {
            val uid = requireUserId()
            require(tripId.isNotBlank())
            val tripRef = db.collection("trips").document(tripId)
            val tripSnap = tripRef.get().await()
            if (!tripSnap.exists()) {
                return Result.failure(IllegalArgumentException("Grup tidak ditemukan atau sudah dihapus."))
            }
            val ownerId = tripSnap.getString("ownerId").orEmpty()
            if (ownerId != uid) {
                return Result.failure(IllegalAccessException("Hanya admin pemilik grup yang dapat menghapus grup ini."))
            }
            val inviteCode = tripSnap.getString("inviteCode").orEmpty()

            // Firestore does not cascade-delete subcollections automatically.
            // Delete subcollections first while trips/{tripId} still exists so security rules
            // checking isTripMember/ownerId on trips/{tripId} remain valid.
            val subcollectionRefs = buildList {
                addAll(tripRef.collection("comments").get().await().documents.map { it.reference })
                addAll(tripRef.collection("visits").get().await().documents.map { it.reference })
                addAll(tripRef.collection("members").get().await().documents.map { it.reference })
            }
            subcollectionRefs.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { ref -> batch.delete(ref) }
                batch.commit().await()
            }

            val finalBatch = db.batch()
            if (inviteCode.isNotBlank()) {
                finalBatch.delete(db.collection("trip_invites").document(inviteCode))
            }
            finalBatch.delete(tripRef)
            finalBatch.commit().await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun leaveSharedTrip(tripId: String): Result<Unit> {
        val path = "trips/$tripId"
        return try {
            val uid = requireUserId()
            require(tripId.isNotBlank())
            val tripRef = db.collection("trips").document(tripId)
            val tripSnap = tripRef.get().await()
            if (!tripSnap.exists()) {
                return Result.failure(IllegalArgumentException("Grup tidak ditemukan."))
            }
            val ownerId = tripSnap.getString("ownerId").orEmpty()
            if (ownerId == uid) {
                return Result.failure(
                    IllegalStateException("Pemilik grup tidak dapat keluar tanpa menghapus grup atau menyerahkan peran admin ke anggota lain.")
                )
            }

            val batch = db.batch()
            batch.delete(tripRef.collection("members").document(uid))
            batch.update(
                tripRef,
                mapOf(
                    "memberUids" to FieldValue.arrayRemove(uid),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun transferGroupAdmin(tripId: String, newAdminUid: String): Result<Unit> {
        val path = "trips/$tripId"
        return try {
            val uid = requireUserId()
            require(tripId.isNotBlank() && newAdminUid.isNotBlank())
            if (newAdminUid == uid) {
                return Result.failure(IllegalArgumentException("Pilih anggota lain untuk dijadikan admin."))
            }
            val tripRef = db.collection("trips").document(tripId)
            val tripSnap = tripRef.get().await()
            if (!tripSnap.exists()) {
                return Result.failure(IllegalArgumentException("Grup tidak ditemukan."))
            }
            val ownerId = tripSnap.getString("ownerId").orEmpty()
            if (ownerId != uid) {
                return Result.failure(IllegalAccessException("Hanya admin pemilik grup yang dapat menyerahkan peran admin."))
            }
            val memberUids = (tripSnap.get("memberUids") as? List<*>)?.filterIsInstance<String>().orEmpty()
            if (newAdminUid !in memberUids) {
                return Result.failure(IllegalArgumentException("Pengguna yang dipilih bukan anggota grup ini."))
            }
            val inviteCode = tripSnap.getString("inviteCode").orEmpty()
            val inviteRef = if (inviteCode.isNotBlank()) {
                db.collection("trip_invites").document(inviteCode).takeIf { it.get().await().exists() }
            } else {
                null
            }

            val batch = db.batch()
            batch.update(
                tripRef,
                mapOf(
                    "ownerId" to newAdminUid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                tripRef.collection("members").document(newAdminUid),
                mapOf("role" to "owner")
            )
            batch.update(
                tripRef.collection("members").document(uid),
                mapOf("role" to "member")
            )
            if (inviteRef != null) {
                batch.update(inviteRef, mapOf("ownerId" to newAdminUid))
            }
            batch.commit().await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    private suspend fun generateUniqueInviteCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val random = SecureRandom()
        repeat(6) {
            val code = buildString {
                repeat(6) {
                    append(alphabet[random.nextInt(alphabet.length)])
                }
            }
            val exists = db.collection("trip_invites").document(code).get().await().exists()
            if (!exists) return code
        }
        return buildString {
            repeat(6) { append(alphabet[random.nextInt(alphabet.length)]) }
        }
    }

    companion object {
        const val DEFAULT_DATABASE_ID = "(default)"

        fun resolveConfiguredDatabaseId(context: Context): String {
            return try {
                context.applicationContext.getString(R.string.firestore_database_id)
                    .trim()
                    .ifBlank { DEFAULT_DATABASE_ID }
            } catch (_: Exception) {
                DEFAULT_DATABASE_ID
            }
        }

        fun getConfiguredFirestore(context: Context): FirebaseFirestore {
           return FirebaseFirestore.getInstance()
        }

        /* fun getConfiguredFirestore(context: Context): FirebaseFirestore {
            val dbId = resolveConfiguredDatabaseId(context)
            return if (dbId == DEFAULT_DATABASE_ID || dbId.isBlank()) {
                FirebaseFirestore.getInstance()
            } else {
                FirebaseFirestore.getInstance(dbId)
            }
        }
        */
        

        fun extractInviteCode(raw: String): String? {
            val trimmed = raw.trim().uppercase()
            if (trimmed.matches(Regex("^[A-Z0-9]{6}$"))) return trimmed
            val match = Regex("(?:JOIN/|CODE=)([A-Z0-9]{6})").find(trimmed)
            return match?.groupValues?.getOrNull(1)
        }
    }
}
