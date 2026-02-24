# MTG Agent Visualizer for VS Code

Visualize your AI agents as Magic: The Gathering cards with generated artwork.

## Features

- **Visualize Agent as Card** - Right-click any `.md` agent file to see it as an MTG card
- **Generate Agent Deck** - Scan your workspace for all agents
- **Agent Battlefield** - View all agents as draggable cards with relationship lines
- **AI Art Generation** - Optional custom art via Stability AI

## Installation

### From VSIX (Local Install)
1. Download the `.vsix` file from releases
2. In VS Code: `Ctrl+Shift+P` → "Install from VSIX"
3. Select the downloaded file

### From Marketplace
Search for "MTG Agent Visualizer" in the Extensions view.

## Usage

### Commands
- `MTG Agent: Visualize as MTG Card` - Show current file as a card
- `MTG Agent: Generate Agent Deck` - Scan workspace for all agents
- `MTG Agent: Open Agent Battlefield` - View all agents on the battlefield
- `MTG Agent: Clear Art Cache` - Delete cached art to regenerate

### Agent Format
Create `.md` files with YAML frontmatter:

```yaml
---
name: my-agent
display_name: My Agent
description: What this agent does
model: sonnet
color: blue
card_style: borderless
---

Your agent instructions here...
```

## Settings

| Setting | Description | Default |
|---------|-------------|---------|
| `mtgAgent.cloudProvider` | Art generation provider | `none` |
| `mtgAgent.stabilityApiKey` | Stability AI API key | |
| `mtgAgent.artGenerationSteps` | Generation quality (10-50) | `40` |
| `mtgAgent.cfgScale` | Prompt adherence (1-20) | `6.5` |

## Custom Art Generation

By default, cards use bundled fallback art. For custom AI-generated art:

1. Get an API key at [platform.stability.ai](https://platform.stability.ai)
2. Open VS Code Settings (`Ctrl+,`)
3. Search for "MTG Agent"
4. Set **Cloud Provider** to "Stability AI"
5. Enter your API key

## Building from Source

```bash
cd plugins/vscode
npm install
npm run compile
npm run package  # Creates .vsix file
```

## License

MIT
