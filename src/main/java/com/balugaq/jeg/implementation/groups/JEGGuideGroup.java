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

package com.balugaq.jeg.implementation.groups;

import com.balugaq.jeg.api.groups.ClassicGuideGroup;
import com.balugaq.jeg.api.interfaces.JEGSlimefunGuideImplementation;
import com.balugaq.jeg.api.interfaces.NotDisplayInCheatMode;
import com.balugaq.jeg.api.objects.enums.FilterType;
import com.balugaq.jeg.api.objects.exceptions.ArgumentMissingException;
import com.balugaq.jeg.implementation.JustEnoughGuide;
import com.balugaq.jeg.implementation.option.BeginnersGuideOption;
import com.balugaq.jeg.utils.Debug;
import com.balugaq.jeg.utils.GuideUtil;
import com.balugaq.jeg.utils.compatibility.Converter;
import com.balugaq.jeg.utils.formatter.Formats;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerProfile;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideImplementation;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideMode;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An implementation of the ClassicGuideGroup for JEG.
 *
 * @author balugaq
 * @since 1.3
 */
@Getter
@NotDisplayInCheatMode
@NullMarked
public class JEGGuideGroup extends ClassicGuideGroup {
    public static final ItemStack HEADER = Converter.getItem(
        Material.BEACON, "&bJEG 使用指南", "&b作者: 大香蕉", "&7Legacy fork maintained by wickidcow", "&bBrowse the entries below to learn JEG's enhanced guide features.");
    public static final int[] GUIDE_SLOTS =
        Formats.helper.getChars('h').stream().mapToInt(i -> i).toArray();

    public static final int[] BORDER_SLOTS =
        Formats.helper.getChars('B').stream().mapToInt(i -> i).toArray();

    @SuppressWarnings("SameParameterValue")
    protected JEGGuideGroup(NamespacedKey key, ItemStack icon) {
        super(key, icon, Integer.MAX_VALUE);
        for (int slot : BORDER_SLOTS) {
            addGuide(slot, ChestMenuUtils.getBackground());
        }
        boolean loaded = false;
        for (int s : Formats.helper.getChars('A')) {
            addGuide(s, HEADER);
            loaded = true;
        }

        if (!loaded) {
            // Well... the user removed my author information
            throw new ArgumentMissingException(
                "You're not supposed to remove symbol 'A'... Which means Author Information. " + "format="
                    + Formats.helper);
        }

        final AtomicInteger index = new AtomicInteger(0);
        doIf(
            JustEnoughGuide.getConfigManager().isPinyinSearch(),
            () -> addGuide(
                GUIDE_SLOTS[index.getAndIncrement()],
                Converter.getItem(Material.CLOCK, "&bFeature: Pinyin Search", "&7Search Chinese item names by pinyin when this optional feature is enabled.", "&bClick to try this feature."),
                (p, s, i, a) -> {
                    try {
                        p.performCommand("sf search ding");
                    } catch (Exception e) {
                        p.sendMessage("§cUnable to perform this action. Verify that Slimefun is installed and enabled.");
                        Debug.trace(e);
                    }
                    return false;
                }
            )
        );

        addGuide(
            GUIDE_SLOTS[index.getAndIncrement()],
            Converter.getItem(Material.NAME_TAG, "&bFeature: Search Pagination", "&7Browse multiple pages of search results.", "&bClick to try this feature."),
            (p, s, i, a) -> {
                try {
                    p.performCommand("sf search a");
                } catch (Exception e) {
                    p.sendMessage("§cUnable to perform this action. Verify that Slimefun is installed and enabled.");
                    Debug.trace(e);
                }
                return false;
            }
        );

        doIf(
            JustEnoughGuide.getConfigManager().isBookmark(),
            () -> addGuide(
                GUIDE_SLOTS[index.getAndIncrement()],
                Converter.getItem(
                    Material.BOOK,
                    "&bFeature: Mark Items",
                    "&7Open a supported item group and enter item-marking mode.",
                    "&7Use the book icon at the bottom of the item-group page.",
                    "&aUse the back button to leave marking mode.",
                    "&bClick to try this feature."
                ),
                (p, s, i, a) -> {
                    try {
                        if (Slimefun.instance() == null) {
                            p.sendMessage("§cSlimefun is unavailable, so this feature cannot be used.");
                        }

                        SlimefunGuideImplementation guide =
                            GuideUtil.getGuide(p, SlimefunGuideMode.SURVIVAL_MODE);

                        if (!(guide instanceof JEGSlimefunGuideImplementation jegGuide)) {
                            p.sendMessage("§cThis feature is not enabled.");
                            return false;
                        }

                        PlayerProfile profile = PlayerProfile.find(p).orElse(null);
                        if (profile == null) {
                            p.sendMessage("§cCould not load your Slimefun player profile.");
                            return false;
                        }

                        for (ItemGroup itemGroup :
                            new ArrayList<>(Slimefun.getRegistry().getAllItemGroups())) {
                            if (itemGroup
                                .getKey()
                                .equals(new NamespacedKey(Slimefun.instance(), "basic_machines"))) {
                                jegGuide.openItemMarkGroup(itemGroup, p, profile);
                                return false;
                            }
                        }
                    } catch (Exception e) {
                        p.sendMessage("§cUnable to perform this action. Verify that Slimefun is installed and enabled.");
                        Debug.trace(e);
                    }
                    return false;
                }
            )
        );

        doIf(
            JustEnoughGuide.getConfigManager().isBookmark(),
            () -> addGuide(
                GUIDE_SLOTS[index.getAndIncrement()],
                Converter.getItem(
                    Material.NETHER_STAR,
                    "&bFeature: View Bookmarks",
                    "&7Browse items you previously bookmarked.",
                    "&7Use the nether-star icon to open your bookmarks.",
                    "&aUse the back button to leave the bookmark view.",
                    "&bClick to try this feature."
                ),
                (p, s, i, a) -> {
                    try {
                        if (Slimefun.instance() == null) {
                            p.sendMessage("§cSlimefun is unavailable, so this feature cannot be used.");
                        }

                        SlimefunGuideImplementation guide =
                            GuideUtil.getGuide(p, SlimefunGuideMode.SURVIVAL_MODE);
                        if (!(guide instanceof JEGSlimefunGuideImplementation jegGuide)) {
                            p.sendMessage("§cThis feature is not enabled.");
                            return false;
                        }

                        PlayerProfile profile = PlayerProfile.find(p).orElse(null);
                        if (profile == null) {
                            p.sendMessage("§cCould not load your Slimefun player profile.");
                            return false;
                        }

                        jegGuide.openBookMarkGroup(p, profile);
                    } catch (Exception e) {
                        p.sendMessage("§cUnable to perform this action. Verify that Slimefun is installed and enabled.");
                        Debug.trace(e);
                    }
                    return false;
                }
            )
        );

        addGuide(
            GUIDE_SLOTS[index.getAndIncrement()],
            Converter.getItem(
                Material.CRAFTING_TABLE,
                "&bFeature: Jump to Item Group",
                "&7While viewing a recipe, jump directly to an ingredient's item group.",
                "&7Shift + left-click an ingredient to use this shortcut.",
                "&bClick to try this feature."
            ),
            (p, s, i, a) -> {
                try {
                    if (Slimefun.instance() == null) {
                        p.sendMessage("§cSlimefun is unavailable, so this feature cannot be used.");
                        return false;
                    }

                    SlimefunGuideImplementation guide = GuideUtil.getGuide(p, SlimefunGuideMode.SURVIVAL_MODE);
                    if (!(guide instanceof JEGSlimefunGuideImplementation jegGuide)) {
                        p.sendMessage("§cThis feature is not enabled.");
                        return false;
                    }

                    PlayerProfile profile = PlayerProfile.find(p).orElse(null);
                    if (profile == null) {
                        p.sendMessage("§cCould not load your Slimefun player profile.");
                        return false;
                    }

                    SlimefunItem exampleItem = SlimefunItems.ELECTRIC_DUST_WASHER_3.getItem();
                    if (exampleItem == null) {
                        p.sendMessage("§cCould not load the example Slimefun item.");
                        return false;
                    }

                    if (exampleItem.isDisabledIn(p.getWorld())) {
                        p.sendMessage("§cThe example item is disabled in this world.");
                        return false;
                    }

                    jegGuide.displayItem(profile, exampleItem, true);
                } catch (Exception e) {
                    p.sendMessage("§cUnable to perform this action. Verify that Slimefun is installed and enabled.");
                    Debug.trace(e);
                }
                return false;
            }
        );

        addGuide(
            GUIDE_SLOTS[index.getAndIncrement()],
            Converter.getItem(
                Material.NAME_TAG,
                "&bFeature: Quick Search",
                "&7While viewing recipes, quickly search item, ingredient, or recipe-type names.",
                "&7Shift + right-click a displayed item to search for it.",
                "&bClick to try this feature."
            ),
            (p, s, i, a) -> {
                try {
                    if (Slimefun.instance() == null) {
                        p.sendMessage("§cSlimefun is unavailable, so this feature cannot be used.");
                        return false;
                    }

                    SlimefunGuideImplementation guide = GuideUtil.getGuide(p, SlimefunGuideMode.SURVIVAL_MODE);
                    if (!(guide instanceof JEGSlimefunGuideImplementation jegGuide)) {
                        p.sendMessage("§cThis feature is not enabled.");
                        return false;
                    }

                    PlayerProfile profile = PlayerProfile.find(p).orElse(null);
                    if (profile == null) {
                        p.sendMessage("§cCould not load your Slimefun player profile.");
                        return false;
                    }

                    if (!BeginnersGuideOption.instance().isEnabled(p)) {
                        p.sendMessage("§cEnable Beginner Guide in JEG settings to use this example.");
                        return false;
                    }

                    SlimefunItem exampleItem = SlimefunItems.ELECTRIC_DUST_WASHER_3.getItem();
                    if (exampleItem == null) {
                        p.sendMessage("§cCould not load the example Slimefun item.");
                        return false;
                    }

                    if (exampleItem.isDisabledIn(p.getWorld())) {
                        p.sendMessage("§cThe example item is disabled in this world.");
                        return false;
                    }

                    jegGuide.displayItem(profile, exampleItem, true);
                } catch (Exception e) {
                    p.sendMessage("§cUnable to perform this action. Verify that Slimefun is installed and enabled.");
                    Debug.trace(e);
                }
                return false;
            }
        );

        doIf(
            Slimefun.getConfigManager().isResearchingEnabled(),
            () -> addGuide(
                GUIDE_SLOTS[index.getAndIncrement()],
                Converter.getItem(
                    Material.ENCHANTED_BOOK,
                    "&bFeature: Portable Research",
                    "&7Unlock eligible research directly while browsing recipes.",
                    "&bClick to try this feature."
                ),
                (p, s, i, a) -> {
                    try {
                        if (Slimefun.instance() == null) {
                            p.sendMessage("§cSlimefun is unavailable, so this feature cannot be used.");
                            return false;
                        }

                        SlimefunGuideImplementation guide =
                            GuideUtil.getGuide(p, SlimefunGuideMode.SURVIVAL_MODE);
                        if (!(guide instanceof JEGSlimefunGuideImplementation jegGuide)) {
                            p.sendMessage("§cThis feature is not enabled.");
                            return false;
                        }

                        PlayerProfile profile = PlayerProfile.find(p).orElse(null);
                        if (profile == null) {
                            p.sendMessage("§cCould not load your Slimefun player profile.");
                            return false;
                        }

                        SlimefunItem exampleItem = SlimefunItems.ELECTRIC_DUST_WASHER_3.getItem();
                        if (exampleItem == null) {
                            p.sendMessage("§cCould not load the example Slimefun item.");
                            return false;
                        }

                        if (exampleItem.isDisabledIn(p.getWorld())) {
                            p.sendMessage("§cThe example item is disabled in this world.");
                            return false;
                        }

                        jegGuide.displayItem(profile, exampleItem, true);
                    } catch (Exception e) {
                        p.sendMessage("§cUnable to perform this action. Verify that Slimefun is installed and enabled.");
                        Debug.trace(e);
                    }
                    return false;
                }
            )
        );

        addGuide(
            GUIDE_SLOTS[index.getAndIncrement()],
            Converter.getItem(
                Material.COMPARATOR,
                "&bFeature: Smart Search",
                "&7Search also finds relevant machines and recipe relationships.",
                "&cThis filter does not use pinyin matching.",
                "&bClick to try this feature."
            ),
            (p, s, i, a) -> {
                try {
                    p.performCommand("sf search battery");
                } catch (Exception e) {
                    p.sendMessage("§cUnable to perform this action. Verify that Slimefun is installed and enabled.");
                    Debug.trace(e);
                }
                return false;
            }
        );

        String flag_recipe_item_name = FilterType.BY_RECIPE_ITEM_NAME.getFirstSymbol();
        addGuide(
            GUIDE_SLOTS[index.getAndIncrement()],
            Converter.getItem(
                Material.LODESTONE,
                "&bFeature: Search Filters",
                "&7Use " + flag_recipe_item_name + "<recipe_item_name> to filter by recipe ingredient name.",
                "&7Example: " + FilterType.BY_RECIPE_ITEM_NAME.apply("battery") + " finds recipes using items whose name contains 'battery'.",
                "&c      不支持拼音搜索。",
                "&cMultiple filters can be combined.",
                "&bClick to try this feature."
            ),
            (p, s, i, a) -> {
                try {
                    p.performCommand("sf search " + FilterType.BY_RECIPE_ITEM_NAME.apply("battery"));
                } catch (Exception e) {
                    p.sendMessage("§cUnable to perform this action. Verify that Slimefun is installed and enabled.");
                    Debug.trace(e);
                }
                return false;
            }
        );

        String flag_recipe_type_name = FilterType.BY_RECIPE_TYPE_NAME.getFirstSymbol();
        addGuide(
            GUIDE_SLOTS[index.getAndIncrement()],
            Converter.getItem(
                Material.LODESTONE,
                "&bFeature: Search Filters",
                "&7Use " + flag_recipe_type_name + "<recipe_type_name> to filter by recipe-type name.",
                "&7Example: " + FilterType.BY_RECIPE_TYPE_NAME.apply("crafting table") + " filters recipes by type name.",
                "&c      不支持拼音搜索。",
                "&cMultiple filters can be combined.",
                "&bClick to try this feature."
            ),
            (p, s, i, a) -> {
                try {
                    p.performCommand("sf search " + FilterType.BY_RECIPE_TYPE_NAME.apply("crafting table"));
                } catch (Exception e) {
                    p.sendMessage("§cUnable to perform this action. Verify that Slimefun is installed and enabled.");
                    Debug.trace(e);
                }
                return false;
            }
        );

        String flag_display_item_name = FilterType.BY_DISPLAY_ITEM_NAME.getFirstSymbol();
        addGuide(
            GUIDE_SLOTS[index.getAndIncrement()],
            Converter.getItem(
                Material.LODESTONE,
                "&bFeature: Search Filters",
                "&7Use " + flag_display_item_name + "<display_item_name> to filter by displayed recipe item name.",
                "&7Example: " + FilterType.BY_DISPLAY_ITEM_NAME.apply("copper dust") + " filters by displayed recipe items.",
                "&c      不支持拼音搜索。",
                "&cMultiple filters can be combined.",
                "&bClick to try this feature."
            ),
            (p, s, i, a) -> {
                try {
                    p.performCommand("sf search " + FilterType.BY_DISPLAY_ITEM_NAME.apply("copper dust"));
                } catch (Exception e) {
                    p.sendMessage("§cUnable to perform this action. Verify that Slimefun is installed and enabled.");
                    Debug.trace(e);
                }
                return false;
            }
        );

        String flag_addon_name = FilterType.BY_ADDON_NAME.getFirstSymbol();
        addGuide(
            GUIDE_SLOTS[index.getAndIncrement()],
            Converter.getItem(
                Material.LODESTONE,
                "&bFeature: Search Filters",
                "&7Use " + flag_addon_name + "<addon_name> to filter by addon name.",
                "&7Example: " + FilterType.BY_ADDON_NAME.apply("Slimefun") + " filters items by addon name.",
                "&c      不支持拼音搜索。",
                "&cMultiple filters can be combined.",
                "&bClick to try this feature."
            ),
            (p, s, i, a) -> {
                try {
                    p.performCommand("sf search " + FilterType.BY_ADDON_NAME.apply("Slimefun"));
                } catch (Exception e) {
                    p.sendMessage("§cUnable to perform this action. Verify that Slimefun is installed and enabled.");
                    Debug.trace(e);
                }
                return false;
            }
        );

        String flag_item_name = FilterType.BY_ITEM_NAME.getFirstSymbol();
        addGuide(
            GUIDE_SLOTS[index.getAndIncrement()],
            Converter.getItem(
                Material.LODESTONE,
                "&bFeature: Search Filters",
                "&7Use " + flag_item_name + "<item_name> to filter by item name.",
                "&7Example: " + FilterType.BY_ITEM_NAME.apply("battery") + " filters item names containing 'battery'.",
                "&bPinyin matching is supported when enabled.",
                "&cMultiple filters can be combined.",
                "&bClick to try this feature."
            ),
            (p, s, i, a) -> {
                try {
                    p.performCommand("sf search " + FilterType.BY_ITEM_NAME.apply("battery"));
                } catch (Exception e) {
                    p.sendMessage("§cUnable to perform this action. Verify that Slimefun is installed and enabled.");
                    Debug.trace(e);
                }
                return false;
            }
        );

        String flag_item_lore = FilterType.BY_ITEM_LORE.getFirstSymbol();
        addGuide(
            GUIDE_SLOTS[index.getAndIncrement()],
            Converter.getItem(
                Material.LODESTONE,
                "&bFeature: Search Filters",
                "&7Use " + flag_item_lore + "<item_lore> to filter by item lore.",
                "&7Example: " + FilterType.BY_ITEM_LORE.apply("carrot") + " filters lore containing 'carrot'.",
                "&bPinyin matching is supported when enabled.",
                "&cMultiple filters can be combined.",
                "&bClick to try this feature."
            ),
            (p, s, i, a) -> {
                try {
                    p.performCommand("sf search " + FilterType.BY_ITEM_LORE.apply("carrot"));
                } catch (Exception e) {
                    p.sendMessage("§cUnable to perform this action. Verify that Slimefun is installed and enabled.");
                    Debug.trace(e);
                }
                return false;
            }
        );

        String flag_material_name = FilterType.BY_MATERIAL_NAME.getFirstSymbol();
        addGuide(
            GUIDE_SLOTS[index.getAndIncrement()],
            Converter.getItem(
                Material.LODESTONE,
                "&bFeature: Search Filters",
                "&7Use " + flag_material_name + "<material_name> to filter by Minecraft material name.",
                "&7Example: " + FilterType.BY_MATERIAL_NAME.apply("iron") + " filters material names containing 'iron'.",
                "&c      不支持拼音搜索。",
                "&cMultiple filters can be combined.",
                "&bClick to try this feature."
            ),
            (p, s, i, a) -> {
                try {
                    p.performCommand("sf search " + FilterType.BY_MATERIAL_NAME.apply("iron"));
                } catch (Exception e) {
                    p.sendMessage("§cUnable to perform this action. Verify that Slimefun is installed and enabled.");
                    Debug.trace(e);
                }
                return false;
            }
        );

        String flag_full_name = FilterType.BY_FULL_NAME.getFirstSymbol();
        addGuide(
            GUIDE_SLOTS[index.getAndIncrement()],
            Converter.getItem(
                Material.LODESTONE,
                "&bFeature: Search Filters",
                "&7Use " + flag_full_name + "<item_name> to require an exact item name.",
                "&7Example: " + FilterType.BY_FULL_NAME.apply("Aluminum Ingot") + " requires the exact name 'Aluminum Ingot'.",
                "&c      不支持拼音搜索。",
                "&cMultiple filters can be combined.",
                "&bClick to try this feature."
            ),
            (p, s, i, a) -> {
                try {
                    p.performCommand("sf search " + FilterType.BY_FULL_NAME.apply("Aluminum Ingot"));
                } catch (Exception e) {
                    p.sendMessage("§cUnable to perform this action. Verify that Slimefun is installed and enabled.");
                    Debug.trace(e);
                }
                return false;
            }
        );

        addGuide(
            GUIDE_SLOTS[index.getAndIncrement()],
            Converter.getItem(
                Material.STONE_PICKAXE, "&bFeature: Item Sharing", "&7Press Q on a guide item to share it with other players.", "&bClick to try the feature"),
            (p, s, i, a) -> {
                try {
                    if (Slimefun.instance() == null) {
                        p.sendMessage("§cSlimefun is unavailable, so this feature cannot be used.");
                        return false;
                    }

                    SlimefunGuideImplementation guide = GuideUtil.getGuide(p, SlimefunGuideMode.SURVIVAL_MODE);
                    if (!(guide instanceof JEGSlimefunGuideImplementation jegGuide)) {
                        p.sendMessage("§cThis feature is not enabled.");
                        return false;
                    }

                    PlayerProfile profile = PlayerProfile.find(p).orElse(null);
                    if (profile == null) {
                        p.sendMessage("§cCould not load your Slimefun player profile.");
                        return false;
                    }

                    if (!BeginnersGuideOption.instance().isEnabled(p)) {
                        p.sendMessage("§cEnable Beginner Guide in JEG settings to use this example.");
                        return false;
                    }

                    SlimefunItem exampleItem = SlimefunItems.ELECTRIC_DUST_WASHER_3.getItem();
                    if (exampleItem == null) {
                        p.sendMessage("§cCould not load the example Slimefun item.");
                        return false;
                    }

                    if (exampleItem.isDisabledIn(p.getWorld())) {
                        p.sendMessage("§cThe example item is disabled in this world.");
                        return false;
                    }

                    jegGuide.displayItem(profile, exampleItem, true);
                } catch (Exception e) {
                    p.sendMessage("§cUnable to perform this action. Verify that Slimefun is installed and enabled.");
                    Debug.trace(e);
                }
                return false;
            }
        );

        Formats.helper.renderCustom(this);
    }

    public static void doIf(boolean expression, Runnable runnable) {
        if (expression) {
            try {
                runnable.run();
            } catch (Exception e) {
                Debug.trace(e, "loading guide group");
            }
        }
    }
}
