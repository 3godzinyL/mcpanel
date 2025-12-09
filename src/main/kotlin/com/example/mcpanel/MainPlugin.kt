@file:Suppress("DEPRECATION")
package com.example.mcpanel

import com.example.mcpanel.storage.DataService
import com.example.mcpanel.storage.PanelConfigService
import com.example.mcpanel.web.PanelWebServer
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.ComponentBuilder
import net.md_5.bungee.api.chat.HoverEvent
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scoreboard.Team
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class MainPlugin : JavaPlugin(), Listener, CommandExecutor, TabCompleter {

    lateinit var dataService: DataService
    lateinit var panelConfig: PanelConfigService
    lateinit var panelServer: PanelWebServer

    private val waitingForNgrok = ConcurrentHashMap.newKeySet<UUID>()
    private val sessionJoinTimes = ConcurrentHashMap<UUID, Long>()

    override fun onEnable() {
        if (!dataFolder.exists()) {
            dataFolder.mkdirs()
        }

        logger.info("╔════════════════════════════════════╗")
        logger.info("║      McPanel v${description.version} Starting...     ║")
        logger.info("╚════════════════════════════════════╝")

        dataService = DataService(this)
        panelConfig = PanelConfigService(this)

        panelConfig.load()
        dataService.load()

        // Callback do odświeżania rang po reload danych
        dataService.onDataReloaded = {
            refreshAllPlayerRanks()
        }

        server.pluginManager.registerEvents(this, this)

        getCommand("start")?.setExecutor(this)
        getCommand("rangi")?.setExecutor(this)
        getCommand("staty")?.setExecutor(this)
        getCommand("rangaadd")?.apply {
            setExecutor(this@MainPlugin)
            tabCompleter = this@MainPlugin
        }
        getCommand("rangausun")?.apply {
            setExecutor(this@MainPlugin)
            tabCompleter = this@MainPlugin
        }
        getCommand("mcpanel")?.apply {
            setExecutor(this@MainPlugin)
            tabCompleter = this@MainPlugin
        }

        // Informacja przy starcie serwera
        Bukkit.getScheduler().runTaskLater(this, Runnable {
            if (dataService.data != null) {
                broadcastPrefixed("${ChatColor.GREEN}Panel załadowany! ${ChatColor.GRAY}(${dataService.data!!.ranks.size} rang)")
                
                if (!panelConfig.ngrokUrl.isNullOrBlank()) {
                    startPanelIfNeeded()
                }
            } else {
                broadcastPrefixed("${ChatColor.YELLOW}Użyj ${ChatColor.GOLD}/start${ChatColor.YELLOW} aby skonfigurować panel.")
            }
        }, 40L)

        logger.info("McPanel enabled successfully!")
    }

    override fun onDisable() {
        if (::panelServer.isInitialized) {
            panelServer.stop()
        }
        dataService.saveIfPresent()
        logger.info("McPanel disabled.")
    }

    // ════════════════════════════════════════════════════════
    //                      COMMANDS
    // ════════════════════════════════════════════════════════

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        return when (command.name.lowercase()) {
            "start" -> handleStart(sender)
            "rangi" -> handleRangi(sender)
            "staty" -> handleStaty(sender)
            "rangaadd" -> handleRangaAdd(sender, args)
            "rangausun" -> handleRangaUsun(sender, args)
            "mcpanel" -> handleMcPanel(sender, args)
            else -> false
        }
    }

    private fun handleStart(sender: CommandSender): Boolean {
        if (sender !is Player) {
            sender.sendMessage("${ChatColor.RED}Tę komendę może wywołać tylko gracz.")
            return true
        }
        if (!sender.isOp) {
            sender.sendMessage("${ChatColor.RED}Tylko operator może używać tej komendy.")
            return true
        }

        // Nagłówek
        sender.sendMessage("")
        sender.sendMessage("${ChatColor.DARK_GRAY}╔══════════════════════════════════════════╗")
        sender.sendMessage("${ChatColor.DARK_GRAY}║  ${ChatColor.AQUA}✦ ${ChatColor.WHITE}${ChatColor.BOLD}McPanel ${ChatColor.GRAY}- Panel Administratora ${ChatColor.AQUA}✦  ${ChatColor.DARK_GRAY}║")
        sender.sendMessage("${ChatColor.DARK_GRAY}╚══════════════════════════════════════════╝")
        sender.sendMessage("")

        // Tworzenie danych
        if (dataService.data == null) {
            dataService.createDefaultData()
            sender.sendMessage("  ${ChatColor.GREEN}✔ ${ChatColor.WHITE}Utworzono plik konfiguracyjny")
            sender.sendMessage("    ${ChatColor.GRAY}Domyślne rangi: ${ChatColor.RED}admin${ChatColor.GRAY}, ${ChatColor.GREEN}vip${ChatColor.GRAY}, ${ChatColor.WHITE}default")
        } else {
            sender.sendMessage("  ${ChatColor.YELLOW}● ${ChatColor.WHITE}Plik konfiguracyjny już istnieje")
            sender.sendMessage("    ${ChatColor.GRAY}Rangi: ${ChatColor.AQUA}${dataService.data!!.ranks.size}${ChatColor.GRAY}, Gracze: ${ChatColor.AQUA}${dataService.data!!.players.size}")
        }

        // Uruchom panel
        startPanelIfNeeded()
        sender.sendMessage("  ${ChatColor.GREEN}✔ ${ChatColor.WHITE}Panel WWW uruchomiony")
        
        // Lokalny link - klikalny
        val localUrl = "http://localhost:8090"
        val localMsg = TextComponent("    ${ChatColor.GRAY}Lokalnie: ")
        val localLink = TextComponent("${ChatColor.YELLOW}${ChatColor.UNDERLINE}$localUrl")
        localLink.clickEvent = ClickEvent(ClickEvent.Action.OPEN_URL, localUrl)
        localLink.hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, 
            ComponentBuilder("${ChatColor.AQUA}Kliknij aby otworzyć panel").create())
        localMsg.addExtra(localLink)
        sender.spigot().sendMessage(localMsg)

        sender.sendMessage("")

        // Status ngrok
        if (!panelConfig.ngrokUrl.isNullOrBlank()) {
            sender.sendMessage("  ${ChatColor.GREEN}✔ ${ChatColor.WHITE}Ngrok skonfigurowany")
            
            val ngrokMsg = TextComponent("    ${ChatColor.GRAY}URL: ")
            val ngrokLink = TextComponent("${ChatColor.AQUA}${ChatColor.UNDERLINE}${panelConfig.ngrokUrl}")
            ngrokLink.clickEvent = ClickEvent(ClickEvent.Action.OPEN_URL, panelConfig.ngrokUrl!!)
            ngrokLink.hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT,
                ComponentBuilder("${ChatColor.GREEN}Kliknij aby otworzyć panel zdalnie").create())
            ngrokMsg.addExtra(ngrokLink)
            sender.spigot().sendMessage(ngrokMsg)
            
            sender.sendMessage("")
            sender.sendMessage("  ${ChatColor.GRAY}Wpisz nowy URL na czacie aby zmienić.")
            waitingForNgrok.add(sender.uniqueId)
        } else {
            sender.sendMessage("  ${ChatColor.YELLOW}○ ${ChatColor.WHITE}Ngrok nie skonfigurowany")
            sender.sendMessage("")
            sender.sendMessage("  ${ChatColor.GRAY}Aby panel był dostępny z zewnątrz:")
            sender.sendMessage("  ${ChatColor.WHITE}1. ${ChatColor.GRAY}Uruchom: ${ChatColor.YELLOW}ngrok http 8090")
            sender.sendMessage("  ${ChatColor.WHITE}2. ${ChatColor.GRAY}Wklej URL na czacie")
            waitingForNgrok.add(sender.uniqueId)
        }

        sender.sendMessage("")
        sender.sendMessage("${ChatColor.DARK_GRAY}══════════════════════════════════════════")
        sender.sendMessage("")

        return true
    }

    private fun handleMcPanel(sender: CommandSender, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            sender.sendMessage("${ChatColor.AQUA}McPanel ${ChatColor.GRAY}v${description.version}")
            sender.sendMessage("${ChatColor.GRAY}Użycie: /mcpanel <reload|status>")
            return true
        }

        when (args[0].lowercase()) {
            "reload" -> {
                if (!sender.isOp) {
                    sender.sendMessage("${ChatColor.RED}Brak uprawnień.")
                    return true
                }
                if (dataService.reload()) {
                    sender.sendMessage("${ChatColor.GREEN}Przeładowano dane!")
                    refreshAllPlayerRanks()
                } else {
                    sender.sendMessage("${ChatColor.RED}Błąd podczas przeładowywania.")
                }
            }
            "status" -> {
                sender.sendMessage("${ChatColor.AQUA}=== Status McPanel ===")
                sender.sendMessage("${ChatColor.GRAY}Panel WWW: ${if (::panelServer.isInitialized) "${ChatColor.GREEN}Aktywny" else "${ChatColor.RED}Nieaktywny"}")
                sender.sendMessage("${ChatColor.GRAY}Port: ${ChatColor.WHITE}8090")
                sender.sendMessage("${ChatColor.GRAY}Ngrok: ${panelConfig.ngrokUrl ?: "${ChatColor.YELLOW}Nie skonfigurowany"}")
                sender.sendMessage("${ChatColor.GRAY}Rangi: ${ChatColor.WHITE}${dataService.data?.ranks?.size ?: 0}")
                sender.sendMessage("${ChatColor.GRAY}Gracze: ${ChatColor.WHITE}${dataService.data?.players?.size ?: 0}")
            }
            else -> {
                sender.sendMessage("${ChatColor.RED}Nieznana komenda. Użyj: reload, status")
            }
        }
        return true
    }

    private fun handleRangi(sender: CommandSender): Boolean {
        val data = dataService.data
        if (data == null) {
            sender.sendMessage("${ChatColor.RED}Panel nie jest zainicjalizowany. Użyj /start")
            return true
        }

        if (data.ranks.isEmpty()) {
            sender.sendMessage("${ChatColor.YELLOW}Brak zdefiniowanych rang.")
            return true
        }

        sender.sendMessage("")
        sender.sendMessage("${ChatColor.DARK_GRAY}╔═══════════════════════════╗")
        sender.sendMessage("${ChatColor.DARK_GRAY}║   ${ChatColor.AQUA}${ChatColor.BOLD}RANGI SERWERA${ChatColor.DARK_GRAY}        ║")
        sender.sendMessage("${ChatColor.DARK_GRAY}╠═══════════════════════════╣")
        
        data.ranks.sortedBy { it.priority }.forEach { rank ->
            val prefix = ChatColor.translateAlternateColorCodes('&', rank.prefix.ifEmpty { rank.color })
            sender.sendMessage("${ChatColor.DARK_GRAY}║ ${prefix}${rank.name} ${ChatColor.DARK_GRAY}- ${ChatColor.GRAY}${rank.permissions.size} perms")
        }
        
        sender.sendMessage("${ChatColor.DARK_GRAY}╚═══════════════════════════╝")
        sender.sendMessage("")
        return true
    }

    private fun handleStaty(sender: CommandSender): Boolean {
        if (sender !is Player) {
            sender.sendMessage("${ChatColor.RED}Tę komendę może wywołać tylko gracz.")
            return true
        }
        val data = dataService.data
        if (data == null) {
            sender.sendMessage("${ChatColor.RED}Panel nie jest zainicjalizowany. Użyj /start")
            return true
        }

        val stats = data.players[sender.uniqueId.toString()]
        if (stats == null) {
            sender.sendMessage("${ChatColor.YELLOW}Brak statystyk. Zagraj trochę dłużej!")
            return true
        }

        val rank = dataService.getRank(stats.rank)
        val rankDisplay = if (rank != null) {
            ChatColor.translateAlternateColorCodes('&', rank.prefix + rank.name)
        } else {
            stats.rank
        }

        val minutes = stats.playTimeMs / 1000 / 60
        val hours = minutes / 60
        val mins = minutes % 60

        sender.sendMessage("")
        sender.sendMessage("${ChatColor.DARK_GRAY}╔═══════════════════════════╗")
        sender.sendMessage("${ChatColor.DARK_GRAY}║   ${ChatColor.AQUA}${ChatColor.BOLD}TWOJE STATYSTYKI${ChatColor.DARK_GRAY}     ║")
        sender.sendMessage("${ChatColor.DARK_GRAY}╠═══════════════════════════╣")
        sender.sendMessage("${ChatColor.DARK_GRAY}║ ${ChatColor.GRAY}Nick: ${ChatColor.WHITE}${stats.name}")
        sender.sendMessage("${ChatColor.DARK_GRAY}║ ${ChatColor.GRAY}Ranga: $rankDisplay")
        sender.sendMessage("${ChatColor.DARK_GRAY}║ ${ChatColor.GRAY}Zabójstwa: ${ChatColor.GREEN}${stats.kills}")
        sender.sendMessage("${ChatColor.DARK_GRAY}║ ${ChatColor.GRAY}Zgony: ${ChatColor.RED}${stats.deaths}")
        sender.sendMessage("${ChatColor.DARK_GRAY}║ ${ChatColor.GRAY}Czas gry: ${ChatColor.AQUA}${hours}h ${mins}m")
        sender.sendMessage("${ChatColor.DARK_GRAY}╚═══════════════════════════╝")
        sender.sendMessage("")
        return true
    }

    private fun handleRangaAdd(sender: CommandSender, args: Array<out String>): Boolean {
        if (!sender.isOp) {
            sender.sendMessage("${ChatColor.RED}Tylko operator może zmieniać rangi.")
            return true
        }
        val data = dataService.data
        if (data == null) {
            sender.sendMessage("${ChatColor.RED}Panel nie jest zainicjalizowany. Użyj /start")
            return true
        }
        if (args.size < 2) {
            sender.sendMessage("${ChatColor.RED}Użycie: ${ChatColor.YELLOW}/rangaadd <nick> <ranga>")
            sender.sendMessage("${ChatColor.GRAY}Dostępne rangi: ${data.ranks.joinToString(", ") { it.name }}")
            return true
        }

        val nick = args[0]
        val rankName = args[1]

        val rank = data.ranks.find { it.name.equals(rankName, ignoreCase = true) }
        if (rank == null) {
            sender.sendMessage("${ChatColor.RED}Nie znaleziono rangi: ${ChatColor.YELLOW}$rankName")
            sender.sendMessage("${ChatColor.GRAY}Dostępne rangi: ${data.ranks.joinToString(", ") { it.name }}")
            return true
        }

        val offline = Bukkit.getOfflinePlayer(nick)
        val uuid = offline.uniqueId.toString()

        val stats = dataService.getOrCreatePlayerStats(uuid, nick)
        stats.rank = rank.name
        dataService.save()

        val rankPrefix = ChatColor.translateAlternateColorCodes('&', rank.prefix)
        sender.sendMessage("${ChatColor.GREEN}✔ Ustawiono rangę $rankPrefix${rank.name} ${ChatColor.GREEN}dla ${ChatColor.WHITE}$nick")
        
        if (offline.isOnline) {
            val onlinePlayer = offline.player
            if (onlinePlayer != null) {
                onlinePlayer.sendMessage("")
                onlinePlayer.sendMessage("${ChatColor.DARK_GRAY}[${ChatColor.AQUA}✦${ChatColor.DARK_GRAY}] ${ChatColor.GREEN}Otrzymałeś nową rangę: $rankPrefix${rank.name}")
                onlinePlayer.sendMessage("")
                updatePlayerRankDisplay(onlinePlayer)
            }
        }
        return true
    }

    private fun handleRangaUsun(sender: CommandSender, args: Array<out String>): Boolean {
        if (!sender.isOp) {
            sender.sendMessage("${ChatColor.RED}Tylko operator może usuwać rangi.")
            return true
        }
        val data = dataService.data
        if (data == null) {
            sender.sendMessage("${ChatColor.RED}Panel nie jest zainicjalizowany. Użyj /start")
            return true
        }
        if (args.isEmpty()) {
            sender.sendMessage("${ChatColor.RED}Użycie: ${ChatColor.YELLOW}/rangausun <nick>")
            return true
        }

        val nick = args[0]
        val offline = Bukkit.getOfflinePlayer(nick)
        val uuid = offline.uniqueId.toString()

        val stats = data.players[uuid]
        if (stats == null) {
            sender.sendMessage("${ChatColor.RED}Nie znaleziono gracza: ${ChatColor.YELLOW}$nick")
            return true
        }

        val oldRank = stats.rank
        val defaultRank = data.ranks.find { it.name.equals("default", ignoreCase = true) }?.name ?: "default"
        
        stats.rank = defaultRank
        dataService.save()

        sender.sendMessage("${ChatColor.GREEN}✔ Usunięto rangę ${ChatColor.YELLOW}$oldRank ${ChatColor.GREEN}graczowi ${ChatColor.WHITE}$nick")
        sender.sendMessage("${ChatColor.GRAY}Przypisano domyślną rangę: ${ChatColor.WHITE}$defaultRank")
        
        if (offline.isOnline) {
            val onlinePlayer = offline.player
            if (onlinePlayer != null) {
                onlinePlayer.sendMessage("")
                onlinePlayer.sendMessage("${ChatColor.DARK_GRAY}[${ChatColor.YELLOW}!${ChatColor.DARK_GRAY}] ${ChatColor.YELLOW}Twoja ranga została zresetowana do domyślnej.")
                onlinePlayer.sendMessage("")
                updatePlayerRankDisplay(onlinePlayer)
            }
        }
        return true
    }

    // ════════════════════════════════════════════════════════
    //                    TAB COMPLETION
    // ════════════════════════════════════════════════════════

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): MutableList<String>? {
        val data = dataService.data ?: return mutableListOf()

        return when (command.name.lowercase()) {
            "rangaadd" -> {
                when (args.size) {
                    1 -> {
                        // Pierwszy argument - nick gracza (online gracze)
                        val partial = args[0].lowercase()
                        Bukkit.getOnlinePlayers()
                            .map { it.name }
                            .filter { it.lowercase().startsWith(partial) }
                            .toMutableList()
                    }
                    2 -> {
                        // Drugi argument - nazwa rangi
                        val partial = args[1].lowercase()
                        data.ranks
                            .map { it.name }
                            .filter { it.lowercase().startsWith(partial) }
                            .toMutableList()
                    }
                    else -> mutableListOf()
                }
            }
            "rangausun" -> {
                when (args.size) {
                    1 -> {
                        // Gracze którzy mają rangę inną niż default
                        val partial = args[0].lowercase()
                        val playersWithRanks = data.players.values
                            .filter { !it.rank.equals("default", ignoreCase = true) }
                            .map { it.name }
                        
                        // Dodaj też online graczy
                        val online = Bukkit.getOnlinePlayers().map { it.name }
                        (playersWithRanks + online)
                            .distinct()
                            .filter { it.lowercase().startsWith(partial) }
                            .toMutableList()
                    }
                    else -> mutableListOf()
                }
            }
            "mcpanel" -> {
                when (args.size) {
                    1 -> {
                        val partial = args[0].lowercase()
                        listOf("reload", "status")
                            .filter { it.startsWith(partial) }
                            .toMutableList()
                    }
                    else -> mutableListOf()
                }
            }
            else -> null
        }
    }

    // ════════════════════════════════════════════════════════
    //                    RANK DISPLAY SYSTEM
    // ════════════════════════════════════════════════════════

    fun updatePlayerRankDisplay(player: Player) {
        val rank = dataService.getPlayerRank(player.uniqueId.toString()) ?: return

        val prefix = ChatColor.translateAlternateColorCodes('&', rank.prefix.ifEmpty { rank.color })
        val suffix = ChatColor.translateAlternateColorCodes('&', rank.suffix)
        
        // Tab lista - format: [PREFIX]Nick
        player.setPlayerListName("$prefix${player.name}")
        
        // Display name (czat i inne)
        player.setDisplayName("$prefix${player.name}${ChatColor.RESET}")
        
        // Scoreboard team dla nazwy nad głową
        setupScoreboardTeam(player, rank.name, rank.priority, prefix, suffix)
    }

    private fun setupScoreboardTeam(player: Player, rankName: String, priority: Int, prefix: String, suffix: String) {
        val scoreboard = Bukkit.getScoreboardManager().mainScoreboard
        
        // Usuń gracza z innych teamów
        scoreboard.teams.forEach { team ->
            if (team.hasEntry(player.name)) {
                team.removeEntry(player.name)
            }
        }
        
        // Nazwa teamu z priorytetem dla sortowania (00_admin, 10_vip, 99_default)
        val teamName = "${String.format("%02d", priority)}_${rankName.take(10)}"
        var team: Team? = scoreboard.getTeam(teamName)
        
        if (team == null) {
            team = scoreboard.registerNewTeam(teamName)
        }
        
        // Ustaw prefix i suffix
        team.prefix = prefix
        if (suffix.isNotEmpty()) {
            team.suffix = suffix
        }
        team.color = getLastColor(prefix)
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS)
        
        team.addEntry(player.name)
        player.scoreboard = scoreboard
    }

    private fun getLastColor(text: String): ChatColor {
        val translated = ChatColor.translateAlternateColorCodes('&', text)
        val lastColorIndex = translated.lastIndexOf('§')
        if (lastColorIndex >= 0 && lastColorIndex < translated.length - 1) {
            val colorChar = translated[lastColorIndex + 1]
            return ChatColor.getByChar(colorChar) ?: ChatColor.WHITE
        }
        return ChatColor.WHITE
    }

    fun refreshAllPlayerRanks() {
        Bukkit.getOnlinePlayers().forEach { player ->
            updatePlayerRankDisplay(player)
        }
        logger.info("Odświeżono rangi dla ${Bukkit.getOnlinePlayers().size} graczy")
    }

    private fun startPanelIfNeeded() {
        if (!::panelServer.isInitialized) {
            panelServer = PanelWebServer(this, dataService)
            panelServer.start()
        }
    }

    private fun broadcastPrefixed(message: String) {
        val prefix = "${ChatColor.DARK_GRAY}[${ChatColor.AQUA}McPanel${ChatColor.DARK_GRAY}] "
        Bukkit.broadcastMessage(prefix + message)
    }

    // ════════════════════════════════════════════════════════
    //                        EVENTS
    // ════════════════════════════════════════════════════════

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player
        val now = System.currentTimeMillis()
        sessionJoinTimes[player.uniqueId] = now

        dataService.data ?: return
        dataService.getOrCreatePlayerStats(player.uniqueId.toString(), player.name)
        
        // Ustaw rangę z małym opóźnieniem
        Bukkit.getScheduler().runTaskLater(this, Runnable {
            updatePlayerRankDisplay(player)
        }, 5L)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        val player = event.player
        waitingForNgrok.remove(player.uniqueId)
        
        val start = sessionJoinTimes.remove(player.uniqueId) ?: return
        val duration = System.currentTimeMillis() - start

        val currentData = dataService.data ?: return
        val stats = currentData.players[player.uniqueId.toString()] ?: return
        stats.playTimeMs += duration
        dataService.save()
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onChatFormat(event: AsyncPlayerChatEvent) {
        // Nie formatuj wiadomości ngrok
        if (waitingForNgrok.contains(event.player.uniqueId)) {
            return
        }
        
        val player = event.player
        val rank = dataService.getPlayerRank(player.uniqueId.toString()) ?: return
        
        val prefix = ChatColor.translateAlternateColorCodes('&', rank.prefix.ifEmpty { rank.color })
        // Format: [PREFIX]Nick: wiadomość
        event.format = "$prefix%s${ChatColor.GRAY}: ${ChatColor.WHITE}%s"
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onChatNgrok(event: AsyncPlayerChatEvent) {
        val player = event.player
        if (!waitingForNgrok.contains(player.uniqueId)) {
            return
        }

        event.isCancelled = true
        val rawUrl = event.message.trim()
        
        logger.info("Otrzymano URL ngrok od ${player.name}: $rawUrl")
        waitingForNgrok.remove(player.uniqueId)

        // Zapisz na main thread
        Bukkit.getScheduler().runTask(this, Runnable {
            panelConfig.ngrokUrl = rawUrl
            panelConfig.save()
            
            player.sendMessage("")
            player.sendMessage("${ChatColor.GREEN}✔ ${ChatColor.WHITE}Zapisano adres ngrok!")
            
            val urlMsg = TextComponent("${ChatColor.GRAY}Panel: ")
            val urlLink = TextComponent("${ChatColor.AQUA}${ChatColor.UNDERLINE}$rawUrl")
            urlLink.clickEvent = ClickEvent(ClickEvent.Action.OPEN_URL, rawUrl)
            urlLink.hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT,
                ComponentBuilder("${ChatColor.GREEN}Kliknij aby otworzyć").create())
            urlMsg.addExtra(urlLink)
            player.spigot().sendMessage(urlMsg)
            player.sendMessage("")
            
            broadcastPrefixed("${ChatColor.GREEN}Panel dostępny: ${ChatColor.AQUA}$rawUrl")
        })
    }
}
