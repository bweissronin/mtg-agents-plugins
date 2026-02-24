# MTG Agent Visualizer - Installation Guide

A JetBrains Rider plugin that visualizes your AI agents as Magic: The Gathering cards with generated artwork.

## Installation

### Prerequisites
- JetBrains Rider 2024.1 or later

### Step 1: Get the Plugin File

Download the latest `.zip` file from the releases.

### Step 2: Install in Rider

1. Open Rider
2. Go to **Settings** (Cmd+, on macOS, Ctrl+Alt+S on Windows/Linux)
3. Navigate to **Plugins**
4. Click the **gear icon** ⚙️ → **Install Plugin from Disk...**
5. Select the `.zip` file
6. Click **OK** and restart Rider when prompted

## Usage

### View All Agents as Cards
1. Open your project containing AI agent definitions
2. Go to **Tools → Generate Agent Deck**
3. The **MTG Battlefield** tool window opens showing all your agents as cards

### View Single Agent
1. Right-click on an agent markdown file
2. Select **Visualize Agent as MTG Card**

## Agent Definition Format

Create `.md` files with YAML frontmatter:

```yaml
---
name: my-agent
display_name: My Agent
description: What this agent does
model: sonnet
color: blue

# Card customization (optional)
mana_cost: "{2}{U}{U}"
power: 3
toughness: 4
type_line: Legendary Creature — AI Wizard
flavor_text: "Knowledge is power."
card_style: borderless

# Art hints (optional)
creature_type: wise wizard with glowing staff
art_style: mystical, ethereal blue light
art_prompt: A powerful wizard in flowing robes...
---

You are an AI agent that...
```

## Custom Art Generation (Optional)

By default, the plugin uses bundled fantasy art based on your agent's color:

| Color | Art |
|-------|-----|
| White | Knight |
| Blue | Scholar |
| Black | Vampire |
| Red | Goblin |
| Green | Elf |

### Enable Custom AI-Generated Art

To generate unique art for each agent:

1. Get an API key at [platform.stability.ai](https://platform.stability.ai)
2. In Rider: **Settings → Tools → MTG Agent Visualizer**
3. Set **Provider** to "Stability AI"
4. Enter your API key
5. Regenerate your deck with **Tools → Generate Agent Deck**

Custom art uses your agent's `creature_type`, `art_style`, and `art_prompt` fields to generate unique artwork.

## Configuration

**Settings → Tools → MTG Agent Visualizer**

| Setting | Description |
|---------|-------------|
| Cloud Provider | None (bundled art) or Stability AI |
| Stability AI Key | Your API key for custom art |
| Generation Steps | Art quality vs speed (20-50) |
| CFG Scale | How closely art follows the prompt (5-8) |

## Clearing the Art Cache

Generated artwork is cached to avoid regenerating images. To force new art generation:

1. Go to **Settings → Tools → MTG Agent Visualizer**
2. Click **Clear Art Cache**
3. Run **Tools → Generate Agent Deck** to regenerate

## Troubleshooting

### Cards show placeholder instead of art
- The bundled fallback art should always work
- Check Rider's event log for errors (**View → Tool Windows → Event Log**)

### Custom art not generating
- Verify your Stability AI API key is correct
- Check you have API credits remaining at [platform.stability.ai](https://platform.stability.ai)

### Want to regenerate art for an agent?
- Go to **Settings → Tools → MTG Agent Visualizer** and click **Clear Art Cache**
- Run **Tools → Generate Agent Deck**

### Plugin not showing in Tools menu
- Restart Rider after installation
- Check **Settings → Plugins** that it's enabled

## Support

- Issues: [GitHub Issues](<repository-url>/issues)
