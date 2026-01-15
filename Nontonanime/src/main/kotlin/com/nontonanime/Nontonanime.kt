package com.Nontonanime

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addScore
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class Nontonanime : MainAPI() {
    override var mainUrl = "https://s7.nontonanimeid.boats"
    override var name = "NontonAnime"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true

    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.OVA
    )

    override val mainPage = mainPageOf(
        "" to "Latest Update",
        "ongoing-list/" to "Ongoing List",
        "popular-series/" to "Popular Series",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}").document
        val home = document.select(".animeseries, .result li, article").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, home, hasNext = false)
    }

    private fun Element.toSearchResult(): AnimeSearchResponse {
        val a = this.selectFirst("a")
        val href = fixUrl(a?.attr("href") ?: "")
        val title = this.selectFirst(".title, h2, h3")?.text() ?: ""
        
        // Perbaikan selector poster: Mengambil data-src atau src
        val posterUrl = this.selectFirst("img")?.let { 
            val url = it.attr("data-src").ifEmpty { it.attr("src") }
            fixUrlNull(url)
        }

        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        
        // PERBAIKAN UTAMA: Mencari link halaman "Series" jika user mengklik dari episode (Latest Update)
        // Tombol ini biasanya ada di bawah judul dengan teks "Series" atau ikon rumah
        val detailUrl = document.selectFirst(".nvs.nvsc a, .nvs a")?.attr("href") ?: url
        
        val detailDoc = if (detailUrl != url) app.get(detailUrl).document else document

        // Selector judul yang lebih akurat
        val title = detailDoc.selectFirst("h1.entry-title")?.text()
            ?.replace("Nonton Anime", "")?.replace("Sub Indo", "")?.trim() ?: ""
        
        val poster = detailDoc.selectFirst(".poster img, .thumb img")?.attr("abs:src")
        
        // PERBAIKAN PLOT: Selector untuk deskripsi/sinopsis
        val description = detailDoc.select(".entry-content.seriesdesc p, .sinopsis p, .desc").text().trim()
        
        val rating = detailDoc.select(".nilaiseries, .rating strong").text().trim()

        // PERBAIKAN TOMBOL PLAY/EPISODE: Mengambil daftar episode dari tabel atau list misha
        val episodes = detailDoc.select(".misha_posts_wrap2 li, .list-episode li").mapNotNull {
            val a = it.selectFirst("a") ?: return@mapNotNull null
            val name = a.text()
            // Ekstrak angka episode
            val epNum = Regex("""Episode\s?(\d+)""").find(name)?.groupValues?.get(1)?.toIntOrNull()
            
            newEpisode(fixUrl(a.attr("href"))) {
                this.name = name
                this.episode = epNum
            }
        }.reversed()

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            this.posterUrl = poster
            addEpisodes(DubStatus.Subbed, episodes)
            this.plot = if (description.isEmpty()) "No plot available" else description
            addScore(rating)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val res = app.get(data)
        val document = res.document

        // Mengambil nonce dari script tag
        val nonce = Regex("""["']nonce["']\s*:\s*["']([^"']+)""").find(res.text)?.groupValues?.get(1)

        // Cari semua provider video
        document.select(".container1 > ul > li[data-post], .player-option").amap {
            val dataPost = it.attr("data-post")
            val dataNume = it.attr("data-nume")
            val dataType = it.attr("data-type")

            if (!nonce.isNullOrEmpty() && dataPost.isNotEmpty()) {
                val response = app.post(
                    url = "$mainUrl/wp-admin/admin-ajax.php",
                    data = mapOf(
                        "action" to "player_ajax",
                        "post" to dataPost,
                        "nume" to dataNume,
                        "type" to dataType,
                        "nonce" to nonce
                    ),
                    referer = data,
                    headers = mapOf("X-Requested-With" to "XMLHttpRequest")
                ).text

                val iframeUrl = Regex("""src=['"]([^"']+)""").find(response)?.groupValues?.get(1)
                
                if (!iframeUrl.isNullOrEmpty()) {
                    loadExtractor(fixUrl(iframeUrl), "$mainUrl/", subtitleCallback, callback)
                }
            }
        }
        return true
    }
}
