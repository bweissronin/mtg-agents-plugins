/**
 * Development server with hot-reload
 */

import express, { Request, Response } from 'express';
import * as http from 'http';
import * as path from 'path';
import * as fs from 'fs';
import chokidar from 'chokidar';
import { WebSocketServer, WebSocket } from 'ws';
import { parseFile } from './parser';
import { renderBattlefieldHtml } from './renderer/html';
import { ArtGenerator } from './artGenerator';
import { CardData, Config } from './types';
import { loadConfig } from './config';

export interface ServerOptions {
    port: number;
    directory: string;
    patterns: string[];
    hotReload: boolean;
}

export function startServer(options: ServerOptions): void {
    const { port, directory, patterns, hotReload } = options;

    const config = loadConfig();
    const artGenerator = new ArtGenerator(config);

    const app = express();
    const server = http.createServer(app);

    let clients: WebSocket[] = [];
    let cachedCards: CardData[] = [];

    // WebSocket for hot-reload
    if (hotReload) {
        const wss = new WebSocketServer({ server });
        wss.on('connection', (ws) => {
            clients.push(ws);
            ws.on('close', () => {
                clients = clients.filter(c => c !== ws);
            });
        });
    }

    // Scan for agent files
    async function scanAgents(): Promise<CardData[]> {
        const cards: CardData[] = [];
        const absoluteDir = path.resolve(directory);

        function scanDirectory(dir: string) {
            if (!fs.existsSync(dir)) return;

            const entries = fs.readdirSync(dir, { withFileTypes: true });
            for (const entry of entries) {
                const fullPath = path.join(dir, entry.name);

                if (entry.isDirectory()) {
                    // Skip node_modules, .git, etc.
                    if (!['node_modules', '.git', 'dist', 'build'].includes(entry.name)) {
                        scanDirectory(fullPath);
                    }
                } else if (entry.isFile()) {
                    // Check if file matches patterns
                    const ext = path.extname(entry.name).toLowerCase();
                    if (['.md', '.yaml', '.yml', '.json'].includes(ext)) {
                        try {
                            const card = parseFile(fullPath);
                            if (card) {
                                cards.push(card);
                            }
                        } catch (e) {
                            // Skip files that can't be parsed
                        }
                    }
                }
            }
        }

        scanDirectory(absoluteDir);

        // Generate art for cards without it
        for (const card of cards) {
            if (!card.artUrl) {
                card.artUrl = await artGenerator.generateArt(card) || undefined;
            }
        }

        return cards;
    }

    // Initial scan
    scanAgents().then(cards => {
        cachedCards = cards;
    });

    // Watch for changes
    if (hotReload) {
        const watcher = chokidar.watch(directory, {
            ignored: /(^|[\/\\])\..|(node_modules|dist|build)/,
            persistent: true
        });

        watcher.on('change', async (filePath) => {
            console.log(`File changed: ${filePath}`);
            cachedCards = await scanAgents();

            // Notify all clients to reload
            clients.forEach(client => {
                if (client.readyState === WebSocket.OPEN) {
                    client.send('reload');
                }
            });
        });

        watcher.on('add', async () => {
            cachedCards = await scanAgents();
            clients.forEach(client => {
                if (client.readyState === WebSocket.OPEN) {
                    client.send('reload');
                }
            });
        });

        watcher.on('unlink', async () => {
            cachedCards = await scanAgents();
            clients.forEach(client => {
                if (client.readyState === WebSocket.OPEN) {
                    client.send('reload');
                }
            });
        });
    }

    // Routes
    app.get('/', async (req: Request, res: Response) => {
        // Re-scan if not using hot-reload
        if (!hotReload) {
            cachedCards = await scanAgents();
        }

        let html = renderBattlefieldHtml(cachedCards);

        // Inject hot-reload script
        if (hotReload) {
            const reloadScript = `
<script>
    const ws = new WebSocket('ws://' + window.location.host);
    ws.onmessage = (event) => {
        if (event.data === 'reload') {
            window.location.reload();
        }
    };
    ws.onclose = () => {
        console.log('Dev server disconnected. Attempting reconnect...');
        setTimeout(() => window.location.reload(), 2000);
    };
</script>
`;
            html = html.replace('</body>', reloadScript + '</body>');
        }

        res.send(html);
    });

    app.get('/api/agents', (req: Request, res: Response) => {
        res.json(cachedCards);
    });

    // Start server
    server.listen(port, () => {
        console.log(`
┌─────────────────────────────────────────────────┐
│  MTG Agent Visualizer - Dev Server              │
├─────────────────────────────────────────────────┤
│                                                 │
│  🎴 Battlefield: http://localhost:${port.toString().padEnd(5)}       │
│  📁 Watching: ${directory.substring(0, 30).padEnd(30)}  │
│  🔄 Hot-reload: ${hotReload ? 'enabled' : 'disabled'}                        │
│  📊 Agents found: ${cachedCards.length.toString().padEnd(27)} │
│                                                 │
│  Press Ctrl+C to stop                           │
└─────────────────────────────────────────────────┘
`);
    });
}
