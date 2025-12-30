package com.kazefuri

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class KazefuriPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Kazefuri())
    }
}

