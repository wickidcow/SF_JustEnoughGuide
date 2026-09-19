/*
 * Slimefun Legacy migration support for the maintained SF_JustEnoughGuide fork.
 */
package com.balugaq.jeg.utils;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

/**
 * Imports compatible settings from Slimefun Legacy's former native enhanced guide.
 *
 * <p>This importer is only called when JEG did not already have a config.yml.
 * Existing JEG installations are never overwritten.</p>
 */
public final class LegacyConfigMigration {

    private LegacyConfigMigration() {
    }

    public static void importFreshInstall(JavaPlugin plugin) {
        File pluginsFolder = plugin.getDataFolder().getParentFile();
        if (pluginsFolder == null) {
            return;
        }

        File legacyFile = new File(pluginsFolder, "Slimefun/enhanced-guide.yml");
        File targetFile = new File(plugin.getDataFolder(), "config.yml");
        if (!legacyFile.isFile() || !targetFile.isFile()) {
            return;
        }

        YamlConfiguration legacy = YamlConfiguration.loadConfiguration(legacyFile);
        YamlConfiguration target = YamlConfiguration.loadConfiguration(targetFile);

        int imported = 0;
        imported += copyString(legacy, target, "titles.survival", "guide.survival-guide-title");
        imported += copyString(legacy, target, "titles.cheat", "guide.cheat-guide-title");
        imported += copyBoolean(legacy, target, "features.bookmarks", "improvements.bookmark");
        imported += copyBoolean(legacy, target, "features.display-item-id", "improvements.slimefun-id-display");

        target.set("data.legacy-enhanced-guide-imported", true);

        try {
            target.save(targetFile);
            if (imported > 0) {
                plugin.getLogger().info(
                    "Imported " + imported
                        + " compatible settings from Slimefun Legacy enhanced-guide.yml."
                );
            }
        } catch (IOException exception) {
            plugin.getLogger().log(
                Level.WARNING,
                "Could not save the imported Slimefun Legacy enhanced-guide settings.",
                exception
            );
        }
    }

    private static int copyString(
        YamlConfiguration source,
        YamlConfiguration target,
        String sourcePath,
        String targetPath
    ) {
        if (!source.isString(sourcePath)) {
            return 0;
        }

        String value = source.getString(sourcePath);
        if (value == null || value.isBlank()) {
            return 0;
        }

        target.set(targetPath, value);
        return 1;
    }

    private static int copyBoolean(
        YamlConfiguration source,
        YamlConfiguration target,
        String sourcePath,
        String targetPath
    ) {
        if (!source.isBoolean(sourcePath)) {
            return 0;
        }

        target.set(targetPath, source.getBoolean(sourcePath));
        return 1;
    }
}
