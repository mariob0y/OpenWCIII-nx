# OpenWCIII-nx

**Work in progress.** A port of the [OpenWCIII](https://github.com/awest813/OpenWCIII) /
[Warsmash](https://github.com/Retera/WarsmashModEngine) Warcraft III engine (Java, libGDX) to the
Nintendo Switch 2 as homebrew.

Expect missing features, bugs and changes without notice. Nothing here is a finished or supported
release.

## What is here

- `core/`, `desktop/`: the engine, with fixes and additions made while getting the campaigns
  playable (controller input, campaign UI, trigger and ability fixes).
- `switch2/`: the Switch 2 build. The Java code is translated to C++ with
  [Clearwing VM](https://github.com/TheLogicMaster/clearwing-vm) (a git submodule, with local
  patches in `switch2/patches/`) and built against [switch-gdx](https://github.com/TheLogicMaster/switch-gdx).

## Game data

The repository contains no game files. You need your own copy of Warcraft III; the port is
developed against version 1.31 data.

## Running on PC

The engine still runs on desktop, which is how most changes are tested. Use JDK 17:

```bash
./gradlew :desktop:runGame
```

Point `core/assets/warsmash.ini` (or a local copy of it) at your game data first.

## License

AGPL-3.0, as the upstream projects. See [LICENSE](LICENSE).
