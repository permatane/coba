package com.Anichin

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import android.content.Context

@CloudstreamPlugin
class AnichinPlugin: BasePlugin() {
    override fun load(context: Context) {
        // Mendaftarkan provider Anichin
        registerMainAPI(Anichin())
        
        // Mendaftarkan extractor tambahan dari referensi Anda jika diperlukan
        registerExtractorAPI(Vtbe())
        registerExtractorAPI(waaw())
        registerExtractorAPI(wishfast())
        registerExtractorAPI(FileMoonSx())
        registerExtractorAPI(Ultrahd())
        registerExtractorAPI(Rumble())
        registerExtractorAPI(PlayStreamplay())
    }
}
