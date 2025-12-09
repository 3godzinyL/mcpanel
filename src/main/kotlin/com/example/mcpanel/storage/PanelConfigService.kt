package com.example.mcpanel.storage

import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class PanelConfigService(private val plugin: JavaPlugin) {

    private val confFile: File = File(plugin.dataFolder, "panel.conf")

    var ngrokUrl: String? = null
    var sidebarEnabled: Boolean = true
    var sidebarTitle: String = "McPanel"
    var sidebarAccent: String = "&b"

    fun load() {
        if (!confFile.exists()) {
            ngrokUrl = null
            sidebarEnabled = true
            return
        }
        val lines = confFile.readLines(Charsets.UTF_8)
        val entry = lines.firstOrNull { it.startsWith("ngrok=") }
        ngrokUrl = entry?.substringAfter("ngrok=")?.trim()?.ifEmpty { null }

        sidebarEnabled = lines.firstOrNull { it.startsWith("sidebarEnabled=") }
            ?.substringAfter("sidebarEnabled=")
            ?.toBooleanStrictOrNull() ?: true

        sidebarTitle = lines.firstOrNull { it.startsWith("sidebarTitle=") }
            ?.substringAfter("sidebarTitle=")
            ?.ifEmpty { "McPanel" } ?: "McPanel"

        sidebarAccent = lines.firstOrNull { it.startsWith("sidebarAccent=") }
            ?.substringAfter("sidebarAccent=")
            ?.ifEmpty { "&b" } ?: "&b"
    }

    fun save() {
        if (!confFile.parentFile.exists()) {
            confFile.parentFile.mkdirs()
        }
        val value = ngrokUrl ?: ""
        val content = buildString {
            appendLine("ngrok=$value")
            appendLine("sidebarEnabled=$sidebarEnabled")
            appendLine("sidebarTitle=$sidebarTitle")
            appendLine("sidebarAccent=$sidebarAccent")
        }
        confFile.writeText(content, Charsets.UTF_8)
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
