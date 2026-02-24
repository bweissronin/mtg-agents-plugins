# MTG Agent Visualizer — Plugin Development Plan
> **Free-tier, local-first approach** | VS Code + JetBrains Rider

---

## 🎯 Objective

Build an IDE plugin for **Visual Studio Code** and **JetBrains Rider** that parses AI agent definitions from a developer's codebase and renders each agent as a visually accurate **Magic: The Gathering card** — complete with AI-generated artwork (fully local, free), mana cost, abilities, flavor text, and a "Battlefield" relationship board showing the full agent graph.

No cloud dependencies. No API keys. No cost to run.

---

## ✅ Success Criteria

A successful delivery means a developer can:

1. **Right-click any agent class/function** in their code → select "Visualize as MTG Card" → see a fully rendered card within 5 seconds (excluding first-time art generation).
2. **Open a "Battlefield" panel** showing all agents in the project laid out as a connected MTG board, with arrows representing handoff/tool-call relationships.
3. **Export any card or the full board** as a PNG file.
4. **Generate unique AI art** for each card locally, with no internet connection required, using Stable Diffusion via AUTOMATIC1111 or ComfyUI running on `localhost`.
5. Cards are **semantically accurate** — the card type, color, power/toughness, abilities, and flavor text are all derived meaningfully from the agent's actual code and purpose.
6. The plugin works against at least **3 major frameworks**: LangChain, OpenAI Agents SDK, and CrewAI.
7. The **same rendering engine** (HTML/Canvas) runs in both VS Code (WebviewPanel) and Rider (JCEF).

---

## 🗂️ Card Anatomy — Agent Mapping Reference

| MTG Card Element     | Agent Property Derived From                                                  |
|----------------------|-------------------------------------------------------------------------------|
| **Card Name**        | Agent class name / `name=` constructor arg                                   |
| **Mana Cost**        | Tool count (complexity proxy) — more tools = higher mana cost                |
| **Color Identity**   | Domain: Blue=reasoning, Red=speed/reactive, Green=data, Black=external APIs, White=orchestration |
| **Card Type Line**   | `Legendary Creature — AI Agent [FrameworkName]`                              |
| **Art Box**          | Locally generated image via SD prompt built from agent description           |
| **Abilities Text**   | Each `@tool` / registered function = one ability line                        |
| **Flavor Text**      | First 120 chars of system prompt, italicized                                 |
| **Power**            | Number of tools the agent can invoke                                         |
| **Toughness**        | Max retries or fallback depth (defaults to 1 if not set)                     |
| **Set Symbol**       | Project/repository name (auto-detected from workspace root)                  |
| **Collector Info**   | LLM model name (e.g., `gpt-4o`, `claude-3-5-sonnet`) + framework version    |

---

## 🏗️ Architecture Overview

```
┌─────────────────────────────────────────────────────────┐
│                     IDE Plugin Layer                    │
│   VS Code Extension (TypeScript)  │  Rider Plugin (Kotlin) │
│   - Command palette integration   │  - AnAction + JCEF     │
│   - WebviewPanel host             │  - Tool Window host     │
└────────────────────┬────────────────────────────────────┘
                     │  shared webview bundle (HTML/JS/CSS)
┌────────────────────▼────────────────────────────────────┐
│                  Card Engine (Browser JS)               │
│   Parser → CardBuilder → ArtClient → CardRenderer      │
│   Konva.js canvas   |   mana-font SVG   |   Beleren font │
└────────────────────┬────────────────────────────────────┘
                     │  HTTP to localhost
┌────────────────────▼────────────────────────────────────┐
│         Local Art Generation (Stable Diffusion)         │
│   AUTOMATIC1111 or ComfyUI  →  REST API :7860 / :8188   │
│   Fantasy art LoRA + MTG-style prompt template          │
└─────────────────────────────────────────────────────────┘
```

---

## 🔧 Technology Stack (All Free / Open Source)

| Layer              | Technology                          | Why                                           |
|--------------------|--------------------------------------|-----------------------------------------------|
| VS Code Plugin     | TypeScript, VS Code API             | Native extension model                        |
| Rider Plugin       | Kotlin, IntelliJ Platform SDK, JCEF | Native JetBrains model, JCEF = embedded Chrome |
| Card Rendering     | Konva.js (Canvas 2D)                | Lightweight, no deps, works in WebView        |
| Mana Symbols       | mana-font (SVG icon font)           | Open source, pixel-perfect MTG symbols        |
| Fonts              | Beleren (name), MPlantin (body)     | Authentic MTG typography, free for personal   |
| Code Parsing       | Tree-sitter (Python/TS/C# grammars) | Fast, accurate, runs in-process               |
| Art Generation     | AUTOMATIC1111 or ComfyUI            | Free, local, REST API                         |
| SD Model           | DreamShaper XL or SDXL base         | Best fantasy art quality, freely downloadable |
| Graph Layout       | d3-force (Battlefield view)         | Free, battle-tested force-directed graphs     |
| Export             | html2canvas → PNG                   | In-browser PNG export, no server needed       |

---

## 📅 Build Phases

### Phase 1 — Foundation (Weeks 1–2)
**Goal:** Skeleton plugin that shows a hardcoded card in a panel.

- [ ] Scaffold VS Code extension (`yo code`)
- [ ] Create `CardPanel.ts` — registers a WebviewPanel
- [ ] Build static HTML card template with all MTG zones (art box, name, type, text box, PT box)
- [ ] Load mana-font and Beleren/MPlantin fonts inside the webview
- [ ] Render a hardcoded "Hello Agent" card to confirm the layout is pixel-accurate
- [ ] Scaffold Rider plugin project with JCEF Tool Window loading the same HTML

**Deliverable:** Both IDEs can open a panel showing a static MTG card.

---

### Phase 2 — Code Parser (Weeks 3–4)
**Goal:** Extract real agent data from code.

- [ ] Integrate Tree-sitter with Python grammar (primary language for AI agents)
- [ ] Build `LangChainParser` — detects `AgentExecutor`, `@tool` decorators, `StateGraph.add_node()`
- [ ] Build `OpenAIAgentsParser` — detects `Agent(name=, instructions=, tools=[])`, `handoffs=[]`
- [ ] Build `CrewAIParser` — detects `@agent`, `Agent(role=, goal=, backstory=)`, `@task`
- [ ] Build `GenericParser` — heuristic fallback: finds classes with "agent" in name + LLM client usage
- [ ] Implement `CardBuilder` — maps parsed data to `CardData` JSON model
- [ ] Wire "right-click → Visualize" command to parser + card render pipeline

**Deliverable:** Right-clicking a LangChain agent renders a real card with its actual name, tools, and system prompt.

---

### Phase 3 — Art Generation (Weeks 5–6)
**Goal:** Unique local AI art per card.

- [ ] Build `ArtGenerator.ts` — constructs SD prompt from agent description:
  ```
  "[agent role/description], fantasy creature portrait, magic the gathering card art,
  dramatic lighting, intricate detail, by Raymond Swanland, 4k"
  ```
- [ ] Implement AUTOMATIC1111 client: `POST localhost:7860/sdapi/v1/txt2img`
- [ ] Implement ComfyUI fallback client: `POST localhost:8188/prompt`
- [ ] Add user setting to choose backend + base URL
- [ ] Cache generated art by agent name hash (avoid re-generating on each open)
- [ ] Add placeholder art (generic glowing orb) for when SD is not running
- [ ] Display art loading spinner in the card's art box during generation

**Deliverable:** Each card shows unique AI-generated art that fits the agent's purpose.

---

### Phase 4 — Gallery + Export (Week 7)
**Goal:** Multi-card view and PNG export.

- [ ] Build `gallery.html` — grid of all detected agents in the workspace rendered as cards
- [ ] Add command: `MTG: Generate Full Agent Deck` — scans entire project and renders gallery
- [ ] Implement PNG export per card using `html2canvas`
- [ ] Add "Export All as ZIP" — exports each card as a numbered PNG
- [ ] Add Set Symbol auto-detection from `package.json` / `.git` repo name

**Deliverable:** Developers can export their full agent roster as MTG card PNGs.

---

### Phase 5 — Battlefield View (Week 8)
**Goal:** The hero feature — the full agent graph as a MTG board.

- [ ] Integrate `d3-force` network layout
- [ ] Parse agent `handoffs`, `tools`, and sub-agent references to build an edge list
- [ ] Render mini-cards as nodes, directed arrows as edges (tool calls = dashed, handoffs = solid)
- [ ] Orchestrator agents float to top of the graph (like a Commander)
- [ ] Clicking any mini-card opens its full card view
- [ ] Add zoom/pan support for large graphs
- [ ] Port Battlefield view into Rider JCEF panel

**Deliverable:** Full visual agent relationship board that looks like a MTG battlefield.

---

### Phase 6 — Polish + Publish (Week 9–10)
**Goal:** Production-ready, published to both marketplaces.

- [ ] Settings panel: SD backend URL, color identity overrides per agent, font size
- [ ] Keyboard shortcuts for all commands
- [ ] Error states: agent not detected, SD offline, parse failure — all handled gracefully
- [ ] README with screenshots, GIF demo, setup instructions for AUTOMATIC1111
- [ ] Publish to VS Code Marketplace
- [ ] Publish to JetBrains Marketplace
- [ ] Write blog post / demo video

---

## 📁 Project Structure

```
mtg-agent-visualizer/
├── vscode-extension/
│   ├── src/
│   │   ├── extension.ts               ← entry point, command registration
│   │   ├── parser/
│   │   │   ├── LangChainParser.ts
│   │   │   ├── OpenAIAgentsParser.ts
│   │   │   ├── CrewAIParser.ts
│   │   │   └── GenericParser.ts
│   │   ├── card/
│   │   │   ├── CardBuilder.ts         ← parsed data → CardData model
│   │   │   ├── ArtGenerator.ts        ← SD prompt + API calls
│   │   │   └── CardRenderer.ts        ← injects CardData into webview
│   │   └── panels/
│   │       ├── CardPanel.ts           ← single card WebviewPanel
│   │       ├── GalleryPanel.ts        ← full deck WebviewPanel
│   │       └── BattlefieldPanel.ts    ← agent graph WebviewPanel
│   └── package.json
│
├── rider-plugin/
│   └── src/main/kotlin/
│       ├── MtgAgentAction.kt          ← right-click AnAction
│       ├── AgentParser.kt             ← PSI-based parser (reuses same logic)
│       └── MtgToolWindow.kt           ← JCEF panel loading shared webview
│
└── webview/                           ← SHARED by both plugins
    ├── card.html                      ← single card view
    ├── gallery.html                   ← deck grid view
    ├── battlefield.html               ← d3-force graph view
    ├── js/
    │   ├── konva.min.js
    │   ├── d3.min.js
    │   └── card-engine.js             ← core rendering logic
    ├── css/
    │   └── mtg-card.css
    ├── fonts/
    │   ├── Beleren.woff2
    │   └── MPlantin.woff2
    └── assets/
        ├── frames/                    ← card frame PNGs (W, U, B, R, G, Multi, Artifact)
        └── placeholder-art.png        ← fallback when SD is offline
```

---

## 🎨 Art Prompt Template

```
System builds this prompt dynamically per agent:

"[AGENT_ROLE_DESCRIPTION] depicted as a [CREATURE_TYPE], 
fantasy illustration, Magic: The Gathering card art style, 
oil painting, dramatic rim lighting, intricate detail, 
dark fantasy atmosphere, by Raymond Swanland and Magali Villeneuve, 
8k, sharp focus, no text, no watermark"

Negative prompt:
"text, watermark, logo, blurry, low quality, photo, realistic, 
human face close-up, ui elements"

Settings: 512x384px, 20 steps, CFG 7.5, DPM++ 2M Karras sampler
```

---

## 🖌️ Color Identity Rules (Auto-assigned)

| Agent Characteristic                    | Color(s)       |
|-----------------------------------------|----------------|
| Reasoning, chain-of-thought, planning   | 🔵 Blue        |
| Fast, reactive, event-driven            | 🔴 Red         |
| Data processing, RAG, embeddings        | 🟢 Green       |
| External APIs, web search, side effects | ⚫ Black       |
| Orchestration, routing, delegation      | ⚪ White       |
| Multi-role / ambiguous                  | 🟡 Gold (Multi)|
| Pure tool wrapper, no LLM               | ⬜ Artifact    |

---

## 🛠️ Local SD Setup (One-Time, User Prerequisite)

The plugin README will instruct users to:

1. Install [AUTOMATIC1111](https://github.com/AUTOMATIC1111/stable-diffusion-webui) or [ComfyUI](https://github.com/comfyanonymous/ComfyUI)
2. Download DreamShaper XL model (~2GB, free from Civitai)
3. Launch with `--api` flag: `./webui.sh --api` or `python main.py`
4. Confirm REST API at `http://localhost:7860`
5. Plugin detects and connects automatically — no further setup needed

If SD is not running, the plugin gracefully falls back to placeholder art and shows a one-time notification with setup instructions.

---

## 📋 Claude Clarification Prompt

> Use this prompt when starting any implementation phase with Claude to ensure requirements are fully understood before writing code.

---

```
You are an expert IDE plugin developer and Magic: The Gathering card rendering specialist.

Before writing any code, ask me clarifying questions if ANY of the following are unclear:

1. TARGET FRAMEWORKS: Which agent frameworks should be prioritized first? 
   (LangChain, OpenAI Agents SDK, CrewAI, AutoGen, Semantic Kernel, custom?)

2. TARGET LANGUAGE: Are the user's agents written in Python, TypeScript, C#, or multiple?

3. SD BACKEND: Should we default to AUTOMATIC1111, ComfyUI, or detect both?

4. IDE PRIORITY: Should VS Code or Rider be built first, or truly in parallel?

5. CARD ACCURACY: How closely should we match official MTG card layout? 
   (Pixel-perfect recreation vs. stylistically inspired vs. simplified?)

6. PARSING DEPTH: Should we parse only decorated functions as tools, or also 
   infer tools from method signatures and docstrings?

7. MULTI-AGENT DETECTION: How should we identify that Agent A calls Agent B? 
   (Explicit handoffs array, constructor injection, function calls within code?)

8. EXPORT FORMAT: Cards as PNG only, or also JSON (CardData) for portability?

9. OFFLINE MODE: Should the plugin be fully functional (with placeholder art) 
   when SD is not running? Or block card generation entirely?

10. EXISTING CARD FRAMES: Should we use community-recreated MTG frame PNGs, 
    or build vector frames from scratch in Canvas/SVG?

Do not generate any code until you have confirmed understanding of the above. 
Once confirmed, proceed with the specific phase I tell you to implement.
```

---

## 📌 Key Resources & Links

| Resource               | URL / Source                                           |
|------------------------|--------------------------------------------------------|
| mana-font (mana symbols) | https://github.com/andrewgioia/mana                  |
| Keyrune (set symbols)  | https://github.com/andrewgioia/keyrune                |
| AUTOMATIC1111          | https://github.com/AUTOMATIC1111/stable-diffusion-webui |
| ComfyUI                | https://github.com/comfyanonymous/ComfyUI             |
| DreamShaper XL model   | https://civitai.com/models/4384/dreamshaper           |
| Konva.js               | https://konvajs.org                                   |
| Tree-sitter            | https://tree-sitter.github.io                         |
| d3-force               | https://d3js.org                                      |
| VS Code Extension API  | https://code.visualstudio.com/api                     |
| JetBrains Plugin SDK   | https://plugins.jetbrains.com/docs/intellij           |
| MTG Card Anatomy Guide | https://magic.wizards.com/en/news/feature/anatomy-card |

---

*Plan version 1.0 — Free/local-first approach*
*Estimated total build time: 9–10 weeks (solo developer)*