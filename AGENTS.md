# AGENTS.md — SF_JustEnoughGuide / Slimefun Legacy

This file is the working contract for contributors, coding assistants, and automation operating on **wickidcow/SF_JustEnoughGuide**.

This repository is the maintained English-facing JustEnoughGuide fork for **Slimefun Legacy**. The goal is not to mirror upstream blindly. The goal is to preserve the useful JEG experience while keeping it stable, English-facing, performant, and compatible with the maintained Slimefun Legacy ecosystem.

When this document conflicts with the current code or CI, verify the implementation first and update this document so it reflects reality.

## 1. Repository identity

- Repository: `wickidcow/SF_JustEnoughGuide`
- Plugin name: `JustEnoughGuide`
- Maintained distribution name: `SF_JustEnoughGuide`
- Main class: `com.balugaq.jeg.implementation.JustEnoughGuide`
- Primary platform: Paper / Purpur
- Secondary compatibility targets: Leaf / Folia where supported
- Runtime baseline: Java 21+
- Build JDK: JDK 25
- Compiled bytecode target: Java 21
- Build system: Gradle Kotlin DSL
- Required plugin dependency: Slimefun
- Primary integration target: Slimefun Legacy

The fork is based on balugaq/JustEnoughGuide and remains GPL-3.0 licensed. Upstream changes may be reviewed and ported when they improve this maintained fork, but upstream behavior does not automatically take precedence over Slimefun Legacy compatibility or this repository's maintained behavior.

## 2. Project priorities

In order of importance:

1. Protect existing player data and registered Slimefun items.
2. Keep guide interaction stable and intuitive.
3. Preserve Slimefun Legacy compatibility and supported integration APIs.
4. Prevent performance regressions on live servers.
5. Keep player-facing text and maintained documentation in English.
6. Keep compatibility with relevant addons without introducing unnecessary hard dependencies.
7. Prefer small, reviewable fixes over broad rewrites unless a rewrite is required for correctness.

Do not trade data safety or server stability for cleaner-looking code.

## 3. English-facing policy

This is the maintained **English-facing** fork.

New or modified player-facing text, configuration comments, diagnostics, admin menus, CI messages, and maintained documentation must be English unless a compatibility path specifically needs to recognize legacy non-English input.

Legacy aliases may remain internally when removing them would break compatibility. They should not be emitted to players by default.

Examples:

- Pinyin search support may remain optional.
- Legacy Chinese search suffixes may remain accepted as input.
- Generated searches should use the maintained English/prefix form.
- New menu labels, lore, warnings, settings, and README text should be English.

Do not add new Chinese-only player-facing strings to maintained paths.

## 4. Slimefun Legacy first

When Slimefun Legacy exposes a supported API for a feature, use that API instead of duplicating or replacing core behavior.

Important maintained integration areas include:

- guide registration and ownership
- recipe browsing and recipe usage lookup
- machine recipe providers
- safe machine-input filling
- Doctor / Recovery Center access
- ticker diagnostics
- item texture and resource-pack recovery tooling
- addon compatibility surfaces

Compatibility fallbacks for older/upstream Slimefun may remain where they are isolated and safe.

Do not make unrelated changes to Slimefun Legacy core or other addons to solve a JEG-local problem.

## 5. Guide behavior invariants

The following behaviors are intentional and should receive regression protection when touched.

### Issue #264 / recipe guide layout

- Keep the recipe display centered.
- Do not reintroduce the duplicate machine-recipe slot that shifts the 3x3 recipe grid.
- JEG and Slimefun must not both give a guide on first join.
- Item recipe-usage browsing must remain available.
- Keep the maintained Slimefun Legacy guide registration/ownership path.

### Cheat guide item controls

For `/sf cheat` item entries:

- Left click: take 1 item.
- Right click: take one full legal stack.
- Shift + Right click: search recipes that use the item.

Do not change survival-guide right-click behavior as a side effect of cheat-mode work.

### Search output

When a search filter has multiple aliases, generated commands must use a deterministic maintained form. Do not depend on `Set` iteration order when selecting a displayed/generated search flag.

Legacy aliases may be parsed for compatibility, but should not randomly appear in generated search titles or commands.

## 6. SlimeHUD behavior

JEG may integrate with SlimeHUD but must not replace SlimeHUD with a second competing scheduled HUD loop.

Do not:

- pause SlimeHUD's native PlayerWAILA to take over rendering
- create a duplicate BukkitRunnable/Folia loop that continuously replaces SlimeHUD output
- hard-fail JEG when SlimeHUD is absent

Use the maintained compatibility adapter and external override hooks.

## 7. Code map

Use the existing structure instead of creating parallel systems.

- `com.balugaq.jeg.implementation` — plugin lifecycle, guide implementations, setup
- `com.balugaq.jeg.core.managers` — configuration and feature managers
- `com.balugaq.jeg.core.listeners` — Bukkit/Slimefun event listeners
- `com.balugaq.jeg.core.integrations.*` — optional addon/plugin integrations
- `com.balugaq.jeg.api.groups` — search, bookmarks, custom groups
- `com.balugaq.jeg.api.recipe_complete` — recipe completion framework
- `com.balugaq.jeg.api.patches` — maintained guide/settings patches
- `com.balugaq.jeg.utils` — guide utilities, reflection helpers, compatibility utilities
- `com.balugaq.jeg.utils.clickhandler` — click routing and keybind actions
- `com.balugaq.jeg.utils.formatter` — guide/menu layouts

Before adding a new manager, listener, scheduler, or integration abstraction, verify that an existing maintained path cannot handle it.

## 8. Scheduler and platform safety

Do not introduce direct scheduling with `Bukkit.getScheduler()` for maintained gameplay paths when the repository's platform scheduler abstraction can be used.

Use the existing `JustEnoughGuide` / platform scheduling utilities so behavior remains compatible with the server targets this fork supports.

Do not move Bukkit/Paper operations off-thread unless the API is explicitly safe for asynchronous use.

## 9. Optional integrations

Optional integrations must remain optional.

Before calling classes from another plugin:

- keep the dependency `compileOnly` where appropriate
- check that the integration/plugin is available
- use the existing `IntegrationManager` pattern
- handle missing classes safely when required
- avoid turning a soft dependency into a startup requirement

Do not add a new hard dependency just to simplify one code path.

## 10. Logging and diagnostics

Use the existing logging/diagnostic utilities.

Preferred paths:

- plugin logger for normal lifecycle information
- `Debug.debug(...)` for debug-only output
- `Debug.warn(...)` / `Debug.severe(...)` for maintained diagnostics
- `Debug.trace(...)` / `Debug.traceExactly(...)` for exceptions that need reports

Do not use `System.out.println` or raw `printStackTrace()` in production code.

Error messages should explain the actual failing subsystem and, where possible, the next useful action for the server owner.

## 11. Data and compatibility safety

Do not casually change:

- Slimefun item IDs
- bookmark persistence format
- persistent data keys
- group tier storage
- migration markers
- registered recipe behavior
- serialized machine/item metadata

If a migration is unavoidable:

1. make it explicit
2. preserve old data
3. make it idempotent
4. add a rollback/recovery path where practical
5. verify existing worlds can load safely

Avoid destructive automatic cleanup.

## 12. Reflection and patches

Reflection is sometimes required for compatibility, but it is a last-mile compatibility tool, not the default design.

When patching or replacing Slimefun internals:

- prefer a supported Slimefun Legacy API first
- isolate reflection in the existing helper/patch layer
- fail safely if a field/method is unavailable
- restore patched state on unload/reload when applicable
- do not leave duplicate listeners, guide implementations, or schedulers behind after reload

Any patch added during enable should have a clear unload/recovery story.

## 13. Build and verification

The CI build uses JDK 25 and produces Java 21-compatible bytecode.

Minimum verification for code changes:

```bash
./gradlew clean shadowJar --no-daemon
```

For release-ready or broader changes, prefer:

```bash
./gradlew clean build --no-daemon
```

Do not bypass failing checks with `-x test`, `--offline`, or equivalent shortcuts just to obtain a JAR.

The distributable artifact is the shaded JAR:

```text
build/libs/SF_JustEnoughGuide<version>.jar
```

The thin Gradle JAR is intentionally disabled.

Documentation-only changes do not require a full Gradle build unless they also change build configuration, generated resources, or commands that need verification.

## 14. CI regression checks

When fixing a bug with a stable textual or structural invariant, add a lightweight CI regression check when practical.

Existing checks intentionally protect areas such as:

- English EMC integration labels
- SlimeHUD integration behavior
- Doctor / Tick Top / Recovery Center wiring
- issue #264 recipe layout and guide ownership
- canonical shaded JAR contents

Keep regression checks targeted. They should catch the known failure without making harmless refactors unnecessarily difficult.

## 15. Dependencies and shading

Do not add libraries without a concrete need.

When adding a dependency:

- decide deliberately between `compileOnly` and `implementation`
- avoid bundling server-provided APIs
- relocate shaded libraries that are likely to conflict with other plugins
- preserve the current shaded-JAR release model
- do not introduce private repositories or credentials into the build

Runtime-loaded libraries must continue to use the project's established Libby path.

## 16. Git and GitHub workflow

Use Conventional Commits where practical:

- `fix:`
- `feat:`
- `refactor:`
- `docs:`
- `chore:`
- `ci:`

Normal workflow:

1. inspect the current branch and relevant source
2. make one coherent change set
3. verify it
4. commit with a meaningful message
5. push a feature/fix branch
6. open or update a PR
7. check CI before merging

Direct changes to `master` should only be made when the repository owner explicitly asks for a direct master update.

Never force-push shared history unless the repository owner explicitly requests history repair and the risk has been explained.

AI/coding assistants may create branches, commits, pushes, and pull requests when the repository owner explicitly asks them to work on GitHub. Do not claim a build passed unless it actually ran and passed.

Do not add "generated by ChatGPT", "AI-authored", or similar attribution to commits, PRs, code comments, releases, or documentation unless the repository owner specifically asks for it.

## 17. Release discipline

A version bump should describe a coherent user-visible or maintenance change.

Before treating a build as release-ready:

- CI should be green
- the shaded JAR should exist and open successfully
- no known startup errors should be introduced
- guide behavior touched by the release should be verified
- migration-sensitive changes should be reviewed for existing-world safety
- README/release references should match the release version when publishing

Do not publish an official release from an unverified source state.

## 18. Scope discipline

While working on a focused bug, do not opportunistically rewrite unrelated systems.

It is appropriate to fix directly related problems discovered during investigation when they share the same root cause or regression surface. Otherwise, record them separately and keep the current change set coherent.

For high-risk areas such as persistence, recipe registration, guide ownership, scheduler behavior, and addon integration, prefer one well-reviewed batch over many tiny speculative commits.

## 19. Definition of done

A change is complete when:

- the requested behavior is implemented
- related legacy behavior still works
- player-facing maintained text is English
- data compatibility has been considered
- code follows existing project structure
- relevant build/CI checks pass
- no temporary debug code remains
- the PR/commit description explains the actual change without unnecessary attribution

This file belongs to the maintained Slimefun Legacy fork. Update it whenever the repository's real operating rules materially change.
