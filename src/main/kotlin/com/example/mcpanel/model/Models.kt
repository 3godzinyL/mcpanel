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
    var lastSeen: Long = 0L
)

data class PluginData(
    var ranks: MutableList<Rank> = mutableListOf(),
    var players: MutableMap<String, PlayerStats> = mutableMapOf()
)
