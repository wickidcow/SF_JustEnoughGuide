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

import com.balugaq.jeg.api.patches.JEGGuideSettings;
import com.balugaq.jeg.api.patches.Priorities;
import com.balugaq.jeg.api.patches.PrioritySlimefunGuideOption;
import com.balugaq.jeg.implementation.JustEnoughGuide;
import com.balugaq.jeg.utils.Calculator;
import com.balugaq.jeg.utils.KeyUtil;
import com.balugaq.jeg.utils.compatibility.Converter;
import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon;
import io.github.thebusybiscuit.slimefun4.libraries.dough.chat.ChatInput;
import io.github.thebusybiscuit.slimefun4.libraries.dough.common.ChatColors;
import io.github.thebusybiscuit.slimefun4.libraries.dough.data.persistent.PersistentDataAPI;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NullMarked;

import java.util.Optional;

import static com.balugaq.jeg.utils.RecipeCompletionUtils.RECIPE_DEPTH_THRESHOLD;

/**
 * @author balugaq
 * @since 1.9
 */
@SuppressWarnings({"UnnecessaryUnicodeEscape", "SameReturnValue"})
@NullMarked
public class RecursiveRecipeFillingGuideOption implements PrioritySlimefunGuideOption<Integer> {
    private static final RecursiveRecipeFillingGuideOption instance = new RecursiveRecipeFillingGuideOption();

    public static RecursiveRecipeFillingGuideOption instance() {
        return instance;
    }

    @Override
    public int priority() {
        return Priorities.RecursiveRecipeFillingGuideOption;
    }

    public static NamespacedKey key0() {
        return KeyUtil.newKey("recursive_recipe_filling");
    }

    public static int getDepth(Player p) {
        return PersistentDataAPI.getInt(p, key0(), 1);
    }

    @Override
    public SlimefunAddon getAddon() {
        return JustEnoughGuide.getInstance();
    }

    @Override
    public Optional<ItemStack> getDisplayItem(Player p, ItemStack guide) {
        int value = getSelectedOption(p, guide).orElse(1);
        if (value > RECIPE_DEPTH_THRESHOLD) {
            value = RECIPE_DEPTH_THRESHOLD;
            PersistentDataAPI.setInt(p, key0(), value);
        }

        ItemStack item = Converter.getItem(
            Material.FURNACE,
            "&aRecursive Recipe Depth",
            "&7Higher depth can take longer to resolve.",
            "&7When an ingredient is missing, JEG can try",
            "&7to complete that ingredient's recipe recursively.",
            "&e&lExperimental feature; use with care.",
            "&c&lComplex addon recipes may not be suitable.",
            "",
            "&7Current depth: " + value + " (allowed: 1-" + RECIPE_DEPTH_THRESHOLD + ")",
            "&7\u21E8 &eClick to set depth"
        );
        return Optional.of(item);
    }

    @Override
    public void onClick(Player p, ItemStack guide) {
        p.closeInventory();
        p.sendMessage(ChatColors.color("&aEnter the recipe completion recursion depth."));
        ChatInput.waitForPlayer(
            JustEnoughGuide.getInstance(), p, s -> {
                try {
                    int value = Calculator.calculate(s).intValue();
                    if (value < 1 || value > RECIPE_DEPTH_THRESHOLD) {
                        p.sendMessage("Enter a positive integer from 1 to " + RECIPE_DEPTH_THRESHOLD + ".");
                        return;
                    }

                    setSelectedOption(p, guide, value);
                    JEGGuideSettings.openSettings(p, guide);
                } catch (NumberFormatException ignored) {
                    p.sendMessage("Enter a positive integer from 1 to " + RECIPE_DEPTH_THRESHOLD + ".");
                }
            }
        );
    }

    @Override
    public NamespacedKey getKey() {
        return key0();
    }

    @Override
    public Optional<Integer> getSelectedOption(Player p, ItemStack guide) {
        return Optional.of(getDepth(p));
    }

    @Override
    public void setSelectedOption(Player p, ItemStack guide, Integer value) {
        PersistentDataAPI.setInt(p, getKey(), value);
    }
}
