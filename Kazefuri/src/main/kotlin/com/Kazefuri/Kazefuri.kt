package com.lagradost.cloudstream3.plugins

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.extractors.*
import org.jsoup.nodes.Element
import java.util.*

class KazefuriPlugin : MainAPI() {
    override var mainUrl = "https://sv3.kazefuri.cloud"
    override var name = "Kazefuri"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Anime)
    
    // ==================== SCRAPING UTILITAS ====================
    private suspend fun scrapVideoFromIframe(iframeUrl: String, callback: (ExtractorLink) -> Unit): Boolean {
        return try {
            val iframeDoc = app.get(iframeUrl).document
            
            // Teknik 1: Cari source video langsung di iframe
            val directSources = iframeDoc.select("source[src]").mapNotNull {
                val src = it.attr("src")
                if (src.contains(".m3u8") || src.contains(".mp4")) {
                    ExtractorLink(
                        name,
                        "Direct Source",
                        src,
                        "$mainUrl/",
                        Qualities.Unknown.value,
                        isM3u8 = src.contains(".m3u8")
                    )
                } else null
            }
            
            // Teknik 2: Ekstrak dari JavaScript variables
            val scriptContent = iframeDoc.select("script").html()
            val videoRegex = Regex("""(?i)(src|file|url)\s*[=:]\s*['"]([^'"]+\.(?:m3u8|mp4|mkv)[^'"]*)['"]""")
            val jsMatches = videoRegex.findAll(scriptContent).map {
                ExtractorLink(
                    name,
                    "JS Variable",
                    it.groupValues[2],
                    iframeUrl,
                    Qualities.Unknown.value,
                    isM3u8 = it.groupValues[2].contains(".m3u8")
                )
            }.toList()
            
            // Teknik 3: Cari di window.player atau player config
            val playerConfigRegex = Regex("""player\.setup\((\{.*?\})""", RegexOption.DOT_MATCHES_ALL)
            playerConfigRegex.find(scriptContent)?.groupValues?.get(1)?.let { jsonStr ->
                // Parse JSON sederhana untuk file/video
                val fileMatch = Regex(""""file"\s*:\s*"([^"]+)"""").find(jsonStr)
                fileMatch?.groupValues?.get(1)?.let { videoUrl ->
                    callback(ExtractorLink(
                        name,
                        "Player Config",
                        videoUrl,
                        iframeUrl,
                        Qualities.Unknown.value
                    ))
                }
            }
            
            // Gabungkan semua hasil
            (directSources + jsMatches).forEach { callback(it) }
            
            directSources.isNotEmpty() || jsMatches.isNotEmpty()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    private suspend fun extractFromEmbedApi(episodeId: String, callback: (ExtractorLink) -> Unit) {
        // Coba berbagai pola API yang umum
        val apiPatterns = listOf(
            "$mainUrl/api/player?id=$episodeId",
            "$mainUrl/embed/$episodeId",
            "$mainUrl/player?episode=$episodeId",
            "$mainUrl/ajax/player?hash=$episodeId"
        )
        
        for (apiUrl in apiPatterns) {
            try {
                val response = app.get(apiUrl)
                if (response.isSuccessful) {
                    val json = response.parsedSafe<Map<String, Any>>()
                    
                    // Coba berbagai key yang mungkin
                    val videoKeys = listOf("url", "source", "file", "video_url", "m3u8", "mp4")
                    for (key in videoKeys) {
                        (json?.get(key) as? String)?.let { videoUrl ->
                            if (videoUrl.isNotBlank() && (videoUrl.contains("://") || videoUrl.startsWith("//"))) {
                                val finalUrl = if (videoUrl.startsWith("//")) "https:$videoUrl" else videoUrl
                                callback(ExtractorLink(
                                    name,
                                    "API Source",
                                    finalUrl,
                                    apiUrl,
                                    Qualities.Unknown.value,
                                    isM3u8 = finalUrl.contains(".m3u8")
                                ))
                                return
                            }
                        }
                    }
                    
                    // Coba nested sources
                    (json?.get("sources") as? List<Map<String, Any>>)?.forEach { source ->
                        (source["file"] as? String)?.let { videoUrl ->
                            callback(ExtractorLink(
                                name,
                                "API Source",
                                videoUrl,
                                apiUrl,
                                (source["label"] as? String)?.let { parseQuality(it) } ?: Qualities.Unknown.value
                            ))
                        }
                    }
                }
            } catch (e: Exception) {
                continue
            }
        }
    }
    
    // ==================== LOAD LINKS - MULTI-LEVEL SCRAPING ====================
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        
        // CATATAN: Anda perlu menyesuaikan selector berdasarkan struktur HTML sebenarnya
        // Berikut adalah kemungkinan pola yang perlu diverifikasi:
        
        // 1. Extract episode ID dari URL
        val episodeId = data.substringAfterLast("/").substringBefore("?")
        
        // 2. Scrap dari iframe player (paling umum)
        val iframeSrc = document.selectFirst("iframe[src]")?.attr("src")
        if (!iframeSrc.isNullOrBlank()) {
            val fullIframeUrl = if (iframeSrc.startsWith("http")) iframeSrc else "$mainUrl$iframeSrc"
            if (scrapVideoFromIframe(fullIframeUrl, callback)) {
                return true
            }
        }
        
        // 3. Cari script dengan video data (biasanya di player JavaScript)
        val scripts = document.select("script")
        for (script in scripts) {
            val scriptHtml = script.html()
            
            // Pola 1: videojs atau plyr setup
            if (scriptHtml.contains("videojs") || scriptHtml.contains("plyr.setup")) {
                val sourcesMatch = Regex("""sources\s*:\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL)
                    .find(scriptHtml)
                sourcesMatch?.groupValues?.get(1)?.let { sourcesStr ->
                    val urlMatch = Regex(""""(https?://[^"]+\.(?:m3u8|mp4)[^"]*)"""").findAll(sourcesStr)
                    urlMatch.forEach { match ->
                        callback(ExtractorLink(
                            name,
                            "VideoJS Source",
                            match.groupValues[1],
                            data,
                            Qualities.Unknown.value,
                            isM3u8 = match.groupValues[1].contains(".m3u8")
                        ))
                    }
                }
            }
            
            // Pola 2: Base64 encoded video data
            if (scriptHtml.contains("base64") && scriptHtml.length > 1000) {
                val b64Match = Regex("""["']([A-Za-z0-9+/=]{100,})["']""").find(scriptHtml)
                b64Match?.groupValues?.get(1)?.let { b64String ->
                    try {
                        val decoded = String(Base64.getDecoder().decode(b64String))
                        val videoUrls = Regex("""https?://[^\s"']+\.(?:m3u8|mp4)""").findAll(decoded)
                        videoUrls.forEach { match ->
                            callback(ExtractorLink(
                                name,
                                "Base64 Decoded",
                                match.value,
                                data,
                                Qualities.Unknown.value
                            ))
                        }
                    } catch (e: Exception) {
                        // Ignore base64 decode errors
                    }
                }
            }
        }
        
        // 4. Coba API extraction sebagai fallback
        extractFromEmbedApi(episodeId, callback)
        
        // 5. Cari data attributes di player div
        document.select("div[data-player], div[data-src], video[data-src]").forEach { playerDiv ->
            playerDiv.attr("data-src").takeIf { it.isNotBlank() }?.let { videoUrl ->
                callback(ExtractorLink(
                    name,
                    "Data Attribute",
                    if (videoUrl.startsWith("//")) "https:$videoUrl" else videoUrl,
                    data,
                    Qualities.Unknown.value
                ))
            }
        }
        
        // 6. Coba dengan extractor eksternal jika ada
        val extractors = listOf(
            StreamTapeExtractor(),
            StreamWishExtractor(),
            Mp4UploadExtractor()
        )
        
        for (extractor in extractors) {
            try {
                val links = extractor.getUrl(data, mainUrl, false)
                if (links.isNotEmpty()) {
                    links.forEach { callback(it) }
                    return true
                }
            } catch (e: Exception) {
                continue
            }
        }
        
        return false
    }
    
    // ==================== PARSING UTILITIES ====================
    private fun parseQuality(label: String): Int {
        return when {
            label.contains("1080") -> Qualities.P1080.value
            label.contains("720") -> Qualities.P720.value
            label.contains("480") -> Qualities.P480.value
            label.contains("360") -> Qualities.P360.value
            else -> Qualities.Unknown.value
        }
    }
    
    // ==================== SUBTITLE SUPPORT ====================
    private suspend fun extractSubtitles(document: Element): List<SubtitleFile> {
        val subtitles = mutableListOf<SubtitleFile>()
        
        // Cari subtitle track
        document.select("track[kind=subtitles], track[src]").forEach { track ->
            val src = track.attr("src")
            if (src.isNotBlank() && (src.endsWith(".vtt") || src.endsWith(".srt"))) {
                val lang = track.attr("srclang").ifBlank { 
                    track.attr("label").ifBlank { "id" }
                }
                subtitles.add(SubtitleFile(lang, src))
            }
        }
        
        return subtitles
    }
    
    companion object {
        // Helper untuk debugging
        fun debugLog(message: String) {
            println("[Kazefuri] $message")
        }
    }
}