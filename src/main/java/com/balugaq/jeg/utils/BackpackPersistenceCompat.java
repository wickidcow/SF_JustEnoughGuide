package com.balugaq.jeg.utils;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.ProfileDataController;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerBackpack;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/**
 * Bridges backpack persistence across the maintained Slimefun API generations.
 *
 * <p>Modern Legacy/United/Gugu cores compute changed slots internally and expose
 * {@code saveBackpackInventory(PlayerBackpack)}. Older supported Gugu APIs only
 * expose slot-aware overloads, so JEG preserves the original slot hints there.
 */
public final class BackpackPersistenceCompat {

    private static final Method MODERN_SAVE = findMethod(PlayerBackpack.class);
    private static final Method LEGACY_VARARGS_SAVE = findMethod(PlayerBackpack.class, Integer[].class);
    private static final Method LEGACY_SET_SAVE = findMethod(PlayerBackpack.class, Set.class);

    private BackpackPersistenceCompat() {}

    public static void save(
        @NotNull ProfileDataController controller,
        @NotNull PlayerBackpack backpack,
        int... legacyChangedSlots) {

        if (MODERN_SAVE != null) {
            invoke(MODERN_SAVE, controller, backpack);
            return;
        }

        Integer[] slots = Arrays.stream(legacyChangedSlots).boxed().toArray(Integer[]::new);
        if (LEGACY_VARARGS_SAVE != null) {
            invoke(LEGACY_VARARGS_SAVE, controller, backpack, (Object) slots);
            return;
        }

        if (LEGACY_SET_SAVE != null) {
            invoke(LEGACY_SET_SAVE, controller, backpack, Set.of(slots));
            return;
        }

        throw new IllegalStateException("No compatible Slimefun backpack persistence API is available");
    }

    private static Method findMethod(Class<?>... parameterTypes) {
        try {
            return ProfileDataController.class.getMethod("saveBackpackInventory", parameterTypes);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static void invoke(Method method, ProfileDataController controller, Object... arguments) {
        try {
            method.invoke(controller, arguments);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Could not access Slimefun backpack persistence API", exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Slimefun backpack persistence failed", cause);
        }
    }
}
