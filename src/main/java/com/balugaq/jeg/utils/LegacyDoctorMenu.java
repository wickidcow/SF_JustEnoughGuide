/*
 * Slimefun Legacy integration for the maintained SF_JustEnoughGuide fork.
 */
package com.balugaq.jeg.utils;

import com.balugaq.jeg.api.patches.JEGGuideSettings;
import com.balugaq.jeg.utils.compatibility.Converter;
import com.balugaq.jeg.utils.formatter.Format;
import com.balugaq.jeg.utils.formatter.Formats;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideMode;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

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
            if (!LegacyMachineRecipeBridge.isLegacyAvailable() || !player.hasPermission(DOCTOR_PERMISSION)) {
                menu.addItem(slot, ChestMenuUtils.getBackground(), ChestMenuUtils.getEmptyClickHandler());
                continue;
            }

            menu.addItem(
                slot,
                Converter.getItem(
                    Material.HEART_OF_THE_SEA,
                    "&b&lSlimefun Legacy Doctor",
                    "",
                    "&7Open Slimefun Legacy health and",
                    "&7compatibility diagnostics.",
                    mode == SlimefunGuideMode.CHEAT_MODE ? "&cContext: Cheat Mode" : "&aContext: Survival Mode",
                    "",
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
        if (!player.hasPermission(DOCTOR_PERMISSION)) {
            player.sendMessage(ChatColor.RED + "You do not have permission to use Slimefun Doctor.");
            return;
        }

        ChestMenu menu = new ChestMenu("&b&lSlimefun Legacy Doctor");
        menu.setSize(27);
        menu.setEmptySlotsClickable(false);

        for (int slot = 0; slot < 27; slot++) {
            menu.addItem(slot, ChestMenuUtils.getBackground(), ChestMenuUtils.getEmptyClickHandler());
        }

        addCommand(menu, 9, Material.LIME_DYE, "&aStatus", "doctor status",
            "&7Shutdown state, pending writes,", "&7paused circuits and repair state.");
        addCommand(menu, 10, Material.NETHER_STAR, "&aCore", "doctor core",
            "&7Core and platform health evidence.");
        addCommand(menu, 11, Material.COMPARATOR, "&eCompatibility", "doctor compatibility",
            "&7Addon compatibility declarations", "&7and runtime evidence.");
        addCommand(menu, 12, Material.TRIPWIRE_HOOK, "&eDependencies", "doctor dependencies",
            "&7Missing, disabled or aliased", "&7plugin dependencies.");
        addCommand(menu, 13, Material.REDSTONE, "&6Runtime", "doctor runtime",
            "&7Machine/runtime isolation", "&7and retry state.");
        addCommand(menu, 14, Material.ENDER_EYE, "&6Integrations", "doctor integrations",
            "&7Optional integration capabilities", "&7and failures.");
        addCommand(menu, 15, Material.RECOVERY_COMPASS, "&bUpgrade", "doctor upgrade",
            "&7Upgrade readiness and", "&7migration overview.");
        addCommand(menu, 16, Material.SPYGLASS, "&bItem Scan", "doctor scan",
            "&7Read-only item/storage scan.", "&7No repairs are performed.");

        boolean returnToSettings = settingsGuide != null;
        menu.addItem(
            18,
            Converter.getItem(
                Material.ARROW,
                returnToSettings ? "&fBack to Settings & Info" : "&fBack to Guide",
                "",
                returnToSettings
                    ? "&7Return to the current guide's Settings & Info."
                    : "&7Return to the Slimefun Legacy guide."
            )
        );
        menu.addMenuClickHandler(18, (pl, slot, item, action) -> {
            if (returnToSettings) {
                JEGGuideSettings.openSettings(pl, settingsGuide, mode);
            } else {
                GuideUtil.openMainMenuAsync(pl, mode, 1);
            }
            return false;
        });

        menu.addItem(
            22,
            Converter.getItem(
                Material.WRITABLE_BOOK,
                "&fDoctor Safety",
                "",
                "&7These buttons call Slimefun Legacy's",
                "&7own guarded Doctor commands.",
                "",
                "&cRepair and migration actions still",
                "&crequire their normal explicit",
                "&cconfirmation/fingerprint workflow."
            ),
            ChestMenuUtils.getEmptyClickHandler()
        );

        menu.open(player);
    }

    private static void addCommand(
        ChestMenu menu,
        int slot,
        Material material,
        String name,
        String command,
        String... lore
    ) {
        menu.addItem(slot, Converter.getItem(material, name, lore));
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
