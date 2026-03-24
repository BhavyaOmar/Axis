# Axis Editor and IDE
### Java Programming Lab Project

---

## Phase Status

| Phase | Goal | Status |
|-------|------|--------|
| 1 | Basic UI — open, edit, save | ✅ Done |
| 2 | Syntax highlighting, smart editing, find/replace | ✅ Done |
| 3 | Run scripts via Scilab CLI, console output | ✅ Done |
| 4 | Error detection + line highlighting | 🔲 Upcoming |
| 5 | File explorer, toolbar icons, recent files | 🔲 Upcoming |
| 6 | Testing and documentation | 🔲 Final |

---

## Prerequisites

| Tool | Version | Download |
|------|---------|---------|
| Java JDK | 17 or later | https://adoptium.net |
| Apache Maven | 3.8+ | https://maven.apache.org |
| Scilab | 6.x or 2024.x | https://www.scilab.org/download |

---

## Build and Run

```bash
cd AxisIDE
mvn clean package
java -jar target/axis-ide.jar
```

---

## Scilab Path Setup (Phase 3)

Set SCILAB_HOME before running:

Windows:
  SCILAB_HOME=C:\Program Files\scilab-2024.1.0

Linux / macOS:
  export SCILAB_HOME=/usr/local/scilab-2024.1.0

Or add scilab's bin/ folder to your system PATH.

---

## Project Structure

```
AxisIDE/
├── pom.xml
└── src/main/java/com/axiseditor/
    ├── Main.java
    ├── ui/
    │   ├── MainWindow.java
    │   ├── ToolbarPanel.java
    │   ├── ConsolePanel.java
    │   └── StatusBar.java
    ├── editor/
    │   ├── EditorPanel.java
    │   ├── FindReplaceDialog.java
    │   └── scilab/
    │       ├── ScilabTokenMaker.java
    │       ├── ScilabTokenMakerFactory.java
    │       ├── ScilabTheme.java
    │       ├── BracketColorizer.java
    │       ├── ScilabTemplateEngine.java
    │       ├── SmartTypingHandler.java
    │       └── ScilabAutoComplete.java
    ├── execution/
    │   ├── ScilabRunner.java
    │   ├── ExecutionManager.java
    │   └── ErrorParser.java
    ├── filemanager/
    │   └── FileManager.java
    └── utils/
        └── UIConstants.java
```

---

## Phase 2 Features

### Syntax Highlighting
Keywords (for, if, while ...) in blue. Built-ins (disp, sqrt ...) in yellow.
Strings in orange. Numbers in green. Comments in muted green.

### Rainbow Brackets
Depth 0 = gold, depth 1 = teal, depth 2 = violet, depth 3 = salmon.
Applies to ( ) [ ] { } and both quote styles.

### Code Templates — press Enter after keyword

  for       ->  for i = 1:n  /  body  /  end
  while     ->  while condition  /  body  /  end
  if        ->  if condition then  /  body  /  end
  function  ->  function [out] = name(args)  /  body  /  endfunction
  select    ->  select expr / case / otherwise / end
  try       ->  try / body / catch / body / end

Caret lands at the first editable slot. Indentation is preserved for nesting.

### Smart Typing

  Select text + type (   ->  wraps to (selected text)
  Select text + type "   ->  wraps to "selected text"
  Select text + type [   ->  wraps to [selected text]
  Type ( alone           ->  inserts () with caret inside
  Type [ alone           ->  inserts [] with caret inside
  Type " alone           ->  inserts "" with caret inside
  Cursor at (|), type )  ->  skips over, no duplicate
  Cursor at (|), Backspace -> deletes both ( and )

### Comment Toggle — Ctrl+/
  Single line: toggles // on/off at the indentation level.
  Multi-line selection: all lines toggled together as a group.

### Find and Replace — Ctrl+F / Ctrl+H
  Find Next / Find Previous, Replace, Replace All.
  Options: Match Case, Whole Word, Regex.

### Undo / Redo
  Ctrl+Z = undo, Ctrl+Y = redo. Unlimited history.

### Auto-Completion — Ctrl+Space or automatic
  Scilab keywords, built-in functions with description, user-defined variables.

---

## Phase 3 Features

### Run — Ctrl+R or Run button
  Auto-saves, then runs: scilab-cli -cli -quit -nb -f <script>
  Streams stdout (white) and stderr (red) live to console.

### Stop — Stop button
  Kills the running process immediately.

### Console colours
  White = normal output, Red = errors, Green = success, Cyan = info/meta.

### Error line jump
  If Scilab reports an error with a line number, the editor scrolls to it.

---

## Keyboard Shortcuts

  Ctrl+R       Run script
  Ctrl+F       Find
  Ctrl+H       Find and Replace
  Ctrl+Z       Undo
  Ctrl+Y       Redo
  Ctrl+/       Toggle comment
  Ctrl+Space   Force autocomplete
  Enter        Smart template (after keyword on its own line)
