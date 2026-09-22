# SF_JustEnoughGuide

**SF_JustEnoughGuide** is the maintained English-facing JustEnoughGuide fork for Slimefun Legacy.

It keeps the familiar JEG experience while integrating the hardened guide, diagnostics, recipe browsing, bookmark migration, and safe machine-input work developed for Slimefun Legacy.

## Download

Use the latest GitHub release from this repository.

Current release artifact:

```
SF_JEGuide2.0.60.jar
```

The plugin/release version is 2.1.60; the raw release JAR keeps the requested Legacy distribution filename above.

## Requirements

- Slimefun
- Java 21 or newer at runtime (builds use JDK 25 while emitting Java 21 bytecode)

**GuizhanLibPlugin is not required.**

On Slimefun Legacy, the addon uses the supported guide registration API when available. Older/upstream Slimefun builds retain a compatibility fallback.

## Highlights

- Enhanced Slimefun survival and cheat guides
- Real-time and indexed search
- Bookmarks
- Recipe completion and large recipe support
- Custom guide layouts
- Core Slimefun categories first, followed by addon categories alphabetically
- Slimefun item ID display
- Addon integrations
- English EMC labels for FinalTECH/FinalTech and EMC Tech integrations
- Slimefun Recovery Center shortcuts for OP/admin users in both survival and `/sf cheat` Settings & Info
- `/sf tick top` shortcut in Settings & Info for quick ticker profiling
- Slimefun Legacy machine recipe provider support
- Safe machine-input fill delegation to Slimefun Legacy
- Migration from the former Slimefun Legacy native Enhanced Guide

## Slimefun Legacy integration

When used with Slimefun Legacy, JEG can expose:

- Recovery Center access to Doctor status, compatibility, dependency, runtime, integration, upgrade, scan, and resource-pack repair tools
- machine recipes registered through Slimefun Legacy's provider API
- safe machine-input filling through Slimefun Legacy's transaction, protection, region-thread, and rollback checks

Slimefun Legacy keeps those safety services in core even when its former native Enhanced Guide UI is disabled.

## Migration

On a fresh JEG install, compatible settings can be imported from:

```
plugins/Slimefun/enhanced-guide.yml
```

Existing Slimefun Legacy guide bookmarks are imported from:

```
plugins/Slimefun/guide-bookmarks.yml
```

Existing JEG configuration is not overwritten.

## Configuration

The generated `config.yml` is English-facing by default. Pinyin search is disabled by default in this maintained build but can still be enabled by server owners who need it.

The default guide title is **Slimefun Legacy Guide**. The `custom-format` section controls guide layouts. Legacy-specific markers include:

- `D` — optional Slimefun Recovery/Doctor shortcut (admin-only when Legacy is present)
- `U` — Slimefun Recovery Center (OP/admin only; used by the default Settings layout)
- `Q` — Slimefun Legacy Tick Top
- `M` — Slimefun Legacy machine recipes

## Compatibility target

This maintained fork is intended for the Slimefun Legacy compatibility range:

- Minecraft 1.21.11
- Minecraft 26.1.x
- Minecraft 26.2
- Minecraft 26.3

Paper and Purpur are the primary server targets. Folia and Leaf are secondary compatibility targets.

## Upstream

This project is based on JustEnoughGuide by balugaq and remains licensed under GPL-3.0.

Upstream project: `balugaq/JustEnoughGuide`

Maintained Legacy fork: `wickidcow/SF_JustEnoughGuide`

## License

GNU General Public License v3.0.
