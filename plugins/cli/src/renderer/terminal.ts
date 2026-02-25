/**
 * ASCII Terminal Card Renderer
 */

import chalk from 'chalk';
import { CardData, ManaColor } from '../types';

const CARD_WIDTH = 40;

export function renderCardAscii(card: CardData): string {
    const lines: string[] = [];

    // Top border
    lines.push(chalk.gray('┌' + '─'.repeat(CARD_WIDTH - 2) + '┐'));

    // Name and mana cost
    const manaStr = formatManaAscii(card.manaCost);
    const nameWidth = CARD_WIDTH - 4 - manaStr.length;
    const name = card.name.substring(0, nameWidth).padEnd(nameWidth);
    lines.push(chalk.gray('│ ') + chalk.bold.white(name) + ' ' + manaStr + chalk.gray(' │'));

    // Separator
    lines.push(chalk.gray('├' + '─'.repeat(CARD_WIDTH - 2) + '┤'));

    // Art placeholder (5 lines)
    const artLines = generateArtPlaceholder(card.colorIdentity[0] || ManaColor.BLUE);
    artLines.forEach(line => {
        lines.push(chalk.gray('│') + line + chalk.gray('│'));
    });

    // Separator
    lines.push(chalk.gray('├' + '─'.repeat(CARD_WIDTH - 2) + '┤'));

    // Type line
    const typeLine = card.typeLine.substring(0, CARD_WIDTH - 4).padEnd(CARD_WIDTH - 4);
    lines.push(chalk.gray('│ ') + chalk.italic(typeLine) + chalk.gray(' │'));

    // Separator
    lines.push(chalk.gray('├' + '─'.repeat(CARD_WIDTH - 2) + '┤'));

    // Abilities
    const abilityLines = formatAbilities(card.abilities, CARD_WIDTH - 4);
    abilityLines.forEach(line => {
        lines.push(chalk.gray('│ ') + line.padEnd(CARD_WIDTH - 4) + chalk.gray(' │'));
    });

    // Flavor text
    if (card.flavorText) {
        lines.push(chalk.gray('│ ') + ' '.repeat(CARD_WIDTH - 4) + chalk.gray(' │'));
        const flavorLines = wrapText(`"${card.flavorText}"`, CARD_WIDTH - 4);
        flavorLines.forEach(line => {
            lines.push(chalk.gray('│ ') + chalk.dim.italic(line.padEnd(CARD_WIDTH - 4)) + chalk.gray(' │'));
        });
    }

    // Separator
    lines.push(chalk.gray('├' + '─'.repeat(CARD_WIDTH - 2) + '┤'));

    // Power/Toughness
    const ptStr = `${card.power}/${card.toughness}`;
    const collectorInfo = card.collectorInfo || '';
    const ptLine = collectorInfo.substring(0, CARD_WIDTH - 8 - ptStr.length).padEnd(CARD_WIDTH - 4 - ptStr.length) + chalk.bold(ptStr);
    lines.push(chalk.gray('│ ') + ptLine + chalk.gray(' │'));

    // Bottom border
    lines.push(chalk.gray('└' + '─'.repeat(CARD_WIDTH - 2) + '┘'));

    return lines.join('\n');
}

export function renderBattlefieldAscii(cards: CardData[]): string {
    const lines: string[] = [];

    lines.push(chalk.bold.cyan('\n  ⚔️  MTG Agent Battlefield  ⚔️\n'));
    lines.push(chalk.dim(`  ${cards.length} agent${cards.length !== 1 ? 's' : ''} detected\n`));

    // Mini card view for each agent
    cards.forEach((card, i) => {
        const mana = formatManaAscii(card.manaCost);
        const colorFn = getColorFunction(card.colorIdentity[0]);

        lines.push(
            chalk.gray(`  ${(i + 1).toString().padStart(2)}. `) +
            colorFn('■') + ' ' +
            chalk.bold(card.name.padEnd(25)) +
            chalk.dim(mana.padStart(12)) +
            chalk.gray(' │ ') +
            chalk.bold(`${card.power}/${card.toughness}`)
        );

        // Show first ability
        if (card.abilities.length > 0) {
            lines.push(
                chalk.gray('      └─ ') +
                chalk.dim(card.abilities[0].name.substring(0, 35))
            );
        }
    });

    lines.push('');
    return lines.join('\n');
}

function formatManaAscii(manaCost: string): string {
    const symbols = manaCost.match(/\{([^}]+)\}/g) || [];
    return symbols.map(s => {
        const sym = s.replace(/[{}]/g, '');
        switch (sym.toUpperCase()) {
            case 'W': return chalk.bgWhite.black(` ${sym} `);
            case 'U': return chalk.bgBlue.white(` ${sym} `);
            case 'B': return chalk.bgBlack.white(` ${sym} `);
            case 'R': return chalk.bgRed.white(` ${sym} `);
            case 'G': return chalk.bgGreen.white(` ${sym} `);
            default: return chalk.bgGray.white(` ${sym} `);
        }
    }).join('');
}

function generateArtPlaceholder(color: ManaColor): string[] {
    const width = CARD_WIDTH - 2;
    const lines: string[] = [];

    // Color-specific art patterns
    const colorChars: Record<ManaColor, { char: string; colorFn: (s: string) => string }> = {
        [ManaColor.WHITE]: { char: '░', colorFn: chalk.white },
        [ManaColor.BLUE]: { char: '▓', colorFn: chalk.blue },
        [ManaColor.BLACK]: { char: '█', colorFn: chalk.gray },
        [ManaColor.RED]: { char: '▒', colorFn: chalk.red },
        [ManaColor.GREEN]: { char: '▓', colorFn: chalk.green },
        [ManaColor.COLORLESS]: { char: '░', colorFn: chalk.gray }
    };

    const { char, colorFn } = colorChars[color] || colorChars[ManaColor.BLUE];

    // Generate simple pattern
    for (let i = 0; i < 5; i++) {
        let line = '';
        for (let j = 0; j < width; j++) {
            // Create a simple gradient/pattern effect
            const intensity = Math.sin((i + j) * 0.3) * 0.5 + 0.5;
            if (intensity > 0.6) {
                line += colorFn(char);
            } else if (intensity > 0.3) {
                line += colorFn('░');
            } else {
                line += ' ';
            }
        }
        lines.push(line);
    }

    return lines;
}

function formatAbilities(abilities: { name: string; description: string }[], maxWidth: number): string[] {
    const lines: string[] = [];

    abilities.slice(0, 4).forEach(ability => {
        // Ability name with bullet
        lines.push(chalk.yellow('◆') + ' ' + chalk.bold(ability.name.substring(0, maxWidth - 3)));

        // Description if present
        if (ability.description) {
            const descLines = wrapText(ability.description, maxWidth - 2);
            descLines.forEach(line => {
                lines.push('  ' + chalk.dim(line));
            });
        }
    });

    // Indicate if more abilities exist
    if (abilities.length > 4) {
        lines.push(chalk.dim(`  ...and ${abilities.length - 4} more`));
    }

    // Ensure minimum height
    while (lines.length < 4) {
        lines.push('');
    }

    return lines;
}

function wrapText(text: string, maxWidth: number): string[] {
    const words = text.split(' ');
    const lines: string[] = [];
    let currentLine = '';

    words.forEach(word => {
        if (currentLine.length + word.length + 1 <= maxWidth) {
            currentLine += (currentLine ? ' ' : '') + word;
        } else {
            if (currentLine) lines.push(currentLine);
            currentLine = word.substring(0, maxWidth);
        }
    });

    if (currentLine) lines.push(currentLine);
    return lines.slice(0, 3); // Max 3 lines
}

function getColorFunction(color?: ManaColor): (s: string) => string {
    switch (color) {
        case ManaColor.WHITE: return chalk.white;
        case ManaColor.BLUE: return chalk.blue;
        case ManaColor.BLACK: return chalk.gray;
        case ManaColor.RED: return chalk.red;
        case ManaColor.GREEN: return chalk.green;
        default: return chalk.cyan;
    }
}
