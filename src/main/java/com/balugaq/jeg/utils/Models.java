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

package com.balugaq.jeg.utils;

import com.balugaq.jeg.utils.compatibility.Converter;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * @author balugaq
 * @since 1.3
 */
public class Models {
    public static final String RECIPE_COMPLETE_BOOK_MECHANISM_1 = "&aLeft-click &eto open the recipe book";
    public static final String RECIPE_COMPLETE_BOOK_MECHANISM_2 = "&aRight-click &eto repeat the last completion";
    public static final String RECIPE_COMPLETE_BOOK_MECHANISM_3 = "&eAfter completing once, &aShift + right-click &ethe recipe book to fill up to 64 sets";
    public static final String RECIPE_COMPLETE_GUI_MECHANISM_1 = "&aLeft-click &ean item to fill 1 set";
    public static final String RECIPE_COMPLETE_GUI_MECHANISM_2 = "&aRight-click &ean item to fill up to 64 sets";
    public static final ItemStack RTS_ITEM =
        Converter.getItem(new SlimefunItemStack("_UI_RTS_ICON", Converter.getItem(Material.ANVIL, "&bReal-Time Search", "")));
    public static final ItemStack SPECIAL_MENU_ITEM = Converter.getItem(new SlimefunItemStack(
        "_UI_SPECIAL_MENU_ICON", Converter.getItem(Material.COMPASS, "&bLarge Recipe", "", "&aClick to open the large recipe, when available")));
    public static final ItemStack INPUT_TEXT_ICON = Converter.getItem(new SlimefunItemStack(
        "_UI_RTS_INPUT_TEXT_ICON",
        Converter.getItem(
            Material.PAPER,
            "&fSearch: &7enter a search term above",
            "&fTips:",
            "&7 - &eLeft item: back",
            "&7 - &eMiddle item: previous page",
            "&7 - &eRight item: next page"
        )
    ));
    public static final ItemStack JEG_GUIDE_GROUP = Converter.getItem(
        new SlimefunItemStack("JEG_JEG_GUIDE_GROUP", Converter.getItem(Material.KNOWLEDGE_BOOK, "&bJEG Advanced Guide Help")));
    public static final ItemStack HIDDEN_ITEMS_GROUP = Converter.getItem(
        new SlimefunItemStack("JEG_HIDDEN_ITEMS_GROUP", Converter.getItem(Material.BARRIER, "&cHidden Items")));
    public static final ItemStack NEXCAVATE_ITEMS_GROUP = Converter.getItem(new SlimefunItemStack(
        "JEG_NEXCAVATE_ITEMS_GROUP_ICON", Converter.getItem(Material.BLACKSTONE, "&bNexcavate Items")));
    public static final ItemStack VANILLA_ITEMS_GROUP = Converter.getItem(
        new SlimefunItemStack("JEG_VANILLA_ITEMS_GROUP", Converter.getItem(Material.CRAFTING_TABLE, "&7Vanilla Items")));
    public static final ItemStack RECIPE_COMPLETABLE_GROUP = Converter.getItem(
        new SlimefunItemStack("JEG_RECIPE_COMPLETABLE_GROUP", Converter.getItem(Material.CRAFTING_TABLE, "&bRecipe-Compatible Machines")));
    public static final ItemStack JEG_ITEMS_GROUP = Converter.getItem(
        new SlimefunItemStack("JEG_JEG_ITEMS_GROUP", Converter.getItem(Material.BOOK, "&bRecipe Completion Book")));
    public static final ItemStack REPLACEMENT_CARDS_GROUP = Converter.getItem(
        new SlimefunItemStack("JEG_REPLACEMENT_CARDS_GROUP", Converter.getItem(Material.PAPER, "&bReplacement Cards - Companion Items")));
    public static final ItemStack BANNED_ITEMS_GROUP = Converter.getItem(
        new SlimefunItemStack("JEG_BANNED_ITEMS_GROUP", Converter.getItem(Material.COMMAND_BLOCK, "&cDisabled Items")));
    public static final ItemStack MULTI_BLOCK_BUILDER_ITEMS_GROUP = Converter.getItem(
        new SlimefunItemStack("MULTI_BLOCK_BUILDER_ITEMS_GROUP", Converter.getItem(Material.BRICKS, "&bMultiblock Building Items")));
    public static final ItemStack KEYBIND_ACTION_BORDER = Converter.getItem(
        Material.YELLOW_STAINED_GLASS_PANE, " ",
        " "
    );
    public static final SlimefunItemStack RECIPE_COMPLETE_GUIDE = new SlimefunItemStack(
        "JEG_RECIPE_COMPLETE_BOOK",
        Converter.getItem(
            Material.SLIME_BALL,
            "&bRecipe Completion Book",
            "",
            "&fClick to start recipe completion (see the guide below)",
            RECIPE_COMPLETE_BOOK_MECHANISM_1,
            RECIPE_COMPLETE_BOOK_MECHANISM_2,
            RECIPE_COMPLETE_BOOK_MECHANISM_3
        )
    );
    public static final SlimefunItemStack USAGE_INFO = new SlimefunItemStack(
        "JEG_RECIPE_COMPLETE_USAGE_INFO",
        Converter.getItem(
            Material.PAPER,
            "&aHow to Use",
            "",
            "&f1. &eKeep the Recipe Completion Book in your inventory",
            "&f2. &eOpen a supported machine inventory",
            "&f3. &eLeft-click the Recipe Completion Book",
            "&f4. &eSelect the item whose recipe you want to fill"
        )
    );
    public static final SlimefunItemStack MECHANISM = new SlimefunItemStack(
        "JEG_RECIPE_COMPLETE_MECHANISM",
        Converter.getItem(
            Material.PAPER,
            "&aHow It Works",
            "",
            "&7Materials are taken from the player's inventory first",
            "&7If a supported Networks system is connected, materials may be pulled from it",
            "&7If a supported AE network is connected, materials may be pulled from it",
            "&7Connection rules:",
            "&7A network touching any face of the target machine is considered connected",
            "&7The integration does not consume an additional network node",
            "",
            "&9=== Recipe Book Controls ===",
            RECIPE_COMPLETE_BOOK_MECHANISM_1,
            RECIPE_COMPLETE_BOOK_MECHANISM_2,
            RECIPE_COMPLETE_BOOK_MECHANISM_3,
            "&9=== Completion Menu Controls ===",
            RECIPE_COMPLETE_GUI_MECHANISM_1,
            RECIPE_COMPLETE_GUI_MECHANISM_2
        )
    );
    public static final SlimefunItemStack SUPPORTED_ADDONS_INFO = new SlimefunItemStack(
        "JEG_RECIPE_COMPLETE_SUPPORTED_ADDONS_INFO",
        Converter.getItem(
            Material.PAPER,
            "&aRecipe completion supports selected machines from these addons",
            "&7Request additional compatibility on the SF_JustEnoughGuide GitHub issue tracker",
            "",
            "&7- &aSlimefun multiblocks",
            "&7- &aFastMachines",
            "&7- &aFinalTECH 2.0 Preview",
            "&7- &aFinalTECH 2.0",
            "&7- &aFinalTECH 2.0 Modified",
            "&7- &aInfinityExpansion",
            "&7- &aInfinityExpansion2",
            "&7- &aLogiTech",
            "&7- &aNetworks",
            "&7- &aNetworksExpansion",
            "&7- &aObsidianExpansion",
            "&7- &aSlimeAE",
            "&7- &aFluffyMachines",
            "&7- &aSlimeTinker",
            "&7- &aGalactifun",
            "&7- &aGastronomicon",
            "&7- &aRykenSlimefunCustomizer",
            "&7- &aBedrockTechnology",
            "&7- &aAlchimiaVitae",
            "&7- &aClayTech",
            "&7- &aDankTech",
            "&7- &aSimpleUtils",
            "&7- &aCultivation",
            "&7- &aElementManipulation",
            "&7- &aInfinityCompress",
            "&7- &aMagic",
            "&7- &aTsingshanTechnology",
            "&7- &aWilderNether"
        )
    );

    public static final SlimefunItemStack JEG_RECIPE_COMPLETE_BUTTON = new SlimefunItemStack(
        "JEG_RECIPE_COMPLETE_BUTTON",
        Material.KNOWLEDGE_BOOK,
        "&6Recipe Completion",
        "&7Click to open recipe completion"
    );

    public static final ItemStack ITEM_MARK_BACKGROUND = Converter.getItem(
        Material.GREEN_STAINED_GLASS_PANE,
        "&a&lAdd Bookmark",
        "",
        "&7Left-click an item to add it to bookmarks"
    );

    public static final ItemStack SLIMEFUN_RECIPE_EDIT = Converter.getItem(
        Material.DIAMOND,
        "&a&lSlimeFunRecipe Recipe Editor",
        "",
        "&eClick to open the recipe editor"
    );

    public static final SlimefunItemStack CUSTOM_LAG_BLOCK = new SlimefunItemStack(
        "JEG_CUSTOM_LAG_BLOCK",
        Converter.getItem(
            Material.COMMAND_BLOCK,
            "&aCustom Timer",
            "&aPlace and open this item to configure the machine's Slimefun tick delay",
            "&cOperators only"
    ));
}
