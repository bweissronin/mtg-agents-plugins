/**
 * Parser for agent definition files
 * Supports: Markdown with YAML frontmatter, YAML, JSON
 */

import * as fs from 'fs';
import * as path from 'path';
import * as yaml from 'js-yaml';
import { CardData, ManaColor, CardStyle, Ability, AbilityType } from './types';

// Regex patterns
const frontmatterPattern = /^---\s*\n([\s\S]*?)\n---\s*\n([\s\S]*)$/;
const namePattern = /^name:\s*(.+)$/m;
const descriptionPattern = /^description:\s*(.+)$/m;
const modelPattern = /^model:\s*(.+)$/m;
const colorPattern = /^color:\s*(.+)$/m;
const displayNamePattern = /^display_name:\s*(.+)$/m;
const artPromptPattern = /^art_prompt:\s*(.+)$/m;
const creatureTypePattern = /^creature_type:\s*(.+)$/m;
const artStylePattern = /^art_style:\s*(.+)$/m;
const manaCostPattern = /^mana_cost:\s*(.+)$/m;
const powerPattern = /^power:\s*(\d+)$/m;
const toughnessPattern = /^toughness:\s*(\d+)$/m;
const flavorTextPattern = /^flavor_text:\s*(.+)$/m;
const typeLinePattern = /^type_line:\s*(.+)$/m;
const cardStylePattern = /^card_style:\s*(.+)$/m;

export function parseFile(filePath: string): CardData | null {
    const content = fs.readFileSync(filePath, 'utf-8');
    const ext = path.extname(filePath).toLowerCase();

    switch (ext) {
        case '.md':
            return parseMarkdown(content, filePath);
        case '.yaml':
        case '.yml':
            return parseYaml(content, filePath);
        case '.json':
            return parseJson(content, filePath);
        default:
            return null;
    }
}

function parseMarkdown(content: string, filePath: string): CardData | null {
    const match = frontmatterPattern.exec(content);
    if (!match) return null;

    const frontmatter = match[1];
    const body = match[2].trim();

    // Extract fields from frontmatter
    const name = extractField(frontmatter, namePattern);
    if (!name) return null;

    const description = extractField(frontmatter, descriptionPattern);
    const model = extractField(frontmatter, modelPattern) || 'unknown';
    const colorStr = extractField(frontmatter, colorPattern);

    // Card customization fields
    const displayName = extractField(frontmatter, displayNamePattern);
    const artPrompt = extractField(frontmatter, artPromptPattern);
    const creatureType = extractField(frontmatter, creatureTypePattern);
    const artStyle = extractField(frontmatter, artStylePattern);
    const customManaCost = extractField(frontmatter, manaCostPattern);
    const customPower = extractField(frontmatter, powerPattern);
    const customToughness = extractField(frontmatter, toughnessPattern);
    const customFlavorText = extractField(frontmatter, flavorTextPattern);
    const customTypeLine = extractField(frontmatter, typeLinePattern);
    const cardStyleStr = extractField(frontmatter, cardStylePattern);

    const colors = parseColors(colorStr);
    const cardStyle = parseCardStyle(cardStyleStr);
    const abilities = extractAbilitiesFromBody(body);
    const flavorText = customFlavorText || extractFlavorText(body, description);
    const manaCost = customManaCost || buildManaCost(abilities.length, colors);
    const typeLine = customTypeLine || `Legendary Creature — AI Agent ${formatModel(model)}`;
    const projectName = extractProjectName(filePath);

    return {
        name: displayName || formatName(name),
        manaCost,
        colorIdentity: colors,
        typeLine,
        abilities,
        flavorText: flavorText?.substring(0, 120),
        power: customPower ? parseInt(customPower) : Math.max(abilities.length, 1),
        toughness: customToughness ? parseInt(customToughness) : 1,
        setSymbol: projectName,
        collectorInfo: formatModel(model),
        sourceFile: filePath,
        sourceLineNumber: 1,
        artPrompt,
        creatureType,
        artStyle,
        cardStyle
    };
}

function parseYaml(content: string, filePath: string): CardData | null {
    try {
        const data = yaml.load(content) as any;
        if (!data || !data.name) return null;

        const colors = parseColors(data.color);
        const abilities = (data.abilities || data.tools || []).map((a: any) => ({
            name: typeof a === 'string' ? a : a.name || a.tool || 'Unknown',
            description: typeof a === 'object' ? a.description || '' : '',
            abilityType: AbilityType.ACTIVATED
        }));

        return {
            name: data.display_name || formatName(data.name),
            manaCost: data.mana_cost || buildManaCost(abilities.length, colors),
            colorIdentity: colors,
            typeLine: data.type_line || `Legendary Creature — AI Agent`,
            abilities,
            flavorText: data.flavor_text || data.description?.substring(0, 120),
            power: data.power || Math.max(abilities.length, 1),
            toughness: data.toughness || 1,
            setSymbol: extractProjectName(filePath),
            collectorInfo: formatModel(data.model || 'unknown'),
            sourceFile: filePath,
            cardStyle: parseCardStyle(data.card_style)
        };
    } catch {
        return null;
    }
}

function parseJson(content: string, filePath: string): CardData | null {
    try {
        const data = JSON.parse(content);
        return parseYaml(yaml.dump(data), filePath); // Reuse YAML parser
    } catch {
        return null;
    }
}

function extractField(text: string, pattern: RegExp): string | undefined {
    const match = pattern.exec(text);
    return match ? match[1].trim() : undefined;
}

function parseColors(colorStr?: string): ManaColor[] {
    if (!colorStr) return [ManaColor.BLUE];

    const colors: ManaColor[] = [];
    const colorNames = colorStr.toLowerCase().split(/[,\s]+/);

    colorNames.forEach(c => {
        switch (c.trim()) {
            case 'white':
            case 'w':
                colors.push(ManaColor.WHITE);
                break;
            case 'blue':
            case 'u':
                colors.push(ManaColor.BLUE);
                break;
            case 'black':
            case 'b':
                colors.push(ManaColor.BLACK);
                break;
            case 'red':
            case 'r':
                colors.push(ManaColor.RED);
                break;
            case 'green':
            case 'g':
                colors.push(ManaColor.GREEN);
                break;
            case 'colorless':
            case 'c':
                colors.push(ManaColor.COLORLESS);
                break;
        }
    });

    return colors.length > 0 ? [...new Set(colors)] : [ManaColor.BLUE];
}

function parseCardStyle(styleStr?: string): CardStyle {
    if (!styleStr) return CardStyle.BORDERLESS;
    switch (styleStr.toLowerCase().trim()) {
        case 'borderless':
        case 'full-art':
        case 'fullart':
            return CardStyle.BORDERLESS;
        case 'extended':
        case 'extended-art':
            return CardStyle.EXTENDED;
        default:
            return CardStyle.STANDARD;
    }
}

function extractAbilitiesFromBody(body: string): Ability[] {
    const abilities: Ability[] = [];

    // Look for ## headers as major capabilities
    const headerPattern = /^##\s+(.+)$/gm;
    let match;
    while ((match = headerPattern.exec(body)) !== null) {
        const header = match[1].trim();
        // Skip common non-tool headers
        if (['Core Responsibilities', 'Operational Methodology', 'Edge Cases', 'Output Standards'].some(s => header.includes(s))) {
            continue;
        }
        if (header.length < 50) {
            const description = extractDescriptionAfterHeader(body, match.index + match[0].length);
            abilities.push({
                name: header,
                description,
                abilityType: AbilityType.ACTIVATED
            });
        }
    }

    // Also look for ### headers
    const subHeaderPattern = /^###\s+\d*\.?\s*(.+)$/gm;
    while ((match = subHeaderPattern.exec(body)) !== null && abilities.length < 5) {
        const header = match[1].trim();
        if (header.length < 50) {
            const description = extractDescriptionAfterHeader(body, match.index + match[0].length);
            abilities.push({
                name: header,
                description,
                abilityType: AbilityType.ACTIVATED
            });
        }
    }

    // If no headers found, look for bold items
    if (abilities.length === 0) {
        const boldPattern = /\*\*([^*]+)\*\*/g;
        while ((match = boldPattern.exec(body)) !== null && abilities.length < 5) {
            const item = match[1].trim();
            if (item.length < 30 && !item.includes(':')) {
                abilities.push({
                    name: item,
                    description: '',
                    abilityType: AbilityType.STATIC
                });
            }
        }
    }

    return abilities.slice(0, 5);
}

function extractDescriptionAfterHeader(body: string, startPos: number): string {
    const remaining = body.substring(startPos);
    const lines = remaining.split('\n').slice(1); // Skip header line

    for (const line of lines) {
        const trimmed = line.trim();
        if (trimmed.startsWith('#')) break;
        if (trimmed.length === 0) continue;

        const content = trimmed.replace(/^[-*>]\s*/, '').trim();
        if (content.length > 5) {
            const firstSentence = content.split(/[.!?]/)[0]?.trim() || content;
            return firstSentence.substring(0, 80);
        }
    }
    return '';
}

function extractFlavorText(body: string, description?: string): string | undefined {
    // Try "You are" pattern
    const youAreMatch = /You are ([^.]+\.)/.exec(body);
    if (youAreMatch) {
        return `You are ${youAreMatch[1]}`;
    }

    // Use description if available
    if (description) {
        const clean = description.replace(/\\n/g, ' ').replace(/<[^>]+>/g, '').replace(/\s+/g, ' ').trim();
        const firstSentence = clean.split(/[.!?]/)[0]?.trim();
        if (firstSentence && firstSentence.length > 10) {
            return firstSentence;
        }
    }

    // First non-header line
    for (const line of body.split('\n')) {
        const trimmed = line.trim();
        if (trimmed && !trimmed.startsWith('#') && !trimmed.startsWith('-')) {
            return trimmed.substring(0, 120);
        }
    }

    return undefined;
}

function extractProjectName(filePath: string): string {
    let dir = path.dirname(filePath);
    while (dir && dir !== path.dirname(dir)) {
        if (fs.existsSync(path.join(dir, 'package.json')) ||
            fs.existsSync(path.join(dir, '.git')) ||
            fs.existsSync(path.join(dir, 'CLAUDE.md'))) {
            return path.basename(dir);
        }
        dir = path.dirname(dir);
    }
    return path.basename(path.dirname(filePath)) || 'Unknown';
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

    if (colorless > 0) {
        cost += `{${colorless}}`;
    }

    colors.forEach(color => {
        switch (color) {
            case ManaColor.WHITE: cost += '{W}'; break;
            case ManaColor.BLUE: cost += '{U}'; break;
            case ManaColor.BLACK: cost += '{B}'; break;
            case ManaColor.RED: cost += '{R}'; break;
            case ManaColor.GREEN: cost += '{G}'; break;
            case ManaColor.COLORLESS: cost += '{C}'; break;
        }
    });

    return cost || '{1}';
}
