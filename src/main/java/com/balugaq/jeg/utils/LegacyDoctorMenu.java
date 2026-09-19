/*
 * Slimefun Legacy integration for the maintained SF_JustEnoughGuide fork.
 */
package com.balugaq.jeg.utils;

import com.balugaq.jeg.utils.compatibility.Converter;
import com.balugaq.jeg.utils.formatter.Format;
import com.balugaq.jeg.utils.formatter.Formats;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerProfile;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Slimefun Legacy Doctor shortcuts exposed through JEG.
 *
 * <p>The menu deliberately delegates to Slimefun Legacy's own commands instead of
 * duplicating Doctor or migration logic. This keeps all permission, safety,
 * fingerprint and confirmation checks in the Slimefun core that owns them.</p>
 */
public final class LegacyDoctorMenu {

    private static final String DOCTOR_PERMISSION = "slimefun.command.doctor";

    private LegacyDoctorMenu() {
    }

    public static void renderButton(
        ChestMenu menu,
        Format format,
        PlayerProfile profile,
        Player player
    ) {
        for (int slot : format.getChars(Formats.Char.DOCTOR)) {
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
                    "&7Open read-only health and compatibility",
                    "&7shortcuts for Slimefun Legacy.",
                    "",
                    "&eClick to open"
                )
            );
            menu.addMenuClickHandler(slot, (pl, s, item, action) -> {
                open(pl, profile);
                return false;
            });
        }
    }

    public static void open(Player player, PlayerProfile profile) {
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

        menu.addItem(
            18,
            Converter.getItem(Material.ARROW, "&fBack to Guide", "", "&7Return to the Slimefun guide.")
        );
        menu.addMenuClickHandler(18, (pl, slot, item, action) -> {
            GuideUtil.openMainMenuAsync(pl, GuideUtil.getLastGuideMode(pl), 1);
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
