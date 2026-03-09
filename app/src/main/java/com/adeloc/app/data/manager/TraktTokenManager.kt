@file:Suppress("DEPRECATION")

package com.adeloc.app.data.manager

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore

class TraktTokenManager(context: Context) {

    private val sharedPreferences: SharedPreferences = try {
        createEncryptedPrefs(context)
    } catch (e: Exception) {
        Log.e("TraktTokenManager", "CRITICAL: EncryptedSharedPreferences corrupted. Initializing Nuclear Reset.", e)
        nuclearReset(context)
        try {
            createEncryptedPrefs(context)
        } catch (e2: Exception) {
            Log.e("TraktTokenManager", "Nuclear Reset failed to restore encryption. Falling back to plain prefs.", e2)
            context.getSharedPreferences("trakt_prefs_plain", Context.MODE_PRIVATE)
        }
    }

    private fun createEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            "trakt_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun nuclearReset(context: Context) {
        try {
            // 1. Delete the SharedPreferences file properly via Context
            context.deleteSharedPreferences("trakt_prefs")
            
            // 2. Clear the Android KeyStore entries for this app
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            val aliases = keyStore.aliases()
            while (aliases.hasMoreElements()) {
                val alias = aliases.nextElement()
                if (alias.contains("_androidx_security_master_key_")) {
                    keyStore.deleteEntry(alias)
                }
            }
            // Also delete the default one just in case
            keyStore.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            
            Log.d("TraktTokenManager", "Nuclear Reset successful: Prefs and KeyStore cleared.")
        } catch (e: Exception) {
            Log.e("TraktTokenManager", "Nuclear Reset failed during cleanup", e)
        }
    }

    fun saveTokens(accessToken: String, refreshToken: String) {
        sharedPreferences.edit().apply {
            putString("trakt_access_token", accessToken)
            putString("trakt_refresh_token", refreshToken)
            apply()
        }
    }

    fun getAccessToken(): String? {
        return sharedPreferences.getString("trakt_access_token", null)
    }

    fun getRefreshToken(): String? {
        return sharedPreferences.getString("trakt_refresh_token", null)
    }

    fun clearTokens() {
        sharedPreferences.edit().apply {
            remove("trakt_access_token")
            remove("trakt_refresh_token")
            apply()
        }
    }
}
