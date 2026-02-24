# Publishing to JetBrains Marketplace

## Overview

There are two ways to distribute this plugin:

1. **Manual Distribution** - Share the `.zip` file directly
2. **JetBrains Marketplace** - Official plugin repository (recommended for public release)

---

## Option 1: Manual Distribution (Quick)

### Build the Plugin

```bash
cd rider-plugin
./gradlew buildPlugin
```

Output: `build/distributions/rider-plugin-1.0.0.zip`

### Share with Friends

1. Send them the `.zip` file
2. They install via: **Settings → Plugins → ⚙️ → Install Plugin from Disk**

---

## Option 2: JetBrains Marketplace (Public Release)

### Step 1: Create JetBrains Account

1. Go to [JetBrains Marketplace](https://plugins.jetbrains.com/)
2. Click **Sign In** → Create account or use existing JetBrains account

### Step 2: Get Upload Token

1. Go to [My Tokens](https://plugins.jetbrains.com/author/me/tokens)
2. Click **Create New Token**
3. Name it (e.g., "MTG Agent Visualizer Upload")
4. Copy the token - you'll only see it once!

### Step 3: Configure Gradle for Publishing

Add to `rider-plugin/build.gradle.kts`:

```kotlin
plugins {
    // ... existing plugins
    id("org.jetbrains.intellij") version "1.17.0"
}

intellij {
    // ... existing config

    // For publishing
    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
        // Optional: publish to beta channel first
        // channels.set(listOf("beta"))
    }
}
```

### Step 4: Publish

```bash
# Set your token
export PUBLISH_TOKEN="your-token-here"

# Build and publish
cd rider-plugin
./gradlew publishPlugin
```

### Step 5: Wait for Approval

- JetBrains reviews all plugins (usually 1-2 business days)
- You'll receive an email when approved
- Plugin appears at: `https://plugins.jetbrains.com/plugin/YOUR_PLUGIN_ID`

---

## Pre-Publishing Checklist

### Required

- [ ] **Unique plugin ID** in `plugin.xml` (currently: `com.mtgagents.visualizer`)
- [ ] **Version number** (currently: `1.0.0`)
- [ ] **Vendor info** with valid email
- [ ] **Description** explaining what the plugin does
- [ ] **Change notes** for this version

### Recommended

- [ ] **Plugin icon** (add `pluginIcon.svg` to `src/main/resources/META-INF/`)
  - 40x40px SVG for plugin list
  - 80x80px version for details page
- [ ] **Screenshots** for marketplace listing
- [ ] **README** with usage instructions
- [ ] **License** file (MIT, Apache 2.0, etc.)

### Before Each Release

- [ ] Update `version` in `plugin.xml`
- [ ] Update `change-notes` in `plugin.xml`
- [ ] Test on a fresh IDE installation
- [ ] Verify all features work without local SD (fallback art)

---

## Plugin Icon

Create `src/main/resources/META-INF/pluginIcon.svg`:

```xml
<svg width="40" height="40" viewBox="0 0 40 40" xmlns="http://www.w3.org/2000/svg">
  <!-- Your icon design -->
</svg>
```

Also create `pluginIcon_dark.svg` for dark theme.

---

## Versioning

Follow semantic versioning:
- `1.0.0` → Initial release
- `1.0.1` → Bug fixes
- `1.1.0` → New features (backward compatible)
- `2.0.0` → Breaking changes

---

## Useful Links

- [JetBrains Marketplace](https://plugins.jetbrains.com/)
- [Plugin Development Docs](https://plugins.jetbrains.com/docs/intellij/)
- [Publishing Guide](https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html)
- [Plugin Icon Guidelines](https://plugins.jetbrains.com/docs/intellij/plugin-icon-file.html)
- [Marketplace Quality Guidelines](https://plugins.jetbrains.com/legal/approval-guidelines)

---

## Automated Publishing (CI/CD)

For GitHub Actions, create `.github/workflows/publish.yml`:

```yaml
name: Publish Plugin

on:
  release:
    types: [published]

jobs:
  publish:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Publish Plugin
        env:
          PUBLISH_TOKEN: ${{ secrets.JETBRAINS_PUBLISH_TOKEN }}
        run: |
          cd rider-plugin
          ./gradlew publishPlugin
```

Add `JETBRAINS_PUBLISH_TOKEN` to your GitHub repository secrets.
