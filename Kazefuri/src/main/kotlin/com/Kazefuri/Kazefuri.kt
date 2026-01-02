package com.kazefuri

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import org.jsoup.nodes.Element

class Kazefuri : MainAPI() {
    override var mainUrl = "https://sv3.kazefuri.cloud"
    override var name = "Kazefuri"
    override val hasMainPage = true
    override var lang = "id" // Sesuaikan bahasa konten
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)

    // 1. Scraping Halaman Utama
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(mainUrl).document
        val items = document.select("div.item").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, items)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h2")?.text() ?: return null
        val href = this.selectFirst("a")?.attr("href") ?: return null
        val posterUrl = this.selectFirst("img")?.attr("src")

        return movieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    // 2. Scraping Pencarian
    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        val document = app.get(url).document
        return document.select("div.item").mapNotNull { it.toSearchResult() }
    }

    // 3. Scraping Detail & Episode
    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title = document.selectFirst("h1.entry-title")?.text() ?: ""
        val poster = document.selectFirst("img.poster")?.attr("src")
        val plot = document.selectFirst("div.description")?.text()

        // Logika untuk mendeteksi apakah ini Movie atau Series
        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.plot = plot
        }
    }

    // 4. Scraping Link Video (Play Video)
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        
        // Kazefuri biasanya menggunakan iframe atau player internal
        // Contoh mencari link m3u8 atau mp4:
        document.select("source").forEach { source ->
            val videoUrl = source.attr("src")
            callback.invoke(
                ExtractorLink(
                    this.name,
                    "Kazefuri Player",
                    videoUrl,
                    referer = mainUrl,
                    quality = Qualities.P720.value, // Anda bisa mendeteksi kualitas secara dinamis
                    isM3u8 = videoUrl.contains(".m3u8")
                )
            )
        }
        return true
    }
}
