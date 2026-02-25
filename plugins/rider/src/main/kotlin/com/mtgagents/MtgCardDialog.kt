package com.mtgagents

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.jcef.JBCefBrowser
import com.mtgagents.model.CardData
import com.mtgagents.model.CardStyle
import com.mtgagents.model.ManaColor
import java.awt.BorderLayout
import java.awt.Dimension
import java.io.File
import java.util.Base64
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Dialog that displays a single MTG card in a JCEF browser.
 */
class MtgCardDialog(
    private val project: Project,
    private val card: CardData,
    private val artPath: String? = null
) : DialogWrapper(project, true) {

    private lateinit var browser: JBCefBrowser

    init {
        title = "MTG Card: ${card.name}"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.preferredSize = Dimension(450, 650)

        browser = JBCefBrowser()
        panel.add(browser.component, BorderLayout.CENTER)

        // Load self-contained HTML with embedded CSS and card data
        val html = generateCardHtml(card)
        browser.loadHTML(html)

        return panel
    }

    private fun generateCardHtml(card: CardData): String {
        return when (card.cardStyle) {
            CardStyle.BORDERLESS -> generateBorderlessCardHtml(card)
            CardStyle.EXTENDED -> generateExtendedCardHtml(card)
            else -> generateStandardCardHtml(card)
        }
    }

    private fun generateStandardCardHtml(card: CardData): String {
        val frameColors = getFrameColors(card.colorIdentity)
        val manaSymbolsHtml = parseManaSymbols(card.manaCost)
        val abilitiesHtml = card.abilities.joinToString("") { ability ->
            val desc = if (ability.description.isNotBlank()) {
                """<div class="ability-desc">${escapeHtml(ability.description)}</div>"""
            } else ""
            """<div class="ability"><div class="ability-name">${escapeHtml(ability.name)}</div>$desc</div>"""
        }
        val flavorHtml = if (card.flavorText != null) {
            """<div class="flavor-text">"${escapeHtml(card.flavorText)}"</div>"""
        } else ""

        val hasPT = card.power > 0 || card.toughness > 0

        return """
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<style>
@import url('https://fonts.googleapis.com/css2?family=Cinzel:wght@400;700&family=EB+Garamond:ital,wght@0,400;0,600;1,400&display=swap');

* { box-sizing: border-box; margin: 0; padding: 0; }

body {
    font-family: 'EB Garamond', Georgia, serif;
    background: linear-gradient(135deg, #0d0d14 0%, #1a1a2e 50%, #0d0d14 100%);
    display: flex; justify-content: center; align-items: center;
    min-height: 100vh; padding: 20px;
}

/* === CARD CONTAINER === */
.mtg-card {
    position: relative;
    width: 375px;
    height: 523px;
    border-radius: 12px;
    overflow: hidden;
    box-shadow:
        0 0 0 1px #000,
        0 4px 8px rgba(0,0,0,0.4),
        0 12px 40px rgba(0,0,0,0.6);
}

/* === BLACK OUTER BORDER === */
.card-border {
    position: absolute;
    top: 0; left: 0; right: 0; bottom: 0;
    background: #0a0a0a;
    border-radius: 12px;
}

/* === COLORED FRAME === */
.card-frame {
    position: absolute;
    top: 8px; left: 8px; right: 8px; bottom: 8px;
    background: ${frameColors.frameGradient};
    border-radius: 6px;
    box-shadow: inset 0 0 0 1px rgba(0,0,0,0.3);
}

/* === INNER FRAME TEXTURE === */
.card-frame::before {
    content: '';
    position: absolute;
    top: 0; left: 0; right: 0; bottom: 0;
    background:
        linear-gradient(180deg, rgba(255,255,255,0.1) 0%, transparent 20%, transparent 80%, rgba(0,0,0,0.15) 100%),
        repeating-linear-gradient(90deg, transparent 0px, transparent 2px, rgba(0,0,0,0.02) 2px, rgba(0,0,0,0.02) 4px);
    border-radius: 6px;
    pointer-events: none;
}

/* === NAME BAR === */
.card-name-bar {
    position: absolute;
    top: 18px; left: 18px; right: 18px;
    height: 34px;
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 0 10px;
    background: linear-gradient(180deg,
        #f5f0e1 0%,
        #e8e4d4 40%,
        #ddd8c8 60%,
        #d0cbb8 100%
    );
    border-radius: 4px 4px 0 0;
    border: 1px solid #1a1a1a;
    box-shadow:
        inset 0 1px 0 rgba(255,255,255,0.6),
        inset 0 -1px 0 rgba(0,0,0,0.1),
        0 2px 4px rgba(0,0,0,0.3);
}

.card-name {
    font-family: 'Cinzel', 'Times New Roman', serif;
    font-size: 15px;
    font-weight: 700;
    color: #1a1a1a;
    text-shadow: 0 1px 0 rgba(255,255,255,0.3);
    letter-spacing: 0.3px;
}

/* === MANA COST === */
.mana-cost {
    display: flex;
    gap: 2px;
}

.mana-symbol {
    width: 18px; height: 18px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-family: Arial, sans-serif;
    font-size: 11px;
    font-weight: bold;
}

.mana-icon {
    width: 18px;
    height: 18px;
    border-radius: 50%;
    box-shadow: 0 1px 2px rgba(0,0,0,0.3);
}

.mana-symbol.generic {
    background: radial-gradient(circle at 30% 30%, #e0ddd0 0%, #a8a8a0 60%, #787870 100%);
    color: #1a1a1a;
    border-radius: 50%;
    border: 1px solid rgba(0,0,0,0.4);
    box-shadow: 0 1px 2px rgba(0,0,0,0.4);
}

/* === ART BOX === */
.art-box {
    position: absolute;
    top: 56px; left: 20px; right: 20px;
    height: 195px;
    background: #1a1a1a;
    border: 3px solid #0a0a0a;
    box-shadow:
        inset 0 0 10px rgba(0,0,0,0.8),
        0 2px 4px rgba(0,0,0,0.4);
    overflow: hidden;
}

.art-box img {
    width: 100%;
    height: 100%;
    object-fit: cover;
}

.art-placeholder {
    width: 100%; height: 100%;
    background: linear-gradient(135deg, #2a2a3a 0%, #1a1a2a 50%, #2a2a3a 100%);
    display: flex;
    align-items: center;
    justify-content: center;
}

.art-placeholder::after {
    content: '';
    width: 60px; height: 60px;
    border-radius: 50%;
    background: radial-gradient(circle, ${frameColors.glowColor} 0%, transparent 70%);
    animation: pulse 3s ease-in-out infinite;
}

@keyframes pulse {
    0%, 100% { transform: scale(0.8); opacity: 0.4; }
    50% { transform: scale(1.3); opacity: 0.7; }
}

/* === TYPE LINE === */
.type-line {
    position: absolute;
    top: 259px; left: 18px; right: 18px;
    height: 28px;
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 0 10px;
    background: linear-gradient(180deg,
        #f5f0e1 0%,
        #e8e4d4 40%,
        #ddd8c8 60%,
        #d0cbb8 100%
    );
    border: 1px solid #1a1a1a;
    box-shadow:
        inset 0 1px 0 rgba(255,255,255,0.5),
        inset 0 -1px 0 rgba(0,0,0,0.1),
        0 2px 4px rgba(0,0,0,0.3);
    font-family: 'Cinzel', 'Times New Roman', serif;
    font-size: 13px;
    font-weight: 600;
    color: #1a1a1a;
}

.set-symbol {
    width: 18px; height: 18px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 14px;
}

/* === TEXT BOX === */
.text-box {
    position: absolute;
    top: 295px; left: 18px; right: 18px;
    bottom: ${if (hasPT) "50px" else "30px"};
    background: linear-gradient(180deg,
        #f8f5eb 0%,
        #f2efe5 30%,
        #ebe8de 70%,
        #e5e2d8 100%
    );
    border: 1px solid #1a1a1a;
    border-radius: 0 0 4px 4px;
    padding: 10px 12px;
    overflow-y: auto;
    box-shadow:
        inset 0 1px 3px rgba(0,0,0,0.1),
        0 2px 4px rgba(0,0,0,0.2);
}

/* Parchment texture */
.text-box::before {
    content: '';
    position: absolute;
    top: 0; left: 0; right: 0; bottom: 0;
    background-image: url("data:image/svg+xml,%3Csvg viewBox='0 0 200 200' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='noise'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.9' numOctaves='4' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23noise)'/%3E%3C/svg%3E");
    opacity: 0.03;
    pointer-events: none;
}

.ability {
    font-size: 12px;
    line-height: 1.3;
    color: #1a1a1a;
    margin-bottom: 6px;
}

.ability-name {
    font-weight: 700;
    font-size: 11px;
    text-transform: uppercase;
    letter-spacing: 0.3px;
    margin-bottom: 1px;
}

.ability-desc {
    font-weight: 400;
    font-size: 11px;
    color: #2a2a2a;
    font-style: italic;
}

.flavor-text {
    font-style: italic;
    color: #3a3a3a;
    font-size: 12px;
    line-height: 1.35;
    border-top: 1px solid rgba(0,0,0,0.15);
    padding-top: 8px;
    margin-top: auto;
}

/* === POWER/TOUGHNESS BOX === */
.pt-box {
    position: absolute;
    bottom: 18px; right: 20px;
    min-width: 50px;
    height: 30px;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 0 8px;
    background: linear-gradient(180deg,
        #f5f0e1 0%,
        #e0dbd0 50%,
        #d5d0c5 100%
    );
    border: 2px solid #1a1a1a;
    border-radius: 6px;
    box-shadow:
        inset 0 1px 0 rgba(255,255,255,0.6),
        0 2px 4px rgba(0,0,0,0.4);
    font-family: 'Cinzel', 'Times New Roman', serif;
    font-size: 17px;
    font-weight: 700;
    color: #1a1a1a;
}

/* === COLLECTOR INFO === */
.collector-bar {
    position: absolute;
    bottom: 10px; left: 18px; right: 70px;
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 8px;
    color: ${frameColors.collectorColor};
}

.collector-bar .number { font-weight: bold; }
.collector-bar .artist { font-style: italic; }
.collector-bar .artist::before { content: '✦ '; }
</style>
</head>
<body>
<div class="mtg-card">
    <div class="card-border"></div>
    <div class="card-frame"></div>
    <div class="card-name-bar">
        <span class="card-name">${escapeHtml(card.name)}</span>
        <div class="mana-cost">$manaSymbolsHtml</div>
    </div>
    <div class="art-box">
        ${getArtDataUrl(card.artUrl)?.let { """<img src="$it" alt="${escapeHtml(card.name)}">""" } ?: """<div class="art-placeholder"></div>"""}
    </div>
    <div class="type-line">
        <span>${escapeHtml(card.typeLine)}</span>
        <span class="set-symbol">${getSetSymbol(card.setSymbol)}</span>
    </div>
    <div class="text-box">
        $abilitiesHtml
        $flavorHtml
    </div>
    ${if (hasPT) """<div class="pt-box">${card.power}/${card.toughness}</div>""" else ""}
    <div class="collector-bar">
        <span class="number">${escapeHtml(card.collectorInfo)}</span>
        <span class="artist">AI Generated</span>
    </div>
</div>
</body>
</html>
        """.trimIndent()
    }

    private fun getSetSymbol(setCode: String?): String {
        // Return a unicode symbol based on rarity/set
        return when {
            setCode?.contains("M", ignoreCase = true) == true -> "✦"
            setCode?.contains("R", ignoreCase = true) == true -> "◆"
            setCode?.contains("U", ignoreCase = true) == true -> "◇"
            else -> "●"
        }
    }

    private fun generateBorderlessCardHtml(card: CardData): String {
        val frameColors = getFrameColors(card.colorIdentity)
        val manaSymbolsHtml = parseManaSymbols(card.manaCost)
        val abilitiesHtml = card.abilities.joinToString("") { ability ->
            val desc = if (ability.description.isNotBlank()) {
                """<div class="ability-desc">${escapeHtml(ability.description)}</div>"""
            } else ""
            """<div class="ability"><div class="ability-name">${escapeHtml(ability.name)}</div>$desc</div>"""
        }
        val flavorHtml = if (card.flavorText != null) {
            """<div class="flavor-text">"${escapeHtml(card.flavorText)}"</div>"""
        } else ""

        val hasPT = card.power > 0 || card.toughness > 0
        val artDataUrl = getArtDataUrl(card.artUrl)

        return """
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<style>
@import url('https://fonts.googleapis.com/css2?family=Cinzel:wght@400;700&family=EB+Garamond:ital,wght@0,400;0,600;1,400&display=swap');

* { box-sizing: border-box; margin: 0; padding: 0; }

body {
    font-family: 'EB Garamond', Georgia, serif;
    background: linear-gradient(135deg, #0d0d14 0%, #1a1a2e 50%, #0d0d14 100%);
    display: flex; justify-content: center; align-items: center;
    min-height: 100vh; padding: 20px;
}

/* === BORDERLESS CARD === */
.mtg-card {
    position: relative;
    width: 375px;
    height: 523px;
    border-radius: 12px;
    overflow: hidden;
    box-shadow:
        0 0 0 1px #000,
        0 4px 8px rgba(0,0,0,0.4),
        0 12px 40px rgba(0,0,0,0.6);
}

/* Full art background */
.card-art-full {
    position: absolute;
    top: 0; left: 0; right: 0; bottom: 0;
    background: linear-gradient(180deg, #2a2a3a 0%, #1a1a2a 100%);
}

.card-art-full img {
    width: 100%;
    height: 100%;
    object-fit: cover;
}

/* Gradient overlay for text readability */
.card-overlay {
    position: absolute;
    top: 0; left: 0; right: 0; bottom: 0;
    background: linear-gradient(
        180deg,
        rgba(0,0,0,0.4) 0%,
        transparent 15%,
        transparent 50%,
        rgba(0,0,0,0.6) 70%,
        rgba(0,0,0,0.85) 100%
    );
}

/* Name bar - floating on top of art */
.card-name-bar {
    position: absolute;
    top: 12px; left: 12px; right: 12px;
    height: 36px;
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 0 12px;
    background: linear-gradient(180deg,
        rgba(20,20,30,0.85) 0%,
        rgba(10,10,20,0.9) 100%
    );
    border-radius: 6px;
    border: 1px solid rgba(255,255,255,0.15);
    box-shadow: 0 2px 8px rgba(0,0,0,0.5);
}

.card-name {
    font-family: 'Cinzel', 'Times New Roman', serif;
    font-size: 15px;
    font-weight: 700;
    color: #fff;
    text-shadow: 0 1px 2px rgba(0,0,0,0.8);
}

/* Mana symbols */
.mana-cost { display: flex; gap: 2px; }

.mana-symbol {
    width: 18px; height: 18px;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    font-family: Arial, sans-serif;
    font-size: 11px;
    font-weight: bold;
    border: 1px solid rgba(0,0,0,0.5);
    box-shadow: 0 1px 2px rgba(0,0,0,0.4);
}

.mana-icon { width: 12px; height: 12px; fill: currentColor; }
.mana-symbol.generic { background: radial-gradient(circle at 30% 30%, #e0ddd0 0%, #a8a8a0 60%, #787870 100%); color: #1a1a1a; }
.mana-symbol.w { background: radial-gradient(circle at 30% 30%, #fffef5 0%, #f8f4d8 40%, #e8deb8 100%); color: #1a1a1a; }
.mana-symbol.u { background: radial-gradient(circle at 30% 30%, #c4e4f8 0%, #1e90d0 50%, #0a5080 100%); color: #fff; }
.mana-symbol.b { background: radial-gradient(circle at 30% 30%, #a8a0a0 0%, #484040 50%, #181010 100%); color: #c8c0c0; }
.mana-symbol.r { background: radial-gradient(circle at 30% 30%, #f8b898 0%, #e04020 50%, #a01810 100%); color: #fff8f0; }
.mana-symbol.g { background: radial-gradient(circle at 30% 30%, #a8d8a8 0%, #308830 50%, #104810 100%); color: #fff; }

/* Type line - floating */
.type-line {
    position: absolute;
    top: 310px; left: 12px; right: 12px;
    height: 26px;
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 0 10px;
    background: linear-gradient(180deg,
        rgba(20,20,30,0.8) 0%,
        rgba(10,10,20,0.85) 100%
    );
    border-radius: 4px;
    border: 1px solid rgba(255,255,255,0.1);
    font-family: 'Cinzel', serif;
    font-size: 11px;
    color: #fff;
    z-index: 10;
    transition: top 0.3s ease;
}

.type-line.shifted {
    top: 56px;
}

/* Text box - semi-transparent dark */
.text-box {
    position: absolute;
    top: 342px;
    bottom: ${if (hasPT) "50px" else "30px"};
    left: 12px; right: 12px;
    background: rgba(0, 0, 0, 0.7);
    border-radius: 6px;
    border: 1px solid rgba(255,255,255,0.15);
    padding: 8px 10px;
    padding-bottom: 28px;
    overflow-y: auto;
    backdrop-filter: blur(4px);
    transition: top 0.3s ease;
}

.text-box.expanded {
    top: 88px;
    overflow-y: auto;
}

/* More/Less button */
.more-btn {
    position: absolute;
    bottom: 6px;
    left: 50%;
    transform: translateX(-50%);
    background: rgba(255,255,255,0.15);
    border: 1px solid rgba(255,255,255,0.25);
    border-radius: 12px;
    padding: 3px 14px;
    font-size: 9px;
    color: rgba(255,255,255,0.9);
    cursor: pointer;
    z-index: 20;
    transition: background 0.2s ease;
}

.more-btn:hover {
    background: rgba(255,255,255,0.25);
}

.ability {
    font-size: 12px;
    line-height: 1.4;
    color: #ffffff;
    margin-bottom: 6px;
}

.ability-name { font-weight: 700; font-size: 10px; text-transform: uppercase; letter-spacing: 0.3px; margin-bottom: 1px; color: #ffffff; }
.ability-desc { font-weight: 400; font-size: 10px; color: rgba(255,255,255,0.85); font-style: italic; }

/* P/T box - dark transparent */
.pt-box {
    position: absolute;
    bottom: 12px; right: 14px;
    min-width: 42px;
    height: 26px;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 0 8px;
    background: rgba(0, 0, 0, 0.75);
    border: 1px solid rgba(255,255,255,0.2);
    border-radius: 5px;
    backdrop-filter: blur(4px);
    font-family: 'Cinzel', serif;
    font-size: 14px;
    font-weight: 700;
    color: #ffffff;
}

/* Collector info with flavor text */
.collector-bar {
    position: absolute;
    bottom: 8px; left: 14px; right: ${if (hasPT) "80px" else "14px"};
    font-size: 8px;
    color: rgba(255,255,255,0.7);
    text-shadow: 0 1px 2px rgba(0,0,0,0.8);
    line-height: 1.4;
}

.collector-bar .flavor-text {
    font-style: italic;
    color: rgba(255,255,255,0.9);
    font-size: 9px;
}
</style>
</head>
<body>
<div class="mtg-card">
    <div class="card-art-full">
        ${artDataUrl?.let { """<img src="$it" alt="${escapeHtml(card.name)}">""" } ?: ""}
    </div>
    <div class="card-overlay"></div>
    <div class="card-name-bar">
        <span class="card-name">${escapeHtml(card.name)}</span>
        <div class="mana-cost">$manaSymbolsHtml</div>
    </div>
    <div class="type-line">
        <span>${escapeHtml(card.typeLine)}</span>
        <span>${getSetSymbol(card.setSymbol)}</span>
    </div>
    <div class="text-box" id="textBox">
        $abilitiesHtml
        ${if (card.abilities.size > 2) """<button class="more-btn" id="moreBtn" onclick="toggleExpand()">▼ more</button>""" else ""}
    </div>
    ${if (hasPT) """<div class="pt-box">${card.power}/${card.toughness}</div>""" else ""}
    <div class="collector-bar">
        ${if (card.flavorText != null) """<span class="flavor-text">"${escapeHtml(card.flavorText)}"</span> — """ else ""}${escapeHtml(card.collectorInfo)}
    </div>
</div>
<script>
let expanded = false;
function toggleExpand() {
    expanded = !expanded;
    const textBox = document.getElementById('textBox');
    const typeLine = document.querySelector('.type-line');
    const btn = document.getElementById('moreBtn');

    if (expanded) {
        textBox.classList.add('expanded');
        typeLine.classList.add('shifted');
        btn.textContent = '▲ less';
    } else {
        textBox.classList.remove('expanded');
        typeLine.classList.remove('shifted');
        btn.textContent = '▼ more';
    }
}
</script>
</body>
</html>
        """.trimIndent()
    }

    private fun generateExtendedCardHtml(card: CardData): String {
        // Extended art is similar to borderless but with a partial frame
        // For now, use borderless as the implementation
        return generateBorderlessCardHtml(card)
    }

    private data class FrameColors(
        val frameGradient: String,
        val glowColor: String,
        val collectorColor: String
    )

    private fun getFrameColors(colors: List<ManaColor>): FrameColors {
        if (colors.isEmpty() || colors[0] == ManaColor.COLORLESS) {
            return FrameColors(
                frameGradient = "linear-gradient(180deg, #c8d4dc 0%, #a8b8c4 20%, #98a8b4 50%, #889898 80%, #788888 100%)",
                glowColor = "rgba(150, 180, 200, 0.5)",
                collectorColor = "rgba(0, 0, 0, 0.5)"
            )
        }
        if (colors.size > 1) {
            // Gold/multicolor frame
            return FrameColors(
                frameGradient = "linear-gradient(180deg, #e8c84c 0%, #d4a82c 20%, #c89820 50%, #a87810 80%, #886008 100%)",
                glowColor = "rgba(200, 160, 50, 0.5)",
                collectorColor = "rgba(0, 0, 0, 0.6)"
            )
        }
        return when (colors[0]) {
            ManaColor.WHITE -> FrameColors(
                frameGradient = "linear-gradient(180deg, #f8f4e8 0%, #ede8d8 20%, #e5e0d0 50%, #d8d2c4 80%, #c8c2b4 100%)",
                glowColor = "rgba(255, 250, 230, 0.5)",
                collectorColor = "rgba(0, 0, 0, 0.5)"
            )
            ManaColor.BLUE -> FrameColors(
                frameGradient = "linear-gradient(180deg, #2090d0 0%, #1878b8 20%, #1068a8 50%, #085890 80%, #044870 100%)",
                glowColor = "rgba(100, 180, 255, 0.5)",
                collectorColor = "rgba(200, 220, 255, 0.7)"
            )
            ManaColor.BLACK -> FrameColors(
                frameGradient = "linear-gradient(180deg, #504848 0%, #383030 20%, #282020 50%, #181010 80%, #0c0808 100%)",
                glowColor = "rgba(100, 80, 120, 0.5)",
                collectorColor = "rgba(180, 170, 160, 0.6)"
            )
            ManaColor.RED -> FrameColors(
                frameGradient = "linear-gradient(180deg, #d85040 0%, #c03828 20%, #a82818 50%, #901810 80%, #700c08 100%)",
                glowColor = "rgba(255, 100, 50, 0.5)",
                collectorColor = "rgba(255, 220, 200, 0.7)"
            )
            ManaColor.GREEN -> FrameColors(
                frameGradient = "linear-gradient(180deg, #408040 0%, #306830 20%, #205820 50%, #104810 80%, #083808 100%)",
                glowColor = "rgba(100, 200, 100, 0.5)",
                collectorColor = "rgba(200, 230, 200, 0.7)"
            )
            ManaColor.COLORLESS -> FrameColors(
                frameGradient = "linear-gradient(180deg, #c8d4dc 0%, #a8b8c4 20%, #98a8b4 50%, #889898 80%, #788888 100%)",
                glowColor = "rgba(150, 180, 200, 0.5)",
                collectorColor = "rgba(0, 0, 0, 0.5)"
            )
        }
    }

    private fun parseManaSymbols(manaCost: String): String {
        val regex = Regex("""\{(\d+|[WUBRGC])\}""")
        return regex.findAll(manaCost).map { match ->
            val value = match.groupValues[1]
            if (value.matches(Regex("\\d+"))) {
                """<span class="mana-symbol generic">$value</span>"""
            } else {
                val icon = getManaIcon(value)
                """<span class="mana-symbol ${value.lowercase()}">$icon</span>"""
            }
        }.joinToString("")
    }

    private fun getManaIcon(manaType: String): String {
        // Load PNG from resources and convert to base64 data URL
        val resourcePath = "/icons/mana/${manaType.lowercase()}.png"
        return try {
            val bytes = javaClass.getResourceAsStream(resourcePath)?.readBytes()
            if (bytes != null) {
                val base64 = Base64.getEncoder().encodeToString(bytes)
                """<img src="data:image/png;base64,$base64" class="mana-icon">"""
            } else {
                // Fallback to letter if PNG not found
                manaType.uppercase()
            }
        } catch (e: Exception) {
            manaType.uppercase()
        }
    }

    private fun escapeHtml(text: String?): String {
        return (text ?: "").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
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
            println("MTG: Failed to load art: ${e.message}")
            null
        }
    }

    override fun dispose() {
        browser.dispose()
        super.dispose()
    }
}
