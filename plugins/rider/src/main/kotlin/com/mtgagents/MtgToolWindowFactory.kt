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

    private fun generateBattlefieldHtml(battlefield: BattlefieldData): String {
        val agentsJson = battlefield.agents.map { agent ->
            val artDataUrl = getArtDataUrl(agent.artUrl)
            """{
                "name": "${escapeJs(agent.name)}",
                "power": ${agent.power},
                "toughness": ${agent.toughness},
                "color": "${agent.colorIdentity.firstOrNull()?.name?.lowercase() ?: "blue"}",
                "artUrl": ${if (artDataUrl != null) "\"${escapeJs(artDataUrl)}\"" else "null"}
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

/* Mini Card - MTG Style */
.mini-card {
    position: absolute;
    width: 150px;
    height: 200px;
    border-radius: 8px;
    cursor: move;
    user-select: none;
    transition: transform 0.15s, box-shadow 0.15s;
    z-index: 10;
}

.mini-card:hover {
    transform: translateY(-4px) scale(1.02);
    z-index: 50;
}

/* Card outer border (black) */
.mini-card-border {
    position: absolute;
    top: 0; left: 0; right: 0; bottom: 0;
    background: #0a0a0a;
    border-radius: 8px;
    box-shadow:
        0 4px 8px rgba(0,0,0,0.4),
        0 8px 24px rgba(0,0,0,0.3);
}

.mini-card:hover .mini-card-border {
    box-shadow:
        0 8px 16px rgba(0,0,0,0.5),
        0 16px 40px rgba(0,0,0,0.4);
}

/* Card frame (colored) */
.mini-card-frame {
    position: absolute;
    top: 4px; left: 4px; right: 4px; bottom: 4px;
    border-radius: 5px;
    display: flex;
    flex-direction: column;
    padding: 6px;
}

/* Color variants */
.mini-card.white .mini-card-frame {
    background: linear-gradient(180deg, #f8f4e8 0%, #e5e0d0 50%, #d8d2c4 100%);
}
.mini-card.blue .mini-card-frame {
    background: linear-gradient(180deg, #2090d0 0%, #1068a8 50%, #044870 100%);
}
.mini-card.black .mini-card-frame {
    background: linear-gradient(180deg, #504848 0%, #282020 50%, #0c0808 100%);
}
.mini-card.red .mini-card-frame {
    background: linear-gradient(180deg, #d85040 0%, #a82818 50%, #700c08 100%);
}
.mini-card.green .mini-card-frame {
    background: linear-gradient(180deg, #408040 0%, #205820 50%, #083808 100%);
}
.mini-card.colorless .mini-card-frame {
    background: linear-gradient(180deg, #c8d4dc 0%, #98a8b4 50%, #788888 100%);
}

/* Card name bar */
.card-name {
    font-family: 'Cinzel', serif;
    font-size: 10px;
    font-weight: 700;
    text-align: center;
    padding: 4px 6px;
    background: linear-gradient(180deg, #f5f0e1 0%, #d8d2c4 100%);
    border: 1px solid #1a1a1a;
    border-radius: 3px 3px 0 0;
    color: #1a1a1a;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    box-shadow: inset 0 1px 0 rgba(255,255,255,0.5);
}

/* Card art box */
.card-art {
    flex: 1;
    margin: 4px 0;
    background: #1a1a1a;
    border: 2px solid #0a0a0a;
    display: flex;
    align-items: center;
    justify-content: center;
    overflow: hidden;
    box-shadow: inset 0 0 8px rgba(0,0,0,0.8);
}

.card-art img {
    width: 100%;
    height: 100%;
    object-fit: cover;
}

.art-placeholder {
    width: 40px;
    height: 40px;
    border-radius: 50%;
    background: radial-gradient(circle, rgba(100,150,255,0.4) 0%, transparent 70%);
    animation: pulse 3s ease-in-out infinite;
}

@keyframes pulse {
    0%, 100% { transform: scale(0.8); opacity: 0.4; }
    50% { transform: scale(1.2); opacity: 0.7; }
}

/* Power/Toughness box */
.card-pt {
    font-family: 'Cinzel', serif;
    font-size: 13px;
    font-weight: 700;
    text-align: center;
    padding: 3px 8px;
    background: linear-gradient(180deg, #f5f0e1 0%, #d5d0c5 100%);
    border: 1px solid #1a1a1a;
    border-radius: 4px;
    color: #1a1a1a;
    align-self: flex-end;
    box-shadow:
        inset 0 1px 0 rgba(255,255,255,0.5),
        0 2px 4px rgba(0,0,0,0.3);
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

<script>
const agents = [$agentsJson];
const relationships = [$relationshipsJson];

document.getElementById('agent-count').textContent = agents.length;
document.getElementById('conn-count').textContent = relationships.length;

const nodes = [];
const container = document.getElementById('battlefield');
const nodesDiv = document.getElementById('nodes');
const edgesSvg = document.getElementById('edges');

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
        '<div class="mini-card-border"></div>' +
        '<div class="mini-card-frame">' +
            '<div class="card-name">' + agent.name + '</div>' +
            '<div class="card-art">' + (agent.artUrl ? '<img src="' + agent.artUrl + '">' : '<div class="art-placeholder"></div>') + '</div>' +
            '<div class="card-pt">' + agent.power + '/' + agent.toughness + '</div>' +
        '</div>';

    // Drag functionality
    let isDragging = false, startX, startY, startLeft, startTop;
    card.addEventListener('mousedown', e => {
        isDragging = true;
        startX = e.clientX; startY = e.clientY;
        startLeft = node.x; startTop = node.y;
        card.style.zIndex = 100;
    });
    document.addEventListener('mousemove', e => {
        if (!isDragging) return;
        node.x = startLeft + (e.clientX - startX);
        node.y = startTop + (e.clientY - startY);
        card.style.left = node.x + 'px';
        card.style.top = node.y + 'px';
        updateEdges();
    });
    document.addEventListener('mouseup', () => {
        isDragging = false;
        card.style.zIndex = '';
    });

    // Double-click to open card
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
            MtgCardDialog(project, card).show()
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
