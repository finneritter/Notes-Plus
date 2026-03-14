# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build

```bash
./gradlew build                  # full build → build/libs/notes+<version>.jar (auto-increments Z after each build)
./gradlew compileJava            # compile only (faster error checking)
```

The build script automatically copies the remapped jar to `build/libs/notes+<version>.jar` and then increments the patch version in `gradle.properties` for the next build. No manual copy step needed.

Version convention: `notes+X.Y.Z` — Z is incremented automatically after each successful build.

**Gradle 9.4.0 is required.** Fabric Loom 1.15.4 requires Gradle ≥ 9.2. If the wrapper reverts (e.g. IDE tooling resets it), update `gradle/wrapper/gradle-wrapper.properties`:
```
distributionUrl=https\://services.gradle.org/distributions/gradle-9.4.0-bin.zip
```
The `gradle-wrapper.jar` lives at `gradle/wrapper/gradle-wrapper.jar` — it must be present or the wrapper script will fail with `ClassNotFoundException: GradleWrapperMain`.

## Dependency versions (Minecraft 1.21.11)

| Dependency | Version |
|---|---|
| Minecraft | 1.21.11 |
| Yarn mappings | 1.21.11+build.4 |
| Fabric Loader | 0.18.4 |
| Fabric API | 0.141.3+1.21.11 |
| Fabric Loom | 1.15.4 |
| ModMenu | 17.0.0-beta.2 |
| Gradle | 9.4.0 |

## Architecture

### Entry points
- `IgCalcMod` — `ModInitializer` (server-side init, just logs)
- `IgCalcClient` — `ClientModInitializer`, registers F6/F7/F8 keybindings via `KeyBindingHelper` under custom `NOTES_PLUS_CATEGORY` (displays as "Notes+" in Controls), opens screens on tick, triggers first-run tutorial
- `ModMenuIntegration` — `ModMenuApi`, opens the config screen from Mod Menu

### Screens
- `gui/IgCalcOverlay` — thin `Screen` container that hosts CalculatorScreen, NotesScreen, and TimerScreen simultaneously. Routes render/mouse/keyboard events, tracks focus (via `focusedWindow`), manages tutorial overlay. Has `startTutorial(int)`, `startTutorialFiltered(int[])`, and `getFocusedWindow()` methods. Passes `overlay` reference to each sub-screen for active window indicator.
- `gui/CalculatorScreen` — floating calculator window. Buttons are stored as a `List<CalculatorButtonWidget>` (records) and drawn manually with `DrawContext.fill()` — **not** added as Minecraft child widgets. Contains a recursive-descent `ExpressionParser` inner class. Title bar: [×] left, [?] and [→N] right.
- `gui/NotesScreen` — floating notes editor with a sidebar. Custom multi-line text editor using `List<String> lines` + cursor tracking. No `TextFieldWidget` for the editor area. Uses `TextFieldWidget` only for the search bar and inline rename/folder-name fields. Title bar: [×] [≡] left, [⚑] [↗] [?] right. Title shows full relative path without `.txt` (e.g., `Work/meeting`).
- `gui/TimerScreen` — timer/stopwatch window with countdown and stopwatch modes, lap times, configurable presets. Title bar: [×] left, [?] [⚑] right. `currentMode` is public (used by tutorial). Window size: `WIN_W=310`, `CLOCK_PANEL_W=160`, `CLOCK_CX_OFF=80`.
- `gui/TutorialScreen` — **not** a Screen subclass. Rendered on top of IgCalcOverlay. 12-step guided tutorial with dim overlay, spotlight cutout, glow border, tooltip with formatted text (**bold** as accent color), and Next/Back/Skip navigation. Supports filtered step lists for per-window help buttons.
- `gui/widget/CalculatorButtonWidget` — a plain Java `record` (not a Minecraft widget) holding button layout, colors, and a `Runnable` action. Drawn entirely by `CalculatorScreen.render()`.
- `gui/IgCalcSettingsScreen` — Mod Menu config screen, dark theme. 5 configurable keybind rows + 5 read-only rows (F6/F7/F8/F9/Alt). Color hex fields with clickable preview squares that open an inline HSB color picker popup. HUD display toggles, notes window opacity, "Replay Tutorial" and "HUD Config" buttons. Scrollable with scissor clipping.
- `gui/HudRenderer` — singleton; renders pinned Notes/Timer on the HUD via `HudRenderCallback`. Toast notifications.
- `gui/HudInteractionScreen` — thin Screen while Alt held for interacting with pinned HUD elements.
- `gui/HudConfigScreen` — drag/resize/opacity/presets for both HUD panes.

### Title bar button system
All three screens (Notes, Calculator, Timer) use a uniform button system:
- Constants: `TB_W=14`, `TB_H=12`, `TB_GAP=2`
- Buttons are 14×12px hit areas with 2px gaps — **no pill backgrounds**. Icons are simple text draws with hover color changes only.
- Close [×] always leftmost; feature buttons on right side
- Title text centered between left and right button groups
- Each screen has a [?] help button that opens a filtered tutorial for that window
- Export icon [↗] in NotesScreen is scaled 1.5x via `pushMatrix()`/`scale()`/`popMatrix()`

### Active window indicator
- `IgCalcOverlay.focusedWindow` (package-private) tracks which sub-screen last received a click
- Each sub-screen has a `public IgCalcOverlay overlay` field, set during `initCalc()`/`initNotes()`/`initTimer()`
- `overlay.getFocusedWindow()` used in each screen's `render()` to choose title bar color:
  - Focused: `COLOR_TITLE_BAR` (`0xFF262A3C`), icon default `0xFF6B7099`
  - Inactive: `0xFF1E2030` (darker), icon default `0xFF4A5068` (dimmed)
- Initial focus set in `enableCalc()`/`enableNotes()`/`enableTimer()`

### Persistence
- `config/IgCalcWindowState` — static fields for all windows' positions, sizes, and sidebar state. Saved/loaded via Java `Properties` to `.minecraft/igcalc_window.properties`. Called on `mouseReleased` and `close()`.
- `config/IgCalcConfig` — configurable hotkeys (`HotkeyBinding` record) and color scheme. Saved to `.minecraft/igcalc_config.properties`.
- `config/IgCalcHudState` — persists Notes/Timer HUD position, size, opacity, pinned state, background toggles → `.minecraft/igcalc_hud.properties`.
- `config/IgCalcTutorialState` — tutorial completion state → `.minecraft/igcalc_tutorial.properties`. Static `isCompleted()` / `markCompleted()` / `reset()`.
- Notes files live in `notes+_notes/` under the world save folder (singleplayer) or `.minecraft/notes+_notes/` (multiplayer). On first load, if `igcalc_notes/` exists and `notes+_notes/` does not, the old directory is automatically migrated. Each note is a separate `.txt` file; `.order` and `.pinned` files track ordering and pinned state.

## NotesScreen — detailed behaviour

### Sidebar
- Toggle with `[≡]` button in the title bar (left side).
- Virtual row types: `FOLDER`, `FILE`, `SPACER`, `NAME_HEADER`, `CONTENT_HEADER` — all in `SidebarEntry(kind, value, inFolder, depth)`.
- **Nested folders** (arbitrary depth): `knownFolders` is discovered via `Files.walk(depth=5)`. `buildSidebarRows()` is recursive via `addTreeRows()`. Depth-based indent: 8 px per level for both folders and files.
- Folder display name shows only the last path component (leaf name).
- Folder context menu: `Rename folder | New subfolder | Delete folder` (width 92 px).
- File context menu: `Rename | Delete | Pin/Unpin | New folder` (width 92 px).
- Sidebar background right-click: `New note | New folder`.
- Drag-to-reorder files; drop on a folder to move the file into it.
- Double-click a file to inline-rename; single-click to open.
- Search (`[?]` button): filters by file name; "In Content" section shows content matches.
- Shift+Tab focuses sidebar keyboard navigation; Tab in editor inserts 2 spaces (or indents bullet).

### Editor
- `List<String> lines` + `cursorLine`/`cursorCol` — no `TextFieldWidget`.
- Visual rows cached in `List<VisualRow>` (record: `logLine, startCol, endCol, scale, heightPx, inCodeBlock, wrapIndentPx`). Cache invalidated via `linesVersion`.
- **Word-boundary wrapping**: wrap snaps to last space before the hard char limit.
- **Bullet/heading wrap indent**: continuation lines of bullets/blockquotes/headings are indented to align under the content (not column 0). Computed via `rawContentStart()` + `getLinePrefix()`.
- **Text selection**: `selAnchorLine`/`selAnchorCol` (-1 = no selection). Shift+arrows/Home/End extend selection. Ctrl+A selects all. Ctrl+C/X copy/cut. Ctrl+V paste (multi-line). Backspace/Delete delete selection first. Mouse drag selects. Selection rendered as blue tint (`0x663A5A90`).
- Markdown rendering: `# ## ### ####` headers (scaled), `> ` blockquote, `* -` bullets (•), `- [ ] - [x]` checkboxes, ` ``` ` code blocks, `**bold** *italic* ~~strike~~ __underline__ \`code\` [link](url)`.
- Line color / scale via `getLineColor()` / `getLineScale()`.
- Cursor blink 500 ms on/off.

### Quick note (Ctrl+Shift+N default)
- Creates `Quick Note YYYY-MM-DD HHMM.txt` (deduplicates with `(2)`, `(3)`, … suffix).
- Opens sidebar, immediately starts inline rename with empty field — press Enter to confirm, ESC to keep the auto-generated name.

### Autosave
- 2-second idle autosave after any edit (`AUTOSAVE_DELAY_MS = 2000`).
- Explicit Ctrl+S saves immediately.

### Configurable hotkeys (`IgCalcConfig`)
| Field | Default |
|---|---|
| `keyNewNote` | Ctrl+N |
| `keyFindSidebar` | Ctrl+Shift+F |
| `keyQuickNote` | Ctrl+Shift+N |
| `keyInsertCoords` | Ctrl+Shift+G |
| `keyCopyClipboard` | Ctrl+Shift+E |
| `keyHudToggle` | F9 (read-only in Controls) |
| `keyHudInteract` | Left Alt (read-only in Controls) |

## Tutorial system

### First-run detection
`IgCalcClient.onEndTick()` checks `IgCalcTutorialState.isCompleted()` once per session (guarded by `tutorialChecked` flag). If not completed and a world is loaded with no screen open, opens `IgCalcOverlay(false, false, false)` and calls `startTutorial(0)`.

### 12 steps (indices 0–11)
0: Welcome (center card), 1: Notes, 2: Sidebar, 3: Markdown, 4: Note linking, 5: Export, 6: Calculator, 7: Calc→Notes, 8: Timer, 9: Lap times (stopwatch mode), 10: HUD pinning, 11: Quick shortcuts (center card).

### Filtered mode
Per-window [?] buttons open a filtered tutorial showing only relevant steps:
- `NOTES_STEPS = {1, 2, 3, 4, 5, 10}` — notes features + HUD pinning
- `CALC_STEPS = {6, 7}` — calculator features
- `TIMER_STEPS = {8, 9, 10}` — timer features + HUD pinning

Called via `overlay.startTutorialFiltered(TutorialScreen.NOTES_STEPS)` etc.

### Replay
- "Replay Tutorial" button in `IgCalcSettingsScreen` calls `IgCalcTutorialState.reset()` and opens the full tutorial.
- [?] buttons in each window title bar open filtered tutorials without resetting completion state.

### Rendering
- Dim overlay (`0xAA000000`) with spotlight cutout around highlighted UI element
- Blue glow border (`0xFF0A84FF`) with expanding alpha layers
- Tooltip box: dark theme (`0xF0262A3C`), **bold** text rendered in accent color (`0xFF7EB8FF`)
- Navigation: Back/Next buttons + step counter + "Skip tour" link on separate rows
- Keyboard: Right/Enter = next, Left = back, Escape = skip

## Minecraft 1.21.11 API — critical differences from older versions

This version has breaking API changes. All code in this repo already uses the new API.

### Mouse events
```java
// Screen / ParentElement — new signatures:
boolean mouseClicked(Click click, boolean propagated)   // click.x(), click.y(), click.button()
boolean mouseDragged(Click click, double dX, double dY)
boolean mouseReleased(Click click)
boolean mouseScrolled(double, double, double, double)   // unchanged
// Import: net.minecraft.client.gui.Click
```

### Keyboard / char events
```java
boolean keyPressed(KeyInput input)   // input.key(), input.scancode(), input.modifiers()
boolean charTyped(CharInput input)   // (char)input.codepoint(), input.asString()
// Imports: net.minecraft.client.input.KeyInput, net.minecraft.client.input.CharInput
```

### ButtonWidget
`ButtonWidget` is now **abstract** and `PressableWidget.renderWidget()` is **final**. Custom-drawn buttons must be rendered manually — do not extend `ButtonWidget` or `PressableWidget` for this purpose. Use `ButtonWidget.builder(...).dimensions(...).build()` only for standard-looking buttons.

### KeyBinding category
```java
// Custom category via Identifier — displays as translation key "key.categories.<namespace>.<path>"
KeyBinding.Category CUSTOM = KeyBinding.Category.create(Identifier.of("igcalc", "keybind_category"));
new KeyBinding("key.name", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F6, CUSTOM)
// Translation key in en_us.json: "key.categories.igcalc.keybind_category": "Notes+"
// KeyBinding.Category.create(String) is PRIVATE — use the Identifier overload
```

### DrawContext matrix transforms
```java
// DrawContext.getMatrices() returns org.joml.Matrix3x2fStack (NOT MatrixStack)
// Use 2D methods — NO 3-arg overloads:
context.getMatrices().pushMatrix();
context.getMatrices().translate(x, y);       // 2 args, NOT 3
context.getMatrices().scale(sx, sy);         // 2 args, NOT 3
// ... draw calls ...
context.getMatrices().popMatrix();           // NOT pop()
```

## Naming
User-visible name is **"Notes+"** (capital N). The jar filename and notes directory use lowercase `notes+` (`notes+_notes/`, `notes+X.Y.Z.jar`). Internal class names use `IgCalc` prefix (historical).

## Mouse cursor
`IgCalcOverlay.tick()` calls `client.mouse.unlockCursor()` to keep the cursor free while the overlay is open (`shouldPause()` returns `false` so the world keeps running).
