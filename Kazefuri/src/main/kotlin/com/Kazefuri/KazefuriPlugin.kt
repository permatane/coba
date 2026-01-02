package com.Kazefuri

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class KazefuriPlugin: Plugin() {
    override fun load(context: Context) {
        // Baris ini memanggil class Kazefuri() dari file sebelah
        registerMainAPI(Kazefuri())
    }
}
