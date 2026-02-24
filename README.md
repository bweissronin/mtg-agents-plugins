# MTG Agent Visualizer

**Visualize your AI agents as Magic: The Gathering cards!**

IDE plugins that parse AI agent definitions from your codebase and render each agent as a visually accurate MTG card — complete with AI-generated artwork, mana cost, abilities, flavor text, and a "Battlefield" relationship board.

## Supported IDEs

| IDE | Status | Install |
|-----|--------|---------|
| **JetBrains Rider** | ✅ Ready | [plugins/rider](plugins/rider) |
| **VS Code** | ✅ Ready | [plugins/vscode](plugins/vscode) |

## Features

- **MTG Card Visualization** — Each agent rendered as a pixel-perfect MTG card
- **AI-Generated Art** — Custom art via Stability AI, or bundled fallback art
- **Smart Parsing** — Detects agents from Markdown, LangChain, OpenAI Agents SDK, and CrewAI
- **Battlefield View** — See all agents and their relationships as a connected graph
- **Borderless Cards** — Support for alt-art style cards
- **Works Offline** — Bundled fallback art means no API keys required

## Quick Start

### Installation

See [INSTALL.md](docs/INSTALL.md) for detailed installation instructions.

**Quick version:**
1. Download the plugin for your IDE from releases
2. Install from disk (Rider) or VSIX (VS Code)
3. Restart your IDE

### Usage

1. **Create an agent file** (`.md` with YAML frontmatter):
   ```yaml
   ---
   name: my-agent
   display_name: My Agent
   model: sonnet
   color: blue
   card_style: borderless
   ---

   You are an AI agent that...
   ```

2. **Visualize:**
   - Right-click the file → **"Visualize as MTG Card"**
   - Or use **Tools → Generate Agent Deck** to scan all agents

## Card Anatomy

| MTG Element | Derived From |
|-------------|--------------|
| **Card Name** | `display_name` or `name` field |
| **Mana Cost** | `mana_cost` or calculated from abilities |
| **Color Identity** | `color` field (white, blue, black, red, green) |
| **Type Line** | `type_line` or generated |
| **Abilities** | Extracted from markdown headers |
| **Flavor Text** | `flavor_text` or first paragraph |
| **Power/Toughness** | `power`/`toughness` or calculated |
| **Art** | AI-generated or color-based fallback |

## Custom Art Generation (Optional)

By default, cards use bundled fantasy art based on color. For custom AI-generated art:

1. Get an API key at [platform.stability.ai](https://platform.stability.ai)
2. Configure in your IDE's settings
3. Regenerate your deck

See [INSTALL.md](docs/INSTALL.md) for details.

## Project Structure

```
MtgAgentsPlugin/
├── plugins/
│   ├── rider/          # JetBrains Rider plugin (Kotlin)
│   └── vscode/         # VS Code extension (TypeScript)
├── releases/           # Pre-built downloadable plugins
├── docs/               # Documentation (INSTALL, PUBLISHING, TESTING)
├── test-fixtures/      # Sample agent files
├── scripts/            # Build utilities
└── CLAUDE.md           # Development documentation
```

## Building from Source

### Rider Plugin
```bash
cd plugins/rider
./gradlew build
./gradlew runIde        # Run in dev mode
./gradlew buildPlugin   # Create ZIP
```

### VS Code Extension
```bash
cd plugins/vscode
npm install
npm run compile
npm run package         # Create VSIX
```

## License

MIT License

## Acknowledgments

- Magic: The Gathering is © Wizards of the Coast. This project is unofficial and not affiliated with Wizards.
