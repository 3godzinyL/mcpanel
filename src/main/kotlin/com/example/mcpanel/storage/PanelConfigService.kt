package com.example.mcpanel.storage

import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class PanelConfigService(private val plugin: JavaPlugin) {

    private val confFile: File = File(plugin.dataFolder, "panel.conf")

    var ngrokUrl: String? = null

    fun load() {
        if (!confFile.exists()) {
            ngrokUrl = null
            return
        }
        val lines = confFile.readLines(Charsets.UTF_8)
        val entry = lines.firstOrNull { it.startsWith("ngrok=") }
        ngrokUrl = entry?.substringAfter("ngrok=")?.trim()?.ifEmpty { null }
    }

    fun save() {
        if (!confFile.parentFile.exists()) {
            confFile.parentFile.mkdirs()
        }
        val value = ngrokUrl ?: ""
        confFile.writeText("ngrok=$value\n", Charsets.UTF_8)
    }

    fun testNgrokUrl(): Boolean {
        val url = ngrokUrl ?: return false
        return runCatching {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.requestMethod = "GET"
            conn.instanceFollowRedirects = true
            val code = conn.responseCode
            conn.disconnect()
            code in 200..399
        }.getOrDefault(false)
    }
}
