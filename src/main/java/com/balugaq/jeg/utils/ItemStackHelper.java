/*
 * Local replacement for the small GuizhanLib ItemStack display-name helper.
 */
package com.balugaq.jeg.utils;

import java.util.Locale;
import java.util.StringJoiner;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;

/**
 * ItemStack naming helpers used by search, sharing and guide rendering.
 *
 * <p>Custom display names are preserved exactly. Vanilla items fall back to a
 * readable English name derived from the Bukkit material enum so this helper
 * has no runtime plugin dependency.</p>
 */
public final class ItemStackHelper {

    private ItemStackHelper() {
    }

    public static String getDisplayName(@Nullable ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return "";
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return meta.getDisplayName();
        }

        return humanize(item.getType());
    }

    private static String humanize(Material material) {
        StringJoiner result = new StringJoiner(" ");
        for (String part : material.name().toLowerCase(Locale.ROOT).split("_")) {
            if (part.isEmpty()) {
                continue;
            }
            result.add(Character.toUpperCase(part.charAt(0)) + part.substring(1));
        }
        return result.toString();
    }
}
