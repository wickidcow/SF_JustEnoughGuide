package com.balugaq.jeg.utils;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.jetbrains.annotations.NotNull;

/**
 * Cross-version RecipeChoice factory.
 *
 * <p>Paper 26.3 replaces the ExactChoice constructors with static RecipeChoice factories,
 * while the 1.21.11 compatibility floor does not expose those factories yet. Reflection
 * keeps the public JEG bytecode compatible with both API generations without calling an
 * API that is deprecated for removal on 26.3.
 */
public final class RecipeChoiceCompat {

    private static final Method EXACT_CHOICE_FACTORY = findExactChoiceFactory();
    private static final Constructor<RecipeChoice.ExactChoice> LEGACY_LIST_CONSTRUCTOR =
        findLegacyListConstructor();

    private RecipeChoiceCompat() {}

    public static @NotNull RecipeChoice.ExactChoice exact(
        @NotNull ItemStack first,
        @NotNull ItemStack... others) {

        List<ItemStack> choices = new ArrayList<>(1 + others.length);
        choices.add(first);
        choices.addAll(Arrays.asList(others));
        return exact(choices);
    }

    public static @NotNull RecipeChoice.ExactChoice exact(@NotNull List<ItemStack> choices) {
        if (choices.isEmpty()) {
            throw new IllegalArgumentException("Exact recipe choice cannot be empty");
        }

        List<ItemStack> snapshot = List.copyOf(choices);

        if (EXACT_CHOICE_FACTORY != null) {
            try {
                return (RecipeChoice.ExactChoice) EXACT_CHOICE_FACTORY.invoke(null, snapshot);
            } catch (IllegalAccessException | InvocationTargetException exception) {
                throw new IllegalStateException("Could not invoke Paper RecipeChoice.exactChoice factory", exception);
            }
        }

        if (LEGACY_LIST_CONSTRUCTOR != null) {
            try {
                return LEGACY_LIST_CONSTRUCTOR.newInstance(snapshot);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException exception) {
                throw new IllegalStateException("Could not invoke legacy ExactChoice constructor", exception);
            }
        }

        throw new IllegalStateException("No compatible ExactChoice creation API is available");
    }

    private static Method findExactChoiceFactory() {
        try {
            return RecipeChoice.class.getMethod("exactChoice", List.class);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Constructor<RecipeChoice.ExactChoice> findLegacyListConstructor() {
        try {
            return RecipeChoice.ExactChoice.class.getConstructor(List.class);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }
}
