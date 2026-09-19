/*
 * Copyright (c) 2024-2026 balugaq
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 */

package com.balugaq.jeg.implementation;

import com.balugaq.jeg.api.CustomGroupConfigurations;
import com.balugaq.jeg.api.cost.please_set_cer_patch_to_false_in_config_when_you_see_this.CERCalculator;
import com.balugaq.jeg.api.cost.please_set_cer_patch_to_false_in_config_when_you_see_this.ValueTable;
import com.balugaq.jeg.api.editor.GroupResorter;
import com.balugaq.jeg.api.groups.SearchGroup;
import com.balugaq.jeg.api.multiblock.MultiBlockBuilder;
import com.balugaq.jeg.api.patches.JEGGuideSettings;
import com.balugaq.jeg.api.recipe_complete.source.RecipeCompleteProvider;
import com.balugaq.jeg.core.integrations.finaltechs.finalTECHCommon.FinalTECHValueDisplayGuideOption;
import com.balugaq.jeg.core.listeners.RecipeCompletableListener;
import com.balugaq.jeg.core.listeners.SlimefunRegistryFinalizeListener;
import com.balugaq.jeg.core.managers.BookmarkManager;
import com.balugaq.jeg.core.managers.CommandManager;
import com.balugaq.jeg.core.managers.ConfigManager;
import com.balugaq.jeg.core.managers.IntegrationManager;
import com.balugaq.jeg.core.managers.ListenerManager;
import com.balugaq.jeg.core.managers.RTSBackpackManager;
import com.balugaq.jeg.implementation.groups.GroupSetup;
import com.balugaq.jeg.implementation.groups.VanillaItemsGroup;
import com.balugaq.jeg.implementation.guide.CheatGuideImplementation;
import com.balugaq.jeg.implementation.guide.SurvivalGuideImplementation;
import com.balugaq.jeg.implementation.items.ItemsSetup;
import com.balugaq.jeg.implementation.items.ReplacementCardAdapter;
import com.balugaq.jeg.utils.Debug;
import com.balugaq.jeg.utils.GuideUtil;
import com.balugaq.jeg.utils.LegacyConfigMigration;
import com.balugaq.jeg.utils.MinecraftVersion;
import com.balugaq.jeg.utils.ReflectionUtil;
import com.balugaq.jeg.utils.SlimefunRegistryUtil;
import com.balugaq.jeg.utils.SpecialMenuProvider;
import com.balugaq.jeg.utils.UUIDUtils;
import com.balugaq.jeg.utils.formatter.Formats;
import com.balugaq.jeg.utils.platform.PlatformUtil;
import com.balugaq.jeg.utils.platform.scheduler.TaskScheduler;
import com.tcoded.folialib.FoliaLib;
import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideImplementation;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideMode;
import io.github.thebusybiscuit.slimefun4.core.guide.options.SlimefunGuideOption;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.guide.CheatSheetSlimefunGuide;
import io.github.thebusybiscuit.slimefun4.implementation.guide.SurvivalSlimefunGuide;
import io.github.thebusybiscuit.slimefun4.utils.NumberUtils;
import lombok.Getter;
import net.byteflux.libby.BukkitLibraryManager;
import net.byteflux.libby.Library;
import net.byteflux.libby.LibraryManager;
import net.kyori.adventure.internal.properties.AdventureProperties;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;
import org.jspecify.annotations.NullMarked;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * This is the main class of the JustEnoughGuide plugin. It depends on the Slimefun4 plugin and provides a set of
 * features to improve the game experience.
 *
 * @author balugaq
 * @since 1.0
 */
@SuppressWarnings({"unused", "deprecation", "ResultOfMethodCallIgnored", "removal"})
@Getter
@NullMarked
public class JustEnoughGuide extends JavaPlugin implements SlimefunAddon {
    public static final int RECOMMENDED_JAVA_VERSION = 21;
    public static final int LEAST_JAVA_VERSION = 21;
    public static final MinecraftVersion RECOMMENDED_MC_VERSION = MinecraftVersion.V1_21_10;
    public static final MinecraftVersion LEAST_MC_VERSION = MinecraftVersion.V1_16;

    @Getter
    @UnknownNullability
    private static JustEnoughGuide instance = null;

    @Getter
    @UnknownNullability
    private static UUID serverUUID = null;

    @Getter
    private final String author;

    @Getter
    private final String repo;

    @Getter
    private final String branch;

    @UnknownNullability
    private BookmarkManager bookmarkManager = null;

    @UnknownNullability
    private CommandManager commandManager = null;

    @UnknownNullability
    private ConfigManager configManager = null;

    @UnknownNullability
    private IntegrationManager integrationManager = null;

    @UnknownNullability
    private ListenerManager listenerManager = null;

    @UnknownNullability
    private RTSBackpackManager rtsBackpackManager = null;

    @UnknownNullability
    private MinecraftVersion minecraftVersion = null;

    @UnknownNullability
    private TaskScheduler scheduler = null;

    @Getter
    @UnknownNullability
    private JEGMetrics metrics = null;

    @Getter
    @UnknownNullability
    private FoliaLib foliaLib = null;

    @Getter
    private int javaVersion = 0;

    private final Map<SlimefunGuideMode, SlimefunGuideImplementation> previousGuides =
        new EnumMap<>(SlimefunGuideMode.class);
    private final Map<SlimefunGuideMode, SlimefunGuideImplementation> installedGuides =
        new EnumMap<>(SlimefunGuideMode.class);

    public JustEnoughGuide() {
        this.author = "wickidcow";
        this.repo = "SF_JustEnoughGuide";
        this.branch = "master";
    }

    public static BookmarkManager getBookmarkManager() {
        return getInstance().bookmarkManager;
    }

    public static CommandManager getCommandManager() {
        return getInstance().commandManager;
    }

    public static ListenerManager getListenerManager() {
        return getInstance().listenerManager;
    }

    public static IntegrationManager getIntegrationManager() {
        return getInstance().integrationManager;
    }

    public static MinecraftVersion getMinecraftVersion() {
        return getInstance().minecraftVersion;
    }

    public static void postServerStartup(Runnable runnable) {
        JustEnoughGuide.runAsync(runnable);
    }

    public static void runAsync(Runnable runnable) {
        getScheduler().runAsync(runnable);
    }

    public static TaskScheduler getScheduler() {
        return getInstance().scheduler;
    }

    public static void postServerStartupAsynchronously(Runnable runnable) {
        JustEnoughGuide.runLaterAsync(runnable, 1L);
    }

    public static void runLaterAsync(Runnable runnable, long delay) {
        getScheduler().runLaterAsync(runnable, delay);
    }

    public static void runLaterAsync(Supplier<?> callable, long delay) {
        getScheduler().runLaterAsync(callable, delay);
    }

    public static boolean disableAutomaticallyLoadItems() {
        boolean before = Slimefun.getConfigManager().isAutoLoadingEnabled();
        Slimefun.getConfigManager().setAutoLoadingMode(false);
        return before;
    }

    public static void setAutomaticallyLoadItems(boolean value) {
        Slimefun.getConfigManager().setAutoLoadingMode(value);
    }

    public static void runLater(Runnable runnable, long delay) {
        getScheduler().runLater(runnable, delay);
    }

    public static void runTimer(Runnable runnable, long delay, long period) {
        getScheduler().runTimer(runnable, delay, period);
    }

    public static void runTimerAsync(Runnable runnable, long delay, long period) {
        getScheduler().runTimerAsync(runnable, delay, period);
    }

    public static void reload(CommandSender sender) {
        var plugin = getInstance();
        sender.sendMessage(ChatColor.GREEN + "Reloading plugin...");
        try {
            if (plugin == null) {
                sender.sendMessage(ChatColor.RED + "Failed to reload plugin.");
                return;
            }

            plugin.unloadInternal();
            plugin.onEnable();
            plugin.reloadConfig();
            SlimefunRegistryFinalizeListener.getTasks().forEach(Runnable::run);
            SlimefunRegistryFinalizeListener.clearTasks();
            Debug.info("Plugin reloaded.");
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Failed to reload plugin.");
            Debug.trace(e);
        }
    }

    public static ConfigManager getConfigManager() {
        return getInstance().configManager;
    }

    /**
     * Returns the JavaPlugin instance.
     *
     * @return the JavaPlugin instance
     */
    @Override
    public JavaPlugin getJavaPlugin() {
        return this;
    }

    /**
     * Returns the bug tracker URL for the plugin.
     *
     * @return the bug tracker URL
     */
    @Nullable
    @Override
    public String getBugTrackerURL() {
        return MessageFormat.format("https://github.com/{0}/{1}/issues/", this.author, this.repo);
    }

    /**
     * Logs a debug message if debugging is enabled.
     *
     * @param message the debug message to log
     */
    public void debug(String message) {
        Debug.debug(message);
    }

    public String getVersion() {
        return getDescription().getVersion();
    }

    /**
     * Initializes the plugin and sets up all necessary components.
     */
    @Override
    public void onEnable() {
        instance = this;

        if (!Boolean.TRUE.equals(AdventureProperties.TEXT_WARN_WHEN_LEGACY_FORMATTING_DETECTED.value())) {
            Debug.warn("=======================================================================");
            Debug.warn("Detected net.kyori.adventure.text.warnWhenLegacyFormattingDetected = false");
            Debug.warn("To avoid excessive legacy-formatting warnings, add this JVM argument:                 ");
            Debug.warn("-Dnet.kyori.adventure.text.warn_when_legacy_formatting_detected=false  ");
            Debug.warn("See https://docs.papermc.io/paper/reference/system-properties/#netkyoriadventuretextwarnwhenlegacyformattingdetected");
            Debug.warn("=======================================================================");
        }

        // Checking environment compatibility
        boolean isCompatible = environmentCheck();

        if (!isCompatible) {
            getLogger().warning("The environment is incompatible. The plugin has been disabled.");
            onDisable();
            return;
        }

        this.foliaLib = new FoliaLib(JustEnoughGuide.getInstance());
        PlatformUtil.initialize();
        this.scheduler = TaskScheduler.create();

        getLogger().info("Loading dependencies...");
        loadLibraries();

        getLogger().info("Loading configuration...");
        boolean freshConfig = !new File(getDataFolder(), "config.yml").isFile();
        saveDefaultConfig();
        if (freshConfig) {
            LegacyConfigMigration.importFreshInstall(this);
            reloadConfig();
        }
        this.configManager = new ConfigManager(this);
        this.configManager.load();
        Formats.load();

        getLogger().info("Registering listeners...");
        this.listenerManager = new ListenerManager(this);
        this.listenerManager.load();

        getLogger().info("Registering commands...");
        this.commandManager = new CommandManager(this);
        this.commandManager.load();

        getLogger().info("Installing the enhanced guide...");
        Map<SlimefunGuideMode, SlimefunGuideImplementation> newGuides = new EnumMap<>(SlimefunGuideMode.class);
        newGuides.put(SlimefunGuideMode.SURVIVAL_MODE, new SurvivalGuideImplementation());
        newGuides.put(SlimefunGuideMode.CHEAT_MODE, new CheatGuideImplementation());
        installGuides(newGuides);

        getLogger().info("Loading bookmarks...");
        this.bookmarkManager = new BookmarkManager(this);
        this.bookmarkManager.load();

        getLogger().info("Loading item groups...");
        GroupSetup.setup();
        JustEnoughGuide.runLaterAsync(CustomGroupConfigurations::load, 1L);

        if (getConfigManager().isCerPatch()) {
            getLogger().info("Value/efficiency display is enabled.");
            CERCalculator.load();
            ValueTable.load();
        }

        ItemsSetup.setup(this);

        this.rtsBackpackManager = new RTSBackpackManager(this);
        this.rtsBackpackManager.load();

        setupServerUUID();
        SearchGroup.load();
        GroupResorter.load();
        SpecialMenuProvider.load();
        ReplacementCardAdapter.load();
        MultiBlockBuilder.load();
        ThirdPartyWarnings.check();
        IntegrationManager.scheduleRun(JEGGuideSettings::sortOptions);

        getLogger().info("Loading addon integrations...");
        this.integrationManager = new IntegrationManager(this);
        this.integrationManager.load();

        getLogger().info("Loading metrics...");
        metrics = new JEGMetrics();

        getLogger().info("SF_JustEnoughGuide enabled successfully.");
    }

    public void unloadInternal() {
        CustomGroupConfigurations.unload();
        GroupResorter.rollback();

        GroupSetup.shutdown();
        RecipeCompleteProvider.shutdown();
        GuideUtil.shutdown();

        /**
         * Unregister all {@link SlimefunItem}
         *
         * @see VanillaItemsGroup
         * @see ItemsSetup#RECIPE_COMPLETE_GUIDE
         */
        SlimefunRegistryUtil.unregisterItems(JustEnoughGuide.getInstance());

        try {
            List<SlimefunGuideOption<?>> l = JEGGuideSettings.getOptions();
            List<SlimefunGuideOption<?>> copy = new ArrayList<>(l);
            for (SlimefunGuideOption<?> option : copy) {
                if (option.getAddon() instanceof JustEnoughGuide) {
                    l.remove(option);
                }
            }
            JEGGuideSettings.unpatchSlimefun();
            FinalTECHValueDisplayGuideOption.setBooted(false);
        } catch (Exception ignored) {
        }

        restoreGuides();

        // Managers
        if (this.bookmarkManager != null) {
            this.bookmarkManager.unload();
        }

        if (this.integrationManager != null) {
            this.integrationManager.unload();
        }

        if (this.commandManager != null) {
            this.commandManager.unload();
        }

        if (this.listenerManager != null) {
            this.listenerManager.unload();
        }

        if (this.rtsBackpackManager != null) {
            this.rtsBackpackManager.unload();
        }

        if (this.metrics != null) {
            this.metrics.shutdown();
        }

        if (this.configManager != null) {
            this.configManager.unload();
        }

        ReplacementCardAdapter.getReplacementCards().clear();
        SearchGroup.LOADED = false;
    }

    /**
     * Cleans up resources and shuts down the plugin.
     */
    @Override
    public void onDisable() {
        unloadInternal();

        this.bookmarkManager = null;
        this.integrationManager = null;
        this.commandManager = null;
        this.listenerManager = null;
        this.rtsBackpackManager = null;
        this.metrics = null;
        this.configManager = null;
        Debug.setPlugin(null);

        // Other fields
        this.minecraftVersion = null;
        this.javaVersion = 0;

        // Clear instance
        instance = null;
        getLogger().info("SF_JustEnoughGuide disabled successfully.");
    }

    /**
     * Checks if debugging is enabled.
     *
     * @return true if debugging is enabled, false otherwise
     */
    public boolean isDebug() {
        return getConfigManager().isDebug();
    }

    /**
     * Checks the environment compatibility for the plugin.
     *
     * @return true if the environment is compatible, false otherwise
     */
    private boolean environmentCheck() {
        this.minecraftVersion = MinecraftVersion.current();
        this.javaVersion = NumberUtils.getJavaVersion();
        if (minecraftVersion == null) {
            getLogger().warning("Unable to determine the Minecraft version!");
            return false;
        }

        if (minecraftVersion == MinecraftVersion.UNKNOWN) {
            getLogger().warning("Unrecognized Minecraft version! (" + javaVersion + ")");
        } else if (!minecraftVersion.isAtLeast(LEAST_MC_VERSION)) {
            getLogger()
                .warning("Minecraft version is below the supported minimum (" + minecraftVersion.humanize() + "), 请使用 Minecraft "
                    + RECOMMENDED_MC_VERSION.humanize() + " 或以上版本!");
        }

        if (javaVersion < LEAST_JAVA_VERSION) {
            getLogger().warning("Java version is too old. Use Java " + RECOMMENDED_JAVA_VERSION + " or newer!");
        }

        return true;
    }

    private void installGuides(Map<SlimefunGuideMode, SlimefunGuideImplementation> newGuides) {
        previousGuides.clear();
        installedGuides.clear();

        for (SlimefunGuideMode mode : SlimefunGuideMode.values()) {
            try {
                previousGuides.put(mode, Slimefun.getRegistry().getSlimefunGuide(mode));
            } catch (RuntimeException ignored) {
                // A missing mode will simply have nothing to restore.
            }
        }

        try {
            var method = Slimefun.getRegistry()
                .getClass()
                .getMethod("registerSlimefunGuide", SlimefunGuideMode.class, SlimefunGuideImplementation.class);
            for (var entry : newGuides.entrySet()) {
                method.invoke(Slimefun.getRegistry(), entry.getKey(), entry.getValue());
                installedGuides.put(entry.getKey(), entry.getValue());
            }
            getLogger().info("Installed JEG through Slimefun's public guide registration API.");
            return;
        } catch (NoSuchMethodException ignored) {
            // Upstream/older Slimefun fallback below.
        } catch (ReflectiveOperationException | LinkageError e) {
            Debug.trace(e);
        }

        try {
            ReflectionUtil.setValue(Slimefun.getRegistry(), "guides", new EnumMap<>(newGuides));
            installedGuides.putAll(newGuides);
            getLogger().info("Installed JEG using the compatibility guide-registry fallback.");
        } catch (Exception e) {
            Debug.trace(e);
        }
    }

    private void restoreGuides() {
        if (previousGuides.isEmpty()) {
            return;
        }

        boolean restoredWithApi = false;
        try {
            var method = Slimefun.getRegistry()
                .getClass()
                .getMethod(
                    "compareAndSetSlimefunGuide",
                    SlimefunGuideMode.class,
                    SlimefunGuideImplementation.class,
                    SlimefunGuideImplementation.class
                );

            restoredWithApi = true;
            for (var entry : previousGuides.entrySet()) {
                SlimefunGuideImplementation installed = installedGuides.get(entry.getKey());
                if (installed == null) {
                    continue;
                }
                method.invoke(Slimefun.getRegistry(), entry.getKey(), installed, entry.getValue());
            }
        } catch (NoSuchMethodException ignored) {
            // Upstream/older Slimefun fallback below.
        } catch (ReflectiveOperationException | LinkageError e) {
            Debug.trace(e);
        }

        if (!restoredWithApi) {
            try {
                Map<SlimefunGuideMode, SlimefunGuideImplementation> restored =
                    new EnumMap<>(SlimefunGuideMode.class);
                restored.putAll(previousGuides);
                ReflectionUtil.setValue(Slimefun.getRegistry(), "guides", restored);
            } catch (Exception e) {
                Debug.trace(e);
            }
        }

        installedGuides.clear();
        previousGuides.clear();
    }

    private void setupServerUUID() {
        File uuidFile = new File(getDataFolder(), "server-uuid");
        Path path = Path.of(uuidFile.getPath());
        if (uuidFile.exists()) {
            try {
                serverUUID = UUID.nameUUIDFromBytes(Files.readAllBytes(path));
            } catch (IOException e) {
                Debug.warn(e);
            }
        } else {
            serverUUID = UUID.randomUUID();
            try {
                getDataFolder().mkdirs();
                uuidFile.createNewFile();
                Files.write(path, UUIDUtils.toByteArray(serverUUID));
            } catch (IOException e) {
                Debug.warn(e);
            }
        }
    }

    private void loadLibraries() {
        LibraryManager libraryManager = new BukkitLibraryManager(this);
        libraryManager.addMavenCentral();

        getLogger().info("Loading Pinyin support");
        Library pinyin = Library.builder()
            .groupId("com{}github{}houbb")
            .artifactId("pinyin")
            .version("0.4.0")
            .build();
        libraryManager.loadLibrary(pinyin);

        getLogger().info("Loading opencc4j");
        Library opencc4j = Library.builder()
            .groupId("com{}github{}houbb")
            .artifactId("opencc4j")
            .version("1.14.0")
            .build();
        libraryManager.loadLibrary(opencc4j);

        getLogger().info("Loading heaven");
        Library heaven = Library.builder()
            .groupId("com{}github{}houbb")
            .artifactId("heaven")
            .version("0.13.0")
            .build();
        libraryManager.loadLibrary(heaven);

        getLogger().info("Loading nlp-common");
        Library nlp = Library.builder()
            .groupId("com{}github{}houbb")
            .artifactId("nlp-common")
            .version("0.0.5")
            .build();
        libraryManager.loadLibrary(nlp);
    }
}
