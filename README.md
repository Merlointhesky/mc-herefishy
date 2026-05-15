# HereFishy

A [Paper](https://papermc.io) Minecraft plugin for **auto-fishing** — automatically catch fish and re-cast your rod without manual intervention.

## Features

### Core (always on)

- **Auto-fishing toggle** — `/herefishy start` and `/herefishy stop`.
- **Automatic re-cast** — After catches or failed attempts the rod is cast again automatically.
- **Vanilla-ish loot & XP** — Simulated vanilla fishing loot (fish, junk, treasure) plus Minecraft XP (1–6 per catch).
- **Rod durability protection** — Stops when only a few casts remain (`≤ 5` uses by default).
- **Inventory-full handling**
  - *Default:* stops with a chat message when you have **no empty slots** (`firstEmpty`).
  - *Optional routed setup:* when you bind crates + junk spot (below), teleport **fish stash → trophy chest → junk stand → fishing spot**, then resumes if offload succeeds.
- **Activity Summary (New in 1.2.1)** — Instead of individual chat messages for every catch, a summary is displayed when auto-fishing stops or after each successful offload.
  - Breakdowns by category: Fish, Treasure, and Junk.
  - Displays the number of times inventory was dumped.
- **Surplus buffering** — If a catch cannot be placed in your inventory (`addItem` overflow) while routed, that stack is routed on the teleport trip instead of spamming drops onshore.
- **Rod break detection** — Stops if the autofish rod shatters mid-session.
- **Session persistence** — Loot routing overrides and chest bindings are saved and reloaded automatically when you reconnect.
- **AuraSkills hooks** *(optional)* — When AuraSkills is installed:
  - **Better simulated rolls** — More treasure / less junk (+0.25% Fishing level scaling). Capped to ensure standard treasure (books, etc.) remains available at high levels.
  - **Faster re-casting** — 1 tick shaved per ten Fishing levels.
  - **Vanilla XP +2% per Fishing level** on each catch plus **bonus AuraSkills XP** (+2% per level multiplier on the Aura XP payload).
  - **Rarity-based treasure** — Standard, Rare, Epic with scaling bonuses per fishing level.

### Routed loot dump (Paper 1.2.1)

1. **`/herefishy setup`**:
   - Starts the setup wizard.
   - First sneak-click on an inventory block → **treasure** chest/barrel/etc.
   - Second sneak-click on an inventory block → **fish** stash.
   - Third sneak-click (**any block**) → anchors your **junk** drop location.
2. **`/herefishy config`** — GUI for every junk/treasure material in the simulated tables. Toggle whether each stacks into the **treasure chest** vs **junk drops** *(fish ALWAYS use the fish chest)*. Routing updates chat colors & AuraSkills XP weights to reflect the overrides.

Luck of the Sea on the autofish rod still affects simulated treasure/junk probabilities.

## Requirements

- Paper `1.21+` *(plugin `api-version: '1.21'`; built against Paper `1.21.4` API)*
- Java `21`
- *(Optional)* AuraSkills `2.x`

## Installation

1. Grab `HereFishy-1.2.1.jar` from Releases (or `./gradlew build` locally — see below).
2. Drop the JAR in `plugins/`.
3. Restart the server (recommended over hot-reloads on production worlds).

## Building from Source

```bash
./gradlew build   # POSIX
```

```powershell
.\gradlew.bat build   # Windows PowerShell/cmd
```

The compiled plugin jar is written to:

```
build/libs/HereFishy-1.2.1.jar
```

## Commands & permissions

| Command | Description | Permission |
|---------|-------------|------------|
| `/herefishy start` | Enable autofish mode | `herefishy.use` |
| `/herefishy stop` | Disable autofish mode | `herefishy.use` |
| `/herefishy config` | Open the treasure/junk routing GUI | `herefishy.use` |
| `/herefishy setup` | Start the dump routing setup wizard | `herefishy.use` |

| Permission | Description | Default |
|------------|-------------|---------|
| `herefishy.use` | Command + sneak-binding interactions | `true` |

## How to use

1. Hold any fishing rod in main or off-hand.
2. Run `/herefishy start`.
3. Cast once manually — the hook automates reels + recasts.
4. *(Optional)* Configure `/herefishy config` BEFORE or DURING farming to match personal junk vs treasure taxonomy.
5. *(Optional)* Run `/herefishy setup` and follow chat instructions to bind:
   - treasure chest → fish chest → lava/junk footing.
   - Without this trio the plugin behaves like `<1.1.0`: hard stop once inventory lacks space.

Autofishing halts instantly if rods break/nearly snap, inventories overflow without valid routing, offload fails (`chest full` mid-route), `/herefishy stop` fires, or the player logs off.

## Version

Current release documented here: **`1.2.1`** (Gradle + `plugin.yml` stay in sync).

## License

Licensed under [GNU GPLv3](LICENSE).
