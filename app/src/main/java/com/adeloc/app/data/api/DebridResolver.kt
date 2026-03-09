package com.adeloc.app.data.api

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.delay

object DebridResolver {

    private fun showStatus(context: Context, message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    suspend fun resolveStream(context: Context, magnetLink: String): String? {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val activeProvider = prefs.getString("active_provider", "RD")

        return when (activeProvider) {
            "AD" -> {
                val token = prefs.getString("ad_token", "") ?: ""
                android.util.Log.d("AD_DEBUG", "Starting AD Resolver. Token length: ${token.length}")

                if (token.isEmpty()) {
                    showStatus(context, "AllDebrid: Please link in Settings.")
                    return null
                }

                try {
                    val bearer = "Bearer $token"

                    // 1. Instant Check
                    var isCached = false
                    try {
                        android.util.Log.d("AD_DEBUG", "API: Checking Instant Cache...")
                        val instantRes = RetrofitClient.allDebrid.checkInstant(bearer, magnetLink)
                        val instantItem = instantRes.data?.magnets?.firstOrNull()
                        if (instantItem?.instant == true) {
                            isCached = true
                        }
                        android.util.Log.d("AD_DEBUG", "API: Cache Result - isCached=$isCached")
                    } catch (e: Exception) {
                        android.util.Log.e("AD_DEBUG", "API: Cache check failed: ${e.message}")
                    }

                    if (isCached) {
                        showStatus(context, "AllDebrid: Instant Play (Cached)...")
                    } else {
                        showStatus(context, "AllDebrid: Adding Torrent...")
                    }

                    // 2. Upload
                    android.util.Log.d("AD_DEBUG", "API: Uploading magnet to AD...")
                    val uploadRes = RetrofitClient.allDebrid.uploadMagnet(bearer, magnetLink, "Flixtorrent")

                    if (uploadRes.status != "success") {
                        android.util.Log.e("AD_DEBUG", "API: Upload Failed! Status: ${uploadRes.status} | Error: ${uploadRes.error?.message}")
                        showStatus(context, "AD: ${uploadRes.error?.message ?: "Upload failed"}")
                        return null
                    }

                    val magnetId = uploadRes.data?.magnets?.firstOrNull()?.id?.toString()
                    android.util.Log.d("AD_DEBUG", "API: Upload Success! Magnet ID: $magnetId")
                    if (magnetId == null) return null

                    // 3. High-Speed Polling Loop
                    var finalLink: String? = null
                    android.util.Log.d("AD_DEBUG", "API: Starting status polling loop...")

                    for (i in 1..30) {
                        val response = RetrofitClient.allDebrid.getStatus(bearer, magnetId)

                        // DUMP THE RAW JSON TO SEE THE SHAPE
                        android.util.Log.d("AD_DEBUG", "RAW RESPONSE: ${com.google.gson.Gson().toJson(response)}")

                        val magnets = response.data?.magnets
                        var detail: com.google.gson.JsonObject? = null

                        if (magnets != null) {
                            if (magnets.isJsonObject) {
                                val obj = magnets.asJsonObject
                                if (obj.has(magnetId)) detail = obj.getAsJsonObject(magnetId)
                                else detail = obj
                            } else if (magnets.isJsonArray && !magnets.asJsonArray.isEmpty) {
                                detail = magnets.asJsonArray.get(0).asJsonObject
                            }
                        }

                        val status = detail?.get("status")?.asString
                        android.util.Log.d("AD_DEBUG", "API: Poll Attempt $i - Status is: $status")

                        if (status.equals("Ready", ignoreCase = true)) {
                            // In v4.1 they renamed 'links' to 'files'
                            val files = detail?.getAsJsonArray("files")
                            android.util.Log.d("AD_DEBUG", "API: Status is Ready! Files found: ${files?.size()}")

                            if (files != null && !files.isEmpty) {
                                // And the actual url is under the 'l' key
                                val linkToUnlock = files.get(0).asJsonObject.get("l").asString
                                android.util.Log.d("AD_DEBUG", "API: Attempting to unlock link: $linkToUnlock")

                                // 4. UNLOCK
                                val unlockRes = RetrofitClient.allDebrid.unlockLink(bearer, linkToUnlock)
                                if (unlockRes.status == "success") {
                                    finalLink = unlockRes.data?.link
                                    android.util.Log.d("AD_DEBUG", "API: SUCCESS! Final Video Link Acquired!")
                                    break
                                } else {
                                    android.util.Log.e("AD_DEBUG", "API: Unlock Failed! Status: ${unlockRes.status}")
                                }
                            }
                        }

                        if (finalLink == null) {
                            delay(2000)
                        }
                    }

                    if (finalLink == null) {
                        android.util.Log.e("AD_DEBUG", "API: Polling timed out after 30 attempts (60 seconds).")
                    }
                    return finalLink

                } catch (e: Exception) {
                    // This is the safety net that catches silent API crashes
                    android.util.Log.e("AD_DEBUG", "API: Exception caught in AD Resolver!")
                    if (e is retrofit2.HttpException) {
                        val errorBody = e.response()?.errorBody()?.string()
                        android.util.Log.e("AD_DEBUG", "HTTP Error Body: $errorBody")
                    } else {
                        android.util.Log.e("AD_DEBUG", "Error: ${e.message}")
                    }
                    e.printStackTrace()
                    showStatus(context, "AD: Error resolving link.")
                    return null
                }
            }
            "TB" -> {
                val token = prefs.getString("tb_token", "") ?: ""
                if (token.isEmpty()) return null

                try {
                    val bearer = "Bearer $token"
                    val rawApiKey = token.trim()

                    // Extract Hash and Check Cache
                    val infoHash = magnetLink.substringAfter("xt=urn:btih:").substringBefore("&").lowercase()
                    var isCached = false

                    try {
                        val cacheRes = RetrofitClient.torbox.checkCached(bearer, infoHash)
                        if (cacheRes.success == true && cacheRes.data?.containsKey(infoHash) == true) {
                            isCached = true
                        }
                    } catch (e: Exception) {}

                    if (isCached) {
                        showStatus(context, "TorBox: Instant Play (Cached)...")
                        android.util.Log.d("TORBOX_SPEED", "UI: Showing Instant Play")
                    } else {
                        showStatus(context, "TorBox: Adding Torrent...")
                        android.util.Log.d("TORBOX_SPEED", "UI: Showing Adding Torrent")
                    }

                    // Add to TorBox
                    android.util.Log.d("TORBOX_SPEED", "API: Calling createTorrent...")
                    val createRes = RetrofitClient.torbox.createTorrent(bearer, magnetLink, 1, false)
                    if (createRes.success != true) return null
                    val newId = createRes.data?.torrent_id ?: return null
                    android.util.Log.d("TORBOX_SPEED", "API: Torrent Created! ID: $newId")

                    // Wait for TorBox to populate the files
                    var torrentId = 0
                    var fileId = 0

                    android.util.Log.d("TORBOX_SPEED", "API: Starting MyList Loop to find fileId...")
                    for (i in 1..30) {
                        val listRes = RetrofitClient.torbox.getMyList(bearer)
                        val torrent = listRes.data?.find { it.id == newId }

                        if (torrent != null && !torrent.files.isNullOrEmpty()) {
                            torrentId = torrent.id
                            fileId = torrent.files!!.maxByOrNull { it.size }?.id ?: 0
                            android.util.Log.d("TORBOX_SPEED", "API: Files found on attempt $i! FileID: $fileId")
                            break // THIS IS THE MAGIC WORD! It stops the loop instantly!
                        }

                        android.util.Log.d("TORBOX_SPEED", "API: Waiting on TorBox to populate files... (Attempt $i)")
                        delay(2000)
                    }

                    // Get the Download Link
                    if (torrentId != 0) {
                        android.util.Log.d("TORBOX_SPEED", "API: Starting requestDownload Loop...")
                        var streamLink: String? = null
                        var attempts = 0

                        while (attempts < 15) {
                            try {
                                val dlRes = RetrofitClient.torbox.requestDownload(bearer, rawApiKey, torrentId, fileId)
                                if (dlRes.success == true && !dlRes.data.isNullOrEmpty()) {
                                    streamLink = dlRes.data
                                    android.util.Log.d("TORBOX_SPEED", "API: SUCCESS! Link Acquired!")
                                    break
                                }
                            } catch (e: Exception) {}

                            android.util.Log.d("TORBOX_SPEED", "API: Waiting for CDN link... (Attempt ${attempts + 1})")
                            delay(2000)
                            attempts++
                        }

                        if (streamLink != null) return streamLink
                    }
                    return null
                } catch (e: Exception) { return null }
            }
            "PM" -> {
                val token = prefs.getString("pm_token", "") ?: ""
                if (token.isEmpty()) return null
                try {
                    val res = RetrofitClient.premiumize.directDownload(src = magnetLink, apiKey = token)
                    if (res.status == "success") res.location else null
                } catch (e: Exception) { null }
            }
            else -> {
                val token = prefs.getString("rd_token", "") ?: ""
                if (token.isEmpty()) return null
                try {
                    val bearer = "Bearer $token"
                    val addResponse = RetrofitClient.realDebrid.addMagnet(bearer, magnetLink)
                    val id = addResponse.id
                    RetrofitClient.realDebrid.selectFiles(bearer, id, "all")
                    var link = ""
                    repeat(10) {
                        val info = RetrofitClient.realDebrid.getTorrentInfo(bearer, id)
                        if (info.links.isNotEmpty()) {
                            link = info.links[0]
                            return@repeat
                        }
                        delay(1000)
                    }
                    if (link.isNotEmpty()) {
                        RetrofitClient.realDebrid.unrestrictLink(bearer, link).download
                    } else null
                } catch (e: Exception) { null }
            }
        }
    }
}