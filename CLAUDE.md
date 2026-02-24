# MTG Agent Visualizer Plugin

A JetBrains Rider plugin that visualizes AI agents as Magic: The Gathering cards with AI-generated artwork.

## Project Structure

```
MtgAgentsPlugin/
├── plugins/
│   ├── rider/                       # JetBrains Rider plugin (Kotlin)
│   │   ├── src/main/kotlin/com/mtgagents/
│   │   │   ├── actions/             # IDE actions (menu items)
│   │   │   ├── art/                 # Art generation (SD, Stability AI, fallback)
│   │   │   ├── model/               # Data models (CardData, ManaColor, etc.)
│   │   │   ├── parser/              # Agent file parsers
│   │   │   └── settings/            # Plugin settings
│   │   └── src/main/resources/
│   │       ├── art/fallback/        # Bundled fallback art per color
│   │       ├── icons/mana/          # Mana symbol PNGs
│   │       └── META-INF/plugin.xml  # Plugin manifest
│   │
│   └── vscode/                      # VS Code extension (TypeScript)
│       ├── src/
│       │   ├── extension.ts         # Main entry point
│       │   ├── parser.ts            # Agent file parser
│       │   ├── artGenerator.ts      # Art generation
│       │   └── cardRenderer.ts      # HTML generation
│       ├── resources/
│       │   ├── art/fallback/        # Bundled fallback art
│       │   └── icons/mana/          # Mana symbol PNGs
│       └── package.json             # Extension manifest
│
├── scripts/
│   └── generate-fallback-art.sh     # Script to regenerate fallback art
├── test-fixtures/
│   └── sample-agents/               # Example agent markdown files
└── references/                      # (gitignored) Reference images
```

## Key Technologies

- **Language**: Kotlin
- **IDE Platform**: IntelliJ Platform SDK (Rider)
- **UI Rendering**: JCEF (Chromium) with inline HTML/CSS
- **Art Generation**: Stable Diffusion via AUTOMATIC1111 REST API

## Agent Definition Format (Markdown)

Agents are defined in Markdown files with YAML frontmatter:

```yaml
---
name: agent-id
display_name: Display Name
description: What this agent does
model: sonnet | opus | gpt-4
color: blue | white | black | red | green | (comma-separated for multi-color)

# Card customization
mana_cost: "{2}{U}{U}"
power: 3
toughness: 4
type_line: Legendary Creature — AI Type Subtype
flavor_text: Flavor text for the card
card_style: standard | borderless

# Art generation hints
creature_type: description of the creature for art generation
art_style: art style keywords
art_prompt: full custom prompt for Stable Diffusion
---

Agent system prompt content here...
```

## Card Styles

- **standard**: Traditional MTG card frame with bordered art box
- **borderless**: Full-bleed art with floating text overlay (like alt-art cards)

## Agent Relationships (Battlefield View)

The battlefield shows connections between agents based on references in their body text.

**Detection Patterns** (MarkdownParser.kt):
The parser looks for these patterns in agent body text:
- "use the X agent"
- "invoke the X agent"
- "call the X agent"
- "the X agent"

**Relationship Types**:
| Type | Visual | Description |
|------|--------|-------------|
| HANDOFF | Solid green line | Agent explicitly passes control |
| TOOL_CALL | Blue dashed line | Agent uses another as a tool |
| SUB_AGENT | Orange thick line | Hierarchical parent-child |
| REFERENCE | Gray dotted line | Agent mentions another |

**Adding Relationships**:
In your agent markdown, add text like:
```markdown
## Agent Collaboration

When dashboards need design refinement, use the ui-owner agent for visual consistency.
Before finalizing code, invoke the code-reviewer agent to ensure quality.
```

## Mana Colors

| Color | Letter | Model Mapping |
|-------|--------|---------------|
| White | W | - |
| Blue | U | claude/sonnet |
| Black | B | - |
| Red | R | - |
| Green | G | - |
| Multi | Gold | Multiple colors specified |

## Art Generation

Art generation uses a fallback chain:

1. **Local Stable Diffusion** (if available at configured URL)
2. **Stability AI Cloud API** (if API key configured in settings)
3. **Bundled fallback art** (generic art per mana color)

### Local SD Configuration
- Default: AUTOMATIC1111 at `http://127.0.0.1:7860`
- Also supports ComfyUI backend

### Cloud API (Stability AI)
- Configure in **Settings → Tools → MTG Agent Visualizer**
- Get API key at [platform.stability.ai](https://platform.stability.ai)
- Uses SDXL 1.0 model, 1024x1024 resolution

### Bundled Fallback Art
When no art generation is available, uses pre-generated art per color:
| Color | Creature |
|-------|----------|
| White | Knight |
| Blue | Scholar |
| Black | Vampire |
| Red | Goblin |
| Green | Elf |

### Cache
- Directory: `System.getProperty("java.io.tmpdir")/mtg-agent-art/`
  - macOS: `/var/folders/.../T/mtg-agent-art/`
- To force art regeneration, delete the cache directory

### Art Style Settings (for traditional MTG look)

Default settings optimized for painterly, traditional art style with sharp detail:
- **CFG Scale**: 6.5 (balanced: painterly but defined)
- **Steps**: 40 (more refinement passes)
- **Sampler**: DPM++ 2M Karras
- **Size**: 768x768 (higher resolution for detail)
- **Hi-Res Fix**: Enabled (1.5x upscale, 15 second-pass steps, 0.4 denoising)

The prompts emphasize:
- Traditional oil painting with confident brushstrokes
- Sharp defined edges, clear focal point, crisp details
- Reference classic fantasy artists (Donato Giancola, Terese Nielsen, Todd Lockwood, Michael Whelan)
- Color-specific atmospheric lighting per mana color

### Tips for Better Art
- Use descriptive `creature_type` in agent metadata
- Add `art_style` for specific looks (e.g., "dark fantasy", "ethereal")
- Custom `art_prompt` gives full control over the image
- Recommended model: DreamShaper XL or similar painterly checkpoint

## Building & Running

```bash
cd rider-plugin
export JAVA_HOME="/opt/homebrew/opt/openjdk@17"
./gradlew runIde
```

## IDE Actions

- **Tools → Generate Agent Deck**: Scans project for agents, generates art for all
- **Tools → Open Agent Battlefield**: Opens the battlefield view showing all agents
- **Right-click file → Visualize Agent as MTG Card**: Shows single card dialog

## Debugging

Log output appears in the Run window of the main IDE (where `./gradlew runIde` is running).
Key log prefixes:
- `MTG GenerateDeck:` - Deck generation progress
- `MTG ArtGenerator:` - Art generation and caching
- `MTG Art Generator:` - SD API calls

## Dependencies

- Stable Diffusion with AUTOMATIC1111 WebUI running on port 7860
- Recommended model: DreamShaper XL for painterly MTG-style art
