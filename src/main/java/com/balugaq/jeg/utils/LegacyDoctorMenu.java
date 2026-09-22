/*
 * Slimefun Legacy integration for the maintained SF_JustEnoughGuide fork.
 */
package com.balugaq.jeg.utils;

import com.balugaq.jeg.api.patches.JEGGuideSettings;
import com.balugaq.jeg.utils.compatibility.Converter;
import com.balugaq.jeg.utils.formatter.Format;
import com.balugaq.jeg.utils.formatter.Formats;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuide;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideMode;
import io.github.thebusybiscuit.slimefun4.core.guide.options.SlimefunGuideSettings;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Slimefun Legacy Doctor shortcuts exposed through JEG.
 *
 * <p>The menu deliberately delegates to Slimefun Legacy's own commands instead of
 * duplicating Doctor or ticker logic. This keeps permission and safety checks in
 * the Slimefun core that owns them.</p>
 */
public final class LegacyDoctorMenu {

    private static final String DOCTOR_PERMISSION = "slimefun.command.doctor";
    private static final String TICK_PERMISSION = "slimefun.command.tick";

    private LegacyDoctorMenu() {
    }

    /**
     * Recovery Center access is intentionally limited to Slimefun operators/admins.
     * The Slimefun permission defaults to OP and can also be granted explicitly by
     * the server's permission plugin.
     */
    public static boolean canAccessRecoveryCenter(Player player) {
        return LegacyMachineRecipeBridge.isLegacyAvailable() && player.hasPermission(DOCTOR_PERMISSION);
    }

    /**
     * Renders the Doctor shortcut on normal JEG guide screens.
     */
    public static void renderButton(
        ChestMenu menu,
        Format format,
        Player player
    ) {
        renderDoctorButton(
            menu,
            player,
            GuideUtil.getLastGuideMode(player),
            null,
            resolveDoctorSlots(format)
        );
    }

    /**
     * Renders Slimefun Legacy diagnostics entries on Settings & Info.
     *
     * <p>Older saved JEG layouts may not contain the dedicated Doctor or Tick Top
     * format characters. In that case, the maintained fork uses unused top-row
     * background slots so server owners do not have to delete or regenerate
     * their existing config.</p>
     *
     * <p>When {@code settingsGuide} is non-null, the Doctor back button returns
     * to Settings & Info instead of the main guide.</p>
     */
    public static void renderButton(
        ChestMenu menu,
        Format format,
        Player player,
        SlimefunGuideMode mode,
        @Nullable ItemStack settingsGuide
    ) {
        List<Integer> doctorSlots = resolveDoctorSlots(format);
        renderDoctorButton(menu, player, mode, settingsGuide, doctorSlots);

        if (settingsGuide != null) {
            renderTickTopButton(menu, player, mode, resolveTickTopSlots(format, doctorSlots));
        }
    }

    private static void renderDoctorButton(
        ChestMenu menu,
        Player player,
        SlimefunGuideMode mode,
        @Nullable ItemStack settingsGuide,
        List<Integer> slots
    ) {
        for (int slot : slots) {
            if (!canAccessRecoveryCenter(player)) {
                menu.addItem(slot, ChestMenuUtils.getBackground(), ChestMenuUtils.getEmptyClickHandler());
                continue;
            }

            menu.addItem(
                slot,
                Converter.getItem(
                    Material.TOTEM_OF_UNDYING,
                    "&6&lSlimefun Recovery Center",
                    "",
                    "&7Open the Slimefun Legacy recovery hub.",
                    "&7Safe checks can be run by clicking.",
                    "&7Repair commands are shown exactly as",
                    "&7commands for the server owner to type.",
                    "",
                    "&8OP/Admin only",
                    "&8Permission: slimefun.command.doctor",
                    mode == SlimefunGuideMode.CHEAT_MODE ? "&8Guide context: Cheat Mode" : "&8Guide context: Survival Mode",
                    "&eClick to open"
                )
            );
            ItemStack returnGuide = settingsGuide == null ? null : settingsGuide.clone();
            menu.addMenuClickHandler(slot, (pl, s, item, action) -> {
                open(pl, mode, returnGuide);
                return false;
            });
        }
    }

    private static void renderTickTopButton(
        ChestMenu menu,
        Player player,
        SlimefunGuideMode mode,
        List<Integer> slots
    ) {
        for (int slot : slots) {
            if (!LegacyMachineRecipeBridge.isLegacyAvailable() || !player.hasPermission(TICK_PERMISSION)) {
                menu.addItem(slot, ChestMenuUtils.getBackground(), ChestMenuUtils.getEmptyClickHandler());
                continue;
            }

            menu.addItem(
                slot,
                Converter.getItem(
                    Material.CLOCK,
                    "&6&lSlimefun Tick Top",
                    "",
                    "&7Show the busiest Slimefun tickers",
                    "&7using Slimefun Legacy's profiler.",
                    mode == SlimefunGuideMode.CHEAT_MODE ? "&cContext: Cheat Mode" : "&aContext: Survival Mode",
                    "",
                    "&eClick to run /sf tick top"
                )
            );
            menu.addMenuClickHandler(slot, (pl, s, item, action) -> {
                pl.closeInventory();
                boolean handled = pl.performCommand("sf tick top");
                if (!handled) {
                    pl.sendMessage(ChatColor.RED + "Slimefun Legacy did not accept /sf tick top.");
                }
                return false;
            });
        }
    }

    private static List<Integer> resolveDoctorSlots(Format format) {
        List<Integer> configured = format.getChars(Formats.Char.DOCTOR);
        if (!configured.isEmpty()) {
            return configured;
        }

        List<Integer> topRowBackgrounds = getTopRowBackgroundSlots(format);
        if (!topRowBackgrounds.isEmpty()) {
            return List.of(topRowBackgrounds.getLast());
        }

        return List.of();
    }

    private static List<Integer> resolveTickTopSlots(Format format, List<Integer> doctorSlots) {
        List<Integer> configured = format.getChars(Formats.Char.TICK_TOP);
        if (!configured.isEmpty()) {
            return configured;
        }

        for (int slot : getTopRowBackgroundSlots(format)) {
            if (!doctorSlots.contains(slot)) {
                return List.of(slot);
            }
        }

        return List.of();
    }

    private static List<Integer> getTopRowBackgroundSlots(Format format) {
        return format.getChars(Formats.Char.BACKGROUND).stream()
            .filter(slot -> slot >= 0 && slot < 9)
            .toList();
    }

    public static void open(Player player) {
        open(player, GuideUtil.getLastGuideMode(player), null);
    }

    public static void open(
        Player player,
        SlimefunGuideMode mode,
        @Nullable ItemStack settingsGuide
    ) {
        if (!canAccessRecoveryCenter(player)) {
            player.sendMessage(ChatColor.RED + "The Slimefun Recovery Center is restricted to server operators/admins.");
            return;
        }

        if (tryOpenNativeRecoveryCenter(player, mode, settingsGuide)) {
            return;
        }

        ChestMenu menu = new ChestMenu("&6&lSlimefun Recovery Center");
        menu.setSize(36);
        menu.setEmptySlotsClickable(false);

        for (int slot = 0; slot < 36; slot++) {
            menu.addItem(slot, ChestMenuUtils.getBackground(), ChestMenuUtils.getEmptyClickHandler());
        }

        menu.addItem(
            4,
            Converter.getItem(
                Material.WRITABLE_BOOK,
                "&6&lRecovery Command Guide",
                "",
                "&7Green/yellow buttons below run safe",
                "&7diagnostic commands and show output in chat.",
                "",
                "&cRepair, migration and texture-ID changes",
                "&care never run silently from this menu.",
                "&7Their exact commands are shown below."
            ),
            ChestMenuUtils.getEmptyClickHandler()
        );

        addCommand(menu, 9, Material.LIME_DYE, "&aDoctor Status", "doctor status",
            "&7Check Doctor activity, database writes,", "&7shutdown state and current health.");
        addCommand(menu, 10, Material.SPYGLASS, "&aFull Doctor Scan", "doctor scan",
            "&7Read-only server-wide item/migration scan.", "&7Start here when something looks wrong.");
        addCommand(menu, 11, Material.CLOCK, "&6Tick Top", "tick top",
            "&7Show the busiest Slimefun tickers.", "&7Useful for lag/TPS investigations.");
        addCommand(menu, 12, Material.COMPARATOR, "&eCompatibility", "doctor compatibility",
            "&7Check addon compatibility declarations", "&7and runtime compatibility evidence.");
        addCommand(menu, 13, Material.TRIPWIRE_HOOK, "&eDependencies", "doctor dependencies",
            "&7Find missing, disabled or aliased", "&7plugin dependencies.");
        addCommand(menu, 14, Material.REDSTONE, "&6Runtime", "doctor runtime",
            "&7Inspect machine/runtime isolation", "&7and retry state.");
        addCommand(menu, 15, Material.ENDER_EYE, "&6Integrations", "doctor integrations",
            "&7Inspect optional integration state", "&7and failures.");
        addCommand(menu, 16, Material.PAPER, "&fSupport Report", "doctor report",
            "&7Print a compact support snapshot", "&7for troubleshooting or bug reports.");

        addInfo(menu, 19, Material.ANVIL, "&aCore Item Presentation Repair",
            "&7After reviewing &f/sf doctor scan&7:",
            "",
            "&eType:",
            "&f/sf doctor repair confirm",
            "",
            "&8Repairs only core-safe names/lore that",
            "&8Doctor can prove safe.");

        addInfo(menu, 20, Material.SMITHING_TABLE, "&6Enable / Upgrade Resource-Pack Items",
            "&7Audit first:",
            "&f/sf doctor item-models enable-pack scan",
            "",
            "&eIf the audit is correct, type:",
            "&f/sf doctor item-models enable-pack confirm",
            "",
            "&8Adds exact Legacy bundled model mappings",
            "&8and updates eligible stored items.");

        addInfo(menu, 21, Material.GRINDSTONE, "&dRemove Resource-Pack Item Models",
            "&7Removes only Legacy's exact bundled texture mappings.",
            "&8Does NOT unregister Slimefun items or machines.",
            "&7Audit the removal:",
            "&f/sf doctor item-models remove-resourcepack-texture-ids",
            "",
            "&eTo confirm, type:",
            "&f/sf doctor item-models remove-resourcepack-texture-ids confirm",
            "",
            "&cDo not use if your resource pack still",
            "&cdepends on the Legacy model mappings.");

        addInfo(menu, 22, Material.BOOKSHELF, "&bAddon Schema Repairs",
            "&7Find addon-owned migration candidates:",
            "",
            "&eType:",
            "&f/sf doctor migrations schemas scan",
            "",
            "&8Then run the exact fingerprinted",
            "&8execute command printed by Doctor.");

        addInfo(menu, 23, Material.NAME_TAG, "&eUnknown Slimefun IDs",
            "&7Correlate unknown IDs with registered",
            "&7addon migration providers.",
            "",
            "&eType:",
            "&f/sf doctor migrations unknown",
            "",
            "&8Doctor will not guess unknown ownership.");

        addInfo(menu, 24, Material.RECOVERY_COMPASS, "&bUpgrade / Legacy-ID Planning",
            "&7Review upgrade and legacy-ID migration",
            "&7readiness before changing stored data.",
            "",
            "&eType:",
            "&f/sf doctor upgrade plan",
            "&f/sf doctor migrations plan");

        addInfo(menu, 25, Material.COMPARATOR, "&eResource Pack Item Texture Repairs",
            "&7Check for stale resource-pack item texture data when",
            "&7storage/machine matching or item textures are broken.",
            "",
            "&eType:",
            "&f/sf doctor item-models scan",
            "",
            "&7If candidates are correct, type:",
            "&f/sf doctor item-models repair confirm");

        boolean returnToSettings = settingsGuide != null;
        menu.addItem(
            27,
            Converter.getItem(
                Material.ARROW,
                returnToSettings ? "&fBack to Settings & Info" : "&fBack to Guide",
                "",
                returnToSettings
                    ? "&7Return to the current guide's Settings & Info."
                    : "&7Return to the Slimefun Legacy guide."
            )
        );
        menu.addMenuClickHandler(27, (pl, slot, item, action) -> {
            if (returnToSettings) {
                JEGGuideSettings.openSettings(pl, settingsGuide, mode);
            } else {
                GuideUtil.openMainMenuAsync(pl, mode, 1);
            }
            return false;
        });

        menu.addItem(
            31,
            Converter.getItem(
                Material.KNOWLEDGE_BOOK,
                "&fRecommended Order",
                "",
                "&71. &f/sf doctor status",
                "&72. &f/sf doctor scan",
                "&73. Follow the &eSlimefun Doctor Next Steps",
                "&74. Run only the specialist command it names",
                "&75. Re-run &f/sf doctor scan &7after repairs",
                "",
                "&8Use /sf tick top separately for performance."
            ),
            ChestMenuUtils.getEmptyClickHandler()
        );

        menu.open(player);
    }

    /**
     * Slimefun Legacy exposes its own richer Recovery Center. Prefer that native
     * menu when present so JEG and the classic guide always show the same tools.
     * Older/non-Legacy Slimefun builds fall back to JEG's compact command guide.
     */
    private static boolean tryOpenNativeRecoveryCenter(
        Player player,
        SlimefunGuideMode mode,
        @Nullable ItemStack settingsGuide
    ) {
        try {
            Method method = SlimefunGuideSettings.class.getMethod(
                "openDoctorTools",
                Player.class,
                ItemStack.class
            );
            ItemStack returnGuide = settingsGuide == null
                ? SlimefunGuide.getItem(mode)
                : settingsGuide.clone();
            method.invoke(null, player, returnGuide);
            return true;
        } catch (NoSuchMethodException | LinkageError ignored) {
            return false;
        } catch (IllegalAccessException | InvocationTargetException ex) {
            player.sendMessage(ChatColor.YELLOW
                + "Native Recovery Center could not be opened; using the JEG fallback menu.");
            return false;
        }
    }

    private static void addInfo(
        ChestMenu menu,
        int slot,
        Material material,
        String name,
        String... lore
    ) {
        menu.addItem(slot, Converter.getItem(material, name, lore), ChestMenuUtils.getEmptyClickHandler());
    }

    private static void addCommand(
        ChestMenu menu,
        int slot,
        Material material,
        String name,
        String command,
        String... lore
    ) {
        String[] displayLore = new String[lore.length + 3];
        System.arraycopy(lore, 0, displayLore, 0, lore.length);
        displayLore[lore.length] = "";
        displayLore[lore.length + 1] = "&8Command: &f/sf " + command;
        displayLore[lore.length + 2] = "&eClick to run";
        menu.addItem(slot, Converter.getItem(material, name, displayLore));
        menu.addMenuClickHandler(slot, (player, s, item, action) -> {
            player.closeInventory();
            boolean handled = player.performCommand("sf " + command);
            if (!handled) {
                player.sendMessage(ChatColor.RED + "Slimefun Legacy did not accept /sf " + command + '.');
            }
            return false;
        });
    }
}
