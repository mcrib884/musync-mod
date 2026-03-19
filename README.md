# MuSync

A client and server-side mod that synchronizes Minecraft background music across all players on a server.

## Features
- **In-Game Music Controls:** A graphical interface and commands that let server operators instantly pause, skip, seek, or queue music tracks for the entire server.

- **Dimensional Syncing:** Maintains independent synchronized music streams for different dimensions.

- **Support for Resource Packs:** Automatically loads and synchronizes custom music added through server resource packs, custom music folder in .minecraft, or mods without requiring any extra configuration. Supports .ogg, .wav, and .mp3.

- **Client Synchronization:** New players joining the server or switching dimensions automatically sync.

## Commands & Keybinds
- Gui can be opened via keybind.

- Operator command: `/musync` to control playback, manage the playlist, or trigger immediate syncs.

## Building
This mod uses [Stonecutter](https://github.com/kikugie/stonecutter) to build for multiple supported Minecraft versions and loaders from a single source tree.

### Build All Versions
To completely build all targets (creates the jars inside `versions/.../build/libs/`):
```bash
./gradlew build
```

### Build Specific Version
To build a specific version using the Gradle wrapper directly, set the Stonecutter active project first:

```bash
./gradlew 1.20.1-forge:build
```
```bash
./gradlew 1.19.2-forge:build
```
```bash
./gradlew 1.21.1-neoforge:build
```
```bash
./gradlew 1.19.2-fabric:build
```
```bash
./gradlew 1.20.1-fabric:build
```
```bash
./gradlew 1.21.1-fabric:build
```
