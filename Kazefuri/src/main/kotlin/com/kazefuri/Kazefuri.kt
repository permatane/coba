package com.kazefuri

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addAniListId
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Document

class Kazefuri : MainAPI() {

    override var mainUrl = "https://sv3.kazefuri.cloud"
    override var name = "Kazefuri"
    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.Movie
    )

    override var lang = "id"
    override val hasMainPage = true

    // ===================== HOMEPAGE =====================

    override val mainPage = mainPageOf(
        "" to "Terbaru"
    )

    override fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {

        val url = if (page == 1)
            mainUrl
        else
            "$mainUrl/page/$page/"

        val document = app.get(url).document

        val items = document.select("article.bs").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            request.name,
            items,
            hasNextPage = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val a = selectFirst("a") ?: return null
        val title = selectFirst("div.tt h2")?.text()?.trim() ?: return null
        val poster = selectFirst("div.limit img")?.attr("src")

        return newMovieSearchResponse(
            title,
            fixUrl(a.attr("href")),
            TvType.Anime
        ) {
            this.posterUrl = poster
        }
    }

    // ===================== SEARCH =====================

    override fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=${query.replace(" ", "+")}"
        val document = app.get(url).document

        return document.select("article.bs").mapNotNull {
            it.toSearchResult()
        }
    }

    // ===================== LOAD DETAIL =====================

    override fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title =
            document.selectFirst("h1")?.text()
                ?: document.selectFirst("meta[property=og:title]")?.attr("content")
                ?: "Kazefuri Video"

        val poster =
            document.selectFirst("div.thumb img")?.attr("src")
                ?: document.selectFirst("meta[property=og:image]")?.attr("content")

        return newMovieLoadResponse(
            title,
            url,
            TvType.Anime,
            url
        ) {
            this.posterUrl = poster
            this.plot =
                document.selectFirst("meta[property=og:description]")?.attr("content")
        }
    }

    // ===================== VIDEO LINKS =====================

    override fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val document = app.get(data).document

        document.select("iframe[src]").forEach { iframe ->
            val src = iframe.attr("src")
            if (src.isNotBlank()) {
                callback.invoke(
                    ExtractorLink(
                        source = name,
                        name = name,
                        url = fixUrl(src),
                        referer = mainUrl,
                        quality = Qualities.Unknown.value,
                        isM3u8 = src.contains(".m3u8")
                    )
                )
            }
        }

        return true
    }
}
