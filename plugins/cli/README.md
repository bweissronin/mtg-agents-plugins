# MTG Agent Visualizer CLI

Visualize your AI agents as Magic: The Gathering cards from the command line.

## Installation

```bash
# From the cli directory
npm install
npm run build
npm link  # Makes 'mtg-agents' available globally
```

Or run directly:
```bash
node dist/cli.js <command>
```

## Commands

### Generate a Single Card

```bash
# Open card in browser
mtg-agents card ./agents/researcher.md

# ASCII art in terminal
mtg-agents card ./agents/researcher.md --format ascii

# Save to file
mtg-agents card ./agents/researcher.md --output ./card.html
```

### View Battlefield (All Agents)

```bash
# Open battlefield in browser
mtg-agents battlefield ./agents/

# ASCII summary in terminal
mtg-agents battlefield ./agents/ --ascii

# Start dev server with hot-reload
mtg-agents battlefield ./agents/ --serve
mtg-agents battlefield ./agents/ --serve --port 8080
```

### Configuration

```bash
# Show current config
mtg-agents config show

# Initialize config file
mtg-agents config init          # In current directory
mtg-agents config init --global # In home directory

# Get/set values
mtg-agents config get art.source
mtg-agents config set art.source stability
mtg-agents config set style.cardStyle borderless
```

## Configuration File

Create `.mtg-agents.json` in your project or `~/.config/mtg-agents/config.json` globally:

```json
{
  "art": {
    "source": "fallback",
    "stabilityApiKey": "sk-xxxxx",
    "cacheDir": "~/.cache/mtg-agents/art"
  },
  "style": {
    "cardStyle": "borderless",
    "theme": "dark"
  },
  "output": {
    "format": "html",
    "openBrowser": true
  },
  "server": {
    "port": 3000,
    "hotReload": true
  }
}
```

## Environment Variables

```bash
export MTG_AGENTS_ART_SOURCE=stability
export MTG_AGENTS_STABILITY_KEY=sk-xxxxx
export MTG_AGENTS_CARD_STYLE=borderless
export MTG_AGENTS_SERVER_PORT=8080
```

## Agent File Format

The CLI parses markdown files with YAML frontmatter:

```markdown
---
name: researcher
description: Research and analysis agent
model: sonnet
color: blue

# Optional customizations
display_name: The Researcher
mana_cost: "{2}{U}{U}"
power: 3
toughness: 4
flavor_text: "Knowledge is the ultimate weapon."
card_style: borderless
---

## Capabilities

### Web Search
Search the internet for relevant information.

### Data Analysis
Analyze and summarize complex datasets.
```

Also supports `.yaml` and `.json` files with similar structure.

## Output Formats

### HTML (Default)
Opens in your default browser. Interactive battlefield view with click-to-zoom.

### ASCII
Quick preview in terminal. Great for SSH sessions or quick checks.

```
┌──────────────────────────────────────┐
│ Researcher Agent             {2}{U}  │
├──────────────────────────────────────┤
│  ░░░░░▓▓▓▓▓▓▓▓▓▓▓░░░░░              │
│  ░░░▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓░░░              │
├──────────────────────────────────────┤
│ Legendary Creature — AI Agent        │
├──────────────────────────────────────┤
│ ◆ Web Search                         │
│ ◆ Data Analysis                      │
├──────────────────────────────────────┤
│                                 3/2  │
└──────────────────────────────────────┘
```

## Dev Server

Start a local server with hot-reload for development:

```bash
mtg-agents battlefield ./agents/ --serve
```

- Opens `http://localhost:3000` automatically
- Watches for file changes
- Auto-refreshes browser when agents are modified

## Examples

```bash
# Quick ASCII preview of all agents
mtg-agents battlefield . --ascii

# Generate documentation images
mtg-agents battlefield ./agents/ --output ./docs/agents.html

# Development mode while editing agents
mtg-agents battlefield ./agents/ --serve

# CI/CD: Generate static HTML
mtg-agents battlefield ./agents/ --output ./artifacts/battlefield.html
```

## Library Usage

You can also use it as a library:

```typescript
import { parseFile, renderCardHtml, renderCardAscii } from 'mtg-agent-visualizer';

const card = parseFile('./agent.md');
console.log(renderCardAscii(card));
```
