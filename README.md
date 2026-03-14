 Notes+

A Fabric mod for Minecraft 1.21.11 that adds an in-game notes editor, calculator, and timer — all accessible as floating overlay windows without pausing the game.

## Features

### Notes Editor (F7)
- Multi-file note system with a sidebar file browser
- Nested folder organization with drag-to-reorder
- Markdown rendering: headings, bold, italic, strikethrough, underline, inline code, blockquotes, bullets, checkboxes, code blocks
- Clickable note links with `[[Note Name]]` syntax
- Word-boundary wrapping with smart bullet/heading indentation
- Text selection, copy/cut/paste, undo via keyboard
- Right-click context menu with configurable actions
- Insert player coordinates (dimension-aware)
- Export to clipboard, desktop, or system file manager
- Quick Note shortcut for rapid capture
- Autosave (2-second idle) and manual save (Ctrl+S)
- Per-world notes (singleplayer) or shared notes (multiplayer)
- Configurable window opacity and color scheme

### Calculator (F6)
- Standard arithmetic with recursive-descent expression parser
- Supports `+`, `-`, `*`, `/`, parentheses, decimals, and negative numbers
- Calculation history (session-scoped, up to 50 entries)
- Send result to Notes with one click
- Full keyboard and mouse input

### Timer (F8)
- Countdown timer with digit-entry input and preset buttons
- Stopwatch mode with lap times
- Sound notification on timer completion
- Tab switching between Timer and Stopwatch modes

### HUD Pinning (F9)
- Pin Notes or Timer to the in-game HUD for always-visible display
- Drag, resize, and adjust opacity in the HUD Configuration screen
- Interact with pinned HUD elements by holding Alt
- Scroll pinned notes with Ctrl+Up/Down

### Settings (Mod Menu)
- Fully configurable keybinds for all actions
- Color scheme customization with HSB color picker
- HUD display toggles and background visibility
- Right-click context menu item toggles
- Notes window opacity control
- Replay guided tutorial

### Tutorial
- 12-step interactive guided tour on first launch
- Per-window help cards via [?] buttons with keybind reference
- Spotlight cutout and tooltip-based instruction

## Default Keybinds

| Key | Action |
|-----|--------|
| F6 | Open Calculator |
| F7 | Open Notes |
| F8 | Open Timer |
| F9 | Toggle HUD pin |
| Alt (hold) | Interact with pinned HUD |
| Ctrl+N | New Note |
| Ctrl+Shift+N | Quick Note |
| Ctrl+Shift+F | Find in Sidebar |
| Ctrl+Shift+G | Insert Coordinates |
| Ctrl+Shift+E | Export to Clipboard |
| Ctrl+S | Save |
| Ctrl+A | Select All |

All keybinds are configurable in the Notes+ settings screen (via Mod Menu) or in Minecraft's Controls menu.

## Requirements

| Dependency | Version |
|------------|---------|
| Minecraft | 1.21.11 |
| Fabric Loader | >= 0.15.0 |
| Fabric API | 0.141.3+1.21.11 |
| Mod Menu | 17.0.0-beta.2 |
| Java | 21+ |

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 1.21.11
2. Install [Fabric API](https://modrinth.com/mod/fabric-api) and [Mod Menu](https://modrinth.com/mod/modmenu)
3. Download the latest `notes+X.Y.Z.jar` from [Releases](../../releases)
4. Place the jar in your `.minecraft/mods/` folder
5. Launch Minecraft and press F7 to start

## Building from Source

```bash
git clone https://github.com/finneritter/Notes-Plus.git
cd igcalc
./gradlew build
```

The output jar will be at `build/libs/notes+<version>.jar`.

Requires Gradle 9.4.0 (included via wrapper) and Java 21.

## License

MIT
