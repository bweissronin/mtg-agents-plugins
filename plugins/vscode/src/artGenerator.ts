import * as vscode from 'vscode';
import * as path from 'path';
import * as fs from 'fs';
import * as os from 'os';
import { CardData, ManaColor } from './parser';

export class ArtGenerator {
    private cacheDir: string;

    constructor() {
        this.cacheDir = path.join(os.tmpdir(), 'mtg-agent-art');
        if (!fs.existsSync(this.cacheDir)) {
            fs.mkdirSync(this.cacheDir, { recursive: true });
        }
    }

    /**
     * Generate art for a card. Tries cloud API, then falls back to bundled art.
     */
    async generateArt(card: CardData, context: vscode.ExtensionContext): Promise<string | null> {
        const cacheKey = card.name.toLowerCase().replace(/[^a-z0-9]/g, '_');
        const cachedPath = path.join(this.cacheDir, `${cacheKey}.png`);

        // Check cache first
        if (fs.existsSync(cachedPath)) {
            console.log(`MTG Art: Using cached art for ${card.name}`);
            return cachedPath;
        }

        const config = vscode.workspace.getConfiguration('mtgAgent');
        const provider = config.get<string>('cloudProvider', 'none');
        const apiKey = config.get<string>('stabilityApiKey', '');

        // Try Stability AI if configured
        if (provider === 'stabilityai' && apiKey) {
            try {
                const result = await this.generateWithStabilityAI(card, apiKey, cachedPath);
                if (result) return result;
            } catch (e) {
                console.error('Stability AI generation failed:', e);
            }
        }

        // Fall back to bundled art
        return this.getBundledFallbackArt(card, context, cacheKey);
    }

    private async generateWithStabilityAI(card: CardData, apiKey: string, outputPath: string): Promise<string | null> {
        const config = vscode.workspace.getConfiguration('mtgAgent');
        const steps = config.get<number>('artGenerationSteps', 40);
        const cfgScale = config.get<number>('cfgScale', 6.5);

        const prompt = this.buildPrompt(card);
        const negativePrompt = this.buildNegativePrompt();

        console.log(`MTG Art: Generating with Stability AI for ${card.name}`);

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
                cfg_scale: cfgScale,
                height: 1024,
                width: 1024,
                steps: Math.min(steps, 50),
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
            console.log(`MTG Art: Saved to ${outputPath}`);
            return outputPath;
        }

        return null;
    }

    private getBundledFallbackArt(card: CardData, context: vscode.ExtensionContext, cacheKey: string): string | null {
        const primaryColor = card.colorIdentity[0] || ManaColor.BLUE;

        const resourceName = {
            [ManaColor.WHITE]: 'white_knight.png',
            [ManaColor.BLUE]: 'blue_scholar.png',
            [ManaColor.BLACK]: 'black_vampire.png',
            [ManaColor.RED]: 'red_goblin.png',
            [ManaColor.GREEN]: 'green_elf.png',
            [ManaColor.COLORLESS]: 'blue_scholar.png'
        }[primaryColor];

        const resourcePath = path.join(context.extensionPath, 'resources', 'art', 'fallback', resourceName);

        if (fs.existsSync(resourcePath)) {
            const outputPath = path.join(this.cacheDir, `${cacheKey}_fallback.png`);
            fs.copyFileSync(resourcePath, outputPath);
            console.log(`MTG Art: Using fallback art for ${card.name}`);
            return outputPath;
        }

        console.error(`MTG Art: Fallback art not found at ${resourcePath}`);
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
}
