# Guide renderer linkage recovery: 2.1.70

A live Paper 26.2 / Slimefun Legacy 4.1.63 report failed while restoring guide history with `NoClassDefFoundError: com/balugaq/jeg/utils/clickhandler/OnDisplay$ItemGroup` in JEG 2.1.69.

## Verified finding and limits

The published 2.1.69 JAR SHA-256 is `08b762197b877d9f2dc85c71456e388df846091754141ddc98da38374685d17b`. Its matching downloaded CI artifact and the standalone release fetched in validation both contain the named class and the renderer's nested classes. Previously built canonical addon archives contain it too. The trace alone therefore does not establish that the released source omitted this class, nor prove which installed/remapped file the running server actually used. An incomplete or replaced loaded JAR, duplicate installation or stale transformed artifact remains an installation diagnosis, not a confirmed cause without the server's actual files.

Version 2.1.70 is a complete replacement with preventive runtime and packaging checks, not a rewrite of guide behavior or saved data.

## Runtime protection

Before any new configuration, managers, item registration or guide takeover, link OnDisplay, OnClick and their declared nested classes through the owning plugin loader. Do not execute their static initialization early. A missing or foreign-loaded class refuses JEG startup with the actual linkage error and recovery instructions; it does not install a broken guide over the core's existing implementation. Early rejection skips teardown of components that were never initialized.

This is a cold-start safety boundary. It does not make hot-replacing a running JAR safe, reload a corrupted class loader, certify plugin-manager reloads, or diagnose every optional integration. Use a clean server stop/start for installation.

## Artifact protection

The shaded build compares every compiled JEG class, including `$` nested/anonymous class files, with the final archive. CI and release validation inspect the actual JAR for required renderer classes, full compiled-class membership, CRC, duplicate entries, version agreement, Java 21 base classes and unwanted unrelocated/test classes. Existing English, guide layout, recipe-use, cheat-click, diagnostics and SlimeHUD checks remain in place. CI and release Actions artifacts now expose the raw JAR too.

Nine Python archive regressions and eight standalone JDK linkage assertions passed. The latter checks nested class loading, missing roots/nested classes, foreign loaders and absence of early static initialization. These are not JUnit/Bukkit mock tests. Full Gradle build passed in run 36956463876; Gradle reported no existing Java unit-test source. The actual new JAR contains 830 base classes; the original has 829. The added class is the guard. Existing Gradle deprecation and unchecked notes remain separate cleanup work.

The audit also removes the exact reported class from a disposable JAR and proves artifact validation rejects it. Real-Paper menu rendering and damaged-addon startup are separately tracked by run 36956670506; do not claim those passed from packaging results alone. Evidence artifact 11205779940 SHA-256: `2a0cc13168aece9779dd1f7fd9d997f2971e841a703a52069f5af914e8bec535`. The eight reviewed source blobs were downloaded and hash-checked before promotion.

## Safe installation

Stop the server completely and back up plugin data. Remove duplicate/older JEG plugin JARs from the plugins root and place only the new raw JAR there. Keep the JustEnoughGuide data folder and all Slimefun data. Start the server and test `/sf open_guide`, category browsing and `/sf cheat`.

If the same missing-class error persists, stop again and remove only cached JEG JAR entries from Paper's `.paper-remapped` directory so Paper can regenerate them. Do not remove the whole plugins directory, bookmarks, player profiles, databases or worlds. Retain the original installed JAR and startup log for checksum/class-loader diagnosis.

No item/research IDs, group keys, bookmarks, recipe layouts, menus, persistent data formats or SlimeHUD behavior were changed. Core upgrades or storage migration are not required for this focused recovery. Temporary validation workflows and deliberately damaged fixtures are excluded from the release branch.
