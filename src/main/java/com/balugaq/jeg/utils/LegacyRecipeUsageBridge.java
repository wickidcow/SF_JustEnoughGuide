/*
 * Copyright (c) 2024-2026 balugaq
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 */

package com.balugaq.jeg.utils;

import com.balugaq.jeg.api.interfaces.JEGSlimefunGuideImplementation;
import com.balugaq.jeg.implementation.JustEnoughGuide;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerProfile;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideImplementation;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.logging.Level;

/**
 * Optional bridge to Slimefun Legacy's budgeted reverse recipe-usage browser.
 *
 * <p>The maintained JEG remains the registered guide owner. This bridge only asks
 * Legacy to decorate the already-open JEG item page, so JEG does not build a second
 * recipe index or replace the supported guide registration/restore API path.</p>
 */
public final class LegacyRecipeUsageBridge {
    private static final String BROWSER_CLASS =
        "io.github.thebusybiscuit.slimefun4.implementation.guide.enhanced.LegacyRecipeUsageBrowser";

    private static volatile boolean resolved;
    private static volatile Method getBrowser;
    private static volatile Method decorateItemPage;

    private LegacyRecipeUsageBridge() {
    }

    public static void decorate(
        Player player,
        PlayerProfile profile,
        JEGSlimefunGuideImplementation guide,
        SlimefunItem item
    ) {
        resolve();
        if (getBrowser == null || decorateItemPage == null) {
            return;
        }

        try {
            Object browser = getBrowser.invoke(null);
            decorateItemPage.invoke(browser, player, profile, guide, item);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            JustEnoughGuide.getInstance().getLogger().log(
                Level.FINE,
                "Slimefun Legacy recipe-usage integration is not currently available",
                exception
            );
        }
    }

    private static synchronized void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;

        try {
            Class<?> browserClass = Slimefun.class.getClassLoader().loadClass(BROWSER_CLASS);
            getBrowser = browserClass.getMethod("get");
            decorateItemPage = browserClass.getMethod(
                "decorateItemPage",
                Player.class,
                PlayerProfile.class,
                SlimefunGuideImplementation.class,
                SlimefunItem.class
            );
        } catch (ClassNotFoundException | NoSuchMethodException | LinkageError exception) {
            getBrowser = null;
            decorateItemPage = null;
        }
    }
}
