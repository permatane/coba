package com.kazefuri

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class KazefuriPlugin {
    lateinit var pluginData: PluginData

    fun registerMainAPI(pluginData: PluginData) {
        this.pluginData = pluginData
        pluginData.mainApiList.add(Kazefuri())
    }

    fun load(context: Context) {
        registerMainAPI(pluginData)
    }
}