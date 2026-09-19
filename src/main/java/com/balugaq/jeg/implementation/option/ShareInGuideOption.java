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

package com.balugaq.jeg.implementation.option;

import com.balugaq.jeg.api.patches.Priorities;
import com.balugaq.jeg.utils.JEGVersionedItemFlag;
import com.balugaq.jeg.utils.compatibility.Converter;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NullMarked;

/**
 * @author balugaq
 * @since 1.9
 */
@SuppressWarnings({"UnnecessaryUnicodeEscape", "SameReturnValue"})
@NullMarked
public class ShareInGuideOption extends AbstractBooleanGuideOption {
    private static final ShareInGuideOption instance = new ShareInGuideOption();

    public static ShareInGuideOption instance() {
        return instance;
    }

    @Override
    public int priority() {
        return Priorities.ShareInGuideOption;
    }

    public String key0() {
        return "share_in";
    }

    @Override
    public ItemStack getDisplayItem(Player p, ItemStack guide, boolean enabled) {
        return Converter.getItem(
            Converter.getItem(
                Material.WRITTEN_BOOK,
                meta -> meta.addItemFlags(JEGVersionedItemFlag.HIDE_ADDITIONAL_TOOLTIP)
            ),
            "&bReceive Shared Items: &" + (enabled ? "aEnabled" : "4Disabled"),
            "",
            "&7Choose whether to receive",
            "&7guide notifications when another",
            "&7player shares an item with you.",
            "",
            "&7\u21E8 &e点击 " + (enabled ? "disable" : "enable") + " Receive Shared Items"
        );
    }
}
