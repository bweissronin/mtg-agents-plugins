/**
 * MTG Agent Visualizer Types
 */

export enum ManaColor {
    WHITE = 'WHITE',
    BLUE = 'BLUE',
    BLACK = 'BLACK',
    RED = 'RED',
    GREEN = 'GREEN',
    COLORLESS = 'COLORLESS'
}

export enum CardStyle {
    STANDARD = 'STANDARD',
    BORDERLESS = 'BORDERLESS',
    EXTENDED = 'EXTENDED'
}

export enum AbilityType {
    ACTIVATED = 'ACTIVATED',
    TRIGGERED = 'TRIGGERED',
    STATIC = 'STATIC'
}

export interface Ability {
    name: string;
    description: string;
    abilityType: AbilityType;
}

export interface CardData {
    name: string;
    manaCost: string;
    colorIdentity: ManaColor[];
    typeLine: string;
    artUrl?: string;
    abilities: Ability[];
    flavorText?: string;
    power: number;
    toughness: number;
    setSymbol?: string;
    collectorInfo?: string;
    sourceFile?: string;
    sourceLineNumber?: number;
    artPrompt?: string;
    creatureType?: string;
    artStyle?: string;
    cardStyle: CardStyle;
}

export interface Config {
    art: {
        source: 'stability' | 'local-sd' | 'fallback';
        stabilityApiKey?: string;
        localSdUrl?: string;
        cacheDir?: string;
        autoSaveAvatars?: boolean;
    };
    style: {
        cardStyle: 'standard' | 'borderless';
        theme: 'dark' | 'light';
        showFlavorText?: boolean;
        showCollectorInfo?: boolean;
    };
    output: {
        format: 'html' | 'png' | 'ascii';
        openBrowser?: boolean;
        outputDir?: string;
    };
    server: {
        port: number;
        hotReload?: boolean;
    };
    parser: {
        agentPatterns: string[];
        excludePatterns: string[];
    };
}

export const defaultConfig: Config = {
    art: {
        source: 'fallback',
        cacheDir: '~/.cache/mtg-agents/art',
        autoSaveAvatars: true
    },
    style: {
        cardStyle: 'borderless',
        theme: 'dark',
        showFlavorText: true,
        showCollectorInfo: true
    },
    output: {
        format: 'html',
        openBrowser: true,
        outputDir: './mtg-cards'
    },
    server: {
        port: 3000,
        hotReload: true
    },
    parser: {
        agentPatterns: [
            '**/*.md',
            '**/agents/**/*.yaml',
            '**/agents/**/*.yml',
            '**/.claude/commands/*.md'
        ],
        excludePatterns: [
            'node_modules/**',
            '.git/**',
            'dist/**',
            'build/**'
        ]
    }
};
