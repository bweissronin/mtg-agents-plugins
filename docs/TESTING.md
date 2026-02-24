# MTG Agent Visualizer - Testing Guide

This guide explains how to build, run, and test the MTG Agent Visualizer plugin for JetBrains Rider.

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Building the Plugin](#building-the-plugin)
3. [Running the Plugin](#running-the-plugin)
4. [Testing with Sample Agents](#testing-with-sample-agents)
5. [Testing Features](#testing-features)
6. [Setting Up Stable Diffusion (Optional)](#setting-up-stable-diffusion-optional)
7. [Troubleshooting](#troubleshooting)

---

## Prerequisites

### Required Software

1. **JDK 17 or higher**
   ```bash
   # Check your Java version
   java -version

   # On macOS with Homebrew
   brew install openjdk@17
   ```

2. **JetBrains Rider 2023.3 or later**
   - Download from: https://www.jetbrains.com/rider/download/

3. **Gradle** (bundled with the project)
   - The project uses the Gradle wrapper, so no separate installation is needed

### Optional (for AI Art Generation)

4. **AUTOMATIC1111 Stable Diffusion WebUI**
   - For generating unique card art locally
   - See [Setting Up Stable Diffusion](#setting-up-stable-diffusion-optional)

---

## Building the Plugin

### 1. Clone/Navigate to the Project

```bash
cd /Users/brianweiss/RiderProjects/MtgAgentsPlugin
```

### 2. Build the Plugin

```bash
# Navigate to the rider-plugin directory
cd rider-plugin

# Build using Gradle wrapper
./gradlew build

# Or on Windows
gradlew.bat build
```

### 3. Build the Plugin Distribution

```bash
# Create the plugin ZIP for installation
./gradlew buildPlugin
```

The plugin ZIP will be created at:
```
rider-plugin/build/distributions/mtg-agent-visualizer-1.0.0.zip
```

---

## Running the Plugin

### Option 1: Run in Development Mode (Recommended for Testing)

This launches a sandboxed Rider instance with the plugin loaded:

```bash
cd rider-plugin
./gradlew runIde
```

**Note:** The first run may take a few minutes as it downloads the Rider sandbox.

### Option 2: Install the Built Plugin

1. Build the plugin distribution (see above)
2. In Rider, go to **Settings → Plugins → ⚙️ → Install Plugin from Disk...**
3. Select the ZIP file from `build/distributions/`
4. Restart Rider

---

## Testing with Sample Agents

The project includes sample agent files for testing in `test-fixtures/sample-agents/`:

### 1. Open the Test Fixtures Project

1. In the sandboxed Rider instance (or your main Rider with the plugin installed)
2. Go to **File → Open**
3. Navigate to and open: `MtgAgentsPlugin/test-fixtures/`

### 2. Available Test Files

| File | Framework | Agents Included |
|------|-----------|-----------------|
| `langchain-agents.ts` | LangChain | Research Agent, Code Assistant, Data Analyst, API Integration Agent, Workflow Graph |
| `openai-agents.ts` | OpenAI Agents SDK | Research Specialist, Code Generator, Data Analyst, Notification Manager, Project Orchestrator, Customer Support |
| `crewai-agents.ts` | CrewAI | Senior Researcher, Technical Writer, Data Engineer, Quality Reviewer, Project Manager, Marketing Team |
| `generic-agents.ts` | Generic/Custom | Various agent patterns using OpenAI/Anthropic directly |

### 3. Visualize Individual Agents

1. Open any of the test files
2. Place your cursor on an agent definition (e.g., `const researchAgent = ...`)
3. Right-click and select **"Visualize as MTG Card"**
   - Or use keyboard shortcut: `Ctrl+Alt+M` (Windows/Linux) / `Cmd+Alt+M` (macOS)
4. A dialog should appear showing the MTG card

### 4. Generate Full Deck

1. With the test-fixtures project open
2. Go to **Tools → Generate Agent Deck**
   - Or use keyboard shortcut: `Ctrl+Alt+D`
3. The plugin will scan all TypeScript files and show results

### 5. View Battlefield

1. Go to **Tools → Open Agent Battlefield**
   - Or use keyboard shortcut: `Ctrl+Alt+B`
2. The MTG Battlefield tool window will open on the right
3. Click "Refresh" to load agents from the current project

---

## Testing Features

### Feature Checklist

#### Card Visualization
- [ ] Right-click on agent → "Visualize as MTG Card" works
- [ ] Card displays correct name (from agent name/class)
- [ ] Mana cost reflects tool count
- [ ] Color identity matches agent purpose (Blue for reasoning, Red for speed, etc.)
- [ ] Abilities list shows tools/functions
- [ ] Flavor text shows system prompt (truncated to 120 chars)
- [ ] Power/Toughness shows correctly

#### Gallery View
- [ ] "Generate Agent Deck" finds all agents in project
- [ ] Gallery displays all cards in a grid
- [ ] Toggle between PNG and vector frames works

#### Battlefield View
- [ ] Battlefield shows all agents as mini-cards
- [ ] Relationships (handoffs, tool calls) show as connecting lines
- [ ] Cards are draggable
- [ ] Zoom in/out/reset buttons work
- [ ] Clicking a mini-card opens full card view

#### Parser Tests
- [ ] LangChain agents detected (AgentExecutor, createReactAgent)
- [ ] OpenAI Agents SDK agents detected (new Agent({...}))
- [ ] CrewAI agents detected (Agent with role/goal/backstory)
- [ ] Generic agents detected (classes with "Agent" in name + LLM usage)
- [ ] Handoffs detected from `handoffs: [...]` arrays
- [ ] Tool calls detected from code patterns

#### Settings
- [ ] Settings accessible via **Settings → Tools → MTG Agent Visualizer**
- [ ] SD backend URL configurable
- [ ] Vector frames toggle works
- [ ] Parser settings (infer tools, detect all calls) work

---

## Setting Up Stable Diffusion (Optional)

For AI-generated card art, you need a local Stable Diffusion installation.

### Installing AUTOMATIC1111

1. **Clone the repository:**
   ```bash
   git clone https://github.com/AUTOMATIC1111/stable-diffusion-webui.git
   cd stable-diffusion-webui
   ```

2. **Download DreamShaper XL (SDXL version):**

   > ⚠️ **Important:** There are two different DreamShaper models on Civitai. Make sure you get the **XL** version!

   - Go to: **https://civitai.com/models/112902/dreamshaper-xl** (note: this is different from the SD 1.5 version)
   - **How to verify it's SDXL:**
     - Page title says "DreamShaper XL"
     - Model type shows "Stable Diffusion XL Checkpoint"
     - File size is ~6.5 GB (SDXL models are larger than SD 1.5)
   - **Recommended variant:** DreamShaper XL **Lightning** or **Turbo** for faster generation
   - Download the `.safetensors` file
   - Place in `stable-diffusion-webui/models/Stable-diffusion/`

3. **Launch with API enabled:**
   ```bash
   # macOS/Linux
   ./webui.sh --api

   # Windows
   webui-user.bat --api
   ```

4. **Verify API is running:**
   - Open: http://localhost:7860
   - The web interface should load
   - Select your DreamShaper XL model from the checkpoint dropdown
   - API endpoint: http://localhost:7860/sdapi/v1/txt2img

### Configuring the Plugin

1. Go to **Settings → Tools → MTG Agent Visualizer**
2. Set **Backend** to "AUTOMATIC1111"
3. Set **API URL** to `http://localhost:7860`
4. Adjust generation settings as desired:
   - Steps: 20 (higher = better quality, slower; Lightning/Turbo can use fewer)
   - CFG Scale: 7.5 (how closely to follow the prompt)

### Recommended SDXL Settings

For best results with DreamShaper XL:
- **Resolution:** 1024x768 (keep one dimension at or near 1024)
- **Sampler:** DPM++ SDE Karras (not 2M)
- **Steps:** 20-30 for standard, 4-8 for Lightning/Turbo variants
- **No refiner needed** - DreamShaper XL handles high-res internally

### Without Stable Diffusion

If SD is not running, the plugin will:
- Show placeholder art (animated glowing orb)
- Continue to work for all other features
- Display a one-time notification about setting up SD

---

## Troubleshooting

### Plugin Doesn't Load

**Symptom:** Plugin not visible in Rider

**Solutions:**
1. Check Rider version is 2023.3+
2. Verify plugin is enabled: **Settings → Plugins → Installed**
3. Check for errors in **Help → Show Log in Finder/Explorer**

### "No Agent Found" Error

**Symptom:** Right-clicking shows "No AI agent found at this location"

**Solutions:**
1. Ensure cursor is on/near an agent definition
2. Check the file extension is `.ts` or `.tsx`
3. Verify the agent uses a supported pattern:
   - LangChain: `AgentExecutor`, `createReactAgent`, `StateGraph`
   - OpenAI: `new Agent({...})`
   - CrewAI: `new Agent({role: ...})`
   - Generic: class/object with "Agent" in name + OpenAI/Anthropic usage

### Art Generation Fails

**Symptom:** Cards show placeholder instead of generated art

**Solutions:**
1. Verify SD is running: http://localhost:7860
2. Check API mode: must be launched with `--api` flag
3. Check plugin settings: correct URL configured
4. Check SD console for errors
5. Ensure model is loaded in SD

### Battlefield Empty

**Symptom:** Battlefield view shows no agents

**Solutions:**
1. Click "Refresh" button
2. Run "Generate Agent Deck" first
3. Ensure project has TypeScript files
4. Check files are not in `node_modules/`

### Build Errors

**Symptom:** Gradle build fails

**Solutions:**
1. Ensure JDK 17+: `java -version`
2. Clear Gradle cache: `./gradlew clean`
3. Update Gradle wrapper: `./gradlew wrapper --gradle-version 8.5`
4. Check internet connection (needs to download IntelliJ SDK)

---

## Development Tips

### Hot Reload During Development

For faster iteration:
1. Run `./gradlew runIde` in one terminal
2. Make code changes
3. In the sandboxed IDE, use **Build → Rebuild Project** or restart

### Viewing Logs

```bash
# Plugin logs location
# macOS: ~/Library/Logs/JetBrains/Rider<version>/idea.log
# Windows: %USERPROFILE%\.Rider<version>\system\log\idea.log
# Linux: ~/.Rider<version>/system/log/idea.log
```

### Testing Webview Standalone

You can test the card rendering without the plugin:

1. Open `webview/card.html` in a browser
2. The example card will render
3. Open browser console to test JavaScript:
   ```javascript
   window.renderCard({
     name: "Test Agent",
     manaCost: "{2}{U}",
     colorIdentity: ["BLUE"],
     typeLine: "Legendary Creature — AI Agent",
     abilities: [{name: "Test", abilityType: "ACTIVATED"}],
     flavorText: "Test flavor text",
     power: 2,
     toughness: 1,
     setSymbol: "TEST"
   });
   ```

---

## Reporting Issues

If you encounter bugs:

1. Check the IDE log for errors
2. Note the steps to reproduce
3. Include:
   - Rider version
   - Plugin version
   - Sample code that fails
   - Error messages/stack traces
