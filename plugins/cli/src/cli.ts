#!/usr/bin/env node

/**
 * MTG Agent Visualizer CLI
 *
 * Visualize AI agents as Magic: The Gathering cards
 */

import { Command } from 'commander';
import * as fs from 'fs';
import * as path from 'path';
import chalk from 'chalk';
import open from 'open';

import { parseFile } from './parser';
import { renderCardHtml, renderBattlefieldHtml } from './renderer/html';
import { renderCardAscii, renderBattlefieldAscii } from './renderer/terminal';
import { loadConfig, saveConfig, findConfigFile, printConfig, getConfigValue, setConfigValue } from './config';
import { startServer } from './server';
import { ArtGenerator } from './artGenerator';
import { CardData, defaultConfig } from './types';

const program = new Command();

program
    .name('mtg-agents')
    .description('Visualize AI agents as Magic: The Gathering cards')
    .version('1.0.0');

// ============================================================
// CARD COMMAND - Generate a single card
// ============================================================
program
    .command('card <file>')
    .description('Generate a card for a single agent file')
    .option('-o, --output <path>', 'Output file path')
    .option('-f, --format <type>', 'Output format: html, ascii', 'html')
    .option('--open', 'Open in browser after generation')
    .option('--style <style>', 'Card style: borderless, standard', 'borderless')
    .option('--no-art', 'Skip art generation')
    .option('--config <path>', 'Path to config file')
    .action(async (file: string, options) => {
        const config = loadConfig(options.config);

        // Parse the agent file
        const filePath = path.resolve(file);
        if (!fs.existsSync(filePath)) {
            console.error(chalk.red(`Error: File not found: ${filePath}`));
            process.exit(1);
        }

        const card = parseFile(filePath);
        if (!card) {
            console.error(chalk.red(`Error: Could not parse agent file: ${filePath}`));
            console.error(chalk.dim('Make sure the file has valid YAML frontmatter or is a valid YAML/JSON file.'));
            process.exit(1);
        }

        // Apply style override
        if (options.style) {
            card.cardStyle = options.style.toUpperCase() as any;
        }

        // Generate art if not already present (unless --no-art)
        if (!card.artUrl && options.art !== false) {
            const artGenerator = new ArtGenerator(config);
            console.log(chalk.dim('Generating art...'));
            card.artUrl = await artGenerator.generateArt(card) || undefined;
        }

        const format = options.format || config.output.format;

        if (format === 'ascii') {
            // ASCII output to terminal
            console.log(renderCardAscii(card));
        } else {
            // HTML output
            const html = renderCardHtml(card);

            if (options.output) {
                const outputPath = path.resolve(options.output);
                fs.writeFileSync(outputPath, html);
                console.log(chalk.green(`✓ Card saved to ${outputPath}`));

                if (options.open) {
                    await open(outputPath);
                }
            } else {
                // Save to temp file and open
                const tempPath = path.join(require('os').tmpdir(), `mtg-card-${Date.now()}.html`);
                fs.writeFileSync(tempPath, html);
                console.log(chalk.green(`✓ Card generated: ${tempPath}`));

                if (options.open !== false) {
                    await open(tempPath);
                }
            }
        }
    });

// ============================================================
// BATTLEFIELD COMMAND - Show all agents
// ============================================================
program
    .command('battlefield [directory]')
    .description('Generate battlefield view of all agents in a directory')
    .option('-o, --output <path>', 'Output file path')
    .option('--serve', 'Start local server with hot-reload')
    .option('-p, --port <number>', 'Server port', '3000')
    .option('--ascii', 'Output ASCII art to terminal')
    .option('--no-art', 'Skip art generation')
    .option('--config <path>', 'Path to config file')
    .action(async (directory: string = '.', options) => {
        const config = loadConfig(options.config);
        const dir = path.resolve(directory);

        if (!fs.existsSync(dir)) {
            console.error(chalk.red(`Error: Directory not found: ${dir}`));
            process.exit(1);
        }

        // Start dev server
        if (options.serve) {
            const port = parseInt(options.port) || config.server.port;
            startServer({
                port,
                directory: dir,
                patterns: config.parser.agentPatterns,
                hotReload: config.server.hotReload !== false
            });
            return;
        }

        // Scan for agent files
        const cards = scanDirectory(dir, config.parser.excludePatterns);

        if (cards.length === 0) {
            console.log(chalk.yellow('No agent files found in the directory.'));
            console.log(chalk.dim('Looking for .md files with YAML frontmatter, .yaml, or .json files.'));
            process.exit(0);
        }

        // Generate art for all cards (unless --no-art)
        if (options.art !== false) {
            const artGenerator = new ArtGenerator(config);
            console.log(chalk.dim(`Generating art for ${cards.length} agents...`));
            for (const card of cards) {
                if (!card.artUrl) {
                    card.artUrl = await artGenerator.generateArt(card) || undefined;
                }
            }
        }

        if (options.ascii) {
            // ASCII output
            console.log(renderBattlefieldAscii(cards));
        } else {
            // HTML output
            const html = renderBattlefieldHtml(cards);

            if (options.output) {
                const outputPath = path.resolve(options.output);
                fs.writeFileSync(outputPath, html);
                console.log(chalk.green(`✓ Battlefield saved to ${outputPath}`));
                console.log(chalk.dim(`  ${cards.length} agents included`));
            } else {
                // Save to temp and open
                const tempPath = path.join(require('os').tmpdir(), `mtg-battlefield-${Date.now()}.html`);
                fs.writeFileSync(tempPath, html);
                console.log(chalk.green(`✓ Battlefield generated with ${cards.length} agents`));
                await open(tempPath);
            }
        }
    });

// ============================================================
// CONFIG COMMAND - Manage configuration
// ============================================================
const configCmd = program
    .command('config')
    .description('Manage configuration');

configCmd
    .command('show')
    .description('Show current configuration')
    .action(() => {
        const config = loadConfig();
        const configFile = findConfigFile();
        if (configFile) {
            console.log(chalk.dim(`Config file: ${configFile}\n`));
        } else {
            console.log(chalk.dim('Using default configuration\n'));
        }
        printConfig(config);
    });

configCmd
    .command('init')
    .description('Initialize configuration file')
    .option('--global', 'Create in home directory')
    .action((options) => {
        const existing = findConfigFile();
        if (existing && !options.global) {
            console.log(chalk.yellow(`Config file already exists: ${existing}`));
            return;
        }

        const configPath = saveConfig(defaultConfig, options.global);
        console.log(chalk.green(`✓ Configuration created: ${configPath}`));
    });

configCmd
    .command('get <key>')
    .description('Get a configuration value')
    .action((key: string) => {
        const config = loadConfig();
        const value = getConfigValue(config, key);
        if (value === undefined) {
            console.log(chalk.red(`Key not found: ${key}`));
        } else {
            console.log(typeof value === 'object' ? JSON.stringify(value, null, 2) : value);
        }
    });

configCmd
    .command('set <key> <value>')
    .description('Set a configuration value')
    .option('--global', 'Set in global config')
    .action((key: string, value: string, options) => {
        let config = loadConfig();

        // Parse value
        let parsedValue: any = value;
        if (value === 'true') parsedValue = true;
        else if (value === 'false') parsedValue = false;
        else if (!isNaN(Number(value))) parsedValue = Number(value);

        config = setConfigValue(config, key, parsedValue);
        const configPath = saveConfig(config, options.global);
        console.log(chalk.green(`✓ Set ${key} = ${value}`));
        console.log(chalk.dim(`  Saved to ${configPath}`));
    });

// ============================================================
// HELPER FUNCTIONS
// ============================================================

function scanDirectory(dir: string, excludePatterns: string[]): CardData[] {
    const cards: CardData[] = [];

    function scan(currentDir: string) {
        if (!fs.existsSync(currentDir)) return;

        const entries = fs.readdirSync(currentDir, { withFileTypes: true });

        for (const entry of entries) {
            const fullPath = path.join(currentDir, entry.name);

            // Check exclusions
            const shouldExclude = excludePatterns.some(pattern => {
                const simpleName = pattern.replace('/**', '').replace('**/', '');
                return entry.name === simpleName || fullPath.includes(simpleName);
            });

            if (shouldExclude) continue;

            if (entry.isDirectory()) {
                scan(fullPath);
            } else if (entry.isFile()) {
                const ext = path.extname(entry.name).toLowerCase();
                if (['.md', '.yaml', '.yml', '.json'].includes(ext)) {
                    try {
                        const card = parseFile(fullPath);
                        if (card) {
                            cards.push(card);
                        }
                    } catch {
                        // Skip files that can't be parsed
                    }
                }
            }
        }
    }

    scan(dir);
    return cards;
}

// Run CLI
program.parse();

// Show help if no command
if (!process.argv.slice(2).length) {
    console.log(chalk.bold.cyan('\n  🎴 MTG Agent Visualizer\n'));
    console.log(chalk.dim('  Visualize your AI agents as Magic: The Gathering cards\n'));
    program.outputHelp();
}
