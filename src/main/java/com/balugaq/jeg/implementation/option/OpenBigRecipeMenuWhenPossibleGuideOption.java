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
import com.balugaq.jeg.utils.compatibility.Converter;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NullMarked;

/**
 * @author balugaq
 * @since 2.1
 */
@SuppressWarnings({"UnnecessaryUnicodeEscape", "SameReturnValue"})
@NullMarked
public class OpenBigRecipeMenuWhenPossibleGuideOption extends AbstractBooleanGuideOption {
    private static final OpenBigRecipeMenuWhenPossibleGuideOption instance = new OpenBigRecipeMenuWhenPossibleGuideOption();

    public static OpenBigRecipeMenuWhenPossibleGuideOption instance() {
        return instance;
    }

    @Override
    public int priority() {
        return Priorities.OpenBigRecipeMenuWhenPossibleGuideOption;
    }

    @Override
    public ItemStack getDisplayItem(Player p, ItemStack guide, boolean enabled) {
        return Converter.getItem(
            isEnabled(p) ? Material.GOLD_INGOT : Material.IRON_INGOT,
            "&bAuto-Open Large Recipe Menu: &" + (enabled ? "aEnabled" : "4Disabled"),
            "",
            "&7Choose whether compatible items",
            "&7automatically open their large",
            "&7recipe menu when viewed.",
            "",
            "&7\u21E8 &e点击 " + (enabled ? "disable" : "enable") + " Auto-Open Large Recipe Menu"
        );
    }

    public String key0() {
        return "open_big_recipe_menu_when_possible";
    }

    @Override
    public boolean defaultValue() {
        return false;
    }
}
