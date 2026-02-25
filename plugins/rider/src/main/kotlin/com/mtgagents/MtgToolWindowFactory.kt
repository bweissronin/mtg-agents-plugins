package com.mtgagents

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import com.intellij.ui.content.ContentFactory
import com.mtgagents.model.BattlefieldData
import com.mtgagents.model.ManaColor
import org.cef.browser.CefBrowser
import java.io.File
import java.util.Base64
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.BorderLayout
import javax.swing.JPanel

class MtgToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = MtgBattlefieldPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "Battlefield", false)
        toolWindow.contentManager.addContent(content)
    }
}

class MtgBattlefieldPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val browser: JBCefBrowser
    private val jsQuery: JBCefJSQuery

    init {
        browser = JBCefBrowser()
        jsQuery = JBCefJSQuery.create(browser as JBCefBrowserBase)

        // Handle messages from JavaScript
        jsQuery.addHandler { message ->
            handleJsMessage(message)
            null
        }

        // Add load handler to inject the bridge function
        browser.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(browser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
                if (frame.isMain) {
                    injectJsBridge(browser)
                }
            }
        }, browser.cefBrowser)

        add(browser.component, BorderLayout.CENTER)

        // Load the battlefield HTML with data
        loadBattlefieldView()
    }

    private fun loadBattlefieldView() {
        val service = project.getService(MtgAgentService::class.java)
        val battlefield = service.scanProject()
        val html = generateBattlefieldHtml(battlefield)
        browser.loadHTML(html)
    }

    private fun getManaIconsJson(): String {
        val icons = mutableMapOf<String, String>()
        val manaSymbols = listOf("W", "U", "B", "R", "G", "C")

        for (symbol in manaSymbols) {
            val resourcePath = "/icons/mana/${symbol.lowercase()}.png"
            try {
                val stream = javaClass.getResourceAsStream(resourcePath)
                if (stream != null) {
                    val bytes = stream.readBytes()
                    val base64 = Base64.getEncoder().encodeToString(bytes)
                    icons[symbol] = "data:image/png;base64,$base64"
                    stream.close()
                }
            } catch (e: Exception) {
                // Icon not found, will use fallback
            }
        }

        return icons.entries.joinToString(",") { (k, v) -> "\"$k\": \"$v\"" }
    }

    private fun generateBattlefieldHtml(battlefield: BattlefieldData): String {
        val manaIconsJson = getManaIconsJson()
        val agentsJson = battlefield.agents.map { agent ->
            val artDataUrl = getArtDataUrl(agent.artUrl)
            val abilitiesJson = agent.abilities.map { ability ->
                """{"name": "${escapeJs(ability.name)}", "description": "${escapeJs(ability.description)}"}"""
            }.joinToString(",")
            """{
                "name": "${escapeJs(agent.name)}",
                "power": ${agent.power},
                "toughness": ${agent.toughness},
                "color": "${agent.colorIdentity.firstOrNull()?.name?.lowercase() ?: "blue"}",
                "artUrl": ${if (artDataUrl != null) "\"${escapeJs(artDataUrl)}\"" else "null"},
                "manaCost": "${escapeJs(agent.manaCost)}",
                "typeLine": "${escapeJs(agent.typeLine)}",
                "abilities": [$abilitiesJson],
                "flavorText": ${if (agent.flavorText != null) "\"${escapeJs(agent.flavorText)}\"" else "null"},
                "collectorInfo": "${escapeJs(agent.collectorInfo)}"
            }"""
        }.joinToString(",")

        val relationshipsJson = battlefield.relationships.map { rel ->
            """{ "source": "${escapeJs(rel.sourceAgent)}", "target": "${escapeJs(rel.targetAgent)}", "type": "${rel.relationshipType.name}" }"""
        }.joinToString(",")

        return """
<!DOCTYPE html>
<html>
<head>
<style>
@import url('https://fonts.googleapis.com/css2?family=Cinzel:wght@400;700&display=swap');

* { box-sizing: border-box; margin: 0; padding: 0; }
html, body {
    width: 100%; height: 100%; overflow: hidden;
    background: linear-gradient(135deg, #0d0d14 0%, #1a1a2e 50%, #0d0d14 100%);
    font-family: Georgia, serif;
}
#battlefield { width: 100%; height: 100%; position: relative; overflow: auto; }
svg { position: absolute; top: 0; left: 0; width: 100%; height: 100%; pointer-events: none; z-index: 1; }

/* Edge styles */
.edge { stroke-width: 2; fill: none; filter: drop-shadow(0 1px 2px rgba(0,0,0,0.5)); }
.edge.HANDOFF { stroke: #4CAF50; }
.edge.TOOL_CALL { stroke: #2196F3; stroke-dasharray: 8,4; }
.edge.SUB_AGENT { stroke: #FF9800; stroke-width: 3; }
.edge.REFERENCE { stroke: #9E9E9E; stroke-dasharray: 3,3; opacity: 0.7; }

/* Mini Card - Full Art Style */
.mini-card {
    position: absolute;
    width: 150px;
    height: 200px;
    border-radius: 12px;
    cursor: move;
    user-select: none;
    transition: transform 0.15s, box-shadow 0.15s;
    z-index: 10;
    overflow: hidden;
    box-shadow: 0 4px 12px rgba(0,0,0,0.5), 0 8px 24px rgba(0,0,0,0.3);
}

.mini-card:hover {
    transform: translateY(-4px) scale(1.02);
    z-index: 50;
    box-shadow: 0 8px 20px rgba(0,0,0,0.6), 0 16px 40px rgba(0,0,0,0.4);
}

/* Full art background */
.mini-card-art {
    position: absolute;
    top: 0; left: 0; right: 0; bottom: 0;
    background: linear-gradient(180deg, #1a1a2e 0%, #0d0d14 100%);
}

.mini-card-art img {
    width: 100%;
    height: 100%;
    object-fit: cover;
}

.art-placeholder {
    width: 100%;
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
    background: linear-gradient(180deg, #1a1a2e 0%, #0d0d14 100%);
}

.art-placeholder::after {
    content: '';
    width: 50px;
    height: 50px;
    border-radius: 50%;
    background: radial-gradient(circle, rgba(100,150,255,0.4) 0%, transparent 70%);
    animation: pulse 3s ease-in-out infinite;
}

@keyframes pulse {
    0%, 100% { transform: scale(0.8); opacity: 0.4; }
    50% { transform: scale(1.2); opacity: 0.7; }
}

/* Name bar overlay at top */
.card-name {
    position: absolute;
    top: 8px; left: 8px; right: 8px;
    font-family: 'Cinzel', serif;
    font-size: 11px;
    font-weight: 700;
    text-align: center;
    padding: 6px 10px;
    background: rgba(0, 0, 0, 0.8);
    border-radius: 6px;
    color: #ffffff;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    border: 1px solid rgba(255,255,255,0.15);
    text-shadow: 0 1px 2px rgba(0,0,0,0.8);
}

/* Mana cost overlay at top right */
.card-mana {
    position: absolute;
    top: 8px; right: 8px;
    display: flex;
    gap: 2px;
    z-index: 5;
}

.card-mana .mana-pip {
    width: 16px;
    height: 16px;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 10px;
    font-weight: bold;
    border: 1px solid rgba(0,0,0,0.3);
    box-shadow: 0 1px 3px rgba(0,0,0,0.4);
}

/* Power/Toughness box overlay at bottom right */
.card-pt {
    position: absolute;
    bottom: 8px; right: 8px;
    font-family: 'Cinzel', serif;
    font-size: 14px;
    font-weight: 700;
    text-align: center;
    padding: 4px 10px;
    background: rgba(0, 0, 0, 0.8);
    border-radius: 6px;
    color: #ffffff;
    border: 1px solid rgba(255,255,255,0.15);
    text-shadow: 0 1px 2px rgba(0,0,0,0.8);
}

/* Controls */
.controls {
    position: fixed;
    top: 10px;
    right: 10px;
    display: flex;
    gap: 8px;
    z-index: 1000;
}

.btn {
    padding: 8px 16px;
    background: linear-gradient(180deg, rgba(60,60,80,0.9) 0%, rgba(30,30,50,0.9) 100%);
    border: 1px solid rgba(255,255,255,0.2);
    border-radius: 6px;
    color: #fff;
    cursor: pointer;
    font-size: 12px;
    font-family: 'Cinzel', serif;
    box-shadow: 0 2px 8px rgba(0,0,0,0.4);
    transition: all 0.15s;
}

.btn:hover {
    background: linear-gradient(180deg, rgba(80,80,100,0.9) 0%, rgba(50,50,70,0.9) 100%);
    transform: translateY(-1px);
}

/* Stats panel */
.stats {
    position: fixed;
    top: 10px;
    left: 10px;
    background: linear-gradient(180deg, rgba(20,20,30,0.95) 0%, rgba(10,10,20,0.95) 100%);
    padding: 10px 14px;
    border-radius: 8px;
    border: 1px solid rgba(255,255,255,0.1);
    color: #fff;
    font-size: 12px;
    z-index: 1000;
    box-shadow: 0 4px 12px rgba(0,0,0,0.5);
}

.stats .count {
    color: #4CAF50;
    font-weight: bold;
    font-size: 14px;
}

/* Legend */
.legend {
    position: fixed;
    bottom: 10px;
    left: 10px;
    background: linear-gradient(180deg, rgba(20,20,30,0.95) 0%, rgba(10,10,20,0.95) 100%);
    padding: 12px 16px;
    border-radius: 8px;
    border: 1px solid rgba(255,255,255,0.1);
    color: #fff;
    font-size: 11px;
    z-index: 1000;
    box-shadow: 0 4px 12px rgba(0,0,0,0.5);
}

.legend-item {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 6px;
}

.legend-item:last-child { margin-bottom: 0; }

.legend-line {
    width: 28px;
    height: 3px;
    border-radius: 1px;
}

.legend-line.handoff { background: #4CAF50; }
.legend-line.tool {
    background: repeating-linear-gradient(90deg, #2196F3 0, #2196F3 6px, transparent 6px, transparent 10px);
}
.legend-line.sub { background: #FF9800; height: 4px; }
.legend-line.ref {
    background: repeating-linear-gradient(90deg, #9E9E9E 0, #9E9E9E 3px, transparent 3px, transparent 6px);
    opacity: 0.7;
}

/* Empty state */
.empty-state {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    height: 100%;
    color: rgba(255,255,255,0.5);
    text-align: center;
}

.empty-state h2 {
    font-family: 'Cinzel', serif;
    font-size: 24px;
    margin-bottom: 10px;
}

/* Card Zoom Modal */
.card-modal {
    display: none;
    position: fixed;
    top: 0; left: 0; right: 0; bottom: 0;
    background: rgba(0, 0, 0, 0.9);
    z-index: 2000;
    justify-content: center;
    align-items: center;
}
.card-modal.visible { display: flex; }

.modal-content {
    animation: zoomIn 0.2s ease-out;
    position: relative;
}

@keyframes zoomIn {
    from { transform: scale(0.8); opacity: 0; }
    to { transform: scale(1); opacity: 1; }
}

/* Full card in modal */
.full-card {
    width: 320px;
    height: 450px;
    border-radius: 16px;
    position: relative;
    overflow: hidden;
    box-shadow: 0 20px 60px rgba(0,0,0,0.8), 0 0 40px rgba(100,150,255,0.3);
}

.full-card-art {
    position: absolute;
    top: 0; left: 0; right: 0; bottom: 0;
    background: linear-gradient(180deg, #1a1a2e 0%, #0d0d14 100%);
}
.full-card-art img {
    width: 100%; height: 100%;
    object-fit: cover;
}

/* Name bar overlay */
.full-name-bar {
    position: absolute;
    top: 16px; left: 16px; right: 16px;
    background: linear-gradient(180deg, rgba(30,30,40,0.95) 0%, rgba(20,20,30,0.95) 100%);
    border-radius: 8px;
    padding: 10px 14px;
    display: flex;
    justify-content: space-between;
    align-items: center;
    box-shadow: 0 4px 12px rgba(0,0,0,0.5);
    border: 1px solid rgba(255,255,255,0.1);
}
.full-card-name {
    font-family: 'Cinzel', serif;
    font-size: 16px;
    font-weight: 700;
    color: #fff;
}
.full-mana-cost {
    font-size: 14px;
    color: #ffd700;
}

/* Type line */
.full-type-line {
    position: absolute;
    top: 200px; left: 16px; right: 16px;
    background: rgba(0,0,0,0.8);
    border-radius: 4px;
    padding: 6px 10px;
    font-size: 11px;
    color: rgba(255,255,255,0.9);
    border: 1px solid rgba(255,255,255,0.15);
    transition: top 0.3s ease;
}
.full-type-line.shifted { top: 56px; }

/* Text box */
.full-text-box {
    position: absolute;
    top: 236px; left: 16px; right: 16px; bottom: 60px;
    background: rgba(0,0,0,0.75);
    border-radius: 8px;
    padding: 12px;
    padding-bottom: 28px;
    overflow-y: auto;
    border: 1px solid rgba(255,255,255,0.15);
    transition: top 0.3s ease;
}
.full-text-box.expanded { top: 88px; }
.full-text-box::-webkit-scrollbar { width: 4px; }
.full-text-box::-webkit-scrollbar-track { background: transparent; }
.full-text-box::-webkit-scrollbar-thumb { background: rgba(255,255,255,0.2); border-radius: 2px; }

.full-ability { margin-bottom: 10px; }
.full-ability:last-child { margin-bottom: 0; }
.full-ability-name {
    font-weight: bold;
    color: #fff;
    font-size: 12px;
    margin-bottom: 2px;
}
.full-ability-desc {
    color: rgba(255,255,255,0.7);
    font-size: 11px;
    font-style: italic;
}

/* More/less button */
.modal-more-btn {
    position: absolute;
    bottom: 6px;
    left: 50%;
    transform: translateX(-50%);
    background: rgba(255,255,255,0.15);
    border: 1px solid rgba(255,255,255,0.25);
    border-radius: 12px;
    padding: 3px 14px;
    font-size: 10px;
    color: rgba(255,255,255,0.9);
    cursor: pointer;
    z-index: 20;
}
.modal-more-btn:hover { background: rgba(255,255,255,0.25); }

/* Collector bar */
.full-collector-bar {
    position: absolute;
    bottom: 16px; left: 16px; right: 16px;
    display: flex;
    justify-content: space-between;
    align-items: flex-end;
}
.full-flavor {
    font-size: 9px;
    font-style: italic;
    color: rgba(255,255,255,0.5);
    max-width: 60%;
}
.full-pt-box {
    background: rgba(0,0,0,0.75);
    border: 1px solid rgba(255,255,255,0.2);
    border-radius: 6px;
    padding: 4px 10px;
    font-family: 'Cinzel', serif;
    font-size: 14px;
    font-weight: 700;
    color: #fff;
}

/* Navigation arrows */
.modal-nav {
    position: absolute;
    top: 50%;
    transform: translateY(-50%);
    background: rgba(255,255,255,0.1);
    border: 1px solid rgba(255,255,255,0.2);
    border-radius: 50%;
    width: 40px; height: 40px;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    color: #fff;
    font-size: 20px;
    transition: background 0.2s;
}
.modal-nav:hover { background: rgba(255,255,255,0.2); }
.modal-nav.prev { left: 20px; }
.modal-nav.next { right: 20px; }

/* Close hint */
.modal-hint {
    position: absolute;
    bottom: 20px;
    left: 50%;
    transform: translateX(-50%);
    color: rgba(255,255,255,0.4);
    font-size: 11px;
}
</style>
</head>
<body>
<div id="battlefield">
    <svg id="edges"></svg>
    <div id="nodes"></div>
</div>

<div class="controls">
    <button class="btn" onclick="refresh()">Refresh</button>
</div>

<div class="stats">
    Agents: <span class="count" id="agent-count">0</span> &nbsp;|&nbsp;
    Connections: <span class="count" id="conn-count">0</span>
</div>

<div class="legend">
    <div class="legend-item"><div class="legend-line handoff"></div> Handoff</div>
    <div class="legend-item"><div class="legend-line tool"></div> Tool Call</div>
    <div class="legend-item"><div class="legend-line sub"></div> Sub-Agent</div>
    <div class="legend-item"><div class="legend-line ref"></div> Reference</div>
</div>

<!-- Card Zoom Modal -->
<div id="cardModal" class="card-modal" onclick="closeModal(event)">
    <button class="modal-nav prev" onclick="event.stopPropagation(); navigateCard(-1)">‹</button>
    <div class="modal-content" onclick="event.stopPropagation()">
        <div id="modalCard" class="full-card"></div>
    </div>
    <button class="modal-nav next" onclick="event.stopPropagation(); navigateCard(1)">›</button>
    <div class="modal-hint">Click outside or press Escape to close • Use arrow keys to navigate</div>
</div>

<script>
const agents = [$agentsJson];
const relationships = [$relationshipsJson];
const manaIcons = {$manaIconsJson};

document.getElementById('agent-count').textContent = agents.length;
document.getElementById('conn-count').textContent = relationships.length;

const nodes = [];
const container = document.getElementById('battlefield');
const nodesDiv = document.getElementById('nodes');
const edgesSvg = document.getElementById('edges');

// Mana colors for symbols (must be defined before card creation)
const manaColors = {
    'W': { bg: '#f9faf4', fg: '#000' },
    'U': { bg: '#0e68ab', fg: '#fff' },
    'B': { bg: '#150b00', fg: '#9a8c7c' },
    'R': { bg: '#d3202a', fg: '#fff' },
    'G': { bg: '#00733e', fg: '#fff' },
    'C': { bg: '#ccc2c0', fg: '#000' }
};

function formatManaForMini(cost) {
    if (!cost) return '';
    const symbols = cost.match(/\{([^}]+)\}/g) || [];
    return symbols.map(function(s) {
        const symbol = s.replace(/[{}]/g, '');
        const color = manaColors[symbol.toUpperCase()] || { bg: '#888', fg: '#fff' };
        const isNumber = /^\d+$/.test(symbol);
        return '<span class="mana-pip" style="background:' + (isNumber ? '#888' : color.bg) + ';color:' + (isNumber ? '#fff' : color.fg) + ';">' + symbol + '</span>';
    }).join('');
}

// Create nodes
agents.forEach((agent, i) => {
    const cols = Math.ceil(Math.sqrt(agents.length));
    const row = Math.floor(i / cols);
    const col = i % cols;
    const x = 100 + col * 180;
    const y = 80 + row * 220;

    const node = { id: agent.name, x, y, data: agent };
    nodes.push(node);

    const card = document.createElement('div');
    card.className = 'mini-card ' + agent.color;
    card.id = 'card-' + i;
    card.style.left = x + 'px';
    card.style.top = y + 'px';
    card.innerHTML =
        '<div class="mini-card-art">' + (agent.artUrl ? '<img src="' + agent.artUrl + '">' : '<div class="art-placeholder"></div>') + '</div>' +
        '<div class="card-name">' + agent.name + '</div>' +
        '<div class="card-pt">' + agent.power + '/' + agent.toughness + '</div>';

    // Drag functionality with click detection
    let isDragging = false, hasDragged = false, startX, startY, startLeft, startTop;
    card.addEventListener('mousedown', e => {
        isDragging = true;
        hasDragged = false;
        startX = e.clientX; startY = e.clientY;
        startLeft = node.x; startTop = node.y;
        card.style.zIndex = 100;
    });
    document.addEventListener('mousemove', e => {
        if (!isDragging) return;
        const dx = e.clientX - startX;
        const dy = e.clientY - startY;
        if (Math.abs(dx) > 5 || Math.abs(dy) > 5) hasDragged = true;
        node.x = startLeft + dx;
        node.y = startTop + dy;
        card.style.left = node.x + 'px';
        card.style.top = node.y + 'px';
        updateEdges();
    });
    document.addEventListener('mouseup', () => {
        isDragging = false;
        card.style.zIndex = '';
    });

    // Click to zoom (only if not dragged)
    card.addEventListener('click', e => {
        if (!hasDragged) {
            e.stopPropagation();
            showCardModal(i);
        }
    });

    // Double-click to open full card dialog
    card.addEventListener('dblclick', () => {
        if (window.sendToPlugin) window.sendToPlugin('openCard:' + agent.name);
    });

    nodesDiv.appendChild(card);
});

// Create edges
function updateEdges() {
    edgesSvg.innerHTML = '<defs><marker id="arrow" markerWidth="10" markerHeight="7" refX="9" refY="3.5" orient="auto"><polygon points="0 0, 10 3.5, 0 7" fill="#4CAF50"/></marker></defs>';

    relationships.forEach(rel => {
        const source = nodes.find(n => n.id === rel.source);
        const target = nodes.find(n => n.id === rel.target);
        if (!source || !target) return;

        const x1 = source.x + 70, y1 = source.y + 90;
        const x2 = target.x + 70, y2 = target.y + 90;

        const line = document.createElementNS('http://www.w3.org/2000/svg', 'line');
        line.setAttribute('x1', x1); line.setAttribute('y1', y1);
        line.setAttribute('x2', x2); line.setAttribute('y2', y2);
        line.setAttribute('class', 'edge ' + rel.type);
        line.setAttribute('marker-end', 'url(#arrow)');
        edgesSvg.appendChild(line);
    });
}
updateEdges();

// Card zoom modal
let currentCardIndex = -1;
let modalExpanded = false;

function showCardModal(index) {
    currentCardIndex = index;
    modalExpanded = false;
    const agent = agents[index];
    const modal = document.getElementById('cardModal');
    const modalCard = document.getElementById('modalCard');

    const abilitiesHtml = agent.abilities.map(a =>
        '<div class="full-ability"><div class="full-ability-name">' + a.name + '</div>' +
        (a.description ? '<div class="full-ability-desc">' + a.description + '</div>' : '') + '</div>'
    ).join('');

    const showMoreBtn = agent.abilities.length > 3;
    const moreBtnHtml = showMoreBtn ? '<button class="modal-more-btn" id="modalMoreBtn" onclick="toggleModalExpand()">▼ more</button>' : '';

    modalCard.innerHTML =
        '<div class="full-card-art">' + (agent.artUrl ? '<img src="' + agent.artUrl + '">' : '') + '</div>' +
        '<div class="full-name-bar"><span class="full-card-name">' + agent.name + '</span><span class="full-mana-cost">' + formatManaCost(agent.manaCost) + '</span></div>' +
        '<div class="full-type-line" id="modalTypeLine">' + agent.typeLine + '</div>' +
        '<div class="full-text-box" id="modalTextBox">' + abilitiesHtml + moreBtnHtml + '</div>' +
        '<div class="full-collector-bar">' +
            '<div class="full-flavor">' + (agent.flavorText || '') + '</div>' +
            '<div class="full-pt-box">' + agent.power + '/' + agent.toughness + '</div>' +
        '</div>';

    modal.classList.add('visible');
}

function closeModal(event) {
    if (event.target.classList.contains('card-modal')) {
        document.getElementById('cardModal').classList.remove('visible');
        currentCardIndex = -1;
    }
}

function navigateCard(direction) {
    if (currentCardIndex < 0) return;
    let newIndex = currentCardIndex + direction;
    if (newIndex < 0) newIndex = agents.length - 1;
    if (newIndex >= agents.length) newIndex = 0;
    showCardModal(newIndex);
}

function toggleModalExpand() {
    modalExpanded = !modalExpanded;
    const textBox = document.getElementById('modalTextBox');
    const typeLine = document.getElementById('modalTypeLine');
    const btn = document.getElementById('modalMoreBtn');
    if (modalExpanded) {
        textBox.classList.add('expanded');
        typeLine.classList.add('shifted');
        if (btn) btn.textContent = '▲ less';
    } else {
        textBox.classList.remove('expanded');
        typeLine.classList.remove('shifted');
        if (btn) btn.textContent = '▼ more';
    }
}

function formatManaCost(cost) {
    if (!cost) return '';
    return cost.replace(/\{([^}]+)\}/g, function(m, symbol) {
        const upperSymbol = symbol.toUpperCase();
        // Use PNG icon if available
        if (manaIcons[upperSymbol]) {
            return '<img src="' + manaIcons[upperSymbol] + '" style="width:18px;height:18px;vertical-align:middle;margin-left:2px;">';
        }
        // Fallback for numbers and unknown symbols
        const color = manaColors[upperSymbol] || { bg: '#888', fg: '#fff' };
        const isNumber = /^\d+$/.test(symbol);
        return '<span style="display:inline-flex;align-items:center;justify-content:center;width:18px;height:18px;border-radius:50%;background:' + (isNumber ? '#888' : color.bg) + ';color:' + (isNumber ? '#fff' : color.fg) + ';font-size:11px;font-weight:bold;margin-left:2px;border:1px solid rgba(0,0,0,0.3);">' + symbol + '</span>';
    });
}

// Keyboard navigation
document.addEventListener('keydown', function(e) {
    const modal = document.getElementById('cardModal');
    if (!modal.classList.contains('visible')) return;

    if (e.key === 'Escape') {
        modal.classList.remove('visible');
        currentCardIndex = -1;
    } else if (e.key === 'ArrowLeft') {
        navigateCard(-1);
    } else if (e.key === 'ArrowRight') {
        navigateCard(1);
    }
});

function refresh() {
    if (window.sendToPlugin) window.sendToPlugin('refresh');
}
</script>
</body>
</html>
        """.trimIndent()
    }

    private fun injectJsBridge(cefBrowser: CefBrowser) {
        val js = """
            window.sendToPlugin = function(message) {
                ${jsQuery.inject("message")}
            };
        """.trimIndent()
        cefBrowser.executeJavaScript(js, cefBrowser.url, 0)
    }

    private fun handleJsMessage(message: String): JBCefJSQuery.Response? {
        when {
            message.startsWith("openCard:") -> {
                val cardName = message.removePrefix("openCard:")
                openCardPanel(cardName)
            }
            message == "refresh" -> {
                loadBattlefieldView()
            }
        }
        return null
    }

    private fun openCardPanel(cardName: String) {
        val service = project.getService(MtgAgentService::class.java)
        val card = service.getCachedCard(cardName)
        if (card != null) {
            MtgCardDialog(project, card, card.artUrl).show()
        }
    }

    private fun escapeJs(str: String): String {
        return str.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
    }

    private fun getArtDataUrl(artUrl: String?): String? {
        if (artUrl == null) return null
        return try {
            val file = File(artUrl)
            if (file.exists()) {
                val bytes = file.readBytes()
                val base64 = Base64.getEncoder().encodeToString(bytes)
                val mimeType = when {
                    artUrl.endsWith(".png", ignoreCase = true) -> "image/png"
                    artUrl.endsWith(".jpg", ignoreCase = true) || artUrl.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
                    artUrl.endsWith(".webp", ignoreCase = true) -> "image/webp"
                    else -> "image/png"
                }
                "data:$mimeType;base64,$base64"
            } else null
        } catch (e: Exception) {
            println("MTG: Failed to load art for battlefield: ${e.message}")
            null
        }
    }

    fun refresh() {
        loadBattlefieldView()
    }
}
