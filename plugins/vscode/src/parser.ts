import * as path from 'path';

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

export interface Ability {
    name: string;
    description: string;
}

export interface CardData {
    name: string;
    manaCost: string;
    colorIdentity: ManaColor[];
    typeLine: string;
    artUrl: string | null;
    abilities: Ability[];
    flavorText: string | null;
    power: number;
    toughness: number;
    setSymbol: string;
    collectorInfo: string;
    sourceFile: string;
    cardStyle: CardStyle;
    artPrompt?: string;
    creatureType?: string;
    artStyle?: string;
}

export interface AgentRelationship {
    sourceAgent: string;
    targetAgent: string;
    relationshipType: 'HANDOFF' | 'TOOL_CALL' | 'SUB_AGENT' | 'REFERENCE';
}

/**
 * Parse a markdown file with YAML frontmatter into a CardData object.
 */
export function parseMarkdownAgent(content: string, filePath: string): CardData | null {
    const frontmatterMatch = content.match(/^---\s*\n([\s\S]*?)\n---\s*\n([\s\S]*)$/);
    if (!frontmatterMatch) return null;

    const frontmatter = frontmatterMatch[1];
    const body = frontmatterMatch[2];

    // Parse frontmatter fields
    const name = extractField(frontmatter, 'name');
    if (!name) return null;

    const displayName = extractField(frontmatter, 'display_name');
    const description = extractField(frontmatter, 'description');
    const model = extractField(frontmatter, 'model') || 'unknown';
    const colorStr = extractField(frontmatter, 'color');

    // Card customization
    const customManaCost = extractField(frontmatter, 'mana_cost');
    const customPower = parseInt(extractField(frontmatter, 'power') || '0');
    const customToughness = parseInt(extractField(frontmatter, 'toughness') || '0');
    const customTypeLine = extractField(frontmatter, 'type_line');
    const customFlavorText = extractField(frontmatter, 'flavor_text');
    const cardStyleStr = extractField(frontmatter, 'card_style');

    // Art hints
    const artPrompt = extractField(frontmatter, 'art_prompt');
    const creatureType = extractField(frontmatter, 'creature_type');
    const artStyle = extractField(frontmatter, 'art_style');

    const colors = parseColors(colorStr);
    const abilities = extractAbilities(body);
    const projectName = extractProjectName(filePath);

    return {
        name: displayName || formatName(name),
        manaCost: customManaCost || buildManaCost(abilities.length, colors),
        colorIdentity: colors,
        typeLine: customTypeLine || `Legendary Creature — AI Agent ${formatModel(model)}`,
        artUrl: null,
        abilities,
        flavorText: customFlavorText || extractFlavorText(body, description),
        power: customPower || Math.max(abilities.length, 1),
        toughness: customToughness || 1,
        setSymbol: projectName,
        collectorInfo: formatModel(model),
        sourceFile: filePath,
        cardStyle: parseCardStyle(cardStyleStr),
        artPrompt,
        creatureType,
        artStyle
    };
}

function extractField(frontmatter: string, field: string): string | null {
    const match = frontmatter.match(new RegExp(`^${field}:\\s*(.+)$`, 'm'));
    return match ? match[1].trim().replace(/^["']|["']$/g, '') : null;
}

function parseColors(colorStr: string | null): ManaColor[] {
    if (!colorStr) return [ManaColor.BLUE];

    const colors: ManaColor[] = [];
    const parts = colorStr.toLowerCase().split(/[,\s]+/);

    for (const part of parts) {
        switch (part.trim()) {
            case 'white': case 'w': colors.push(ManaColor.WHITE); break;
            case 'blue': case 'u': colors.push(ManaColor.BLUE); break;
            case 'black': case 'b': colors.push(ManaColor.BLACK); break;
            case 'red': case 'r': colors.push(ManaColor.RED); break;
            case 'green': case 'g': colors.push(ManaColor.GREEN); break;
            case 'colorless': case 'c': colors.push(ManaColor.COLORLESS); break;
        }
    }

    return colors.length > 0 ? colors : [ManaColor.BLUE];
}

function parseCardStyle(styleStr: string | null): CardStyle {
    if (!styleStr) return CardStyle.STANDARD;
    switch (styleStr.toLowerCase()) {
        case 'borderless':
        case 'full-art':
        case 'alt':
        case 'alt-art':
            return CardStyle.BORDERLESS;
        case 'extended':
            return CardStyle.EXTENDED;
        default:
            return CardStyle.STANDARD;
    }
}

function extractAbilities(body: string): Ability[] {
    const abilities: Ability[] = [];

    // Look for ## headers as capabilities
    const headerMatches = body.matchAll(/^##\s+(.+)$/gm);
    for (const match of headerMatches) {
        const header = match[1].trim();
        if (header.length < 40) {
            abilities.push({ name: header, description: 'Capability' });
        }
    }

    // Also look for ### headers
    const subHeaderMatches = body.matchAll(/^###\s+\d*\.?\s*(.+)$/gm);
    for (const match of subHeaderMatches) {
        const header = match[1].trim();
        if (header.length < 50 && abilities.length < 5) {
            abilities.push({ name: header, description: 'Tool' });
        }
    }

    return abilities.slice(0, 5);
}

function extractFlavorText(body: string, description: string | null): string | null {
    // Try "You are..." pattern
    const youAreMatch = body.match(/You are ([^.]+\.)/);
    if (youAreMatch) {
        return `You are ${youAreMatch[1]}`.substring(0, 120);
    }

    if (description) {
        return description.substring(0, 120);
    }

    return null;
}

function extractProjectName(filePath: string): string {
    return path.basename(path.dirname(filePath));
}

function formatName(name: string): string {
    return name
        .replace(/[-_]/g, ' ')
        .split(' ')
        .map(w => w.charAt(0).toUpperCase() + w.slice(1))
        .join(' ');
}

function formatModel(model: string): string {
    switch (model.toLowerCase()) {
        case 'sonnet': return 'Claude Sonnet';
        case 'opus': return 'Claude Opus';
        case 'haiku': return 'Claude Haiku';
        case 'gpt-4':
        case 'gpt4': return 'GPT-4';
        case 'gpt-4o': return 'GPT-4o';
        default: return model;
    }
}

function buildManaCost(toolCount: number, colors: ManaColor[]): string {
    const colorless = Math.max(toolCount - colors.length, 0);
    let cost = '';

    if (colorless > 0) cost += `{${colorless}}`;

    for (const color of colors) {
        switch (color) {
            case ManaColor.WHITE: cost += '{W}'; break;
            case ManaColor.BLUE: cost += '{U}'; break;
            case ManaColor.BLACK: cost += '{B}'; break;
            case ManaColor.RED: cost += '{R}'; break;
            case ManaColor.GREEN: cost += '{G}'; break;
            case ManaColor.COLORLESS: cost += '{C}'; break;
        }
    }

    return cost || '{1}';
}

/**
 * Extract agent references from body text.
 */
export function extractAgentReferences(body: string): string[] {
    const references: string[] = [];
    const pattern = /(?:the|use|invoke|call)\s+(\w+[-_]?\w*)\s+agent/gi;

    let match;
    while ((match = pattern.exec(body)) !== null) {
        references.push(formatName(match[1]));
    }

    return [...new Set(references)];
}
