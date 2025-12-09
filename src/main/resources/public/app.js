// McPanel frontend – bardziej rozbudowany panel z hero statystykami,
// malowaniem kolorów rang i szybkim wyborem permisji.

/// ─────────────────────────────────────────────────────────────
/// DOM ELEMENTY – HUD / HERO STATS
/// ─────────────────────────────────────────────────────────────
const hudPlayers = document.getElementById("hudPlayers");
const hudTps = document.getElementById("hudTps");

const ovPlayers = document.getElementById("ovPlayers");
const ovMaxPlayers = document.getElementById("ovMaxPlayers");
const ovTps = document.getElementById("ovTps");
const ovPlayerCount = document.getElementById("ovPlayerCount");
const ovRankCount = document.getElementById("ovRankCount");
const ovMessages = document.getElementById("ovMessages");
const ovBans = document.getElementById("ovBans");
const ovHelpop = document.getElementById("ovHelpop");
const ovWar = document.getElementById("ovWar");

// Hero – duże statystyki na wejściu
const heroPlayersCurrent = document.getElementById("heroPlayersCurrent");
const heroPlayersMax = document.getElementById("heroPlayersMax");
const heroPlayersBar = document.getElementById("heroPlayersBar");
const heroTpsValue = document.getElementById("heroTpsValue");
const heroTpsStatus = document.getElementById("heroTpsStatus");
const heroTpsBar = document.getElementById("heroTpsBar");
const heroPlayerCount = document.getElementById("heroPlayerCount");
const heroRankCount = document.getElementById("heroRankCount");

/// ─────────────────────────────────────────────────────────────
/// DOM ELEMENTY – STATYSTYKI GRACZY
/// ─────────────────────────────────────────────────────────────
const statsRankFilter = document.getElementById("statsRankFilter");
const statsSearch = document.getElementById("statsSearch");
const statsTableBody = document.getElementById("statsTableBody");
const statsRankChecks = document.getElementById("statsRankChecks");
const playerDrawer = document.getElementById("playerDrawer");
const drawerAvatar = document.getElementById("drawerAvatar");
const drawerName = document.getElementById("drawerName");
const drawerRank = document.getElementById("drawerRank");
const drawerKills = document.getElementById("drawerKills");
const drawerDeaths = document.getElementById("drawerDeaths");
const drawerPlaytime = document.getElementById("drawerPlaytime");
const drawerBans = document.getElementById("drawerBans");
const drawerMutes = document.getElementById("drawerMutes");
const drawerWarns = document.getElementById("drawerWarns");
const drawerHelpop = document.getElementById("drawerHelpop");
const drawerWar = document.getElementById("drawerWar");
const drawerPermSelect = document.getElementById("drawerRankSelect");
const drawerBtnAdd = document.getElementById("drawerAddRank");
const drawerBtnRemove = document.getElementById("drawerRemoveRank");
const drawerBars = document.getElementById("drawerBars");

/// ─────────────────────────────────────────────────────────────
/// DOM ELEMENTY – RANGI
/// ─────────────────────────────────────────────────────────────
const ranksList = document.getElementById("ranksList");
const rankForm = document.getElementById("rankForm");
const rankNameInput = document.getElementById("rankName");
const rankColorInput = document.getElementById("rankColor");
const rankDisplayInput = document.getElementById("rankDisplay");
const rankPermsInput = document.getElementById("rankPerms");
const rankFormStatus = document.getElementById("rankFormStatus");

const rankColorPalette = document.getElementById("rankColorPalette");
const rankColorPicker = document.getElementById("rankColorPicker");
const rankColorPreview = document.getElementById("rankColorPreview");
const quickPermsContainer = document.getElementById("quickPermsContainer");

/// ─────────────────────────────────────────────────────────────
/// DOM ELEMENTY – INNE
/// ─────────────────────────────────────────────────────────────
const toggleSidebar = document.getElementById("toggleSidebar");
const sidebarTitleInput = document.getElementById("sidebarTitle");
const sidebarAccentInput = document.getElementById("sidebarAccent");
const helpopHistoryList = document.getElementById("helpopHistory");

let allRanks = [];
let allStats = [];
let activeRankName = null;
let onlineChart = null;
let onlineLabels = [];
let onlinePoints = [];

/// ─────────────────────────────────────────────────────────────
/// KONFIG – mapa kolorów Minecraft (&0–&f)
/// ─────────────────────────────────────────────────────────────
const MC_COLORS = {
    "0": "#000000",
    "1": "#0000aa",
    "2": "#00aa00",
    "3": "#00aaaa",
    "4": "#aa0000",
    "5": "#aa00aa",
    "6": "#ffaa00",
    "7": "#aaaaaa",
    "8": "#555555",
    "9": "#5555ff",
    "a": "#55ff55",
    "b": "#55ffff",
    "c": "#ff5555",
    "d": "#ff55ff",
    "e": "#ffff55",
    "f": "#ffffff"
};

const QUICK_PERMS = [
    { perm: "minecraft.command.kick", label: "Kick" },
    { perm: "minecraft.command.ban", label: "Ban" },
    { perm: "minecraft.command.teleport", label: "Teleport" },
    { perm: "minecraft.command.gamemode", label: "Gamemode" },
    { perm: "minecraft.command.op", label: "OP / admin" },
    { perm: "minecraft.command.say", label: "Broadcast" },
    { perm: "minecraft.command.tps", label: "TPS / debug" },
    { perm: "minecraft.command.time", label: "Czas świata" },
    { perm: "mcpanel.helpop.receive", label: "Helpop podgląd" },
    { perm: "mcpanel.anshelpop", label: "Helpop odpowiedzi" },
    { perm: "mcpanel.war", label: "Ostrzeżenia /war" }
];

/// ─────────────────────────────────────────────────────────────
/// UTILS
/// ─────────────────────────────────────────────────────────────
function mcColorToHex(code) {
    if (!code) return "#aaaaaa";
    let c = code.trim();
    if (c.startsWith("&")) c = c.substring(1);
    c = c.toLowerCase();
    return MC_COLORS[c] || "#aaaaaa";
}

function normalizeColorValue(value) {
    const v = (value || "").trim();
    if (!v) return "&7";
    if (v.startsWith("#")) return v;
    if (!v.startsWith("&")) return "&" + v;
    return v;
}

function formatMinutes(ms) {
    const minutes = Math.floor((ms ?? 0) / 1000 / 60);
    return minutes.toString();
}

function uniqueArray(arr) {
    return Array.from(new Set(arr.filter(Boolean)));
}

/// ─────────────────────────────────────────────────────────────
/// HERO STATS – aktualizacja z overview
/// ─────────────────────────────────────────────────────────────
function updateHeroFromOverview({ online, maxPlayers, tps, rankCount, playerCount }) {
    if (heroPlayersCurrent && heroPlayersMax && heroPlayersBar) {
        heroPlayersCurrent.textContent = online;
        heroPlayersMax.textContent = maxPlayers;
        const percent = maxPlayers > 0 ? Math.min(100, (online / maxPlayers) * 100) : 0;
        heroPlayersBar.style.width = percent.toFixed(1) + "%";
    }

    if (typeof tps === "string") {
        tps = parseFloat(tps.replace(",", "."));
    }
    if (Number.isNaN(tps)) tps = 20.0;

    if (heroTpsValue && heroTpsBar && heroTpsStatus) {
        heroTpsValue.textContent = tps.toFixed(2);
        const percent = Math.min(100, (tps / 20) * 100);
        heroTpsBar.style.width = percent.toFixed(1) + "%";

        heroTpsBar.classList.remove("low", "critical");
        if (tps >= 19) {
            heroTpsStatus.textContent = "Stabilny";
        } else if (tps >= 17) {
            heroTpsStatus.textContent = "Obciążony";
            heroTpsBar.classList.add("low");
        } else {
            heroTpsStatus.textContent = "Krytyczny";
            heroTpsBar.classList.add("critical");
        }
    }

    if (heroPlayerCount) heroPlayerCount.textContent = playerCount;
    if (heroRankCount) heroRankCount.textContent = rankCount;
}

/// ─────────────────────────────────────────────────────────────
/// API – OVERVIEW
/// ─────────────────────────────────────────────────────────────
async function fetchOverview() {
    try {
        const res = await fetch("/api/overview");
        if (!res.ok) return;
        const data = await res.json();

        const online = data.onlinePlayers ?? 0;
        const maxPlayers = data.maxPlayers ?? 0;
        const tps = data.tps ?? 20.0;
        const rankCount = data.rankCount ?? 0;
        const playerCount = data.playerCount ?? 0;
        const totalMessages = data.totalMessages ?? 0;
        const totalBans = data.totalBans ?? 0;
        const helpopCount = data.helpopCount ?? 0;
        const warCount = data.warCount ?? 0;

        if (hudPlayers) hudPlayers.textContent = `${online}/${maxPlayers}`;
        if (hudTps) hudTps.textContent = tps;

        if (ovPlayers) ovPlayers.textContent = online;
        if (ovMaxPlayers) ovMaxPlayers.textContent = maxPlayers;
        if (ovTps) ovTps.textContent = tps;
        if (ovRankCount) ovRankCount.textContent = rankCount;
        if (ovPlayerCount) ovPlayerCount.textContent = playerCount;
        if (ovMessages) ovMessages.textContent = totalMessages;
        if (ovBans) ovBans.textContent = totalBans;
        if (ovHelpop) ovHelpop.textContent = helpopCount;
        if (ovWar) ovWar.textContent = warCount;

        updateHeroFromOverview({ online, maxPlayers, tps, rankCount, playerCount });
        updateOnlineChart(online);
    } catch (e) {
        console.warn("overview fetch failed", e);
    }
}

/// ─────────────────────────────────────────────────────────────
/// WYKRES ONLINE (LIVE)
/// ─────────────────────────────────────────────────────────────
function updateOnlineChart(online) {
    const ctx = document.getElementById("onlineChart");
    if (!ctx) return;

    const label = new Date().toLocaleTimeString("pl-PL", { hour12: false });
    onlineLabels.push(label);
    onlinePoints.push(online);

    const maxPoints = 240; // ~20 minut przy odświeżaniu 5s
    if (onlineLabels.length > maxPoints) {
        onlineLabels.shift();
        onlinePoints.shift();
    }

    if (!onlineChart) {
        onlineChart = new Chart(ctx, {
            type: "line",
            data: {
                labels: onlineLabels,
                datasets: [
                    {
                        label: "Gracze online",
                        data: onlinePoints,
                        borderColor: "rgba(94,234,212,0.9)",
                        backgroundColor: "rgba(59,130,246,0.15)",
                        borderWidth: 2,
                        tension: 0.25,
                        fill: true,
                        pointRadius: 0
                    }
                ]
            },
            options: {
                responsive: true,
                animation: false,
                scales: {
                    y: {
                        beginAtZero: true,
                        ticks: { color: "#cbd5e1" },
                        grid: { color: "rgba(148,163,184,0.15)" }
                    },
                    x: {
                        ticks: { color: "#cbd5e1", maxRotation: 0, autoSkip: true, maxTicksLimit: 8 },
                        grid: { display: false }
                    }
                },
                plugins: {
                    legend: { labels: { color: "#e2e8f0" } },
                    tooltip: { mode: "nearest", intersect: false }
                }
            }
        });
    } else {
        onlineChart.data.labels = onlineLabels;
        onlineChart.data.datasets[0].data = onlinePoints;
        onlineChart.update();
    }
}

/// ─────────────────────────────────────────────────────────────
/// API – RANGI
/// ─────────────────────────────────────────────────────────────
async function fetchRanks() {
    try {
        const res = await fetch("/api/ranks");
        if (!res.ok) return;
        const data = await res.json();
        allRanks = Array.isArray(data) ? data : [];
        renderRanks();
        renderRankFilter();
    } catch (e) {
        console.warn("ranks fetch failed", e);
    }
}

function renderRanks() {
    if (!ranksList) return;
    ranksList.innerHTML = "";

    allRanks
        .slice()
        .sort((a, b) => (a.priority ?? 0) - (b.priority ?? 0))
        .forEach(rank => {
            const li = document.createElement("li");

            const displayName = rank.displayName || rank.name || "";
            const colorVal = normalizeColorValue(rank.color || "&7");
            const hex = colorVal.startsWith("#") ? colorVal : mcColorToHex(colorVal);

            li.innerHTML = `
                <div class="rank-item-main">
                    <span class="rank-color-pill" style="background:${hex};"></span>
                    <div class="rank-text">
                        <div class="rank-name">${displayName}</div>
                        <div class="rank-meta">
                            <span>${rank.name || ""}</span>
                            <span>• priorytet: ${rank.priority ?? 0}</span>
                            <span>• perms: ${(rank.permissions || []).length}</span>
                        </div>
                    </div>
                </div>
                <div class="rank-actions">
                    <button type="button" class="rank-edit-btn">Edytuj</button>
                    <button type="button" class="rank-delete-btn">Usuń</button>
                </div>
            `;

            const editBtn = li.querySelector(".rank-edit-btn");
            const deleteBtn = li.querySelector(".rank-delete-btn");

            li.addEventListener("click", () => {
                activeRankName = rank.name || null;
                fillRankForm(rank);
            });

            if (editBtn) {
                editBtn.addEventListener("click", (ev) => {
                    ev.stopPropagation();
                    activeRankName = rank.name || null;
                    fillRankForm(rank);
                });
            }

            if (deleteBtn) {
                deleteBtn.addEventListener("click", async (ev) => {
                    ev.stopPropagation();
                    if (!rank.name) return;
                    if (!confirm(`Usunąć rangę "${rank.name}"?`)) return;
                    try {
                        const res = await fetch(`/api/ranks/${encodeURIComponent(rank.name)}`, {
                            method: "DELETE"
                        });
                        if (res.ok) {
                            await fetchRanks();
                            setRankFormStatus(`Usunięto rangę ${rank.name}`, "ok");
                        } else {
                            setRankFormStatus("Nie udało się usunąć rangi", "error");
                        }
                    } catch (e) {
                        console.warn("delete rank failed", e);
                        setRankFormStatus("Błąd przy usuwaniu rangi", "error");
                    }
                });
            }

            ranksList.appendChild(li);
        });
}

function fillRankForm(rank) {
    if (!rank) return;
    rankNameInput.value = rank.name || "";
    rankColorInput.value = normalizeColorValue(rank.color || "&7");
    rankDisplayInput.value = rank.displayName || "";
    rankPermsInput.value = (rank.permissions || []).join("\\n");
    updateColorPainterFromValue(rankColorInput.value);
    syncQuickPermsFromPermList();
}

function setRankFormStatus(message, type) {
    if (!rankFormStatus) return;
    rankFormStatus.textContent = message || "";
    rankFormStatus.className = "form-status";
    if (type === "ok") rankFormStatus.classList.add("ok");
    if (type === "error") rankFormStatus.classList.add("error");
}

if (rankForm) {
    rankForm.addEventListener("submit", async (e) => {
        e.preventDefault();
        const name = (rankNameInput.value || "").trim();
        if (!name) return;

        const color = normalizeColorValue(rankColorInput.value || "&7");
        const displayName = (rankDisplayInput.value || "").trim() || name;

        const perms = uniqueArray(
            (rankPermsInput.value || "")
                .split(/\\r?\\n/)
                .map((s) => s.trim())
        );

        const body = {
            name,
            color,
            displayName,
            permissions: perms
        };

        try {
            const res = await fetch("/api/ranks", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify(body)
            });

            if (res.ok) {
                setRankFormStatus("Ranga zapisana", "ok");
                await fetchRanks();
            } else {
                setRankFormStatus("Błąd przy zapisie rangi", "error");
            }
        } catch (err) {
            console.warn("rank save failed", err);
            setRankFormStatus("Błąd przy zapisie rangi", "error");
        }
    });
}

/// ─────────────────────────────────────────────────────────────
/// API – STATYSTYKI GRACZY
/// ─────────────────────────────────────────────────────────────
async function fetchStats() {
    try {
        const res = await fetch("/api/stats");
        if (!res.ok) return;
        const data = await res.json();
        allStats = Array.isArray(data) ? data : [];
        renderStats();
    } catch (e) {
        console.warn("stats fetch failed", e);
    }
}

function renderRankFilter() {
    const ranks = uniqueArray(allRanks.map(r => r.name || "").filter(Boolean));

    if (statsRankFilter) {
        const current = statsRankFilter.value;
        statsRankFilter.innerHTML = '<option value="">Wszystkie</option>';
        ranks.forEach(name => {
            const opt = document.createElement("option");
            opt.value = name;
            opt.textContent = name;
            statsRankFilter.appendChild(opt);
        });
        statsRankFilter.value = current;
    }

    if (statsRankChecks) {
        statsRankChecks.innerHTML = "";
        const allLabel = document.createElement("label");
        allLabel.className = "check-pill";
        const allInput = document.createElement("input");
        allInput.type = "checkbox";
        allInput.checked = true;
        allInput.dataset.value = "";
        allLabel.appendChild(allInput);
        allLabel.append("Wszyscy");
        statsRankChecks.appendChild(allLabel);

        ranks.forEach((name) => {
            const label = document.createElement("label");
            label.className = "check-pill";
            const input = document.createElement("input");
            input.type = "checkbox";
            input.dataset.value = name;
            input.checked = true;
            label.appendChild(input);
            label.append(name);
            statsRankChecks.appendChild(label);
        });

        statsRankChecks.querySelectorAll("input").forEach((input) => {
            input.addEventListener("change", renderStats);
        });
    }
}

function renderStats() {
    if (!statsTableBody) return;
    const filterRank = statsRankFilter ? statsRankFilter.value : "";
    let activeRanks = [];
    if (statsRankChecks) {
        const checked = Array.from(statsRankChecks.querySelectorAll("input:checked"))
            .map((i) => i.dataset.value || "");
        activeRanks = checked.filter(Boolean);
    }
    const search = statsSearch ? statsSearch.value.trim().toLowerCase() : "";

    statsTableBody.innerHTML = "";
    allStats
        .slice()
        .sort((a, b) => (b.playTimeMs ?? 0) - (a.playTimeMs ?? 0))
        .filter(s => !filterRank || (s.rank || "") === filterRank)
        .filter(s => activeRanks.length === 0 || activeRanks.includes(s.rank || ""))
        .filter(s => !search || (s.name || "").toLowerCase().includes(search))
        .forEach(stats => {
            const tr = document.createElement("tr");

            const tdName = document.createElement("td");
            tdName.textContent = stats.name || "";

            const tdRank = document.createElement("td");
            tdRank.textContent = stats.rank || "";

            const tdKills = document.createElement("td");
            tdKills.textContent = stats.kills ?? 0;

            const tdDeaths = document.createElement("td");
            tdDeaths.textContent = stats.deaths ?? 0;

            const tdPlay = document.createElement("td");
            tdPlay.textContent = formatMinutes(stats.playTimeMs ?? 0);

            const tdBans = document.createElement("td");
            tdBans.textContent = stats.bans ?? 0;

            const tdMutes = document.createElement("td");
            tdMutes.textContent = stats.mutes ?? 0;

            const tdWarns = document.createElement("td");
            tdWarns.textContent = stats.warns ?? 0;

            tr.appendChild(tdName);
            tr.appendChild(tdRank);
            tr.appendChild(tdKills);
            tr.appendChild(tdDeaths);
            tr.appendChild(tdPlay);
            tr.appendChild(tdBans);
            tr.appendChild(tdMutes);
            tr.appendChild(tdWarns);

            tr.addEventListener("click", () => openDrawer(stats));

            statsTableBody.appendChild(tr);
        });
}

function minutesToHoursMinutes(ms) {
    const minutes = Math.floor((ms ?? 0) / 1000 / 60);
    const hours = Math.floor(minutes / 60);
    const rest = minutes % 60;
    return `${hours}h ${rest}m`;
}

function openDrawer(stats) {
    if (!playerDrawer) return;
    playerDrawer.classList.add("open");
    drawerName && (drawerName.textContent = stats.name || "");
    drawerRank && (drawerRank.textContent = stats.rank || "");
    drawerKills && (drawerKills.textContent = stats.kills ?? 0);
    drawerDeaths && (drawerDeaths.textContent = stats.deaths ?? 0);
    drawerPlaytime && (drawerPlaytime.textContent = minutesToHoursMinutes(stats.playTimeMs));
    drawerBans && (drawerBans.textContent = stats.bans ?? 0);
    drawerMutes && (drawerMutes.textContent = stats.mutes ?? 0);
    drawerWarns && (drawerWarns.textContent = stats.warns ?? 0);
    drawerHelpop && (drawerHelpop.textContent = stats.helpopSent ?? 0);
    drawerWar && (drawerWar.textContent = stats.warSent ?? 0);

    if (drawerAvatar) {
        const url = `https://crafatar.com/avatars/${stats.uuid || "0000"}?size=160&overlay`;
        drawerAvatar.src = url;
    }

    if (drawerPermSelect) {
        drawerPermSelect.innerHTML = "";
        allRanks.forEach((r) => {
            const opt = document.createElement("option");
            opt.value = r.name;
            opt.textContent = r.name;
            drawerPermSelect.appendChild(opt);
        });
        drawerPermSelect.value = stats.rank || "";
    }

    if (drawerBars) {
        drawerBars.innerHTML = "";
        const totalMinutes = Math.floor((stats.playTimeMs ?? 0) / 1000 / 60);
        const perDay = Math.max(1, Math.floor(totalMinutes / 7));
        for (let i = 0; i < 7; i++) {
            const bar = document.createElement("div");
            bar.className = "day-bar";
            const height = Math.min(100, perDay / 5);
            bar.style.height = `${height}%`;
            bar.title = `Dzień ${i + 1}: ${perDay} min`;
            drawerBars.appendChild(bar);
        }
    }

    if (drawerBtnAdd && drawerPermSelect) {
        drawerBtnAdd.onclick = async () => {
            const rank = drawerPermSelect.value;
            await updatePlayerRank(stats.uuid, rank);
        };
    }
    if (drawerBtnRemove) {
        drawerBtnRemove.onclick = async () => {
            await updatePlayerRank(stats.uuid, "default");
        };
    }
}

async function updatePlayerRank(uuid, rank) {
    try {
        const res = await fetch(`/api/stats/${uuid}/rank`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ rank })
        });
        if (res.ok) {
            await fetchStats();
            setRankFormStatus("Zmieniono rangę gracza", "ok");
        } else {
            setRankFormStatus("Nie udało się zmienić rangi", "error");
        }
    } catch (e) {
        console.warn("update rank failed", e);
        setRankFormStatus("Błąd przy zmianie rangi", "error");
    }
}

/// ─────────────────────────────────────────────────────────────
/// INNE – helpop historia i konfiguracja sidebaru
/// ─────────────────────────────────────────────────────────────
async function fetchHelpopHistory() {
    try {
        const res = await fetch("/api/helpop");
        if (!res.ok) return;
        const data = await res.json();
        if (!Array.isArray(data) || !helpopHistoryList) return;
        helpopHistoryList.innerHTML = "";
        data.slice().reverse().forEach((item) => {
            const li = document.createElement("li");
            const date = new Date(item.timestamp || Date.now());
            li.innerHTML = `<strong>${item.sender}</strong> → ${item.message} <span>${date.toLocaleTimeString()}</span>`;
            helpopHistoryList.appendChild(li);
        });
    } catch (e) {
        console.warn("helpop history fetch failed", e);
    }
}

async function fetchUiConfig() {
    try {
        const res = await fetch("/api/config/ui");
        if (!res.ok) return;
        const data = await res.json();
        if (toggleSidebar) toggleSidebar.checked = !!data.sidebarEnabled;
        if (sidebarTitleInput) sidebarTitleInput.value = data.sidebarTitle || "McPanel";
        if (sidebarAccentInput) sidebarAccentInput.value = data.sidebarAccent || "&b";
    } catch (e) {
        console.warn("ui config fetch failed", e);
    }
}

async function saveUiConfig() {
    try {
        const body = {
            sidebarEnabled: toggleSidebar ? toggleSidebar.checked : true,
            sidebarTitle: sidebarTitleInput ? sidebarTitleInput.value : "McPanel",
            sidebarAccent: sidebarAccentInput ? sidebarAccentInput.value : "&b"
        };
        await fetch("/api/config/ui", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(body)
        });
    } catch (e) {
        console.warn("ui config save failed", e);
    }
}

if (statsRankFilter) {
    statsRankFilter.addEventListener("change", renderStats);
}
if (statsSearch) {
    statsSearch.addEventListener("input", () => {
        // mały debounce nie jest krytyczny – lista nie będzie ogromna
        renderStats();
    });
}

/// ─────────────────────────────────────────────────────────────
/// COLOR PAINTER – inicjalizacja i logika
/// ─────────────────────────────────────────────────────────────
function buildColorPalette() {
    if (!rankColorPalette) return;
    rankColorPalette.innerHTML = "";
    Object.entries(MC_COLORS).forEach(([code, hex]) => {
        const swatch = document.createElement("div");
        swatch.className = "color-swatch";
        swatch.style.background = hex;
        swatch.dataset.code = "&" + code;
        swatch.textContent = ""; // tekst idzie w ::after
        rankColorPalette.appendChild(swatch);
    });

    rankColorPalette.addEventListener("click", (e) => {
        const target = e.target.closest(".color-swatch");
        if (!target) return;
        const code = target.dataset.code;
        if (!code) return;
        rankColorInput.value = code;
        updateColorPainterFromValue(code);
    });
}

function updateColorPainterFromValue(value) {
    const normalized = normalizeColorValue(value);
    const hex = normalized.startsWith("#") ? normalized : mcColorToHex(normalized);

    if (rankColorPicker) {
        try {
            rankColorPicker.value = hex;
        } catch (_) {
            // ignorujemy, jeśli browser ma problem z wartością
        }
    }

    if (rankColorPreview) {
        rankColorPreview.style.background = `radial-gradient(circle at top left, rgba(15,23,42,0.98), rgba(15,23,42,0.9))`;
        rankColorPreview.style.boxShadow = "0 12px 24px rgba(15,23,42,0.9)";
        rankColorPreview.textContent = "[VIP] Nick";
        rankColorPreview.style.color = "#ffffff";
        rankColorPreview.style.textShadow = "0 0 12px rgba(0,0,0,0.9)";
        rankColorPreview.style.borderColor = "rgba(31,41,55,0.9)";
        rankColorPreview.style.borderWidth = "1px";
        rankColorPreview.style.borderStyle = "solid";
        rankColorPreview.style.setProperty("--rank-preview-color", hex);
        rankColorPreview.style.color = hex;
    }

    if (rankColorPalette) {
        const swatches = rankColorPalette.querySelectorAll(".color-swatch");
        swatches.forEach(el => el.classList.remove("active"));
        swatches.forEach(el => {
            if (el.dataset.code === normalized) {
                el.classList.add("active");
            }
        });
    }
}

if (rankColorPicker) {
    rankColorPicker.addEventListener("input", (e) => {
        const hex = (e.target.value || "").trim();
        if (!hex) return;
        rankColorInput.value = hex;
        updateColorPainterFromValue(hex);
    });
}

if (rankColorInput) {
    rankColorInput.addEventListener("input", (e) => {
        const val = e.target.value;
        updateColorPainterFromValue(val);
    });
}

/// ─────────────────────────────────────────────────────────────
/// QUICK PERMS – generowanie i synchronizacja z textarea
/// ─────────────────────────────────────────────────────────────
function buildQuickPerms() {
    if (!quickPermsContainer) return;
    quickPermsContainer.innerHTML = "";
    QUICK_PERMS.forEach(item => {
        const label = document.createElement("label");
        label.className = "quick-perm";

        const input = document.createElement("input");
        input.type = "checkbox";
        input.dataset.perm = item.perm;

        const span = document.createElement("span");
        span.textContent = item.label;

        label.appendChild(input);
        label.appendChild(span);
        quickPermsContainer.appendChild(label);

        input.addEventListener("change", () => {
            syncPermListFromQuickPerms();
        });
    });
}

function syncPermListFromQuickPerms() {
    if (!rankPermsInput || !quickPermsContainer) return;

    const manualPerms = (rankPermsInput.value || "")
        .split(/\\r?\\n/)
        .map(s => s.trim())
        .filter(Boolean);

    const checkedPerms = Array.from(
        quickPermsContainer.querySelectorAll("input[type='checkbox']")
    )
        .filter(input => input.checked)
        .map(input => input.dataset.perm || "");

    const merged = uniqueArray([...manualPerms, ...checkedPerms]);
    rankPermsInput.value = merged.join("\\n");
    syncQuickPermsFromPermList();
}

function syncQuickPermsFromPermList() {
    if (!rankPermsInput || !quickPermsContainer) return;
    const perms = (rankPermsInput.value || "")
        .split(/\\r?\\n/)
        .map(s => s.trim())
        .filter(Boolean);

    const set = new Set(perms);
    quickPermsContainer.querySelectorAll("label.quick-perm").forEach(label => {
        const input = label.querySelector("input[type='checkbox']");
        const perm = input?.dataset.perm || "";
        const active = set.has(perm);
        if (input) input.checked = active;
        label.classList.toggle("active", active);
    });
}

/// ─────────────────────────────────────────────────────────────
/// TABS
/// ─────────────────────────────────────────────────────────────
function initTabs() {
    const tabs = document.querySelectorAll(".tab");
    const tabContents = document.querySelectorAll(".tab-content");

    tabs.forEach(btn => {
        btn.addEventListener("click", () => {
            const target = btn.dataset.tab;
            if (!target) return;

            tabs.forEach(b => b.classList.remove("active"));
            tabContents.forEach(section => section.classList.remove("active"));

            btn.classList.add("active");
            const content = document.getElementById(`tab-${target}`);
            if (content) content.classList.add("active");
        });
    });
}

/// ─────────────────────────────────────────────────────────────
/// INIT
/// ─────────────────────────────────────────────────────────────
function init() {
    initTabs();
    buildColorPalette();
    buildQuickPerms();
    syncQuickPermsFromPermList();

    fetchOverview();
    fetchRanks();
    fetchStats();
    fetchHelpopHistory();
    fetchUiConfig();

    setInterval(fetchOverview, 5000);
    setInterval(fetchJoins, 15000);
    setInterval(fetchStats, 10000);
    setInterval(fetchHelpopHistory, 15000);

    const saveBtn = document.getElementById("saveUiConfig");
    saveBtn?.addEventListener("click", saveUiConfig);
}

window.addEventListener("load", init);
