/*
 * Copyright (c) 2024-2026 balugaq
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 */

package com.balugaq.jeg.core.integrations.slimehud;

import com.balugaq.jeg.api.objects.enums.HUDLocation;
import com.balugaq.jeg.implementation.JustEnoughGuide;
import io.github.schntgaispock.slimehud.waila.PlayerWAILA;
import io.github.schntgaispock.slimehud.waila.WAILAManager;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NullMarked;

/**
 * Adapts JEG's per-player SlimeHUD guide options to SlimeHUD's native HUD.
 *
 * <p>JEG intentionally does not create a second BossBar, run a second target
 * scanner, or pause SlimeHUD's PlayerWAILA. SlimeHUD remains the source of
 * truth for rendering and lifecycle. Newer SF_SlimeHUD builds expose optional
 * per-player controls that are invoked reflectively so JEG can still load with
 * older SlimeHUD versions without a linkage error.</p>
 *
 * @author balugaq
 * @author wickidcow
 * @since 1.9
 */
@NullMarked
public final class JEGPlayerWAILA {

    private static final Set<UUID> MANAGED_PLAYERS = ConcurrentHashMap.newKeySet();
    private static volatile boolean warnedMissingNativeControls;

    private JEGPlayerWAILA() {}

    /**
     * Refreshes SlimeHUD's native player controller and applies JEG's optional
     * display preferences after SlimeHUD has processed the player event.
     */
    public static void wrap(Player player) {
        if (!player.isOnline()) {
            return;
        }

        player.getScheduler().runDelayed(
            JustEnoughGuide.getInstance(),
            task -> syncNow(player),
            null,
            1L
        );
    }

    private static void syncNow(Player player) {
        if (!player.isOnline()) {
            return;
        }

        refreshNative(player);
        PlayerWAILA nativeWaila = getNativeWaila(player);
        if (nativeWaila == null) {
            return;
        }

        MANAGED_PLAYERS.add(player.getUniqueId());

        boolean rangeApplied = invokeOptional(
            nativeWaila,
            "setMaxDistanceOverride",
            new Class<?>[] {Integer.class},
            HUDReachBlockGuideOption.getReachBlock(player)
        );
        boolean vanillaApplied = invokeOptional(
            nativeWaila,
            "setVanillaEnabledOverride",
            new Class<?>[] {Boolean.class},
            VanillaBlockHUDDisplayGuideOption.isEnabled(player)
        );

        HUDLocation location = HUDMachineInfoLocationGuideOption.getSelectedOption(player);
        if (location != HUDLocation.DEFAULT) {
            applyDisplayMode(nativeWaila, location);
        }

        if ((!rangeApplied || !vanillaApplied) && !warnedMissingNativeControls) {
            warnedMissingNativeControls = true;
            JustEnoughGuide.getInstance().getLogger().warning(
                "The installed SlimeHUD does not expose the native JEG integration controls. "
                    + "The HUD will remain usable, but JEG's SlimeHUD range/vanilla overrides require SF_SlimeHUD 2.0.2 or newer."
            );
        }
    }

    private static void applyDisplayMode(PlayerWAILA nativeWaila, HUDLocation location) {
        try {
            ClassLoader loader = nativeWaila.getClass().getClassLoader();
            Class<?> displayModeClass = loader.loadClass("io.github.schntgaispock.slimehud.waila.DisplayMode");
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object mode = Enum.valueOf(
                (Class) displayModeClass.asSubclass(Enum.class),
                location == HUDLocation.BOSSBAR ? "BOSSBAR" : "ACTIONBAR"
            );
            Method setter = nativeWaila.getClass().getMethod("setDisplayMode", displayModeClass);
            setter.invoke(nativeWaila, mode);
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            warnOldSlimeHud();
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException | LinkageError error) {
            logFine("Could not apply JEG's SlimeHUD display preference for " + nativeWaila.getPlayer().getName(), error);
        }
    }

    private static boolean invokeOptional(
        PlayerWAILA nativeWaila,
        String methodName,
        Class<?>[] parameterTypes,
        Object... args
    ) {
        try {
            Method method = nativeWaila.getClass().getMethod(methodName, parameterTypes);
            method.invoke(nativeWaila, args);
            return true;
        } catch (NoSuchMethodException ignored) {
            return false;
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException | LinkageError error) {
            logFine("Could not invoke SlimeHUD native integration method " + methodName, error);
            return false;
        }
    }

    private static void refreshNative(Player player) {
        try {
            WAILAManager manager = WAILAManager.getInstance();
            Method method = manager.getClass().getMethod("refreshPlayer", Player.class);
            method.invoke(manager, player);
        } catch (NoSuchMethodException ignored) {
            // Older SlimeHUD versions create/refresh their PlayerWAILA through
            // their own event listener. Do not take ownership of that lifecycle.
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException | LinkageError error) {
            logFine("Could not ask SlimeHUD to refresh its native HUD for " + player.getName(), error);
        }
    }

    private static @Nullable PlayerWAILA getNativeWaila(Player player) {
        try {
            return WAILAManager.getInstance().getWailas().get(player.getUniqueId());
        } catch (RuntimeException | LinkageError error) {
            logFine("Could not access SlimeHUD's native PlayerWAILA for " + player.getName(), error);
            return null;
        }
    }

    public static void remove(Player player) {
        MANAGED_PLAYERS.remove(player.getUniqueId());
    }

    /**
     * Clears only JEG-owned overrides. It never pauses or cancels SlimeHUD's
     * native task.
     */
    public static void onDisable() {
        for (UUID uuid : MANAGED_PLAYERS.toArray(UUID[]::new)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                continue;
            }

            PlayerWAILA nativeWaila = getNativeWaila(player);
            if (nativeWaila != null) {
                invokeOptional(nativeWaila, "clearExternalOverrides", new Class<?>[0]);
            }
            refreshNative(player);
        }
        MANAGED_PLAYERS.clear();
    }

    private static void warnOldSlimeHud() {
        if (warnedMissingNativeControls) {
            return;
        }
        warnedMissingNativeControls = true;
        JustEnoughGuide.getInstance().getLogger().warning(
            "The installed SlimeHUD is older than the native JEG adapter. "
                + "Update to SF_SlimeHUD 2.0.2 or newer for JEG display/range controls."
        );
    }

    private static void logFine(String message, Throwable error) {
        JustEnoughGuide.getInstance().getLogger().log(Level.FINE, message, error);
    }
}
