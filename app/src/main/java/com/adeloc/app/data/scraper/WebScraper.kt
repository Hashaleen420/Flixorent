package com.adeloc.app.data.scraper

import android.util.Log
import com.adeloc.app.data.model.StreamSource
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException
import java.net.SocketTimeoutException

object WebScraper {

    suspend fun scrapeWeb(query: String): List<StreamSource> {
        return withContext(Dispatchers.IO) {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val results = mutableListOf<StreamSource>()

            Log.d("WebScraper", "Starting HIGH-SPEED Parallel Scrape for: $query")

            // PARALLEL EXECUTION: Launch every provider independently
            val scrapers = listOf(
                async { fetchProvider("TPB", 5000L) { fetchThePirateBay(encodedQuery, query) } },
                async { fetchProvider("YTS", 5000L) { fetchYTS(encodedQuery, query) } }
                // Add more providers here as async blocks
            )

            // Gather all that finished within 5s
            scrapers.awaitAll().filterNotNull().forEach { results.addAll(it) }

            // Final Sort
            results.sortedByDescending {
                try {
                    it.quality.substringAfter("S:").substringBefore("|").trim().toInt()
                } catch (e: Exception) { 0 }
            }
        }
    }

    private suspend fun fetchProvider(name: String, timeout: Long, block: () -> List<StreamSource>): List<StreamSource>? {
        return try {
            withTimeoutOrNull(timeout) {
                block()
            }
        } catch (e: UnknownHostException) {
            Log.e("WebScraper", "$name: DNS Error")
            null
        } catch (e: SocketTimeoutException) {
            Log.e("WebScraper", "$name: Connection Timeout")
            null
        } catch (e: Exception) {
            Log.e("WebScraper", "$name: Error ${e.message}")
            null
        }
    }

    private fun fetchThePirateBay(encodedQuery: String, originalQuery: String): List<StreamSource> {
        val sources = mutableListOf<StreamSource>()
        try {
            val url = "https://apibay.org/q.php?q=$encodedQuery"
            val jsonStr = URL(url).readText()
            val jsonArray = JSONArray(jsonStr)

            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                val name = item.getString("name")
                val seeds = item.getString("seeders").toInt()
                val sizeBytes = item.getString("size").toLong()
                val infoHash = item.getString("info_hash")

                if (!isRelevant(name, originalQuery)) continue
                if (seeds == 0 || infoHash == "0000000000000000000000000000000000000000") continue

                val magnet = "magnet:?xt=urn:btih:$infoHash&dn=${URLEncoder.encode(name, "UTF-8")}"
                val sizeGB = String.format("%.2f GB", sizeBytes / (1024.0 * 1024.0 * 1024.0))

                sources.add(StreamSource(name, magnet, "TPB | S:$seeds | $sizeGB", true))
            }
        } catch (e: Exception) { throw e }
        return sources
    }

    private fun fetchYTS(encodedQuery: String, originalQuery: String): List<StreamSource> {
        val sources = mutableListOf<StreamSource>()
        try {
            val url = "https://yts.mx/api/v2/list_movies.json?query_term=$encodedQuery"
            val jsonStr = URL(url).readText()
            val root = JSONObject(jsonStr)

            if (root.has("data") && root.getJSONObject("data").has("movies")) {
                val movies = root.getJSONObject("data").getJSONArray("movies")

                for (i in 0 until movies.length()) {
                    val movie = movies.getJSONObject(i)
                    val title = movie.getString("title")

                    if (!isRelevant(title, originalQuery)) continue

                    val year = movie.getInt("year")
                    val torrents = movie.getJSONArray("torrents")

                    for (j in 0 until torrents.length()) {
                        val torrent = torrents.getJSONObject(j)
                        val quality = torrent.getString("quality")
                        val seeds = torrent.getInt("seeds")
                        val size = torrent.getString("size")
                        val hash = torrent.getString("hash")

                        val magnet = "magnet:?xt=urn:btih:$hash&dn=${URLEncoder.encode(title, "UTF-8")}"
                        sources.add(StreamSource("$title ($year)", magnet, "YTS | $quality | S:$seeds | $size", true))
                    }
                }
            }
        } catch (e: Exception) { throw e }
        return sources
    }

    private fun isRelevant(resultTitle: String, query: String): Boolean {
        val cleanResult = resultTitle.lowercase()
        val cleanQuery = query.lowercase().replace(Regex("[^a-z0-9 ]"), "")
        val queryWords = cleanQuery.split(" ").filter { it.length > 1 }
        val stopWords = listOf("the", "and", "or", "of", "in", "a", "an", "movie", "film", "series")

        var significantWords = 0
        var matches = 0

        for (word in queryWords) {
            if (word !in stopWords) {
                significantWords++
                val regex = "\\b$word\\b".toRegex()
                if (regex.containsMatchIn(cleanResult)) {
                    matches++
                }
            }
        }
        return if (significantWords > 0) matches > 0 else true
    }
}
