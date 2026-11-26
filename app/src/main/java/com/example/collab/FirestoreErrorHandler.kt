package com.example.collab

import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.cancellation.CancellationException

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write")
}

fun extractFirestoreException(exception: Throwable): FirebaseFirestoreException? {
    return generateSequence(exception) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()
}

fun formatFirestoreErrorForUi(error: Throwable, fallback: String): String {
    val firestoreEx = extractFirestoreException(error)
    if (firestoreEx != null) {
        val detail = firestoreEx.localizedMessage ?: firestoreEx.message ?: fallback
        return "[${firestoreEx.code.name}] $detail"
    }
    val causes = generateSequence(error) { it.cause }
    val root = causes.firstOrNull { it !is CancellationException } ?: error
    val typeName = root::class.java.simpleName.ifBlank { "Error" }
    val detail = root.localizedMessage ?: root.message ?: fallback
    return "[$typeName] $detail"
}

fun handleFirestoreError(
    exception: Throwable,
    operationType: OperationType,
    path: String?
): String {
    val causes = generateSequence(exception) { it.cause }
    val firestoreEx = causes.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
    val rootError = firestoreEx
        ?: causes.firstOrNull { it !is CancellationException }
        ?: exception

    val errorCode = firestoreEx?.code?.name ?: rootError::class.java.simpleName
    val auth = FirebaseAuth.getInstance()
    val currentUser = auth.currentUser

    val projectId = try {
        FirebaseApp.getInstance().options.projectId
    } catch (_: Exception) {
        null
    }

    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", provider.email)
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("firestoreCode", errorCode)
        put("error", rootError.message ?: rootError.toString())
        put("cause", rootError.cause?.toString())
        put("projectId", projectId)
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error [$errorCode] on $path: $jsonString", rootError)
    return jsonString
}
