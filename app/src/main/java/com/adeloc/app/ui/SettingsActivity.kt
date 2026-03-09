package com.adeloc.app.ui

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import com.adeloc.app.databinding.ActivitySettingsBinding
import com.adeloc.app.data.api.RetrofitClient
import com.adeloc.app.data.manager.TraktTokenManager
import com.google.android.material.chip.Chip
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private var pinCheckJob: Job? = null
    private var activeDialog: AlertDialog? = null
    private lateinit var traktTokenManager: TraktTokenManager

    private val TRAKT_CLIENT_ID = "4678a15d7ee291184d83d3b031101b8eb916aecf6ee5680d7b6145bbd0aadee0"
    private val TRAKT_CLIENT_SECRET = "8e0cbaf9c851beeb431fbc6f3c219802a903ad6cc51d0ac534e3660da9c6c306"
    private val TRAKT_REDIRECT_URI = "flixorent://auth/trakt"

    private val providers = listOf(
        ProviderInfo("Real-Debrid", "RD", "rd_token", "real-debrid.com/apitoken", "Paste Private Token Here"),
        ProviderInfo("AllDebrid", "AD", "ad_token", "alldebrid.com/pin/", "Click 'Link Account' below"),
        ProviderInfo("Premiumize", "PM", "pm_token", "premiumize.me/pin", "Click 'Link Account' below"),
        ProviderInfo("TorBox", "TB", "tb_token", "torbox.app/settings", "Paste TorBox API Key Here")
    )

    private val streamLanguages = listOf(
        "English" to "en", "Spanish" to "es", "French" to "fr", "German" to "de",
        "Italian" to "it", "Portuguese" to "pt", "Hindi" to "hi", "Russian" to "ru",
        "Japanese" to "ja", "Korean" to "ko", "Chinese" to "zh", "Arabic" to "ar"
    )

    private val appLanguages = listOf(
        AppLanguage("English", "en"), AppLanguage("Spanish", "es"), AppLanguage("French", "fr"),
        AppLanguage("German", "de"), AppLanguage("Italian", "it"), AppLanguage("Arabic", "ar"),
        AppLanguage("Russian", "ru"), AppLanguage("Portuguese", "pt")
    )

    private val downloadDirLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            contentResolver.takePersistableUriPermission(it, 
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            
            getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit()
                .putString("download_uri", it.toString())
                .apply()
                
            binding.tvDownloadPath.text = it.path ?: it.toString()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        traktTokenManager = TraktTokenManager(this)

        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

        // 1. App Language (Chips)
        setupAppLanguageChips()

        // 2. Provider Selection
        val providerAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, providers.map { it.name })
        binding.autoCompleteProvider.setAdapter(providerAdapter)
        binding.autoCompleteProvider.setOnItemClickListener { _, _, position, _ ->
            updateUIForProvider(providers[position])
        }

        // 3. Stream Language Chips
        val selectedLangs = prefs.getStringSet("pref_languages", setOf("en")) ?: setOf("en")
        streamLanguages.forEach { (name, code) ->
            val chip = Chip(this).apply {
                text = name
                isCheckable = true
                isChecked = selectedLangs.contains(code)
                tag = code
                chipStrokeWidth = 2f
                updateChipAppearance(this)
                setOnCheckedChangeListener { _, _ -> updateChipAppearance(this) }
            }
            binding.chipGroupLanguages.addView(chip)
        }

        // 4. Provider Save/Login Logic
        // 4. Provider Save/Login Logic
        binding.btnSaveProvider.setOnClickListener {
            val selectedName = binding.autoCompleteProvider.text.toString()
            val provider = providers.find { it.name == selectedName } ?: return@setOnClickListener

            val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val savedToken = prefs.getString(provider.prefKey, "") ?: ""

            when (provider.code) {
                "AD", "PM" -> {
                    if (savedToken.isEmpty()) {
                        // STATE 1: Not linked yet. Start the PIN process.
                        if (provider.code == "AD") startAllDebridPinFlow()
                        else startPremiumizePinFlow()
                    } else {
                        // STATE 2: Already linked! Just set it as active.
                        prefs.edit().putString("active_provider", provider.code).apply()
                        Toast.makeText(this, "${provider.name} Activated!", Toast.LENGTH_SHORT).show()
                        updateUIForProvider(provider) // Refresh the visuals
                    }
                }
                else -> {
                    // STATE 3: Real-Debrid / TorBox manual text entry
                    saveManualToken(provider)
                }
            }
        }

        binding.btnRemoveProvider.setOnClickListener {
            val selectedName = binding.autoCompleteProvider.text.toString()
            val provider = providers.find { it.name == selectedName }
            provider?.let { removeProvider(it) }
        }

        // Trakt.tv Logic
        binding.btnConnectTrakt.setOnClickListener {
            if (traktTokenManager.getAccessToken() == null) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://trakt.tv/oauth/authorize?response_type=code&client_id=$TRAKT_CLIENT_ID&redirect_uri=$TRAKT_REDIRECT_URI"))
                startActivity(intent)
            } else {
                traktTokenManager.clearTokens()
                updateTraktUI()
                Toast.makeText(this, "Trakt.tv Disconnected", Toast.LENGTH_SHORT).show()
            }
        }
        updateTraktUI()

        // Download Logic
        binding.settingDownloadLocation.setOnClickListener {
            downloadDirLauncher.launch(null)
        }
        val savedUri = prefs.getString("download_uri", null)
        binding.tvDownloadPath.text = savedUri?.let { Uri.parse(it).path ?: it } ?: "Not Set"

        // 5. Save All
        binding.btnSaveAll.setOnClickListener {
            saveAllSettings()
        }

        // Added Cache Cleanup Logic
        addClearCacheButton()

        // Initial Provider Load
        val activeCode = prefs.getString("active_provider", "RD")
        val initialProvider = providers.find { it.code == activeCode } ?: providers[0]
        binding.autoCompleteProvider.setText(initialProvider.name, false)
        updateUIForProvider(initialProvider)

        // Handle Trakt Redirect
        handleIntent(intent)
    }

    private fun setupAppLanguageChips() {
        val currentLangCode = if (!AppCompatDelegate.getApplicationLocales().isEmpty) 
            AppCompatDelegate.getApplicationLocales().get(0)?.language ?: "en" else "en"
        
        appLanguages.forEach { lang ->
            val chip = Chip(this).apply {
                text = lang.name
                isCheckable = true
                isChecked = lang.code == currentLangCode
                tag = lang.code
                updateChipAppearance(this)
                setOnCheckedChangeListener { _, isChecked ->
                    updateChipAppearance(this)
                }
            }
            binding.chipGroupAppLanguages.addView(chip)
        }
    }

    private fun saveAllSettings() {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        
        // Save App Language
        var selectedAppLang = "en"
        for (i in 0 until binding.chipGroupAppLanguages.childCount) {
            val chip = binding.chipGroupAppLanguages.getChildAt(i) as Chip
            if (chip.isChecked) {
                selectedAppLang = chip.tag.toString()
                break
            }
        }
        
        // Save Stream Languages
        val selectedStreamLangs = mutableSetOf<String>()
        for (i in 0 until binding.chipGroupLanguages.childCount) {
            val chip = binding.chipGroupLanguages.getChildAt(i) as Chip
            if (chip.isChecked) selectedStreamLangs.add(chip.tag.toString())
        }
        
        prefs.edit().putStringSet("pref_languages", selectedStreamLangs).apply()
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(selectedAppLang))
        
        Toast.makeText(this, "Settings Saved!", Toast.LENGTH_SHORT).show()
    }

    private fun removeProvider(provider: ProviderInfo) {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            remove(provider.prefKey)
            if (prefs.getString("active_provider", "") == provider.code) {
                remove("active_provider")
            }
            apply()
        }
        Toast.makeText(this, "${provider.name} Removed", Toast.LENGTH_SHORT).show()
        updateUIForProvider(provider)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val data: Uri? = intent?.data
        Log.d("TraktAuth", "Received Intent Data: $data")
        if (data != null && data.toString().startsWith(TRAKT_REDIRECT_URI)) {
            val code = data.getQueryParameter("code")
            Log.d("TraktAuth", "Auth Code: $code")
            if (code != null) {
                exchangeTraktCodeForToken(code)
            }
        }
    }

    private fun exchangeTraktCodeForToken(code: String) {
        lifecycleScope.launch {
            try {
                Log.d("TraktAuth", "Exchanging code for token...")
                val response = RetrofitClient.trakt.getToken(
                    code = code,
                    clientId = TRAKT_CLIENT_ID,
                    clientSecret = TRAKT_CLIENT_SECRET,
                    redirectUri = TRAKT_REDIRECT_URI
                )
                Log.d("TraktAuth", "Token exchange successful. AccessToken: ${response.accessToken}")
                traktTokenManager.saveTokens(response.accessToken, response.refreshToken)
                updateTraktUI()
                Toast.makeText(this@SettingsActivity, "Trakt.tv Connected!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("TraktAuth", "Token exchange failed", e)
                Toast.makeText(this@SettingsActivity, "Failed to connect Trakt.tv", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateTraktUI() {
        if (traktTokenManager.getAccessToken() != null) {
            binding.btnConnectTrakt.text = "Disconnect Trakt.tv"
            binding.tvTraktStatus.text = "Connected"
            binding.tvTraktStatus.setTextColor(Color.parseColor("#4CAF50"))
        } else {
            binding.btnConnectTrakt.text = "Connect Trakt.tv"
            binding.tvTraktStatus.text = "Not Connected"
            binding.tvTraktStatus.setTextColor(Color.parseColor("#AAAAAA"))
        }
    }

    private fun addClearCacheButton() {
        val clearBtn = Button(this).apply {
            text = "Clear Cache & Temp Files"
            setBackgroundColor(Color.parseColor("#333333"))
            setTextColor(Color.WHITE)
            setOnClickListener { clearAppCache() }
        }
        
        val parentLayout = binding.btnSaveAll.parent as? LinearLayout
        parentLayout?.let {
            val index = it.indexOfChild(binding.btnSaveAll)
            it.addView(clearBtn, index)
        }
    }

    private fun clearAppCache() {
        val cacheDir = externalCacheDir ?: cacheDir
        val sizeBefore = getFolderSize(cacheDir) / (1024 * 1024)
        cacheDir.deleteRecursively()
        cacheDir.mkdirs()
        Toast.makeText(this, "Cleared ${sizeBefore}MB of temporary files", Toast.LENGTH_LONG).show()
    }

    private fun getFolderSize(file: File): Long {
        var size: Long = 0
        if (file.isDirectory) {
            file.listFiles()?.forEach { size += getFolderSize(it) }
        } else {
            size = file.length()
        }
        return size
    }

    private fun updateUIForProvider(provider: ProviderInfo) {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        binding.tilApiToken.hint = provider.hint
        binding.tvStep1.text = "1. Go to ${provider.url}"
        
        val savedToken = prefs.getString(provider.prefKey, "") ?: ""
        binding.etApiToken.setText(savedToken)
        
        if (savedToken.isNotEmpty()) {
            binding.btnRemoveProvider.visibility = View.VISIBLE
        } else {
            binding.btnRemoveProvider.visibility = View.GONE
        }

        if (provider.code == "AD" || provider.code == "PM") {
            binding.tilApiToken.visibility = View.GONE
            if (savedToken.isNotEmpty()) {
                binding.btnSaveProvider.text = "${provider.name} Linked ✓ / Activate"
                binding.btnSaveProvider.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4CAF50"))
            } else {
                binding.btnSaveProvider.text = "Link ${provider.name} (PIN)"
                binding.btnSaveProvider.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E50914"))
            }
        } else {
            binding.btnSaveProvider.text = "Save & Login Provider"
            binding.btnSaveProvider.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4CAF50"))
            binding.tilApiToken.visibility = View.VISIBLE
        }
    }

    private fun saveManualToken(provider: ProviderInfo?) {
        if (provider == null) return
        val token = binding.etApiToken.text.toString().trim()
        if (token.isNotEmpty()) {
            getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit().apply {
                putString(provider.prefKey, token)
                putString("active_provider", provider.code)
                apply()
            }
            Toast.makeText(this, "${provider.name} Saved!", Toast.LENGTH_SHORT).show()
            updateUIForProvider(provider)
        }
    }

    private fun startAllDebridPinFlow() {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.allDebrid.getPin()
                val data = response.data ?: return@launch

                activeDialog = AlertDialog.Builder(this@SettingsActivity)
                    .setTitle("Link AllDebrid")
                    .setMessage("1. Go to: ${data.user_url}\n2. Enter this code: ${data.pin}\n\nWaiting for you to authorize...")
                    .setNegativeButton("Cancel") { _, _ -> pinCheckJob?.cancel() }
                    .create()
                activeDialog?.show()

                pinCheckJob?.cancel()
                pinCheckJob = lifecycleScope.launch {
                    repeat(24) {
                        delay(5000)

                        // ✅ THE FIX: We put the try/catch INSIDE the loop so it doesn't crash!
                        try {
                            val check = RetrofitClient.allDebrid.checkPin(pin = data.pin!!, check = data.check!!)
                            if (check.data?.activated == true && !check.data.apikey.isNullOrEmpty()) {
                                getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit().apply {
                                    putString("ad_token", check.data.apikey)
                                    putString("active_provider", "AD")
                                    apply()
                                }

                                // Make sure we only dismiss/toast if the app is still alive
                                if (!isFinishing && !isDestroyed) {
                                    activeDialog?.dismiss()
                                    Toast.makeText(this@SettingsActivity, "AllDebrid Linked!", Toast.LENGTH_LONG).show()
                                    updateUIForProvider(providers.find { it.code == "AD" }!!)
                                }
                                pinCheckJob?.cancel()
                            }
                        } catch (e: Exception) {
                            // IGNORE THE ERROR: It just means the user hasn't typed the PIN yet.
                            // The loop will safely continue and ask again in 5 seconds.
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, "Error starting PIN flow", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startPremiumizePinFlow() {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.premiumize.getPin()
                if (response.status != "success") return@launch
                
                activeDialog = AlertDialog.Builder(this@SettingsActivity)
                    .setTitle("Link Premiumize")
                    .setMessage("1. Go to: ${response.user_url}\n2. Enter this code: ${response.pin}\n\nWaiting for you to authorize...")
                    .setNegativeButton("Cancel") { _, _ -> pinCheckJob?.cancel() }
                    .create()
                activeDialog?.show()

                pinCheckJob?.cancel()
                pinCheckJob = lifecycleScope.launch {
                    repeat(24) {
                        delay(5000)
                        val check = RetrofitClient.premiumize.checkPin(pin = response.pin!!)
                        if (check.status == "success" && !check.apikey.isNullOrEmpty()) {
                            getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit().apply {
                                putString("pm_token", check.apikey)
                                putString("active_provider", "PM")
                                apply()
                            }
                            activeDialog?.dismiss()
                            Toast.makeText(this@SettingsActivity, "Premiumize Linked!", Toast.LENGTH_LONG).show()
                            updateUIForProvider(providers.find { it.code == "PM" }!!)
                            pinCheckJob?.cancel()
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, "Error starting PIN flow", Toast.LENGTH_SHORT).show()
            }
        }
    }


    private fun updateChipAppearance(chip: Chip) {
        val netflixRed = Color.parseColor("#E50914")
        if (chip.isChecked) {
            chip.chipBackgroundColor = ColorStateList.valueOf(netflixRed)
            chip.setTextColor(Color.WHITE)
            chip.isCheckedIconVisible = true
        } else {
            chip.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#1E1E1E"))
            chip.setTextColor(Color.GRAY)
            chip.isCheckedIconVisible = false
        }
    }

    data class ProviderInfo(val name: String, val code: String, val prefKey: String, val url: String, val hint: String)
    data class AppLanguage(val name: String, val code: String)

}
