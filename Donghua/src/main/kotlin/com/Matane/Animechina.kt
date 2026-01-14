package com.Matane

import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup

class Animechina : Anichin() {
    override var mainUrl              = "https://animechina.my.id"
    override var name                 = "Anime China"
    override val hasMainPage          = true
    override var lang                 = "id"
    override val hasDownloadSupport   = true
    override val supportedTypes       = setOf(TvType.Movie,TvType.Anime)

    override val mainPage = mainPageOf(
        "" to "Update Terbaru",
        "ongoing/" to "Ongoing"
    )
override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val path = if (request.data.isEmpty()) "/page/$page/" else request.data + "&page=$page"
        val url = "$mainUrl$path".replace("//", "/")

        val response = app.get(url, timeout = 50)
        if (response.code != 200) {
            // Jika 403 atau error koneksi, return empty agar tidak crash
            return newHomePageResponse(emptyList(), hasNext = false)
        }

        val doc = response.document

        // Selector utama dari snippet HTML kamu
        var items = doc.select("div.series__card").mapNotNull { it.toSearchResult() }

        // Fallback selector umum jika series__card tidak ada di semua page
        if (items.isEmpty()) {
            items = doc.select("article, .bsx, .bs, .tip, .anime-item, div.series__thumbnail").mapNotNull { it.toSearchResult() }
        }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = items,
                isHorizontalImages = false
            ),
            hasNext = doc.select("a.next, .pagination a[href*='page=']").isNotEmpty()
        )
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val linkElem = selectFirst("a[href][title]") ?: return null
        val title = linkElem.attr("title").trim().ifEmpty {
            selectFirst("div.series__title h2")?.text()?.trim() ?: ""
        }
        if (title.isEmpty()) return null

        val href = fixUrl(linkElem.attr("href"))

        // Prioritas data-src untuk lazyload (seperti di snippet kamu)
        val imgElem = selectFirst("img.lazyload")
        val poster = imgElem?.let {
            it.attr("data-src").ifBlank {
                it.attr("data-lazy-src").ifBlank { it.attr("src") }
            }
        }?.trim()?.let { fixUrlNull(it) }

        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = poster
        }
    }



}


