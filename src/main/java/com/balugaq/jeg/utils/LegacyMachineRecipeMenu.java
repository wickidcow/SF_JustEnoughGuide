/*
 * Slimefun Legacy machine recipe integration for SF_JustEnoughGuide.
 */
package com.balugaq.jeg.utils;

import com.balugaq.jeg.utils.compatibility.Converter;
import com.balugaq.jeg.utils.formatter.Format;
import com.balugaq.jeg.utils.formatter.Formats;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerProfile;
import io.github.thebusybiscuit.slimefun4.api.recipes.machine.MachineRecipeDisplay;
import io.github.thebusybiscuit.slimefun4.api.recipes.machine.MachineRecipeIngredient;
import io.github.thebusybiscuit.slimefun4.api.recipes.machine.MachineRecipeProvider;
import io.github.thebusybiscuit.slimefun4.api.recipes.machine.MachineRecipeProviderRegistry;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import java.util.ArrayList;
import java.util.List;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Reads Slimefun Legacy's public machine-recipe provider API and renders it in JEG.
 *
 * <p>This class is display-only. Machine input movement remains owned by the
 * hardened Slimefun Legacy fill API/integration layer.</p>
 */
public final class LegacyMachineRecipeMenu {

    private static final int[] RECIPE_SLOTS = {
        9, 10, 11, 12, 13, 14, 15, 16, 17,
        18, 19, 20, 21, 22, 23, 24, 25, 26,
        27, 28, 29, 30, 31, 32, 33, 34, 35
    };

    private LegacyMachineRecipeMenu() {
    }

    public static void renderButton(
        ChestMenu menu,
        Format format,
        PlayerProfile profile,
        Player player,
        SlimefunItem machine
    ) {
        Resolved resolved = resolve(machine, player);
        for (int slot : format.getChars(Formats.Char.MACHINE_RECIPES)) {
            if (resolved == null || resolved.recipes().isEmpty()) {
                continue;
            }

            menu.addItem(
                slot,
                Converter.getItem(
                    Material.KNOWLEDGE_BOOK,
                    "&6&lMachine Recipes",
                    "",
                    "&7Browse everything this machine",
                    "&7can process in the current world.",
                    "",
                    "&7Provider: &f" + resolved.provider().getKey(),
                    "&7Recipes: &f" + resolved.recipes().size(),
                    "",
                    "&eClick to browse"
                )
            );
            menu.addMenuClickHandler(slot, (pl, s, item, action) -> {
                openList(pl, profile, machine, resolved, 1);
                return false;
            });
        }
    }

    private static Resolved resolve(SlimefunItem machine, Player player) {
        for (MachineRecipeProvider provider : MachineRecipeProviderRegistry.getProviders()) {
            try {
                if (!provider.supports(machine)) {
                    continue;
                }
                List<MachineRecipeDisplay> raw = provider.getRecipes(machine, player.getWorld());
                if (raw == null || raw.isEmpty()) {
                    continue;
                }
                List<MachineRecipeDisplay> recipes = raw.stream()
                    .filter(recipe -> recipe != null && !recipe.getOutputs().isEmpty())
                    .toList();
                if (!recipes.isEmpty()) {
                    return new Resolved(provider, recipes);
                }
            } catch (RuntimeException | LinkageError exception) {
                Debug.warn("Machine recipe provider " + provider.getKey()
                    + " failed while rendering " + machine.getId() + ": " + exception.getMessage());
            }
        }
        return null;
    }

    private static void openList(
        Player player,
        PlayerProfile profile,
        SlimefunItem machine,
        Resolved resolved,
        int requestedPage
    ) {
        List<MachineRecipeDisplay> recipes = resolved.recipes();
        int pages = Math.max(1, (recipes.size() + RECIPE_SLOTS.length - 1) / RECIPE_SLOTS.length);
        int page = Math.max(1, Math.min(requestedPage, pages));

        ChestMenu menu = baseMenu("&6Machine Recipes");
        menu.replaceExistingItem(
            4,
            withLore(
                machine.getItem(),
                "",
                ChatColor.GRAY + "Provider: " + ChatColor.WHITE + resolved.provider().getKey(),
                ChatColor.GRAY + "Recipes: " + ChatColor.WHITE + recipes.size(),
                ChatColor.DARK_GRAY + machine.getId()
            )
        );

        menu.replaceExistingItem(0, ChestMenuUtils.getBackButton(player, "", "&7Return to the item recipe"));
        menu.addMenuClickHandler(0, (pl, slot, item, action) -> {
            GuideUtil.getLastGuide(pl).displayItem(profile, machine, false);
            return false;
        });

        int start = (page - 1) * RECIPE_SLOTS.length;
        for (int i = 0; i < RECIPE_SLOTS.length; i++) {
            int recipeIndex = start + i;
            if (recipeIndex >= recipes.size()) {
                break;
            }
            MachineRecipeDisplay recipe = recipes.get(recipeIndex);
            int slot = RECIPE_SLOTS[i];
            menu.replaceExistingItem(slot, recipeIcon(recipe, recipeIndex + 1, recipes.size()));
            menu.addMenuClickHandler(slot, (pl, clickedSlot, item, action) -> {
                openDetail(pl, profile, machine, resolved, recipeIndex);
                return false;
            });
        }

        menu.replaceExistingItem(45, ChestMenuUtils.getPreviousButton(player, page, pages));
        menu.addMenuClickHandler(45, (pl, slot, item, action) -> {
            if (page > 1) {
                openList(pl, profile, machine, resolved, page - 1);
            }
            return false;
        });

        menu.replaceExistingItem(
            49,
            Converter.getItem(Material.PAPER, "&fPage &e" + page + " &7/ &e" + pages)
        );

        menu.replaceExistingItem(53, ChestMenuUtils.getNextButton(player, page, pages));
        menu.addMenuClickHandler(53, (pl, slot, item, action) -> {
            if (page < pages) {
                openList(pl, profile, machine, resolved, page + 1);
            }
            return false;
        });

        menu.open(player);
    }

    private static void openDetail(
        Player player,
        PlayerProfile profile,
        SlimefunItem machine,
        Resolved resolved,
        int recipeIndex
    ) {
        MachineRecipeDisplay recipe = resolved.recipes().get(recipeIndex);
        ChestMenu menu = baseMenu("&6Machine Recipe &f" + (recipeIndex + 1));

        menu.replaceExistingItem(0, ChestMenuUtils.getBackButton(player, "", "&7Return to machine recipes"));
        menu.addMenuClickHandler(0, (pl, slot, item, action) -> {
            openList(pl, profile, machine, resolved, recipeIndex / RECIPE_SLOTS.length + 1);
            return false;
        });

        List<MachineRecipeIngredient> inputs = recipe.getInputs();
        int inputSlot = 9;
        for (int i = 0; i < inputs.size() && inputSlot <= 35; i++, inputSlot++) {
            MachineRecipeIngredient ingredient = inputs.get(i);
            List<ItemStack> choices = ingredient.getChoices();
            if (choices.isEmpty()) {
                continue;
            }
            ItemStack selected = choices.getFirst();
            menu.replaceExistingItem(
                inputSlot,
                withLore(
                    selected,
                    "",
                    ChatColor.AQUA + "Ingredient " + ChatColor.WHITE + (i + 1),
                    ChatColor.GRAY + "Required: " + ChatColor.WHITE + selected.getAmount(),
                    choices.size() > 1
                        ? ChatColor.GRAY + "Alternatives: " + ChatColor.WHITE + choices.size()
                        : ""
                )
            );
        }

        int outputSlot = 46;
        for (ItemStack output : recipe.getOutputs()) {
            if (outputSlot > 52) {
                break;
            }
            menu.replaceExistingItem(
                outputSlot++,
                withLore(output, "", ChatColor.GREEN + "Machine output")
            );
        }

        List<String> info = new ArrayList<>();
        info.add(ChatColor.GRAY + "Inputs: " + ChatColor.WHITE + recipe.getInputs().size());
        info.add(ChatColor.GRAY + "Outputs: " + ChatColor.WHITE + recipe.getOutputs().size());
        info.add(ChatColor.GRAY + "Layout: " + ChatColor.WHITE + recipe.getLayout());
        if (recipe.hasKnownProcessingTime()) {
            info.add(ChatColor.GRAY + "Processing ticks: " + ChatColor.WHITE + recipe.getProcessingTicks());
        }
        if (recipe.hasKnownEnergyUse()) {
            info.add(ChatColor.GRAY + "Energy use: " + ChatColor.WHITE + recipe.getEnergyPerTick() + " J/t");
        }
        if (!recipe.getLabel().isBlank()) {
            info.add(ChatColor.DARK_GRAY + recipe.getLabel());
        }

        menu.replaceExistingItem(
            4,
            Converter.getItem(Material.BOOK, "&6Recipe Information", info.toArray(new String[0]))
        );

        menu.open(player);
    }

    private static ChestMenu baseMenu(String title) {
        ChestMenu menu = new ChestMenu(title);
        menu.setSize(54);
        menu.setEmptySlotsClickable(false);
        for (int slot = 0; slot < 54; slot++) {
            menu.addItem(slot, ChestMenuUtils.getBackground(), ChestMenuUtils.getEmptyClickHandler());
        }
        return menu;
    }

    private static ItemStack recipeIcon(MachineRecipeDisplay recipe, int index, int total) {
        ItemStack output = recipe.getOutputs().getFirst();
        return withLore(
            output,
            "",
            ChatColor.GOLD + "Machine recipe " + ChatColor.WHITE + index + ChatColor.GRAY + "/" + total,
            ChatColor.GRAY + "Inputs: " + ChatColor.WHITE + recipe.getInputs().size(),
            ChatColor.GRAY + "Outputs: " + ChatColor.WHITE + recipe.getOutputs().size(),
            ChatColor.YELLOW + "Click to view"
        );
    }

    private static ItemStack withLore(ItemStack source, String... lines) {
        ItemStack clone = source.clone();
        var meta = clone.getItemMeta();
        List<String> lore = meta.hasLore() && meta.getLore() != null
            ? new ArrayList<>(meta.getLore())
            : new ArrayList<>();
        for (String line : lines) {
            if (line != null && !line.isEmpty()) {
                lore.add(line);
            }
        }
        meta.setLore(lore);
        clone.setItemMeta(meta);
        return clone;
    }

    private record Resolved(MachineRecipeProvider provider, List<MachineRecipeDisplay> recipes) {
    }
}
