package com.example.drive

import android.accounts.Account
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

sealed interface DriveAuthOutcome {
    data class Authorized(
        val accessToken: String,
        val accountEmail: String = "",
        val accountName: String = ""
    ) : DriveAuthOutcome

    data class NeedsResolution(
        val intentSender: IntentSender
    ) : DriveAuthOutcome

    data class Failed(
        val message: String
    ) : DriveAuthOutcome
}

object DriveAuthManager {
    const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"

    private val revokeClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    suspend fun authorizeDriveAccess(
        context: Context,
        preferredEmail: String? = null
    ): DriveAuthOutcome {
        val builder = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))

        val cleanEmail = preferredEmail?.trim().orEmpty()
        if (cleanEmail.isNotEmpty()) {
            builder.setAccount(Account(cleanEmail, "com.google"))
        }

        val request = builder.build()
        return try {
            val result = Identity.getAuthorizationClient(context.applicationContext)
                .authorize(request)
                .await()
            mapAuthorizationResult(result, cleanEmail)
        } catch (e: ApiException) {
            DriveAuthOutcome.Failed(mapPlayServicesError(e))
        } catch (e: Exception) {
            DriveAuthOutcome.Failed(
                e.localizedMessage ?: "Gagal meminta izin akses Google Drive."
            )
        }
    }

    fun extractAuthorizationFromIntent(
        context: Context,
        intent: Intent?,
        fallbackEmail: String = ""
    ): DriveAuthOutcome {
        if (intent == null) {
            return DriveAuthOutcome.Failed("Otorisasi Google Drive dibatalkan.")
        }
        return try {
            val result = Identity.getAuthorizationClient(context.applicationContext)
                .getAuthorizationResultFromIntent(intent)
            mapAuthorizationResult(result, fallbackEmail)
        } catch (e: ApiException) {
            DriveAuthOutcome.Failed(mapPlayServicesError(e))
        } catch (e: Exception) {
            DriveAuthOutcome.Failed(
                e.localizedMessage ?: "Gagal memproses izin Google Drive."
            )
        }
    }

    suspend fun revokeDriveAccess(
        context: Context,
        preferredEmail: String? = null
    ) = withContext(Dispatchers.IO) {
        val token = when (val auth = authorizeDriveAccess(context, preferredEmail)) {
            is DriveAuthOutcome.Authorized -> auth.accessToken
            else -> null
        }
        if (token.isNullOrBlank()) return@withContext
        runCatching {
            val body = FormBody.Builder()
                .add("token", token)
                .build()
            val req = Request.Builder()
                .url("https://oauth2.googleapis.com/revoke")
                .post(body)
                .build()
            revokeClient.newCall(req).execute().use { }
        }
    }

    private fun mapAuthorizationResult(
        result: AuthorizationResult,
        fallbackEmail: String
    ): DriveAuthOutcome {
        if (result.hasResolution()) {
            val pendingIntent = result.pendingIntent
            if (pendingIntent != null) {
                return DriveAuthOutcome.NeedsResolution(pendingIntent.intentSender)
            }
        }
        val token = result.accessToken
        if (!token.isNullOrBlank()) {
            val signInAccount = runCatching { result.toGoogleSignInAccount() }.getOrNull()
            return DriveAuthOutcome.Authorized(
                accessToken = token,
                accountEmail = signInAccount?.email ?: fallbackEmail,
                accountName = signInAccount?.displayName ?: ""
            )
        }
        return DriveAuthOutcome.Failed("Token akses Google Drive tidak tersedia.")
    }

    private fun mapPlayServicesError(e: ApiException): String {
        return when (e.statusCode) {
            16 -> "Izin Google Drive dibatalkan oleh pengguna."
            7 -> "Tidak ada koneksi internet untuk menghubungi layanan Google."
            10 -> "Konfigurasi OAuth Google Drive belum sesuai (Developer Error)."
            else -> e.localizedMessage ?: "Gagal terhubung ke layanan otorisasi Google (kode ${e.statusCode})."
        }
    }
}
