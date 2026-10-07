# Offline profile load diagnostic failure

## Reproduction

The published 2.1.71 JAR throws a NullPointerException from `GuideHistoryPatchListener.onProfileLoad` when Slimefun loads an offline profile without a live Player. The diagnostic expression dereferences `event.getProfile().getPlayer().getName()` before the actual guide-history patch callback executes. Debug settings do not avoid Java argument evaluation.

This was observed using real Paper servers and hash-verified published artifacts in Slimefun Legacy PR #348, run **37637049986**. Downloaded evidence includes:

- Paper 1.21.11, artifact **11490656312**: the event error precedes a separate fixture-only ItemStack-copy failure during the old-release seed phase.
- Paper 26.2, artifact **11489733203**, SHA-256 `c1e6966b65124032129ca759359a67c8c79d7f91a4054de49007841d9b0596a9`.
- Paper 26.3, artifact **11490407068**, SHA-256 `6a3d7f4b1195af9db7f6c80542212556b745c2fe7312fc24d9a92440fcf92222`.

The 26.2/26.3 fixtures completed all four persistence phases with 125 registered sample IDs from 41 item-owning groups, four backpacks, five barrels and 324 checked slots per verification phase. **Each boot still logged the JEG event exception.** Those successful storage assertions are not an error-free runtime result. The error occurs with both the delivered 4.1.69 refresh and 4.1.70 because both bundles include JEG 2.1.71; it is not uniquely introduced by the 4.1.70 core.

## Scoped correction

Capture the supplied PlayerProfile once, invoke the existing `GuideUtil.getProfile(profile)` patch path, and log its UUID only after the callback returns. Do not inspect a live Player from this asynchronous handler. Keep offline patching enabled, keep the existing join callback, and do not swallow patch exceptions or change guide-history/storage formats.

This patch does not alter item/research IDs, recipes, menus, bookmark formats, guide ownership, scheduler submission, core code or published addon pins. The package remains a candidate until validation; no already-published 2.1.71 or 4.1.70 assets are replaced by this branch.

## Validation boundary

The source-contract verifier has eight offline self-tests, including rejection of the original null dereference, skipped patching and premature success logging. These are lightweight structural regression checks, not a simulated Bukkit implementation or proof of real server behavior. The normal Gradle build and genuine Paper/Slimefun runtime checks remain required. Re-run an offline-profile load with the actual patched JAR and verify that the history patch callback completes without the exception before stable publication.
