/**
 * Art Generator for CLI
 * Supports: Stability AI, fallback bundled art, caching
 */

import * as fs from 'fs';
import * as path from 'path';
import * as os from 'os';
import { CardData, ManaColor, Config } from './types';

export class ArtGenerator {
    private cacheDir: string;
    private config: Config;
    private fallbackArtDir: string;

    constructor(config: Config) {
        this.config = config;
        this.cacheDir = config.art.cacheDir
            ? path.resolve(config.art.cacheDir.replace('~', os.homedir()))
            : path.join(os.homedir(), '.cache', 'mtg-agents', 'art');

        // Fallback art is bundled with the CLI
        this.fallbackArtDir = path.join(__dirname, '..', 'resources', 'fallback-art');

        if (!fs.existsSync(this.cacheDir)) {
            fs.mkdirSync(this.cacheDir, { recursive: true });
        }
    }

    /**
     * Generate art for a card.
     * Resolution order: existing art next to agent → cache → generate → fallback
     */
    async generateArt(card: CardData): Promise<string | null> {
        // 1. Check for existing art next to agent file
        const existingArt = this.getExistingAgentArt(card);
        if (existingArt) {
            console.log(`Using existing art for ${card.name}`);
            return existingArt;
        }

        const cacheKey = card.name.toLowerCase().replace(/[^a-z0-9]/g, '_');
        const cachedPath = path.join(this.cacheDir, `${cacheKey}.png`);

        // 2. Check cache
        if (fs.existsSync(cachedPath)) {
            console.log(`Using cached art for ${card.name}`);
            return cachedPath;
        }

        // 3. Try Stability AI if configured
        if (this.config.art.source === 'stability' && this.config.art.stabilityApiKey) {
            try {
                const generatedArt = await this.generateWithStabilityAI(card, cachedPath);
                if (generatedArt) {
                    // Auto-save next to agent file
                    this.saveArtNextToAgent(generatedArt, card);
                    return generatedArt;
                }
            } catch (e) {
                console.error(`Stability AI generation failed: ${e}`);
            }
        }

        // 4. Fall back to bundled art
        const fallbackArt = this.getBundledFallbackArt(card, cacheKey);
        if (fallbackArt) {
            return fallbackArt;
        }

        return null;
    }

    private async generateWithStabilityAI(card: CardData, outputPath: string): Promise<string | null> {
        const apiKey = this.config.art.stabilityApiKey;
        if (!apiKey) {
            return null;
        }

        const prompt = this.buildPrompt(card);
        const negativePrompt = this.buildNegativePrompt();

        console.log(`Generating art with Stability AI for ${card.name}...`);

        const response = await fetch('https://api.stability.ai/v1/generation/stable-diffusion-xl-1024-v1-0/text-to-image', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${apiKey}`,
                'Accept': 'application/json'
            },
            body: JSON.stringify({
                text_prompts: [
                    { text: prompt, weight: 1.0 },
                    { text: negativePrompt, weight: -1.0 }
                ],
                cfg_scale: 6.5,
                height: 1024,
                width: 1024,
                steps: 40,
                samples: 1
            })
        });

        if (!response.ok) {
            const error = await response.text();
            throw new Error(`Stability AI error: ${response.status} - ${error}`);
        }

        const data = await response.json() as { artifacts: Array<{ base64: string }> };
        if (data.artifacts && data.artifacts.length > 0) {
            const imageBuffer = Buffer.from(data.artifacts[0].base64, 'base64');
            fs.writeFileSync(outputPath, imageBuffer);
            console.log(`Art saved to ${outputPath}`);
            return outputPath;
        }

        return null;
    }

    private getBundledFallbackArt(card: CardData, cacheKey: string): string | null {
        const primaryColor = card.colorIdentity[0] || ManaColor.BLUE;

        const resourceName: Record<ManaColor, string> = {
            [ManaColor.WHITE]: 'white_knight.png',
            [ManaColor.BLUE]: 'blue_scholar.png',
            [ManaColor.BLACK]: 'black_vampire.png',
            [ManaColor.RED]: 'red_goblin.png',
            [ManaColor.GREEN]: 'green_elf.png',
            [ManaColor.COLORLESS]: 'blue_scholar.png'
        };

        const resourcePath = path.join(this.fallbackArtDir, resourceName[primaryColor]);

        if (fs.existsSync(resourcePath)) {
            const outputPath = path.join(this.cacheDir, `${cacheKey}_fallback.png`);
            fs.copyFileSync(resourcePath, outputPath);
            console.log(`Using fallback art for ${card.name}`);
            return outputPath;
        }

        console.error(`Fallback art not found at ${resourcePath}`);
        return null;
    }

    private buildPrompt(card: CardData): string {
        const mtgStyle = `traditional fantasy oil painting, masterwork illustration, painted with confident brushstrokes, rich color depth, sharp defined edges, clear focal point, crisp details, professional fantasy book cover art, Magic the Gathering card art quality`;

        const artistStyle = 'by Donato Giancola, Terese Nielsen, Todd Lockwood, Michael Whelan';
        const colorAtmosphere = this.getColorAtmosphere(card.colorIdentity[0]);

        if (card.artPrompt) {
            return `${card.artPrompt}, ${mtgStyle}, ${colorAtmosphere}, ${artistStyle}`;
        }

        const creatureType = card.creatureType || this.inferCreatureType(card);
        const colorPalette = this.getColorPalette(card.colorIdentity[0]);

        return `detailed fantasy portrait of ${creatureType}, ${colorPalette} color scheme, ${mtgStyle}, ${colorAtmosphere}, ${artistStyle}`;
    }

    private buildNegativePrompt(): string {
        return `photograph, photo, photorealistic, 3d render, CGI, anime, cartoon, text, watermark, blurry, ugly, deformed, modern, plain background`;
    }

    private getColorAtmosphere(color: ManaColor | undefined): string {
        switch (color) {
            case ManaColor.WHITE: return 'divine golden light rays, heavenly atmosphere, celestial radiance';
            case ManaColor.BLUE: return 'mystical blue arcane energy, ethereal mist, moonlit atmosphere';
            case ManaColor.BLACK: return 'dark shadows, sickly green necrotic glow, ominous atmosphere';
            case ManaColor.RED: return 'blazing fire and flames, volcanic glow, explosive energy';
            case ManaColor.GREEN: return 'dappled forest sunlight, verdant overgrowth, primal energy';
            default: return 'ethereal silver glow, cosmic atmosphere';
        }
    }

    private getColorPalette(color: ManaColor | undefined): string {
        switch (color) {
            case ManaColor.WHITE: return 'warm white, cream, gold';
            case ManaColor.BLUE: return 'deep blue, cyan, silver';
            case ManaColor.BLACK: return 'dark purple, sickly green, black';
            case ManaColor.RED: return 'fiery red, orange, crimson';
            case ManaColor.GREEN: return 'forest green, brown, emerald';
            default: return 'silver and gray';
        }
    }

    private inferCreatureType(card: CardData): string {
        const text = (card.name + ' ' + (card.flavorText || '')).toLowerCase();

        if (text.includes('search') || text.includes('research')) return 'hooded scholarly wizard';
        if (text.includes('data') || text.includes('analyz')) return 'crystalline golem construct';
        if (text.includes('write') || text.includes('creat')) return 'ethereal spirit scribe';
        if (text.includes('code') || text.includes('program')) return 'mechanical arcane automaton';
        return 'powerful mystical entity';
    }

    /**
     * Get path for art next to the agent file.
     */
    private getAgentArtPath(card: CardData): string | null {
        if (!card.sourceFile) return null;

        const agentDir = path.dirname(card.sourceFile);
        const baseName = path.basename(card.sourceFile, path.extname(card.sourceFile));
        return path.join(agentDir, `${baseName}.png`);
    }

    /**
     * Check for existing art file next to the agent file.
     */
    private getExistingAgentArt(card: CardData): string | null {
        const artPath = this.getAgentArtPath(card);
        if (!artPath) return null;

        if (fs.existsSync(artPath)) {
            return artPath;
        }

        return null;
    }

    /**
     * Save art next to the agent file.
     */
    private saveArtNextToAgent(generatedArtPath: string, card: CardData): string | null {
        const targetPath = this.getAgentArtPath(card);
        if (!targetPath) return null;

        try {
            fs.copyFileSync(generatedArtPath, targetPath);
            console.log(`Art saved next to agent: ${targetPath}`);
            return targetPath;
        } catch (e) {
            console.error(`Failed to save art next to agent: ${e}`);
            return null;
        }
    }
}
