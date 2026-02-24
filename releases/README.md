# Releases

Pre-built plugin files for easy installation.

## Downloads

| IDE | File | Install Instructions |
|-----|------|---------------------|
| **JetBrains Rider** | [mtg-agent-visualizer-1.0.0.zip](mtg-agent-visualizer-1.0.0.zip) | Settings → Plugins → ⚙️ → Install from Disk |
| **VS Code** | [mtg-agent-visualizer-1.0.0.vsix](mtg-agent-visualizer-1.0.0.vsix) | Ctrl+Shift+P → "Install from VSIX" |

## Building Fresh Releases

### Rider Plugin
```bash
cd plugins/rider
./gradlew buildPlugin
cp build/distributions/*.zip ../../releases/
```

### VS Code Extension
```bash
cd plugins/vscode
npm install
npm run package
cp *.vsix ../../releases/
```
