import * as vscode from 'vscode';
import * as path from 'path';
import * as fs from 'fs';
import { CardData, ManaColor, parseMarkdownAgent } from './parser';
import { generateCardHtml, generateBattlefieldHtml } from './cardRenderer';
import { ArtGenerator } from './artGenerator';

let battlefieldPanel: vscode.WebviewPanel | undefined;
const artGenerator = new ArtGenerator();

export function activate(context: vscode.ExtensionContext) {
    console.log('MTG Agent Visualizer is now active');

    // Register commands
    context.subscriptions.push(
        vscode.commands.registerCommand('mtgAgent.visualizeCard', (uri?: vscode.Uri) => {
            visualizeCard(context, uri);
        }),

        vscode.commands.registerCommand('mtgAgent.generateDeck', () => {
            generateDeck(context);
        }),

        vscode.commands.registerCommand('mtgAgent.openBattlefield', () => {
            openBattlefield(context);
        }),

        vscode.commands.registerCommand('mtgAgent.clearCache', () => {
            clearCache();
        })
    );
}

async function visualizeCard(context: vscode.ExtensionContext, uri?: vscode.Uri) {
    // Get the file to visualize
    let filePath: string;
    if (uri) {
        filePath = uri.fsPath;
    } else if (vscode.window.activeTextEditor) {
        filePath = vscode.window.activeTextEditor.document.uri.fsPath;
    } else {
        vscode.window.showErrorMessage('No file selected');
        return;
    }

    // Check if it's a markdown file
    if (!filePath.endsWith('.md')) {
        vscode.window.showErrorMessage('Please select a markdown agent file');
        return;
    }

    // Parse the agent
    const content = fs.readFileSync(filePath, 'utf-8');
    const card = parseMarkdownAgent(content, filePath);

    if (!card) {
        vscode.window.showErrorMessage('Could not parse agent from file');
        return;
    }

    // Generate art
    const artPath = await artGenerator.generateArt(card, context);
    const cardWithArt = { ...card, artUrl: artPath };

    // Show card in webview
    const panel = vscode.window.createWebviewPanel(
        'mtgCard',
        card.name,
        vscode.ViewColumn.Beside,
        {
            enableScripts: true,
            localResourceRoots: [
                vscode.Uri.file(context.extensionPath),
                vscode.Uri.file(path.dirname(artPath || ''))
            ]
        }
    );

    panel.webview.html = generateCardHtml(cardWithArt, panel.webview, context);
}

async function generateDeck(context: vscode.ExtensionContext) {
    const workspaceFolders = vscode.workspace.workspaceFolders;
    if (!workspaceFolders) {
        vscode.window.showErrorMessage('No workspace folder open');
        return;
    }

    await vscode.window.withProgress({
        location: vscode.ProgressLocation.Notification,
        title: 'Generating Agent Deck',
        cancellable: true
    }, async (progress, token) => {
        const agents: CardData[] = [];

        // Find all markdown files
        const files = await vscode.workspace.findFiles('**/*.md', '**/node_modules/**');

        for (let i = 0; i < files.length; i++) {
            if (token.isCancellationRequested) break;

            const file = files[i];
            progress.report({
                message: `Scanning ${path.basename(file.fsPath)}...`,
                increment: (100 / files.length)
            });

            const content = fs.readFileSync(file.fsPath, 'utf-8');
            const card = parseMarkdownAgent(content, file.fsPath);

            if (card) {
                const artPath = await artGenerator.generateArt(card, context);
                agents.push({ ...card, artUrl: artPath });
            }
        }

        if (agents.length > 0) {
            vscode.window.showInformationMessage(`Found ${agents.length} agent(s)`);
            openBattlefieldWithAgents(context, agents);
        } else {
            vscode.window.showWarningMessage('No agents found in workspace');
        }
    });
}

function openBattlefield(context: vscode.ExtensionContext) {
    // For now, trigger a deck generation which opens the battlefield
    generateDeck(context);
}

function openBattlefieldWithAgents(context: vscode.ExtensionContext, agents: CardData[]) {
    if (battlefieldPanel) {
        battlefieldPanel.reveal();
    } else {
        battlefieldPanel = vscode.window.createWebviewPanel(
            'mtgBattlefield',
            'MTG Agent Battlefield',
            vscode.ViewColumn.One,
            {
                enableScripts: true,
                retainContextWhenHidden: true,
                localResourceRoots: [vscode.Uri.file(context.extensionPath)]
            }
        );

        battlefieldPanel.onDidDispose(() => {
            battlefieldPanel = undefined;
        });
    }

    battlefieldPanel.webview.html = generateBattlefieldHtml(agents, battlefieldPanel.webview, context);
}

async function clearCache() {
    const cacheDir = path.join(require('os').tmpdir(), 'mtg-agent-art');

    if (fs.existsSync(cacheDir)) {
        const files = fs.readdirSync(cacheDir);
        const confirm = await vscode.window.showWarningMessage(
            `Delete ${files.length} cached art file(s)?`,
            { modal: true },
            'Delete'
        );

        if (confirm === 'Delete') {
            fs.rmSync(cacheDir, { recursive: true, force: true });
            vscode.window.showInformationMessage('Art cache cleared. Run "Generate Agent Deck" to regenerate.');
        }
    } else {
        vscode.window.showInformationMessage('Cache is already empty.');
    }
}

export function deactivate() {}
