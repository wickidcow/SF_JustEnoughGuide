/*
 * Slimefun Legacy integration for the maintained SF_JustEnoughGuide fork.
 */
package com.balugaq.jeg.utils;

import com.balugaq.jeg.implementation.JustEnoughGuide;
import com.balugaq.jeg.utils.formatter.Format;
import com.balugaq.jeg.utils.formatter.Formats;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerProfile;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;

/**
 * Optional bridge to Slimefun Legacy's machine recipe provider and safe input-fill APIs.
 *
 * <p>No Legacy-only classes appear in this class' signatures. The integration is
 * resolved reflectively so this JAR can still load on upstream Slimefun builds.
 * When Legacy is present, the browser uses its registered providers and delegates
 * transfers back to Legacy's transaction/rollback/protection implementation.</p>
 */
public final class LegacyMachineRecipeBridge {

    private static final String PROVIDER_REGISTRY =
        "io.github.thebusybiscuit.slimefun4.api.recipes.machine.MachineRecipeProviderRegistry";
    private static final String INPUT_FILL_MANAGER =
        "io.github.thebusybiscuit.slimefun4.implementation.guide.enhanced.LegacyMachineInputFillManager";

    private static final int[] LIST_SLOTS = {
        9, 10, 11, 12, 13, 14, 15, 16, 17,
        18, 19, 20, 21, 22, 23, 24, 25, 26,
        27, 28, 29, 30, 31, 32, 33, 34, 35,
        36, 37, 38, 39, 40, 41, 42, 43, 44
    };
    private static final int[] DETAIL_INPUT_SLOTS = {
        9, 10, 11, 12, 13, 14, 15, 16, 17,
        18, 19, 20, 21, 22, 23, 24, 25, 26
    };
    private static final int[] DETAIL_OUTPUT_SLOTS = {
        36, 37, 38, 39, 40, 41, 42, 43, 44
    };

    private LegacyMachineRecipeBridge() {
    }

    public static boolean isLegacyAvailable() {
        return loadClass(PROVIDER_REGISTRY) != null;
    }

    public static void addMachineRecipeButton(
        ChestMenu menu,
        Format format,
        PlayerProfile profile,
        Player player,
        SlimefunItem machine
    ) {
        List<ResolvedRecipe> recipes = resolveRecipes(machine, player.getWorld());
        if (recipes.isEmpty()) {
            return;
        }

        for (int slot : format.getChars(Formats.Char.MACHINE_RECIPES)) {
            menu.addItem(
                slot,
                named(
                    Material.KNOWLEDGE_BOOK,
                    "&6&lMachine Recipes",
                    "",
                    "&7Browse recipes exposed through",
                    "&7Slimefun Legacy's provider API.",
                    "",
                    "&7Recipes: &f" + recipes.size(),
                    "&eClick to browse"
                )
            );
            menu.addMenuClickHandler(slot, (pl, s, item, action) -> {
                openRecipeList(pl, profile, machine, recipes, 1);
                return false;
            });
        }
    }

    private static List<ResolvedRecipe> resolveRecipes(SlimefunItem machine, World world) {
        Class<?> registryClass = loadClass(PROVIDER_REGISTRY);
        if (registryClass == null) {
            return List.of();
        }

        try {
            Method getProviders = registryClass.getMethod("getProviders");
            Object rawProviders = getProviders.invoke(null);
            if (!(rawProviders instanceof Iterable<?> providers)) {
                return List.of();
            }

            for (Object provider : providers) {
                if (provider == null) {
                    continue;
                }

                Method supports = findMethod(provider.getClass(), "supports", 1);
                Method getRecipes = findMethod(provider.getClass(), "getRecipes", 2);
                if (supports == null || getRecipes == null) {
                    continue;
                }

                Object supported = supports.invoke(provider, machine);
                if (!(supported instanceof Boolean value) || !value) {
                    continue;
                }

                Object rawRecipes = getRecipes.invoke(provider, machine, world);
                List<ResolvedRecipe> recipes = decodeRecipes(rawRecipes);
                if (!recipes.isEmpty()) {
                    String providerName = providerName(provider);
                    List<ResolvedRecipe> tagged = new ArrayList<>(recipes.size());
                    for (ResolvedRecipe recipe : recipes) {
                        tagged.add(recipe.withProvider(providerName));
                    }
                    return List.copyOf(tagged);
                }
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            logFine("Could not query Slimefun Legacy machine recipe providers", exception);
        }

        return List.of();
    }

    private static List<ResolvedRecipe> decodeRecipes(Object rawRecipes)
        throws InvocationTargetException, IllegalAccessException {
        if (!(rawRecipes instanceof Iterable<?> iterable)) {
            return List.of();
        }

        List<ResolvedRecipe> result = new ArrayList<>();
        for (Object raw : iterable) {
            if (raw == null) {
                continue;
            }

            Method getInputs = findMethod(raw.getClass(), "getInputs", 0);
            Method getOutputs = findMethod(raw.getClass(), "getOutputs", 0);
            if (getInputs == null || getOutputs == null) {
                continue;
            }

            List<List<ItemStack>> inputs = decodeInputs(getInputs.invoke(raw));
            List<ItemStack> outputs = itemStacks(getOutputs.invoke(raw));
            if (outputs.isEmpty()) {
                continue;
            }

            String label = stringValue(raw, "getLabel");
            int ticks = intValue(raw, "getProcessingTicks", -1);
            long energy = longValue(raw, "getEnergyPerTick", -1L);
            result.add(new ResolvedRecipe(raw, "", inputs, outputs, label, ticks, energy));
        }
        return List.copyOf(result);
    }

    private static List<List<ItemStack>> decodeInputs(Object rawInputs)
        throws InvocationTargetException, IllegalAccessException {
        if (!(rawInputs instanceof Iterable<?> iterable)) {
            return List.of();
        }

        List<List<ItemStack>> inputs = new ArrayList<>();
        for (Object ingredient : iterable) {
            if (ingredient == null) {
                continue;
            }
            Method getChoices = findMethod(ingredient.getClass(), "getChoices", 0);
            if (getChoices == null) {
                continue;
            }
            List<ItemStack> choices = itemStacks(getChoices.invoke(ingredient));
            if (!choices.isEmpty()) {
                inputs.add(choices);
            }
        }
        return List.copyOf(inputs);
    }

    private static void openRecipeList(
        Player player,
        PlayerProfile profile,
        SlimefunItem machine,
        List<ResolvedRecipe> recipes,
        int requestedPage
    ) {
        int pages = Math.max(1, (recipes.size() - 1) / LIST_SLOTS.length + 1);
        int page = Math.max(1, Math.min(requestedPage, pages));

        ChestMenu menu = baseMenu("&6&lMachine Recipes");
        menu.addItem(
            0,
            ChestMenuUtils.getBackButton(player, "", "&7Return to the machine recipe page.")
        );
        menu.addMenuClickHandler(0, (pl, slot, item, action) -> {
            GuideUtil.getLastGuide(pl).displayItem(profile, machine, false);
            return false;
        });

        menu.addItem(
            4,
            named(
                machine.getItem().clone(),
                "&f" + strip(machine.getItemName()),
                "",
                "&7Provider: &f" + recipes.getFirst().provider(),
                "&7Recipes: &f" + recipes.size()
            ),
            ChestMenuUtils.getEmptyClickHandler()
        );

        int start = (page - 1) * LIST_SLOTS.length;
        for (int index = 0; index < LIST_SLOTS.length; index++) {
            int recipeIndex = start + index;
            if (recipeIndex >= recipes.size()) {
                break;
            }

            ResolvedRecipe recipe = recipes.get(recipeIndex);
            ItemStack icon = recipe.outputs().getFirst().clone();
            appendLore(
                icon,
                "",
                ChatColor.GOLD + "Machine recipe " + ChatColor.WHITE + (recipeIndex + 1)
                    + ChatColor.GRAY + "/" + recipes.size(),
                ChatColor.GRAY + "Inputs: " + ChatColor.WHITE + recipe.inputs().size(),
                ChatColor.GRAY + "Outputs: " + ChatColor.WHITE + recipe.outputs().size(),
                ChatColor.YELLOW + "Click to view details"
            );

            int slot = LIST_SLOTS[index];
            menu.addItem(slot, icon);
            menu.addMenuClickHandler(slot, (pl, s, item, action) -> {
                openRecipeDetail(pl, profile, machine, recipes, recipeIndex);
                return false;
            });
        }

        menu.addItem(46, ChestMenuUtils.getPreviousButton(player, page, pages));
        menu.addMenuClickHandler(46, (pl, slot, item, action) -> {
            if (page > 1) {
                openRecipeList(pl, profile, machine, recipes, page - 1);
            }
            return false;
        });
        menu.addItem(
            49,
            named(Material.PAPER, "&fPage &e" + page + " &7/ &e" + pages),
            ChestMenuUtils.getEmptyClickHandler()
        );
        menu.addItem(52, ChestMenuUtils.getNextButton(player, page, pages));
        menu.addMenuClickHandler(52, (pl, slot, item, action) -> {
            if (page < pages) {
                openRecipeList(pl, profile, machine, recipes, page + 1);
            }
            return false;
        });

        menu.open(player);
    }

    private static void openRecipeDetail(
        Player player,
        PlayerProfile profile,
        SlimefunItem machine,
        List<ResolvedRecipe> recipes,
        int requestedIndex
    ) {
        int recipeIndex = Math.max(0, Math.min(requestedIndex, recipes.size() - 1));
        ResolvedRecipe recipe = recipes.get(recipeIndex);
        int[] selectedAlternatives = new int[recipe.inputs().size()];

        ChestMenu menu = baseMenu("&6&lMachine Recipe &7" + (recipeIndex + 1) + "/" + recipes.size());
        menu.addItem(
            0,
            ChestMenuUtils.getBackButton(player, "", "&7Return to the machine recipe list.")
        );
        menu.addMenuClickHandler(0, (pl, slot, item, action) -> {
            openRecipeList(pl, profile, machine, recipes, recipeIndex / LIST_SLOTS.length + 1);
            return false;
        });

        menu.addItem(
            4,
            named(
                Material.PAPER,
                "&fRecipe Information",
                "",
                recipe.label().isBlank() ? "&7No provider label" : "&7" + recipe.label(),
                recipe.processingTicks() >= 0
                    ? "&7Processing time: &f" + recipe.processingTicks() + " ticks"
                    : "&7Processing time: &8Unknown",
                recipe.energyPerTick() >= 0
                    ? "&7Energy: &f" + recipe.energyPerTick() + " J/t"
                    : "&7Energy: &8Unknown"
            ),
            ChestMenuUtils.getEmptyClickHandler()
        );

        int inputCount = Math.min(recipe.inputs().size(), DETAIL_INPUT_SLOTS.length);
        for (int i = 0; i < inputCount; i++) {
            int ingredientIndex = i;
            int slot = DETAIL_INPUT_SLOTS[i];
            List<ItemStack> choices = recipe.inputs().get(i);
            menu.addItem(slot, ingredientIcon(choices, i, 0));
            if (choices.size() > 1) {
                menu.addMenuClickHandler(slot, (pl, s, item, action) -> {
                    int delta = action.isRightClicked() ? -1 : 1;
                    selectedAlternatives[ingredientIndex] =
                        Math.floorMod(selectedAlternatives[ingredientIndex] + delta, choices.size());
                    menu.replaceExistingItem(
                        s,
                        ingredientIcon(
                            choices,
                            ingredientIndex,
                            selectedAlternatives[ingredientIndex]
                        )
                    );
                    return false;
                });
            } else {
                menu.addMenuClickHandler(slot, ChestMenuUtils.getEmptyClickHandler());
            }
        }

        int outputCount = Math.min(recipe.outputs().size(), DETAIL_OUTPUT_SLOTS.length);
        for (int i = 0; i < outputCount; i++) {
            ItemStack output = recipe.outputs().get(i).clone();
            appendLore(output, "", ChatColor.GREEN + "Machine output");
            menu.addItem(
                DETAIL_OUTPUT_SLOTS[i],
                output,
                ChestMenuUtils.getEmptyClickHandler()
            );
        }

        if (supportsSafeInputFill(machine, recipe.raw())) {
            menu.addItem(
                31,
                named(
                    Material.HOPPER,
                    "&a&lFill Machine Inputs",
                    "",
                    "&7Aim at the placed machine.",
                    "&eLeft-click: &7Fill one recipe set",
                    "&eShift + left-click: &7Fill the maximum safe amount",
                    "",
                    "&8Transfer safety is handled by Slimefun Legacy."
                )
            );
            menu.addMenuClickHandler(31, (pl, slot, item, action) -> {
                if (!action.isRightClicked()) {
                    invokeSafeInputFill(
                        pl,
                        machine,
                        recipe.raw(),
                        selectedAlternatives.clone(),
                        action.isShiftClicked()
                    );
                }
                return false;
            });
        }

        menu.addItem(45, ChestMenuUtils.getPreviousButton(player, recipeIndex + 1, recipes.size()));
        menu.addMenuClickHandler(45, (pl, slot, item, action) -> {
            if (recipeIndex > 0) {
                openRecipeDetail(pl, profile, machine, recipes, recipeIndex - 1);
            }
            return false;
        });
        menu.addItem(53, ChestMenuUtils.getNextButton(player, recipeIndex + 1, recipes.size()));
        menu.addMenuClickHandler(53, (pl, slot, item, action) -> {
            if (recipeIndex + 1 < recipes.size()) {
                openRecipeDetail(pl, profile, machine, recipes, recipeIndex + 1);
            }
            return false;
        });

        menu.open(player);
    }

    private static boolean supportsSafeInputFill(SlimefunItem machine, Object recipe) {
        Class<?> managerClass = loadClass(INPUT_FILL_MANAGER);
        if (managerClass == null) {
            return false;
        }

        try {
            Method get = findMethod(managerClass, "get", 0);
            if (get == null) {
                return false;
            }
            Object manager = get.invoke(null);
            Method supports = findMethod(managerClass, "supports", 2);
            if (supports == null) {
                return false;
            }
            Object result = supports.invoke(manager, machine, recipe);
            return result instanceof Boolean value && value;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            logFine("Slimefun Legacy input-fill service is not currently available", exception);
            return false;
        }
    }

    private static void invokeSafeInputFill(
        Player player,
        SlimefunItem machine,
        Object recipe,
        int[] selectedAlternatives,
        boolean maximum
    ) {
        Class<?> managerClass = loadClass(INPUT_FILL_MANAGER);
        if (managerClass == null) {
            player.sendMessage(ChatColor.RED + "Slimefun Legacy's safe input-fill service is unavailable.");
            return;
        }

        try {
            Method get = findMethod(managerClass, "get", 0);
            Method fill = findMethod(managerClass, "fill", 5);
            if (get == null || fill == null) {
                throw new NoSuchMethodException("Legacy input-fill API is incomplete");
            }

            Object manager = get.invoke(null);
            fill.invoke(manager, player, machine, recipe, selectedAlternatives, maximum);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            player.sendMessage(ChatColor.RED + "Could not safely fill this machine. Check the server console.");
            JustEnoughGuide.getInstance().getLogger()
                .log(Level.WARNING, "Slimefun Legacy safe machine input fill failed", exception);
        }
    }

    private static ChestMenu baseMenu(String title) {
        ChestMenu menu = new ChestMenu(ChatColor.translateAlternateColorCodes('&', title));
        menu.setSize(54);
        menu.setEmptySlotsClickable(false);
        for (int slot = 0; slot < 54; slot++) {
            menu.addItem(slot, ChestMenuUtils.getBackground(), ChestMenuUtils.getEmptyClickHandler());
        }
        return menu;
    }

    private static ItemStack ingredientIcon(List<ItemStack> choices, int ingredientIndex, int selected) {
        ItemStack icon = choices.get(Math.max(0, Math.min(selected, choices.size() - 1))).clone();
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(ChatColor.AQUA + "Input " + ChatColor.WHITE + (ingredientIndex + 1));
        if (choices.size() > 1) {
            lore.add(ChatColor.GRAY + "Alternative " + ChatColor.WHITE + (selected + 1)
                + ChatColor.GRAY + "/" + choices.size());
            lore.add(ChatColor.YELLOW + "Left-click: next alternative");
            lore.add(ChatColor.YELLOW + "Right-click: previous alternative");
        }
        appendLore(icon, lore.toArray(String[]::new));
        return icon;
    }

    private static List<ItemStack> itemStacks(Object raw) {
        List<ItemStack> result = new ArrayList<>();
        if (raw instanceof Collection<?> collection) {
            for (Object value : collection) {
                if (value instanceof ItemStack stack && stack.getType() != Material.AIR && stack.getAmount() > 0) {
                    result.add(stack.clone());
                }
            }
        } else if (raw instanceof Iterable<?> iterable) {
            for (Object value : iterable) {
                if (value instanceof ItemStack stack && stack.getType() != Material.AIR && stack.getAmount() > 0) {
                    result.add(stack.clone());
                }
            }
        }
        return List.copyOf(result);
    }

    private static String providerName(Object provider) {
        try {
            Method getKey = findMethod(provider.getClass(), "getKey", 0);
            Object key = getKey == null ? null : getKey.invoke(provider);
            return key == null ? provider.getClass().getSimpleName() : key.toString();
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return provider.getClass().getSimpleName();
        }
    }

    private static String stringValue(Object target, String methodName) {
        try {
            Method method = findMethod(target.getClass(), methodName, 0);
            Object value = method == null ? null : method.invoke(target);
            return value == null ? "" : value.toString();
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return "";
        }
    }

    private static int intValue(Object target, String methodName, int fallback) {
        try {
            Method method = findMethod(target.getClass(), methodName, 0);
            Object value = method == null ? null : method.invoke(target);
            return value instanceof Number number ? number.intValue() : fallback;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return fallback;
        }
    }

    private static long longValue(Object target, String methodName, long fallback) {
        try {
            Method method = findMethod(target.getClass(), methodName, 0);
            Object value = method == null ? null : method.invoke(target);
            return value instanceof Number number ? number.longValue() : fallback;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return fallback;
        }
    }

    private static Method findMethod(Class<?> type, String name, int parameterCount) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == parameterCount) {
                return method;
            }
        }
        return null;
    }

    private static Class<?> loadClass(String name) {
        try {
            return Slimefun.class.getClassLoader().loadClass(name);
        } catch (ClassNotFoundException | LinkageError ignored) {
            return null;
        }
    }

    private static ItemStack named(Material material, String name, String... lore) {
        return named(new ItemStack(material), name, lore);
    }

    private static ItemStack named(ItemStack item, String name, String... lore) {
        ItemStack copy = item.clone();
        ItemMeta meta = copy.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            List<String> coloredLore = new ArrayList<>();
            for (String line : lore) {
                coloredLore.add(ChatColor.translateAlternateColorCodes('&', line));
            }
            meta.setLore(coloredLore);
            copy.setItemMeta(meta);
        }
        return copy;
    }

    private static void appendLore(ItemStack item, String... lines) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        List<String> lore = meta.hasLore() && meta.getLore() != null
            ? new ArrayList<>(meta.getLore())
            : new ArrayList<>();
        for (String line : lines) {
            lore.add(ChatColor.translateAlternateColorCodes('&', line));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    private static String strip(String value) {
        String stripped = ChatColor.stripColor(value);
        return stripped == null ? value : stripped;
    }

    private static void logFine(String message, Throwable throwable) {
        JustEnoughGuide.getInstance().getLogger().log(Level.FINE, message, throwable);
    }

    private record ResolvedRecipe(
        Object raw,
        String provider,
        List<List<ItemStack>> inputs,
        List<ItemStack> outputs,
        String label,
        int processingTicks,
        long energyPerTick
    ) {
        private ResolvedRecipe withProvider(String providerName) {
            return new ResolvedRecipe(
                raw,
                providerName,
                inputs,
                outputs,
                label,
                processingTicks,
                energyPerTick
            );
        }
    }
}
