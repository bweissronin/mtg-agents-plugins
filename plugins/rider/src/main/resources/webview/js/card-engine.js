/**
 * MTG Card Engine
 * Renders AI agents as Magic: The Gathering cards
 */

class MtgCardEngine {
    constructor() {
        this.useVectorFrames = false;
        this.cards = new Map();
    }

    /**
     * Parse mana cost string into symbols
     * e.g., "{2}{U}{U}" -> [{type: 'generic', value: 2}, {type: 'u'}, {type: 'u'}]
     */
    parseManaCost(manaCost) {
        if (!manaCost) return [];

        const symbols = [];
        const regex = /\{(\d+|[WUBRGC])\}/g;
        let match;

        while ((match = regex.exec(manaCost)) !== null) {
            const value = match[1];
            if (/^\d+$/.test(value)) {
                symbols.push({ type: 'generic', value: parseInt(value) });
            } else {
                symbols.push({ type: value.toLowerCase() });
            }
        }

        return symbols;
    }

    /**
     * Get the primary color for frame selection
     */
    getPrimaryFrameColor(colorIdentity) {
        if (!colorIdentity || colorIdentity.length === 0) {
            return 'artifact';
        }
        if (colorIdentity.length > 1) {
            return 'multicolor';
        }
        return colorIdentity[0].toLowerCase();
    }

    /**
     * Create mana symbol HTML
     */
    createManaSymbolHtml(symbol) {
        if (symbol.type === 'generic') {
            return `<span class="mana-symbol generic">${symbol.value}</span>`;
        }
        const symbolMap = {
            'w': 'W', 'u': 'U', 'b': 'B', 'r': 'R', 'g': 'G', 'c': 'C'
        };
        return `<span class="mana-symbol ${symbol.type}">${symbolMap[symbol.type] || symbol.type.toUpperCase()}</span>`;
    }

    /**
     * Create abilities HTML
     */
    createAbilitiesHtml(abilities) {
        if (!abilities || abilities.length === 0) {
            return '<div class="ability"><em>No abilities</em></div>';
        }

        return abilities.map(ability => {
            const icon = this.getAbilityIcon(ability.abilityType);
            return `
                <div class="ability">
                    <span class="ability-icon">${icon}</span>
                    <span class="ability-name">${this.escapeHtml(ability.name)}</span>
                    ${ability.description ? `: <span class="ability-desc">${this.escapeHtml(ability.description)}</span>` : ''}
                </div>
            `;
        }).join('');
    }

    /**
     * Get icon for ability type
     */
    getAbilityIcon(abilityType) {
        switch (abilityType) {
            case 'ACTIVATED': return '⚡';
            case 'TRIGGERED': return '🔔';
            case 'STATIC': return '◆';
            case 'KEYWORD': return '★';
            default: return '•';
        }
    }

    /**
     * Escape HTML to prevent XSS
     */
    escapeHtml(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    /**
     * Render a single card
     */
    renderCard(cardData, container) {
        const frameColor = this.getPrimaryFrameColor(cardData.colorIdentity);
        const manaSymbols = this.parseManaCost(cardData.manaCost);

        const frameClass = this.useVectorFrames ? `vector ${frameColor}` : frameColor;
        const frameImage = this.useVectorFrames ? '' :
            `<img src="assets/frames/frame-${frameColor}.png" alt="" onerror="this.style.display='none'">`;

        const artContent = cardData.artUrl ?
            `<img src="${this.escapeHtml(cardData.artUrl)}" alt="Card Art">` :
            `<div class="art-placeholder">
                <div class="art-loading">
                    <div class="spinner"></div>
                    <span>Generating art...</span>
                </div>
            </div>`;

        const html = `
            <div class="mtg-card" data-card-name="${this.escapeHtml(cardData.name)}">
                <div class="card-frame ${frameClass}">
                    ${frameImage}
                </div>
                <div class="card-inner"></div>

                <div class="card-name-bar">
                    <span class="card-name">${this.escapeHtml(cardData.name)}</span>
                    <div class="mana-cost">
                        ${manaSymbols.map(s => this.createManaSymbolHtml(s)).join('')}
                    </div>
                </div>

                <div class="art-box">
                    ${artContent}
                </div>

                <div class="type-line">
                    <span class="type-text">${this.escapeHtml(cardData.typeLine)}</span>
                    <div class="set-symbol" title="${this.escapeHtml(cardData.setSymbol)}">
                        <svg viewBox="0 0 20 20" fill="currentColor">
                            <path d="M10 2L12 8H18L13 12L15 18L10 14L5 18L7 12L2 8H8L10 2Z"/>
                        </svg>
                    </div>
                </div>

                <div class="text-box">
                    ${this.createAbilitiesHtml(cardData.abilities)}
                    ${cardData.flavorText ? `<div class="flavor-text">"${this.escapeHtml(cardData.flavorText)}"</div>` : ''}
                </div>

                <div class="pt-box">${cardData.power}/${cardData.toughness}</div>

                <div class="collector-info">
                    ${this.escapeHtml(cardData.collectorInfo)} • ${this.escapeHtml(cardData.setSymbol)}
                </div>
            </div>
        `;

        if (typeof container === 'string') {
            container = document.querySelector(container);
        }

        if (container) {
            container.innerHTML = html;
        }

        this.cards.set(cardData.name, cardData);
        return html;
    }

    /**
     * Render a gallery of cards
     */
    renderGallery(cardsData, container) {
        const header = `
            <div class="gallery-header">
                <h1>Agent Deck</h1>
                <p>${cardsData.length} agent${cardsData.length !== 1 ? 's' : ''} found</p>
            </div>
        `;

        const cardsHtml = cardsData.map(card => {
            const div = document.createElement('div');
            this.renderCard(card, div);
            return div.innerHTML;
        }).join('');

        if (typeof container === 'string') {
            container = document.querySelector(container);
        }

        if (container) {
            container.innerHTML = header + cardsHtml;
        }
    }

    /**
     * Set whether to use vector frames or PNG frames
     */
    setUseVectorFrames(useVector) {
        this.useVectorFrames = useVector;
    }

    /**
     * Update card art after generation
     */
    updateCardArt(cardName, artUrl) {
        const cardElement = document.querySelector(`[data-card-name="${cardName}"]`);
        if (cardElement) {
            const artBox = cardElement.querySelector('.art-box');
            if (artBox) {
                artBox.innerHTML = `<img src="${this.escapeHtml(artUrl)}" alt="Card Art">`;
            }
        }

        const card = this.cards.get(cardName);
        if (card) {
            card.artUrl = artUrl;
        }
    }
}

/**
 * Battlefield Engine
 * Renders agents as a connected graph using d3-force
 */
class BattlefieldEngine {
    constructor(containerId) {
        this.container = document.getElementById(containerId) || document.body;
        this.cardEngine = new MtgCardEngine();
        this.nodes = [];
        this.edges = [];
        this.simulation = null;
        this.svg = null;
        this.zoom = null;
    }

    /**
     * Initialize the battlefield SVG
     */
    init() {
        // Clear existing content
        this.container.innerHTML = '';

        // Create SVG
        this.svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
        this.svg.setAttribute('width', '100%');
        this.svg.setAttribute('height', '100%');
        this.svg.style.position = 'absolute';
        this.svg.style.top = '0';
        this.svg.style.left = '0';

        // Add arrow marker definition
        const defs = document.createElementNS('http://www.w3.org/2000/svg', 'defs');
        defs.innerHTML = `
            <marker id="arrowhead" markerWidth="10" markerHeight="7" refX="10" refY="3.5" orient="auto">
                <polygon points="0 0, 10 3.5, 0 7" fill="#999"/>
            </marker>
            <marker id="arrowhead-handoff" markerWidth="10" markerHeight="7" refX="10" refY="3.5" orient="auto">
                <polygon points="0 0, 10 3.5, 0 7" fill="#4CAF50"/>
            </marker>
            <marker id="arrowhead-tool" markerWidth="10" markerHeight="7" refX="10" refY="3.5" orient="auto">
                <polygon points="0 0, 10 3.5, 0 7" fill="#2196F3"/>
            </marker>
        `;
        this.svg.appendChild(defs);

        // Create groups for edges and nodes
        this.edgeGroup = document.createElementNS('http://www.w3.org/2000/svg', 'g');
        this.edgeGroup.setAttribute('class', 'edges');
        this.svg.appendChild(this.edgeGroup);

        this.nodeGroup = document.createElement('div');
        this.nodeGroup.style.position = 'absolute';
        this.nodeGroup.style.top = '0';
        this.nodeGroup.style.left = '0';
        this.nodeGroup.style.width = '100%';
        this.nodeGroup.style.height = '100%';
        this.nodeGroup.style.pointerEvents = 'none';

        this.container.appendChild(this.svg);
        this.container.appendChild(this.nodeGroup);

        // Add controls
        this.addControls();
    }

    /**
     * Add control buttons
     */
    addControls() {
        const controls = document.createElement('div');
        controls.className = 'battlefield-controls';
        controls.innerHTML = `
            <button class="battlefield-btn" onclick="battlefieldEngine.zoomIn()">Zoom +</button>
            <button class="battlefield-btn" onclick="battlefieldEngine.zoomOut()">Zoom -</button>
            <button class="battlefield-btn" onclick="battlefieldEngine.resetView()">Reset</button>
            <button class="battlefield-btn" onclick="battlefieldEngine.refresh()">Refresh</button>
        `;
        this.container.appendChild(controls);
    }

    /**
     * Load battlefield data and render
     */
    loadData(battlefieldData) {
        this.nodes = battlefieldData.agents.map((agent, i) => ({
            id: agent.name,
            data: agent,
            x: Math.random() * (this.container.clientWidth - 140) + 70,
            y: Math.random() * (this.container.clientHeight - 200) + 100
        }));

        this.edges = battlefieldData.relationships.map(rel => ({
            source: rel.sourceAgent,
            target: rel.targetAgent,
            type: rel.relationshipType
        }));

        this.render();
        this.startSimulation();
    }

    /**
     * Render nodes and edges
     */
    render() {
        // Clear existing
        this.edgeGroup.innerHTML = '';
        this.nodeGroup.innerHTML = '';

        // Render edges
        this.edges.forEach(edge => {
            const sourceNode = this.nodes.find(n => n.id === edge.source);
            const targetNode = this.nodes.find(n => n.id === edge.target);

            if (sourceNode && targetNode) {
                const line = document.createElementNS('http://www.w3.org/2000/svg', 'line');
                line.setAttribute('class', `relationship-edge ${edge.type.toLowerCase().replace('_', '-')}`);
                line.setAttribute('data-source', edge.source);
                line.setAttribute('data-target', edge.target);
                this.edgeGroup.appendChild(line);
            }
        });

        // Render nodes as mini cards
        this.nodes.forEach(node => {
            const card = this.createMiniCard(node);
            this.nodeGroup.appendChild(card);
        });

        this.updatePositions();
    }

    /**
     * Create a mini card element
     */
    createMiniCard(node) {
        const card = document.createElement('div');
        card.className = 'mini-card';
        card.id = `node-${node.id}`;
        card.style.pointerEvents = 'auto';

        const frameColor = this.cardEngine.getPrimaryFrameColor(node.data.colorIdentity);

        card.innerHTML = `
            <div class="mini-card-content" style="background: linear-gradient(135deg, var(--mtg-${frameColor === 'multicolor' ? 'gold' : frameColor}, #3a3a5a) 0%, #2a2a4a 100%);">
                <div class="mini-card-name">${this.cardEngine.escapeHtml(node.data.name)}</div>
                <div class="mini-card-art">
                    ${node.data.artUrl ?
                        `<img src="${this.cardEngine.escapeHtml(node.data.artUrl)}" alt="">` :
                        '<div class="art-placeholder"></div>'}
                </div>
                <div class="mini-card-pt">${node.data.power}/${node.data.toughness}</div>
            </div>
        `;

        // Click to open full card view
        card.addEventListener('click', () => {
            if (window.sendToPlugin) {
                window.sendToPlugin(`openCard:${node.id}`);
            }
        });

        // Drag support
        this.makeDraggable(card, node);

        return card;
    }

    /**
     * Make a card draggable
     */
    makeDraggable(element, node) {
        let isDragging = false;
        let startX, startY, startNodeX, startNodeY;

        element.addEventListener('mousedown', (e) => {
            isDragging = true;
            startX = e.clientX;
            startY = e.clientY;
            startNodeX = node.x;
            startNodeY = node.y;
            element.style.zIndex = '1000';
            e.preventDefault();
        });

        document.addEventListener('mousemove', (e) => {
            if (!isDragging) return;

            const dx = e.clientX - startX;
            const dy = e.clientY - startY;

            node.x = startNodeX + dx;
            node.y = startNodeY + dy;

            // Fix position in simulation
            node.fx = node.x;
            node.fy = node.y;

            this.updatePositions();
        });

        document.addEventListener('mouseup', () => {
            if (isDragging) {
                isDragging = false;
                element.style.zIndex = '';
                // Release fixed position
                node.fx = null;
                node.fy = null;
            }
        });
    }

    /**
     * Update visual positions of nodes and edges
     */
    updatePositions() {
        // Update node positions
        this.nodes.forEach(node => {
            const element = document.getElementById(`node-${node.id}`);
            if (element) {
                element.style.left = `${node.x - 60}px`;
                element.style.top = `${node.y - 84}px`;
            }
        });

        // Update edge positions
        const lines = this.edgeGroup.querySelectorAll('line');
        lines.forEach(line => {
            const sourceId = line.getAttribute('data-source');
            const targetId = line.getAttribute('data-target');
            const sourceNode = this.nodes.find(n => n.id === sourceId);
            const targetNode = this.nodes.find(n => n.id === targetId);

            if (sourceNode && targetNode) {
                line.setAttribute('x1', sourceNode.x);
                line.setAttribute('y1', sourceNode.y);
                line.setAttribute('x2', targetNode.x);
                line.setAttribute('y2', targetNode.y);
            }
        });
    }

    /**
     * Start the force simulation
     */
    startSimulation() {
        // Simple force simulation without d3
        const tick = () => {
            // Repulsion between nodes
            for (let i = 0; i < this.nodes.length; i++) {
                for (let j = i + 1; j < this.nodes.length; j++) {
                    const nodeA = this.nodes[i];
                    const nodeB = this.nodes[j];

                    if (nodeA.fx !== null || nodeB.fx !== null) continue;

                    const dx = nodeB.x - nodeA.x;
                    const dy = nodeB.y - nodeA.y;
                    const dist = Math.sqrt(dx * dx + dy * dy) || 1;
                    const minDist = 180;

                    if (dist < minDist) {
                        const force = (minDist - dist) / dist * 0.05;
                        const fx = dx * force;
                        const fy = dy * force;

                        nodeA.x -= fx;
                        nodeA.y -= fy;
                        nodeB.x += fx;
                        nodeB.y += fy;
                    }
                }
            }

            // Attraction along edges
            this.edges.forEach(edge => {
                const sourceNode = this.nodes.find(n => n.id === edge.source);
                const targetNode = this.nodes.find(n => n.id === edge.target);

                if (sourceNode && targetNode) {
                    if (sourceNode.fx !== null || targetNode.fx !== null) return;

                    const dx = targetNode.x - sourceNode.x;
                    const dy = targetNode.y - sourceNode.y;
                    const dist = Math.sqrt(dx * dx + dy * dy) || 1;
                    const idealDist = 250;

                    const force = (dist - idealDist) / dist * 0.02;
                    const fx = dx * force;
                    const fy = dy * force;

                    sourceNode.x += fx;
                    sourceNode.y += fy;
                    targetNode.x -= fx;
                    targetNode.y -= fy;
                }
            });

            // Keep nodes in bounds
            const padding = 80;
            const width = this.container.clientWidth;
            const height = this.container.clientHeight;

            this.nodes.forEach(node => {
                if (node.fx === null) {
                    node.x = Math.max(padding, Math.min(width - padding, node.x));
                    node.y = Math.max(padding, Math.min(height - padding, node.y));
                }
            });

            this.updatePositions();
            requestAnimationFrame(tick);
        };

        tick();
    }

    /**
     * Zoom controls
     */
    zoomIn() {
        // Scale all node positions toward center
        const centerX = this.container.clientWidth / 2;
        const centerY = this.container.clientHeight / 2;

        this.nodes.forEach(node => {
            node.x = centerX + (node.x - centerX) * 0.8;
            node.y = centerY + (node.y - centerY) * 0.8;
        });

        this.updatePositions();
    }

    zoomOut() {
        const centerX = this.container.clientWidth / 2;
        const centerY = this.container.clientHeight / 2;

        this.nodes.forEach(node => {
            node.x = centerX + (node.x - centerX) * 1.25;
            node.y = centerY + (node.y - centerY) * 1.25;
        });

        this.updatePositions();
    }

    resetView() {
        // Reset to random positions
        this.nodes.forEach(node => {
            node.x = Math.random() * (this.container.clientWidth - 140) + 70;
            node.y = Math.random() * (this.container.clientHeight - 200) + 100;
            node.fx = null;
            node.fy = null;
        });

        this.updatePositions();
    }

    refresh() {
        if (window.sendToPlugin) {
            window.sendToPlugin('refresh');
        }
    }
}

// Global instances
let cardEngine = new MtgCardEngine();
let battlefieldEngine = null;

// Global functions for plugin communication
window.renderCard = function(cardData) {
    cardEngine.renderCard(cardData, document.body);
};

window.renderGallery = function(cardsData) {
    cardEngine.renderGallery(cardsData, document.body);
};

window.loadBattlefieldData = function(battlefieldData) {
    if (!battlefieldEngine) {
        battlefieldEngine = new BattlefieldEngine('battlefield-container');
        battlefieldEngine.init();
    }
    battlefieldEngine.loadData(battlefieldData);
};

window.updateCardArt = function(cardName, artUrl) {
    cardEngine.updateCardArt(cardName, artUrl);
};

window.setUseVectorFrames = function(useVector) {
    cardEngine.setUseVectorFrames(useVector);
};
