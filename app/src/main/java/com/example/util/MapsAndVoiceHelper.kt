package com.example.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent

object MapsAndVoiceHelper {
    fun createVoiceSearchIntent(
        prompt: String = "Sebutkan nama lokasi atau tempat wisata..."
    ): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "id-ID")
            putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
        }
    }

    fun isVoiceSearchAvailable(context: Context): Boolean {
        val intent = createVoiceSearchIntent()
        return intent.resolveActivity(context.packageManager) != null
    }

    fun openGoogleMapsSearch(context: Context, query: String): Boolean {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) return false

        val encodedQuery = Uri.encode(cleanQuery)
        val mapsIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("geo:0,0?q=$encodedQuery")
        ).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            if (mapsIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(mapsIntent)
                true
            } else {
                val webMapsIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/maps/search/?api=1&query=$encodedQuery")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webMapsIntent)
                true
            }
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}
