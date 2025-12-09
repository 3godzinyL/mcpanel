package com.example.mcpanel.web

import com.example.mcpanel.storage.DataService
import com.example.mcpanel.model.Rank
import com.example.mcpanel.model.PlayerStats
import io.ktor.http.*
import io.ktor.serialization.gson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.http.content.*
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import org.slf4j.event.Level

class PanelWebServer(
    private val plugin: JavaPlugin,
    private val dataService: DataService
) {

    private var server: ApplicationEngine? = null

    fun start(port: Int = 8090) {
        if (server != null) return

        server = embeddedServer(Netty, port = port) {
            install(CallLogging) {
                level = Level.INFO
            }
            install(ContentNegotiation) {
                gson {
                    setPrettyPrinting()
                }
            }
            install(CORS) {
                anyHost()
                allowHeader(HttpHeaders.ContentType)
                allowMethod(HttpMethod.Get)
                allowMethod(HttpMethod.Post)
                allowMethod(HttpMethod.Delete)
            }

            routing {
                // Redirect root -> /static/index.html
                get("/") {
                    call.respondRedirect("/static/index.html")
                }

                // Static files (HTML/CSS/JS)
                staticResources("/static", "public")

                // ═══════════════════════════════════════════
                //                  API ENDPOINTS
                // ═══════════════════════════════════════════

                // API: Overview / Status
                get("/api/overview") {
                    val data = dataService.data
                    
                    val online: Int
                    val maxPlayers: Int
                    val tps: Double
                    
                    try {
                        online = Bukkit.getOnlinePlayers().size
                        maxPlayers = Bukkit.getMaxPlayers()
                        val tpsArray = Bukkit.getServer().tps
                        tps = if (tpsArray.isNotEmpty()) {
                            tpsArray[0].coerceAtMost(20.0)
                        } else {
                            20.0
                        }
                    } catch (e: Exception) {
                        call.respond(mapOf(
                            "onlinePlayers" to 0,
                            "maxPlayers" to 0,
                            "tps" to "20.00",
                            "rankCount" to (data?.ranks?.size ?: 0),
                            "playerCount" to (data?.players?.size ?: 0),
                            "error" to "Server not ready"
                        ))
                        return@get
                    }

                    call.respond(mapOf(
                        "onlinePlayers" to online,
                        "maxPlayers" to maxPlayers,
                        "tps" to String.format("%.2f", tps),
                        "rankCount" to (data?.ranks?.size ?: 0),
                        "playerCount" to (data?.players?.size ?: 0)
                    ))
                }

                // API: Lista rang
                get("/api/ranks") {
                    dataService.reload() // Zawsze odświeżaj z pliku
                    val ranks = dataService.data?.ranks ?: mutableListOf()
                    call.respond(ranks)
                }

                // API: Dodaj/Edytuj rangę
                post("/api/ranks") {
                    val rank = call.receive<Rank>()
                    dataService.addOrUpdateRank(rank)
                    call.respond(mapOf("status" to "ok", "message" to "Ranga zapisana"))
                }

                // API: Usuń rangę
                delete("/api/ranks/{name}") {
                    val name = call.parameters["name"] ?: return@delete call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Brak nazwy rangi")
                    )
                    if (dataService.deleteRank(name)) {
                        call.respond(mapOf("status" to "ok", "message" to "Ranga usunięta"))
                    } else {
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to "Nie znaleziono rangi"))
                    }
                }

                // API: Lista graczy
                get("/api/stats") {
                    dataService.reload() // Zawsze odświeżaj z pliku
                    val players = dataService.data?.players?.values?.toList() ?: emptyList<PlayerStats>()
                    call.respond(players)
                }

                // API: Ustaw rangę graczowi
                post("/api/stats/{uuid}/rank") {
                    val uuid = call.parameters["uuid"] ?: return@post call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Brak UUID")
                    )
                    val body = call.receive<Map<String, String>>()
                    val rankName = body["rank"] ?: return@post call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Brak nazwy rangi")
                    )
                    
                    val data = dataService.data ?: return@post call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to "Brak danych")
                    )
                    
                    val rank = data.ranks.find { it.name.equals(rankName, ignoreCase = true) }
                        ?: return@post call.respond(
                            HttpStatusCode.NotFound,
                            mapOf("error" to "Nie znaleziono rangi: $rankName")
                        )
                    
                    val stats = data.players[uuid] ?: return@post call.respond(
                        HttpStatusCode.NotFound,
                        mapOf("error" to "Nie znaleziono gracza")
                    )
                    
                    stats.rank = rank.name
                    dataService.save()
                    
                    // Odśwież rangę online gracza
                    try {
                        val player = Bukkit.getPlayer(java.util.UUID.fromString(uuid))
                        if (player != null) {
                            Bukkit.getScheduler().runTask(plugin, Runnable {
                                // Wywołaj callback do odświeżenia rang
                                dataService.onDataReloaded?.invoke()
                            })
                        }
                    } catch (e: Exception) {
                        // Ignoruj błędy
                    }
                    
                    call.respond(mapOf("status" to "ok", "message" to "Ranga gracza zmieniona"))
                }

                // API: Reload danych
                post("/api/reload") {
                    if (dataService.reload()) {
                        call.respond(mapOf("status" to "ok", "message" to "Dane przeładowane"))
                    } else {
                        call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Błąd ładowania"))
                    }
                }

                // API: Pełne dane (dla synchronizacji)
                get("/api/data") {
                    dataService.reload()
                    val data = dataService.data
                    if (data != null) {
                        call.respond(data)
                    } else {
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to "Brak danych"))
                    }
                }
            }
        }.start(wait = false)

        plugin.logger.info("Panel webserver started on port $port")
    }

    fun stop() {
        server?.stop(2000, 5000)
        server = null
        plugin.logger.info("Panel webserver stopped.")
    }
}
