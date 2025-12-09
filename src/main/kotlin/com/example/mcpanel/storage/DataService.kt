package com.example.mcpanel.storage

import com.example.mcpanel.model.HelpopEntry
import com.example.mcpanel.model.PlayerStats
import com.example.mcpanel.model.PluginData
import com.example.mcpanel.model.Rank
import com.example.mcpanel.model.WarEntry
import com.google.gson.GsonBuilder
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

class DataService(private val plugin: JavaPlugin) {

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val dataFile: File = File(plugin.dataFolder, "data.json")
    
    // Callback wywoływany po reload danych
    var onDataReloaded: (() -> Unit)? = null

    var data: PluginData? = null
        private set

    fun load(): Boolean {
        if (!dataFile.exists()) {
            createDefaultData()
            return true
        }
        return runCatching {
            data = gson.fromJson(dataFile.readText(Charsets.UTF_8), PluginData::class.java)
            plugin.logger.info("Załadowano dane: ${data?.ranks?.size ?: 0} rang, ${data?.players?.size ?: 0} graczy")
            true
        }.getOrElse {
            plugin.logger.severe("Nie udało się wczytać data.json: ${it.message}")
            data = null
            false
        }
    }

    // Przeładuj dane z pliku (dla synchronizacji z WWW)
    fun reload(): Boolean {
        val success = load()
        if (success) {
            onDataReloaded?.invoke()
        }
        return success
    }

    fun save(): Boolean {
        val current = data ?: return false
        if (!dataFile.parentFile.exists()) {
            dataFile.parentFile.mkdirs()
        }
        return runCatching {
            dataFile.writeText(gson.toJson(current), Charsets.UTF_8)
            true
        }.getOrElse {
            plugin.logger.severe("Nie udało się zapisać data.json: ${it.message}")
            false
        }
    }

    fun saveIfPresent() {
        save()
    }

    fun ensureData(): PluginData {
        if (data == null) {
            createDefaultData()
        }
        return data!!
    }

    fun createDefaultData() {
        val adminRank = Rank(
            name = "admin",
            color = "&c",
            prefix = "&c[ADMIN] ",
            priority = 0,
            permissions = mutableListOf("*", "mcpanel.helpop.receive", "mcpanel.anshelpop", "mcpanel.war"),
            displayName = "&c&lADMIN"
        )
        val vipRank = Rank(
            name = "vip",
            color = "&a",
            prefix = "&a[VIP] ",
            priority = 10,
            permissions = mutableListOf("minecraft.command.fly"),
            displayName = "&a&lVIP"
        )
        val defaultRank = Rank(
            name = "default",
            color = "&7",
            prefix = "&7",
            priority = 99,
            permissions = mutableListOf("minecraft.command.help"),
            displayName = "&7Gracz"
        )
        data = PluginData(
            ranks = mutableListOf(adminRank, vipRank, defaultRank),
            players = mutableMapOf(),
            totalMessages = 0,
            joinHistory = mutableListOf(),
            helpopLog = mutableListOf(),
            warLog = mutableListOf()
        )
        save()
    }

    fun getOrCreatePlayerStats(uuid: String, name: String): PlayerStats {
        val current = data ?: run {
            val newData = PluginData()
            data = newData
            newData
        }
        val existing = current.players[uuid]
        if (existing != null) {
            existing.name = name
            return existing
        }
        val defaultRankName = current.ranks.find { it.name == "default" }?.name 
            ?: current.ranks.firstOrNull()?.name 
            ?: "default"
        val stats = PlayerStats(
            uuid = uuid,
            name = name,
            rank = defaultRankName
        )
        current.players[uuid] = stats
        save()
        return stats
    }

    fun getRank(rankName: String): Rank? {
        return data?.ranks?.find { it.name.equals(rankName, ignoreCase = true) }
    }

    fun getPlayerRank(uuid: String): Rank? {
        val stats = data?.players?.get(uuid) ?: return null
        return getRank(stats.rank)
    }

    fun addOrUpdateRank(rank: Rank) {
        val current = data ?: return
        val existing = current.ranks.indexOfFirst { it.name.equals(rank.name, ignoreCase = true) }
        if (existing >= 0) {
            current.ranks[existing] = rank
        } else {
            current.ranks.add(rank)
        }
        // Sortuj rangi po priorytecie
        current.ranks.sortBy { it.priority }
        save()
    }

    fun deleteRank(rankName: String): Boolean {
        val current = data ?: return false
        val removed = current.ranks.removeIf { it.name.equals(rankName, ignoreCase = true) }
        if (removed) save()
        return removed
    }

    fun recordJoin(timestamp: Long = System.currentTimeMillis()) {
        val current = data ?: return
        current.joinHistory.add(timestamp)
        // trzymaj ostatnie 400 wpisów
        while (current.joinHistory.size > 400) {
            current.joinHistory.removeAt(0)
        }
        save()
    }

    fun incrementMessageCounter() {
        val current = data ?: return
        current.totalMessages += 1
        save()
    }

    fun appendHelpop(entry: HelpopEntry) {
        val current = data ?: return
        current.helpopLog.add(entry)
        while (current.helpopLog.size > 200) {
            current.helpopLog.removeAt(0)
        }
        save()
    }

    fun appendWar(entry: WarEntry) {
        val current = data ?: return
        current.warLog.add(entry)
        while (current.warLog.size > 200) {
            current.warLog.removeAt(0)
        }
        save()
    }
}
