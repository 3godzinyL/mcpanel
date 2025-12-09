package com.example.mcpanel.model

data class Rank(
    var name: String = "",
    var color: String = "&7",
    var prefix: String = "",      // Prefix przed nickiem np. "&a[VIP] "
    var suffix: String = "",      // Suffix po nicku (opcjonalny)
    var priority: Int = 0,        // Priorytet sortowania w TAB (niższy = wyżej)
    var permissions: MutableList<String> = mutableListOf(),
    var displayName: String = ""  // Pełna nazwa rangi do wyświetlania
)

data class PlayerStats(
    var uuid: String = "",
    var name: String = "",
    var rank: String = "default",
    var kills: Int = 0,
    var deaths: Int = 0,
    var playTimeMs: Long = 0,

    // Moderacja / administracja
    var bans: Int = 0,
    var mutes: Int = 0,
    var warns: Int = 0,
    var kicks: Int = 0,
    var isBanned: Boolean = false,
    var isMuted: Boolean = false,
    var lastSeen: Long = 0L,

    // Panel / pomoc
    var helpopSent: Int = 0,
    var helpopAnswered: Int = 0,
    var warSent: Int = 0
)

data class PluginData(
    var ranks: MutableList<Rank> = mutableListOf(),
    var players: MutableMap<String, PlayerStats> = mutableMapOf(),
    var totalMessages: Int = 0,
    var joinHistory: MutableList<Long> = mutableListOf(),
    var helpopLog: MutableList<HelpopEntry> = mutableListOf(),
    var warLog: MutableList<WarEntry> = mutableListOf()
)

data class HelpopEntry(
    val sender: String = "",
    val uuid: String = "",
    val message: String = "",
    val answeredBy: String? = null,
    val answer: String? = null,
    val timestamp: Long = 0L
)

data class WarEntry(
    val sender: String = "",
    val target: String = "",
    val message: String = "",
    val timestamp: Long = 0L
)
