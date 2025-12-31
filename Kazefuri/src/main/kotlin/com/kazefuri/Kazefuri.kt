package com.kazefuri

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import org.jsoup.nodes.Document

class Kazefuri : MainAPI() {
    override var mainUrl = "https://sv3.kazefuri.cloud"
    override var name = "Kazefuri"
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie)
    override var lang = "id"  // Indonesian subtitles
    override val hasMainPage = true
    override val hasQuickSearch = true

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/").document
        val homeItems = document.select("a").mapNotNull { it.toSearchResult() }
            .distinctBy { it.url }

        val homePageLists = mutableListOf<HomePageList>()
        if (homeItems.isNotEmpty()) {
            homePageLists.add(HomePageList("Series Tersedia / Populer", homeItems))
        }

        return HomePageResponse(homePageLists)
    }

    private fun org.jsoup.nodes.Element.toSearchResult(): SearchResponse? {
        val href = this.attr("href").takeIf { it.startsWith("/") || it.startsWith("http") } ?: return null
        val fullHref = fixUrl(href)

        if (fullHref.contains("-episode-") || fullHref.contains("/genres/") || fullHref.contains("/page/") || fullHref.contains("/search/")) {
            return null
        }

        val title = this.text().trim().takeIf { it.isNotEmpty() } ?: return null

        // Poster extraction: coba img di dalam <a> atau sibling
        val posterImg = this.selectFirst("img") 
            ?: this.parent()?.selectFirst("img")
            ?: this.nextElementSibling()?.selectFirst("img")
        val posterUrl = posterImg?.attr("src")?.takeIf { it.contains("cover", ignoreCase = true) || it.contains("poster", ignoreCase = true) || it.endsWith(".jpg") || it.endsWith(".png") || it.endsWith(".webp") }
            ?.let { fixUrl(it) }
            ?: posterImg?.attr("data-src")?.let { fixUrl(it) }  // Lazyload fallback

        return newAnimeSearchResponse(title, fullHref, TvType.Anime) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> {
        val document = app.get(mainUrl).document
        return document.select("a")
            .mapNotNull { it.toSearchResult() }
            .filter { it.name.contains(query, ignoreCase = true) }
            .distinctBy { it.url }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return emptyList()
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("h1, h2, h3")?.ownText()?.trim()
            ?: document.selectFirst("title")?.text()?.substringBefore(" - Kazefuri")?.trim()
            ?: ""

        val description = document.select("p").joinToString("\n") { it.text().trim() }
            .takeIf { it.isNotEmpty() }
            ?: document.selectFirst("h2:contains(Synopsis), h3:contains(Sinopsis)")?.nextElementSibling()?.text()

        // Poster extraction di detail page (common selectors)
        val posterUrl = document.selectFirst("img.cover, img.poster, img.thumbnail, img[src*=/cover/], img[src*=/poster/], img[src*=/thumb/], img:first-of-type")
            ?.absUrl("src")
            ?: document.selectFirst("img[src$=.jpg], img[src$=.png], img[src$=.webp]")?.absUrl("src")
            ?: document.selectFirst("img[data-src]")?.absUrl("data-src")  // Lazyload

        val episodes = mutableListOf<Episode>()

        document.select("a[href*=-episode-]").forEach { epElement ->
            val epHref = fixUrl(epElement.attr("href"))
            val epText = epElement.text().trim()

            val epNum = Regex("-episode-(\\d+)").find(epHref)?.groupValues?.get(1)?.toIntOrNull()
                ?: Regex("Episode[\\s-]*(\\d+)", ignoreCase = true).find(epText)?.groupValues?.get(1)?.toIntOrNull()
                ?: Regex("(\\d+)").find(epText)?.groupValues?.get(1)?.toIntOrNull()
                ?: 0

            val epName = epText.ifEmpty { "Episode $epNum" }

            var seasonNum = 1
            var current: org.jsoup.nodes.Element? = epElement
            while (current != null) {
                current = current.previousElementSibling()
                if (current == null) break
                if (current.tagName().matches(Regex("h[1-6]"))) {
                    val headingText = current.text()
                    val seasonMatch = Regex("(Musim|Season|Part)[\\s-]*(\\d+)", ignoreCase = true).find(headingText)
                    if (seasonMatch != null) {
                        seasonNum = seasonMatch.groupValues[2].toIntOrNull() ?: 1
                        break
                    }
                }
            }

            if (epNum > 0) {
                episodes.add(Episode(epHref, epName, seasonNum, epNum))
            }
        }

        episodes.sortBy { it.season * 10000 + (it.episode ?: 0) }

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            this.posterUrl = posterUrl  // Akan null jika tidak ada gambar
            this.plot = description
            this.tags = emptyList()
            addEpisodes(DubStatus.Subbed, episodes)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document

        document.select("iframe[src], iframe[data-src], video source[src], button[data-src], a[data-url]").forEach {
            val src = it.attr("src").takeIf { it.isNotEmpty() } 
                ?: it.attr("data-src")?.takeIf { it.isNotEmpty() } 
                ?: it.attr("data-url")?.takeIf { it.isNotEmpty() } 
                ?: return@forEach
            loadExtractor(fixUrl(src), data, subtitleCallback, callback)
        }

        document.select("video source[src]").forEach {
            val quality = it.attr("label").takeIf { it.isNotEmpty() } ?: "720p"
            callback(ExtractorLink(name, name, it.attr("src"), "", getQualityFromName(quality)))
        }

        return document.select("iframe, video").isNotEmpty()
    }
}