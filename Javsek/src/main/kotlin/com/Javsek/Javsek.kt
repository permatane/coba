package com.javsek

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class Javsek : MainAPI() {

    override var name = "Javsek"
    override var mainUrl = "https://javsek.net"
    override var lang = "id"
    override val supportedTypes = setOf(TvType.Movie)
    override val hasMainPage = true

    // ================= MAIN PAGE =================

    override val mainPage = mainPageOf(
        "/category/indo-sub/" to "Sub Indo"
    )

    override fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {

        val url = if (page == 1) {
            mainUrl + request.data
        } else {
            "${mainUrl}${request.data}page/$page/"
        }

        val document = app.get(url).document

        val items = document.select("article").mapNotNull { article: Element ->
            val link = article.selectFirst("a") ?: return@mapNotNull null
            val title = article.selectFirst("h2")?.text()?.trim()
                ?: return@mapNotNull null
            val poster = article.selectFirst("img")?.attr("src")

            newMovieSearchResponse(
                title,
                fixUrl(link.attr("href")),
                TvType.Movie
            ) {
                this.posterUrl = poster
            }
        }

        return newHomePageResponse(
            request.name,
            items,
            hasNextPage = true
        )
    }

    // ================= SEARCH =================

    override fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/?s=${query.replace(" ", "+")}"
        val document = app.get(searchUrl).document

        return document.select("article").mapNotNull { article: Element ->
            val link = article.selectFirst("a") ?: return@mapNotNull null
            val title = article.selectFirst("h2")?.text()?.trim()
                ?: return@mapNotNull null
            val poster = article.selectFirst("img")?.attr("src")

            newMovieSearchResponse(
                title,
                fixUrl(link.attr("href")),
                TvType.Movie
            ) {
                this.posterUrl = poster
            }
        }
    }

    // ================= LOAD DETAIL =================

    override fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("h1.entry-title")?.text()
            ?: "Javsek Video"

        val poster = document
            .selectFirst("meta[property=og:image]")
            ?.attr("content")

        val description = document
            .selectFirst("meta[property=og:description]")
            ?.attr("content")

        return newMovieLoadResponse(
            title,
            url,
            TvType.Movie,
            url
        ) {
            this.posterUrl = poster
            this.plot = description
        }
    }

    // ================= VIDEO LINKS =================

    override fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val document = app.get(data).document

        document.select("iframe[src]").forEach { iframe: Element ->
            val iframeUrl = iframe.attr("src")
            if (iframeUrl.isNotBlank()) {
                loadExtractor(
                    fixUrl(iframeUrl),
                    data,
                    subtitleCallback,
                    callback
                )
            }
        }

        return true
    }
}

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data, headers = mainHeaders).text

        val regex = Regex(pattern = "\"iframe_url\":\"([^\"]*)\"", options = setOf(RegexOption.IGNORE_CASE))
        val iframe = regex.findAll(document)

        iframe.forEach { iframeMatch ->
            val iframeEncoded = iframeMatch.groupValues[1]
            val iframeCoz = base64Decode(iframeEncoded)


            val iframeAl = app.get(iframeCoz, mainHeaders).text

            val frameBaseRegex = Regex(pattern = "frameBase = '([^']*)'", options = setOf(RegexOption.IGNORE_CASE))
            val paramRegex = Regex(pattern = "param = '([^']*)'", options = setOf(RegexOption.IGNORE_CASE))
            val olidRegex = Regex(pattern = "OLID = '([^']*)'", options = setOf(RegexOption.IGNORE_CASE))

            val frameBase = frameBaseRegex.find(iframeAl)?.groupValues?.get(1)
            val param = paramRegex.find(iframeAl)?.groupValues?.get(1)
            val olid = olidRegex.find(iframeAl)?.groupValues?.get(1)

            if (frameBase != null && param != null && olid != null) {
                val olidReverse = olid.reversed()
                val urlOlustur = "$frameBase?$param=$olidReverse"


                try {
                    val sonUrlAl = app.get(urlOlustur, mainHeaders).document
                    val scriptAl = sonUrlAl.selectFirst("script:containsData(eval)")?.data()

                    if (scriptAl != null) {
                        val jsUnpacker = getAndUnpack(scriptAl)

                        val hlsLinkRegex = Regex(pattern = "\"hls[0-9]+\":\"([^\"]*)\"", options = setOf(RegexOption.IGNORE_CASE))
                        val hlsler = hlsLinkRegex.findAll(jsUnpacker)

                        hlsler.forEach { hls ->
                            val m3u8 = hls.groupValues[1]
                            val videoLink = if (!m3u8.contains("http")) {
                                "https://javclan.com$m3u8"
                            } else {
                                m3u8
                            }

                            callback.invoke(
                                newExtractorLink(
                                    this.name,
                                    this.name,
                                    videoLink,
                                    ExtractorLinkType.M3U8
                                ) {
                                    this.referer = "${mainUrl}/"
                                }
                            )
                        }
                    }
                } catch (e: Exception) {
                }
            }
            val directBaseRegex = Regex(pattern = "var directBase = '([^']*)'", options = setOf(RegexOption.IGNORE_CASE))
            val directBase = directBaseRegex.find(iframeAl)?.groupValues?.get(1)

            if (directBase != null) {

                val suffixRegex = Regex(pattern = "var suffix = '([^']*)'", options = setOf(RegexOption.IGNORE_CASE))
                val suffix = suffixRegex.find(iframeAl)?.groupValues?.get(1) ?: ""

                if (olid != null) {
                    val olidReversed = olid.reversed()

                    val decodedId = try {
                        olidReversed.chunked(2)
                            .map { it.toInt(16).toChar() }
                            .joinToString("")
                    } catch (e: Exception) {
                        null
                    }

                    if (decodedId != null) {
                        val vide0Url = "$directBase$decodedId$suffix"

                        try {
                            loadExtractor(vide0Url, data, subtitleCallback, callback)
                        } catch (e: Exception) {
                        }
                    }
                }
            }
        }

        return true
    }
}


