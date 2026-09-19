/*
 * Copyright (c) 2024-2026 balugaq
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 */

package com.balugaq.jeg.implementation.legacy;

import com.balugaq.jeg.utils.compatibility.Converter;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Operator-facing shortcuts into Slimefun Legacy's read-only Doctor and version diagnostics.
 *
 * <p>This menu intentionally delegates to Slimefun's commands instead of duplicating Doctor logic.
 * That keeps JEG aligned with the exact diagnostics provided by the installed Slimefun Legacy build.
 */
public final class LegacyDiagnosticsMenu {

    private LegacyDiagnosticsMenu() {}

    public static void open(Player player) {
        ChestMenu menu = new ChestMenu("Slimefun Legacy Diagnostics");
        menu.setSize(54);
        menu.setEmptySlotsClickable(false);

        for (int slot = 0; slot < 54; slot++) {
            menu.addItem(slot, ChestMenuUtils.getBackground(), ChestMenuUtils.getEmptyClickHandler());
        }

        add(menu, 10, Material.COMPASS, "&6Doctor Status",
            "&7Storage, item repair and machine-failure overview.",
            "sf doctor status");
        add(menu, 12, Material.REDSTONE_TORCH, "&6Core Health",
            "&7Lifecycle, scheduler, storage and machine runtime.",
            "sf doctor core");
        add(menu, 14, Material.COMPARATOR, "&6Addon Compatibility",
            "&7Compatibility declarations and runtime evidence.",
            "sf doctor compatibility");
        add(menu, 16, Material.CHAIN, "&6Dependencies",
            "&7Missing, disabled or aliased plugin dependencies.",
            "sf doctor dependencies");

        add(menu, 28, Material.CLOCK, "&6Runtime Failures",
            "&7Guarded machine and addon callback failures.",
            "sf doctor runtime");
        add(menu, 30, Material.ENDER_EYE, "&6Integrations",
            "&7Optional external integration capabilities and failures.",
            "sf doctor integrations");
        add(menu, 32, Material.WRITABLE_BOOK, "&6Upgrade Readiness",
            "&7Read-only Legacy upgrade and migration readiness.",
            "sf doctor upgrade");
        add(menu, 34, Material.NAME_TAG, "&6Versions",
            "&7Slimefun, server and installed addon versions.",
            "sf versions");

        menu.addItem(
            49,
            Converter.getItem(Material.BARRIER, "&cClose", "", "&7Close the diagnostics menu."),
            (p, slot, item, action) -> {
                p.closeInventory();
                return false;
            }
        );

        menu.open(player);
    }

    private static void add(
        ChestMenu menu,
        int slot,
        Material material,
        String title,
        String description,
        String command
    ) {
        ItemStack icon = Converter.getItem(
            material,
            title,
            "",
            description,
            "",
            "&eClick to run:",
            ChatColor.WHITE + "/" + command
        );
        menu.addItem(slot, icon, (player, clickedSlot, item, action) -> {
            player.closeInventory();
            player.performCommand(command);
            return false;
        });
    }
}
