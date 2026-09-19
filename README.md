# SF_JustEnoughGuide

English-first maintained fork of [JustEnoughGuide](https://github.com/balugaq/JustEnoughGuide) for **Slimefun Legacy**.

This fork tracks upstream JEG while carrying Slimefun Legacy-specific compatibility, diagnostics, and guide-safety work. Upstream credit remains with balugaq and the original contributors.

## Goals

- Keep current JustEnoughGuide features available as a standalone Slimefun addon.
- Keep all player/admin-facing defaults in English.
- Track upstream JEG fixes, including the current 2.1.54 recipe-completion rewrite.
- Integrate with Slimefun Legacy's public recipe/machine APIs instead of duplicating core logic where possible.
- Preserve Legacy safety work such as guarded recipe filling, compatibility diagnostics, and runtime isolation.
- Make future JEG updates an addon update instead of requiring a Slimefun Legacy core release.

## Slimefun Legacy diagnostics

Operators can run:

```text
/jeg diagnostics
/jeg doctor
```

This opens a button menu for common Slimefun Legacy diagnostics:

- Doctor status
- Core health
- Addon compatibility
- Dependency diagnostics
- Runtime failures
- External integrations
- Upgrade readiness
- Installed versions

The buttons delegate to the installed Slimefun Legacy commands, so the addon does not duplicate or second-guess Doctor logic.

## Main JEG features

- Enhanced guide layouts and navigation
- Smart search and optional real-time search
- Bookmarks
- Recipe completion
- Large recipe display
- Custom item groups and ordering
- Addon integrations
- Recipe sharing and guide options

## English-first behavior

The maintained fork ships with English guide titles, config comments, GUI text, command messages, and addon display names by default. Pinyin-specific search support remains available for compatibility but is disabled by default.

## Compatibility target

The maintained Slimefun Legacy ecosystem currently targets Minecraft **1.21.11 through 26.3**, with Paper/Purpur as primary platforms and Folia/Leaf as secondary validation targets.

## Upstream

Original project: https://github.com/balugaq/JustEnoughGuide

Maintained Slimefun Legacy fork: https://github.com/wickidcow/SF_JustEnoughGuide

## License

GPLv3, matching upstream.
