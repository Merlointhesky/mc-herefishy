# HereFishy

A [Paper](https://papermc.io) Minecraft plugin for **auto-fishing** — automatically catch fish and re-cast your rod without manual intervention.

## Features

- **Auto-fishing toggle** — Enable or disable auto-fishing with a simple command (`/herefishy start` or `/herefishy stop`).
- **Automatic re-cast** — After catching a fish or on failed attempts, the rod is automatically re-cast.
- **Vanilla loot & XP** — Awards vanilla fishing loot (fish, junk, treasure) and Minecraft XP (1-6 XP per catch).
- **Rod durability protection** — Stops auto-fishing when your fishing rod has 5 or fewer uses remaining to prevent breakage.
- **Inventory full detection** — Automatically stops when your inventory is full.
- **Rod break handling** — Detects when your fishing rod breaks and stops auto-fishing with a notification.
- **Offline cleanup** — Automatically removes players from auto-fishing when they disconnect.
- **AuraSkills integration** — Awards AuraSkills fishing skill XP (optional, works without it).
- **Luck of the Sea support** — Respects Luck of the Sea enchantment for better treasure chances.

## Requirements

- Paper 1.21+ server
- Java 21+
- (Optional) AuraSkills 2.x for fishing skill XP

## Installation

1. Download the latest `HereFishy-*.jar` from the [releases page](../../releases).
2. Drop the JAR into your server's `plugins/` folder.
3. Restart the server.

## Building from Source

**Using Gradle (recommended):**
```bash
./gradlew build
```

The compiled JAR will be in `build/libs/HereFishy-1.0.1.jar`.

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/herefishy start` | Enable auto-fishing for your player | `herefishy.use` |
| `/herefishy stop` | Disable auto-fishing for your player | `herefishy.use` |

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `herefishy.use` | Allows use of the `/herefishy` command | `true` |

## How to Use

1. Hold a fishing rod in your main hand or off-hand.
2. Run `/herefishy start` to enable auto-fishing.
3. Cast your fishing rod manually once.
4. The plugin will automatically reel in catches and re-cast for you.
5. Run `/herefishy stop` to disable auto-fishing, or it will stop automatically if your rod breaks or inventory fills up.

## License

This project is licensed under the [GNU General Public License v3.0](LICENSE).
