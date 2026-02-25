import * as vscode from 'vscode';
import * as path from 'path';
import * as fs from 'fs';
import { CardData, ManaColor, CardStyle } from './parser';

/**
 * Generate HTML for a single card view.
 */
export function generateCardHtml(card: CardData, webview: vscode.Webview, context: vscode.ExtensionContext, artPath?: string | null): string {
    const artUri = card.artUrl ? webview.asWebviewUri(vscode.Uri.file(card.artUrl)) : null;

    if (card.cardStyle === CardStyle.BORDERLESS) {
        return generateBorderlessCardHtml(card, artUri?.toString() || '', artPath);
    }

    return generateStandardCardHtml(card, artUri?.toString() || '', webview, context, artPath);
}

function generateStandardCardHtml(card: CardData, artUrl: string, webview: vscode.Webview, context: vscode.ExtensionContext, artPath?: string | null): string {
    const frameColor = getFrameColor(card.colorIdentity);
    const manaSymbols = renderManaCost(card.manaCost, webview, context);

    return `<!DOCTYPE html>
<html>
<head>
    <style>
        body {
            margin: 0;
            padding: 20px;
            display: flex;
            justify-content: center;
            align-items: center;
            min-height: 100vh;
            background: linear-gradient(135deg, #1a1a2e 0%, #0d0d14 100%);
            font-family: Georgia, serif;
        }

        .mtg-card {
            width: 375px;
            height: 525px;
            background: #0a0a0a;
            border-radius: 18px;
            padding: 12px;
            box-shadow: 0 10px 40px rgba(0,0,0,0.5);
        }

        .card-frame {
            width: 100%;
            height: 100%;
            background: ${frameColor};
            border-radius: 12px;
            display: flex;
            flex-direction: column;
            padding: 10px;
        }

        .name-bar {
            display: flex;
            justify-content: space-between;
            align-items: center;
            background: linear-gradient(180deg, #f5f0e1 0%, #d8d2c4 100%);
            border: 2px solid #1a1a1a;
            border-radius: 6px 6px 0 0;
            padding: 6px 10px;
        }

        .card-name {
            font-family: 'Cinzel', Georgia, serif;
            font-weight: bold;
            font-size: 16px;
            color: #1a1a1a;
        }

        .mana-cost {
            display: flex;
            gap: 2px;
        }

        .mana-symbol {
            width: 18px;
            height: 18px;
            border-radius: 50%;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 11px;
            font-weight: bold;
            box-shadow: 0 1px 2px rgba(0,0,0,0.3);
        }

        .art-box {
            flex: 1;
            margin: 8px 0;
            background: #1a1a1a;
            border: 3px solid #0a0a0a;
            display: flex;
            align-items: center;
            justify-content: center;
            overflow: hidden;
        }

        .art-box img {
            width: 100%;
            height: 100%;
            object-fit: cover;
        }

        .type-bar {
            background: linear-gradient(180deg, #f5f0e1 0%, #d8d2c4 100%);
            border: 2px solid #1a1a1a;
            padding: 4px 10px;
            font-size: 12px;
            color: #1a1a1a;
        }

        .text-box {
            flex: 0.6;
            background: linear-gradient(180deg, #f5f0e1 0%, #e8e0d0 100%);
            border: 2px solid #1a1a1a;
            border-radius: 0 0 6px 6px;
            padding: 10px;
            font-size: 11px;
            line-height: 1.4;
            overflow-y: auto;
        }

        .ability {
            margin-bottom: 8px;
        }

        .ability-name {
            font-weight: bold;
        }

        .flavor-text {
            font-style: italic;
            color: #444;
            border-top: 1px solid #ccc;
            padding-top: 8px;
            margin-top: 8px;
        }

        .pt-box {
            position: absolute;
            bottom: 22px;
            right: 22px;
            background: linear-gradient(180deg, #f5f0e1 0%, #d5d0c5 100%);
            border: 2px solid #1a1a1a;
            border-radius: 6px;
            padding: 4px 12px;
            font-family: 'Cinzel', Georgia, serif;
            font-weight: bold;
            font-size: 18px;
        }

        .card-wrapper {
            position: relative;
        }
    </style>
</head>
<body>
    <div class="card-wrapper">
        <div class="mtg-card">
            <div class="card-frame">
                <div class="name-bar">
                    <span class="card-name">${escapeHtml(card.name)}</span>
                    <div class="mana-cost">${manaSymbols}</div>
                </div>
                <div class="art-box">
                    ${artUrl ? `<img src="${artUrl}" alt="Card Art">` : '<div style="color:#666;">No Art</div>'}
                </div>
                <div class="type-bar">${escapeHtml(card.typeLine)}</div>
                <div class="text-box">
                    ${card.abilities.map(a => `<div class="ability"><span class="ability-name">${escapeHtml(a.name)}:</span> ${escapeHtml(a.description)}</div>`).join('')}
                    ${card.flavorText ? `<div class="flavor-text">${escapeHtml(card.flavorText)}</div>` : ''}
                </div>
            </div>
        </div>
        <div class="pt-box">${card.power}/${card.toughness}</div>
    </div>
</body>
</html>`;
}

function generateBorderlessCardHtml(card: CardData, artUrl: string, artPath?: string | null): string {
    return `<!DOCTYPE html>
<html>
<head>
    <style>
        body {
            margin: 0;
            padding: 20px;
            display: flex;
            justify-content: center;
            align-items: center;
            min-height: 100vh;
            background: linear-gradient(135deg, #1a1a2e 0%, #0d0d14 100%);
            font-family: Georgia, serif;
        }

        .mtg-card {
            width: 375px;
            height: 525px;
            border-radius: 18px;
            position: relative;
            overflow: hidden;
            box-shadow: 0 10px 40px rgba(0,0,0,0.5);
        }

        .art-background {
            position: absolute;
            top: 0; left: 0; right: 0; bottom: 0;
            background-image: url('${artUrl}');
            background-size: cover;
            background-position: center;
        }

        .art-background::after {
            content: '';
            position: absolute;
            top: 0; left: 0; right: 0; bottom: 0;
            background: linear-gradient(to bottom, transparent 40%, rgba(0,0,0,0.8) 100%);
        }

        .card-content {
            position: relative;
            z-index: 1;
            height: 100%;
            display: flex;
            flex-direction: column;
            padding: 15px;
        }

        .name-bar {
            display: flex;
            justify-content: space-between;
            align-items: center;
            background: rgba(0,0,0,0.6);
            backdrop-filter: blur(4px);
            border-radius: 6px;
            padding: 8px 12px;
        }

        .card-name {
            font-family: 'Cinzel', Georgia, serif;
            font-weight: bold;
            font-size: 18px;
            color: white;
            text-shadow: 0 2px 4px rgba(0,0,0,0.8);
        }

        .mana-cost {
            display: flex;
            gap: 3px;
        }

        .mana-symbol {
            width: 20px;
            height: 20px;
            border-radius: 50%;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 12px;
            font-weight: bold;
        }

        .bottom-section {
            margin-top: auto;
        }

        .type-bar {
            background: rgba(0,0,0,0.6);
            backdrop-filter: blur(4px);
            padding: 6px 12px;
            font-size: 13px;
            color: white;
            border-radius: 4px;
            margin-bottom: 8px;
        }

        .text-box {
            background: rgba(245,240,225,0.95);
            border-radius: 6px;
            padding: 12px;
            font-size: 11px;
            line-height: 1.4;
        }

        .flavor-text {
            font-style: italic;
            color: #444;
            margin-top: 8px;
        }

        .pt-box {
            position: absolute;
            bottom: 15px;
            right: 15px;
            background: linear-gradient(180deg, #f5f0e1 0%, #d5d0c5 100%);
            border: 2px solid #1a1a1a;
            border-radius: 6px;
            padding: 4px 12px;
            font-family: 'Cinzel', Georgia, serif;
            font-weight: bold;
            font-size: 18px;
            z-index: 2;
        }
    </style>
</head>
<body>
    <div class="mtg-card">
        <div class="art-background"></div>
        <div class="card-content">
            <div class="name-bar">
                <span class="card-name">${escapeHtml(card.name)}</span>
                <div class="mana-cost">${renderManaCostSimple(card.manaCost)}</div>
            </div>
            <div class="bottom-section">
                <div class="type-bar">${escapeHtml(card.typeLine)}</div>
                <div class="text-box">
                    ${card.abilities.slice(0, 2).map(a => `<div><b>${escapeHtml(a.name)}</b></div>`).join('')}
                    ${card.flavorText ? `<div class="flavor-text">${escapeHtml(card.flavorText)}</div>` : ''}
                </div>
            </div>
        </div>
        <div class="pt-box">${card.power}/${card.toughness}</div>
    </div>
</body>
</html>`;
}

/**
 * Generate HTML for the battlefield view.
 */
export function generateBattlefieldHtml(agents: CardData[], webview: vscode.Webview, context: vscode.ExtensionContext): string {
    const agentsJson = JSON.stringify(agents.map(a => ({
        name: a.name,
        power: a.power,
        toughness: a.toughness,
        color: a.colorIdentity[0]?.toLowerCase() || 'blue',
        artUrl: a.artUrl ? webview.asWebviewUri(vscode.Uri.file(a.artUrl)).toString() : null
    })));

    return `<!DOCTYPE html>
<html>
<head>
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body {
            width: 100vw;
            height: 100vh;
            overflow: auto;
            background: linear-gradient(135deg, #0d0d14 0%, #1a1a2e 50%, #0d0d14 100%);
            font-family: Georgia, serif;
        }

        .mini-card {
            position: absolute;
            width: 150px;
            height: 200px;
            border-radius: 8px;
            cursor: move;
            transition: transform 0.15s, box-shadow 0.15s;
            background: #0a0a0a;
            padding: 4px;
        }

        .mini-card:hover {
            transform: translateY(-4px) scale(1.02);
            box-shadow: 0 8px 24px rgba(0,0,0,0.4);
        }

        .mini-card-frame {
            width: 100%;
            height: 100%;
            border-radius: 5px;
            display: flex;
            flex-direction: column;
            padding: 6px;
        }

        .mini-card.white .mini-card-frame { background: linear-gradient(180deg, #f8f4e8 0%, #d8d2c4 100%); }
        .mini-card.blue .mini-card-frame { background: linear-gradient(180deg, #2090d0 0%, #044870 100%); }
        .mini-card.black .mini-card-frame { background: linear-gradient(180deg, #504848 0%, #0c0808 100%); }
        .mini-card.red .mini-card-frame { background: linear-gradient(180deg, #d85040 0%, #700c08 100%); }
        .mini-card.green .mini-card-frame { background: linear-gradient(180deg, #408040 0%, #083808 100%); }

        .card-name {
            font-size: 10px;
            font-weight: bold;
            text-align: center;
            padding: 4px;
            background: linear-gradient(180deg, #f5f0e1 0%, #d8d2c4 100%);
            border-radius: 3px;
            color: #1a1a1a;
        }

        .card-art {
            flex: 1;
            margin: 4px 0;
            background: #1a1a1a;
            display: flex;
            align-items: center;
            justify-content: center;
            overflow: hidden;
        }

        .card-art img {
            width: 100%;
            height: 100%;
            object-fit: cover;
        }

        .card-pt {
            font-size: 13px;
            font-weight: bold;
            text-align: center;
            padding: 3px;
            background: linear-gradient(180deg, #f5f0e1 0%, #d5d0c5 100%);
            border-radius: 4px;
            align-self: flex-end;
        }

        .stats {
            position: fixed;
            top: 10px;
            left: 10px;
            background: rgba(20,20,30,0.95);
            padding: 10px 14px;
            border-radius: 8px;
            color: white;
            font-size: 12px;
        }
    </style>
</head>
<body>
    <div class="stats">Agents: <b>${agents.length}</b></div>
    <div id="battlefield"></div>
    <script>
        const agents = ${agentsJson};
        const battlefield = document.getElementById('battlefield');

        agents.forEach((agent, i) => {
            const cols = Math.ceil(Math.sqrt(agents.length));
            const row = Math.floor(i / cols);
            const col = i % cols;

            const card = document.createElement('div');
            card.className = 'mini-card ' + agent.color;
            card.style.left = (100 + col * 180) + 'px';
            card.style.top = (80 + row * 220) + 'px';

            card.innerHTML = \`
                <div class="mini-card-frame">
                    <div class="card-name">\${agent.name}</div>
                    <div class="card-art">
                        \${agent.artUrl ? '<img src="' + agent.artUrl + '">' : ''}
                    </div>
                    <div class="card-pt">\${agent.power}/\${agent.toughness}</div>
                </div>
            \`;

            // Drag functionality
            let isDragging = false, startX, startY, startLeft, startTop;
            card.onmousedown = e => {
                isDragging = true;
                startX = e.clientX;
                startY = e.clientY;
                startLeft = parseInt(card.style.left);
                startTop = parseInt(card.style.top);
                card.style.zIndex = 100;
            };
            document.onmousemove = e => {
                if (!isDragging) return;
                card.style.left = (startLeft + e.clientX - startX) + 'px';
                card.style.top = (startTop + e.clientY - startY) + 'px';
            };
            document.onmouseup = () => {
                isDragging = false;
                card.style.zIndex = '';
            };

            battlefield.appendChild(card);
        });
    </script>
</body>
</html>`;
}

function getFrameColor(colors: ManaColor[]): string {
    if (colors.length === 0) return 'linear-gradient(180deg, #2090d0 0%, #044870 100%)';
    if (colors.length > 1) return 'linear-gradient(180deg, #c9b037 0%, #a08628 100%)';

    switch (colors[0]) {
        case ManaColor.WHITE: return 'linear-gradient(180deg, #f8f4e8 0%, #d8d2c4 100%)';
        case ManaColor.BLUE: return 'linear-gradient(180deg, #2090d0 0%, #044870 100%)';
        case ManaColor.BLACK: return 'linear-gradient(180deg, #504848 0%, #0c0808 100%)';
        case ManaColor.RED: return 'linear-gradient(180deg, #d85040 0%, #700c08 100%)';
        case ManaColor.GREEN: return 'linear-gradient(180deg, #408040 0%, #083808 100%)';
        default: return 'linear-gradient(180deg, #c8d4dc 0%, #788888 100%)';
    }
}

function renderManaCost(manaCost: string, webview: vscode.Webview, context: vscode.ExtensionContext): string {
    const symbols = manaCost.match(/\{([^}]+)\}/g) || [];
    return symbols.map(s => {
        const symbol = s.replace(/[{}]/g, '');
        return getManaSymbolHtml(symbol);
    }).join('');
}

function renderManaCostSimple(manaCost: string): string {
    const symbols = manaCost.match(/\{([^}]+)\}/g) || [];
    return symbols.map(s => {
        const symbol = s.replace(/[{}]/g, '');
        return getManaSymbolHtml(symbol);
    }).join('');
}

function getManaSymbolHtml(symbol: string): string {
    const colors: Record<string, { bg: string; fg: string }> = {
        'W': { bg: '#f8f6d8', fg: '#1a1a1a' },
        'U': { bg: '#0e67ab', fg: 'white' },
        'B': { bg: '#150b00', fg: '#9a8c7c' },
        'R': { bg: '#d3202a', fg: 'white' },
        'G': { bg: '#00733e', fg: 'white' },
        'C': { bg: '#ccc2c0', fg: '#1a1a1a' }
    };

    const style = colors[symbol] || { bg: '#888', fg: 'white' };
    const display = isNaN(parseInt(symbol)) ? symbol : symbol;

    return `<div class="mana-symbol" style="background:${style.bg};color:${style.fg};">${display}</div>`;
}

function escapeHtml(str: string): string {
    return str
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
}
