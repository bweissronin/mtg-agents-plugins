/**
 * HTML Card Renderer
 */

import { CardData, ManaColor, CardStyle } from '../types';

export function renderCardHtml(card: CardData): string {
    if (card.cardStyle === CardStyle.BORDERLESS) {
        return renderBorderlessCard(card);
    }
    return renderStandardCard(card);
}

export function renderBattlefieldHtml(cards: CardData[]): string {
    const agentsJson = JSON.stringify(cards.map(c => ({
        name: c.name,
        power: c.power,
        toughness: c.toughness,
        color: c.colorIdentity[0]?.toLowerCase() || 'blue',
        artUrl: c.artUrl || null,
        manaCost: c.manaCost,
        typeLine: c.typeLine,
        abilities: c.abilities,
        flavorText: c.flavorText
    })));

    return `<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>MTG Agent Battlefield</title>
    <style>
        @import url('https://fonts.googleapis.com/css2?family=Cinzel:wght@400;700&display=swap');

        * { box-sizing: border-box; margin: 0; padding: 0; }
        body {
            width: 100vw;
            height: 100vh;
            overflow: auto;
            background: linear-gradient(135deg, #0d0d14 0%, #1a1a2e 50%, #0d0d14 100%);
            font-family: Georgia, serif;
        }

        /* Mini Card - Full Art Style */
        .mini-card {
            position: absolute;
            width: 150px;
            height: 200px;
            border-radius: 12px;
            cursor: pointer;
            transition: transform 0.15s, box-shadow 0.15s;
            overflow: hidden;
            box-shadow: 0 4px 12px rgba(0,0,0,0.5), 0 8px 24px rgba(0,0,0,0.3);
        }

        .mini-card:hover {
            transform: translateY(-4px) scale(1.05);
            box-shadow: 0 8px 20px rgba(0,0,0,0.6), 0 16px 40px rgba(0,0,0,0.4);
        }

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

        .mini-card .card-mana {
            position: absolute;
            top: 8px; right: 8px;
            display: flex;
            gap: 2px;
            z-index: 5;
        }

        .mini-card .mana-pip {
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

        .mini-card .card-name {
            position: absolute;
            top: 8px; left: 8px; right: 50px;
            font-family: 'Cinzel', serif;
            font-size: 11px;
            font-weight: 700;
            text-align: left;
            padding: 6px 10px;
            background: rgba(0, 0, 0, 0.8);
            border-radius: 6px;
            color: #ffffff;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
            border: 1px solid rgba(255,255,255,0.15);
        }

        .mini-card .card-pt {
            position: absolute;
            bottom: 8px; right: 8px;
            font-family: 'Cinzel', serif;
            font-size: 14px;
            font-weight: 700;
            padding: 4px 10px;
            background: rgba(0, 0, 0, 0.8);
            border-radius: 6px;
            color: #ffffff;
            border: 1px solid rgba(255,255,255,0.15);
        }

        .art-placeholder {
            width: 100%;
            height: 100%;
            display: flex;
            align-items: center;
            justify-content: center;
            background: linear-gradient(180deg, #1a1a2e 0%, #0d0d14 100%);
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
            z-index: 100;
        }

        /* Modal styles */
        .card-modal {
            display: none;
            position: fixed;
            top: 0; left: 0; right: 0; bottom: 0;
            background: rgba(0, 0, 0, 0.9);
            z-index: 1000;
            justify-content: center;
            align-items: center;
        }

        .card-modal.visible { display: flex; }

        .modal-content { animation: zoomIn 0.25s ease-out; }

        @keyframes zoomIn {
            from { transform: scale(0.7); opacity: 0; }
            to { transform: scale(1); opacity: 1; }
        }

        .modal-hint {
            position: fixed;
            bottom: 20px;
            left: 50%;
            transform: translateX(-50%);
            color: rgba(255,255,255,0.6);
            font-size: 12px;
        }

        /* Full card in modal */
        .full-card {
            width: 375px;
            height: 523px;
            border-radius: 12px;
            position: relative;
            overflow: hidden;
            box-shadow: 0 0 60px rgba(0,0,0,0.8);
        }

        .full-card .art-bg {
            position: absolute;
            top: 0; left: 0; right: 0; bottom: 0;
            background-size: cover;
            background-position: center;
            background-color: #1a1a2e;
        }

        .full-card .overlay {
            position: absolute;
            top: 0; left: 0; right: 0; bottom: 0;
            background: linear-gradient(180deg, rgba(0,0,0,0.4) 0%, transparent 20%, transparent 45%, rgba(0,0,0,0.85) 100%);
        }

        .full-card .name-bar {
            position: absolute;
            top: 12px; left: 12px; right: 12px;
            height: 36px;
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding: 0 12px;
            background: rgba(0,0,0,0.7);
            border-radius: 6px;
            border: 1px solid rgba(255,255,255,0.15);
        }

        .full-card .card-name {
            font-size: 16px;
            font-weight: bold;
            color: #fff;
        }

        .full-card .mana-cost { display: flex; gap: 3px; }

        .full-card .mana-symbol {
            width: 20px;
            height: 20px;
            border-radius: 50%;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 12px;
            font-weight: bold;
        }

        .full-card .type-line {
            position: absolute;
            top: 300px; left: 12px; right: 12px;
            padding: 6px 12px;
            background: rgba(0,0,0,0.7);
            border-radius: 4px;
            font-size: 12px;
            color: #fff;
        }

        .full-card .text-box {
            position: absolute;
            top: 340px;
            bottom: 45px;
            left: 12px; right: 12px;
            background: rgba(0,0,0,0.75);
            border-radius: 6px;
            padding: 10px;
            overflow-y: auto;
            border: 1px solid rgba(255,255,255,0.1);
        }

        .full-card .ability { margin-bottom: 8px; color: #fff; }
        .full-card .ability-name { font-weight: bold; font-size: 11px; text-transform: uppercase; }
        .full-card .ability-desc { font-size: 11px; color: rgba(255,255,255,0.8); font-style: italic; }

        .full-card .pt-box {
            position: absolute;
            bottom: 10px; right: 14px;
            background: rgba(0,0,0,0.8);
            border: 1px solid rgba(255,255,255,0.2);
            border-radius: 5px;
            padding: 4px 12px;
            font-size: 16px;
            font-weight: bold;
            color: #fff;
        }

        .full-card .flavor-text {
            position: absolute;
            bottom: 12px; left: 14px; right: 80px;
            font-size: 10px;
            font-style: italic;
            color: rgba(255,255,255,0.7);
        }
    </style>
</head>
<body>
    <div class="stats">Agents: <b>${cards.length}</b> <span style="opacity:0.6; margin-left:8px;">Click a card to zoom</span></div>
    <div id="battlefield"></div>

    <div id="cardModal" class="card-modal" onclick="closeModal(event)">
        <div class="modal-content" onclick="event.stopPropagation()">
            <div id="modalCard" class="full-card"></div>
        </div>
        <div class="modal-hint">Click outside or press Escape to close</div>
    </div>

    <script>
        const agents = ${agentsJson};
        const battlefield = document.getElementById('battlefield');
        let currentCardIndex = -1;

        const manaColors = {
            'W': { bg: '#f8f6d8', fg: '#1a1a1a' },
            'U': { bg: '#0e67ab', fg: 'white' },
            'B': { bg: '#150b00', fg: '#9a8c7c' },
            'R': { bg: '#d3202a', fg: 'white' },
            'G': { bg: '#00733e', fg: 'white' },
            'C': { bg: '#ccc2c0', fg: '#1a1a1a' }
        };

        function renderMana(manaCost) {
            if (!manaCost) return '';
            const symbols = manaCost.match(/\\{([^}]+)\\}/g) || [];
            return symbols.map(s => {
                const sym = s.replace(/[{}]/g, '');
                const color = manaColors[sym] || { bg: '#888', fg: 'white' };
                return '<div class="mana-symbol" style="background:' + color.bg + ';color:' + color.fg + ';">' + sym + '</div>';
            }).join('');
        }

        function renderManaForMini(manaCost) {
            if (!manaCost) return '';
            const symbols = manaCost.match(/\\{([^}]+)\\}/g) || [];
            return symbols.map(s => {
                const sym = s.replace(/[{}]/g, '');
                const color = manaColors[sym] || { bg: '#888', fg: 'white' };
                return '<span class="mana-pip" style="background:' + color.bg + ';color:' + color.fg + ';">' + sym + '</span>';
            }).join('');
        }

        function showCardModal(agent, index) {
            currentCardIndex = index;
            const modal = document.getElementById('cardModal');
            const modalCard = document.getElementById('modalCard');

            const abilitiesHtml = (agent.abilities || []).map(a =>
                '<div class="ability"><div class="ability-name">' + a.name + '</div>' +
                (a.description ? '<div class="ability-desc">' + a.description + '</div>' : '') + '</div>'
            ).join('');

            modalCard.innerHTML =
                '<div class="art-bg" style="background-image: url(' + (agent.artUrl || '') + ')"></div>' +
                '<div class="overlay"></div>' +
                '<div class="name-bar">' +
                    '<span class="card-name">' + agent.name + '</span>' +
                    '<div class="mana-cost">' + renderMana(agent.manaCost) + '</div>' +
                '</div>' +
                '<div class="type-line">' + (agent.typeLine || 'Creature') + '</div>' +
                '<div class="text-box">' + abilitiesHtml + '</div>' +
                '<div class="pt-box">' + agent.power + '/' + agent.toughness + '</div>' +
                (agent.flavorText ? '<div class="flavor-text">"' + agent.flavorText + '"</div>' : '');

            modal.classList.add('visible');
        }

        function closeModal(event) {
            if (event.target.classList.contains('card-modal') || event.key === 'Escape') {
                document.getElementById('cardModal').classList.remove('visible');
                currentCardIndex = -1;
            }
        }

        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape') closeModal(e);
            if (currentCardIndex >= 0) {
                if (e.key === 'ArrowRight' && currentCardIndex < agents.length - 1) {
                    showCardModal(agents[currentCardIndex + 1], currentCardIndex + 1);
                }
                if (e.key === 'ArrowLeft' && currentCardIndex > 0) {
                    showCardModal(agents[currentCardIndex - 1], currentCardIndex - 1);
                }
            }
        });

        agents.forEach((agent, i) => {
            const cols = Math.ceil(Math.sqrt(agents.length));
            const row = Math.floor(i / cols);
            const col = i % cols;

            const card = document.createElement('div');
            card.className = 'mini-card';
            card.style.left = (100 + col * 180) + 'px';
            card.style.top = (80 + row * 220) + 'px';

            card.innerHTML =
                '<div class="mini-card-art">' +
                    (agent.artUrl ? '<img src="' + agent.artUrl + '">' : '<div class="art-placeholder"></div>') +
                '</div>' +
                '<div class="card-name">' + agent.name + '</div>' +
                '<div class="card-pt">' + agent.power + '/' + agent.toughness + '</div>';

            card.onclick = () => showCardModal(agent, i);
            battlefield.appendChild(card);
        });
    </script>
</body>
</html>`;
}

function renderBorderlessCard(card: CardData): string {
    const abilitiesHtml = card.abilities.map(a =>
        `<div class="ability"><div class="ability-name">${escapeHtml(a.name)}</div>${a.description ? `<div class="ability-desc">${escapeHtml(a.description)}</div>` : ''}</div>`
    ).join('');

    return `<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>${escapeHtml(card.name)} - MTG Card</title>
    <style>
        @import url('https://fonts.googleapis.com/css2?family=Cinzel:wght@400;700&display=swap');

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
            ${card.artUrl ? `background-image: url('${card.artUrl}');` : ''}
            background-size: cover;
            background-position: center;
            background-color: #1a1a2e;
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
        }

        .mana-cost { display: flex; gap: 3px; }

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

        .type-bar {
            position: absolute;
            top: 310px;
            left: 15px;
            right: 15px;
            background: rgba(0,0,0,0.6);
            padding: 6px 12px;
            font-size: 13px;
            color: white;
            border-radius: 4px;
        }

        .text-box {
            position: absolute;
            top: 350px;
            bottom: 50px;
            left: 15px;
            right: 15px;
            background: rgba(0, 0, 0, 0.7);
            border-radius: 6px;
            padding: 10px;
            font-size: 11px;
            line-height: 1.3;
            border: 1px solid rgba(255,255,255,0.15);
            overflow-y: auto;
        }

        .ability { margin-bottom: 5px; color: #ffffff; }
        .ability-name { font-weight: 700; font-size: 9px; text-transform: uppercase; }
        .ability-desc { font-size: 9px; color: rgba(255,255,255,0.85); font-style: italic; }

        .pt-box {
            position: absolute;
            bottom: 12px;
            right: 14px;
            background: rgba(0, 0, 0, 0.75);
            border: 1px solid rgba(255,255,255,0.2);
            border-radius: 5px;
            padding: 3px 10px;
            font-family: 'Cinzel', Georgia, serif;
            font-weight: bold;
            font-size: 15px;
            color: #ffffff;
        }

        .collector-bar {
            position: absolute;
            bottom: 8px;
            left: 15px;
            right: 80px;
            font-size: 9px;
            color: rgba(255,255,255,0.8);
        }

        .collector-bar .flavor-text { font-style: italic; }
    </style>
</head>
<body>
    <div class="mtg-card">
        <div class="art-background"></div>
        <div class="card-content">
            <div class="name-bar">
                <span class="card-name">${escapeHtml(card.name)}</span>
                <div class="mana-cost">${renderManaCostHtml(card.manaCost)}</div>
            </div>
        </div>
        <div class="type-bar">${escapeHtml(card.typeLine)}</div>
        <div class="text-box">${abilitiesHtml}</div>
        <div class="pt-box">${card.power}/${card.toughness}</div>
        <div class="collector-bar">${card.flavorText ? `<span class="flavor-text">"${escapeHtml(card.flavorText)}"</span>` : ''}</div>
    </div>
</body>
</html>`;
}

function renderStandardCard(card: CardData): string {
    const frameColor = getFrameColor(card.colorIdentity);
    const abilitiesHtml = card.abilities.map(a =>
        `<div class="ability"><div class="ability-name">${escapeHtml(a.name)}</div>${a.description ? `<div class="ability-desc">${escapeHtml(a.description)}</div>` : ''}</div>`
    ).join('');

    return `<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>${escapeHtml(card.name)} - MTG Card</title>
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

        .card-name { font-weight: bold; font-size: 16px; color: #1a1a1a; }
        .mana-cost { display: flex; gap: 2px; }
        .mana-symbol {
            width: 18px; height: 18px;
            border-radius: 50%;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 11px;
            font-weight: bold;
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

        .art-box img { width: 100%; height: 100%; object-fit: cover; }

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

        .ability { margin-bottom: 6px; }
        .ability-name { font-weight: 700; font-size: 10px; text-transform: uppercase; }
        .ability-desc { font-size: 10px; color: #2a2a2a; font-style: italic; }
        .flavor-text { font-style: italic; color: #444; border-top: 1px solid #ccc; padding-top: 8px; margin-top: 8px; }

        .pt-box {
            position: absolute;
            bottom: 22px;
            right: 22px;
            background: linear-gradient(180deg, #f5f0e1 0%, #d5d0c5 100%);
            border: 2px solid #1a1a1a;
            border-radius: 6px;
            padding: 4px 12px;
            font-weight: bold;
            font-size: 18px;
        }

        .card-wrapper { position: relative; }
    </style>
</head>
<body>
    <div class="card-wrapper">
        <div class="mtg-card">
            <div class="card-frame">
                <div class="name-bar">
                    <span class="card-name">${escapeHtml(card.name)}</span>
                    <div class="mana-cost">${renderManaCostHtml(card.manaCost)}</div>
                </div>
                <div class="art-box">
                    ${card.artUrl ? `<img src="${card.artUrl}" alt="Card Art">` : '<div style="color:#666;">No Art</div>'}
                </div>
                <div class="type-bar">${escapeHtml(card.typeLine)}</div>
                <div class="text-box">
                    ${abilitiesHtml}
                    ${card.flavorText ? `<div class="flavor-text">${escapeHtml(card.flavorText)}</div>` : ''}
                </div>
            </div>
        </div>
        <div class="pt-box">${card.power}/${card.toughness}</div>
    </div>
</body>
</html>`;
}

function renderManaCostHtml(manaCost: string): string {
    const symbols = manaCost.match(/\{([^}]+)\}/g) || [];
    return symbols.map(s => {
        const symbol = s.replace(/[{}]/g, '');
        const colors: Record<string, { bg: string; fg: string }> = {
            'W': { bg: '#f8f6d8', fg: '#1a1a1a' },
            'U': { bg: '#0e67ab', fg: 'white' },
            'B': { bg: '#150b00', fg: '#9a8c7c' },
            'R': { bg: '#d3202a', fg: 'white' },
            'G': { bg: '#00733e', fg: 'white' },
            'C': { bg: '#ccc2c0', fg: '#1a1a1a' }
        };
        const style = colors[symbol] || { bg: '#888', fg: 'white' };
        return `<div class="mana-symbol" style="background:${style.bg};color:${style.fg};">${symbol}</div>`;
    }).join('');
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

function escapeHtml(str: string): string {
    return str
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
}
