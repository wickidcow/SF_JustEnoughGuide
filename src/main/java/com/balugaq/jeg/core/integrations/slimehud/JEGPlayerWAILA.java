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
import com.balugaq.jeg.utils.platform.PlatformUtil;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.schntgaispock.slimehud.SlimeHUD;
import io.github.schntgaispock.slimehud.util.Util;
import io.github.schntgaispock.slimehud.waila.HudRequest;
import io.github.schntgaispock.slimehud.waila.PlayerWAILA;
import io.github.schntgaispock.slimehud.waila.WAILAManager;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NullMarked;

/**
 * JEG's SlimeHUD controller.
 *
 * <p>Do not subclass SlimeHUD's PlayerWAILA here. Newer SlimeHUD builds make
 * PlayerWAILA final, and subclassing it causes an IncompatibleClassChangeError
 * when a player joins. JEG instead pauses the native WAILA instance and runs
 * this independent controller. The native instance is restored on shutdown.</p>
 *
 * @author balugaq
 * @author wickidcow
 * @since 1.9
 */
@SuppressWarnings("deprecation")
@NullMarked
public final class JEGPlayerWAILA extends BukkitRunnable {

    private static final Map<UUID, JEGPlayerWAILA> CONTROLLERS = new ConcurrentHashMap<>();

    private final Player player;
    private final @Nullable PlayerWAILA nativeWaila;
    private final boolean nativeWasPaused;
    private final BossBar bossBar;
    private final boolean keepTextColors;
    private final boolean useAutoBossBarColor;
    private final String configuredLocation;

    private String facing = "";

    private JEGPlayerWAILA(Player player, @Nullable PlayerWAILA nativeWaila) {
        this.player = player;
        this.nativeWaila = nativeWaila;
        this.nativeWasPaused = nativeWaila != null && nativeWaila.isPaused();

        String bossbarColor = SlimeHUD.getInstance()
            .getConfig()
            .getString("waila.bossbar-color", "white")
            .trim()
            .toLowerCase(java.util.Locale.ROOT);

        this.useAutoBossBarColor = "inherit".equals(bossbarColor);
        this.configuredLocation = SlimeHUD.getInstance()
            .getConfig()
            .getString("waila.location", "bossbar")
            .trim()
            .toLowerCase(java.util.Locale.ROOT);
        this.keepTextColors = SlimeHUD.getInstance()
            .getConfig()
            .getBoolean("waila.use-original-colors", true);

        this.bossBar = Bukkit.createBossBar("", parseBarColor(bossbarColor), BarStyle.SOLID);
        this.bossBar.addPlayer(player);
        this.bossBar.setVisible(false);

        if (nativeWaila != null) {
            nativeWaila.setPaused(true);
        }
    }

    /**
     * Starts or refreshes JEG's controller after SlimeHUD has had a chance to
     * create its native PlayerWAILA for the joining player.
     */
    public static void wrap(Player player) {
        Bukkit.getScheduler().runTaskLater(JustEnoughGuide.getInstance(), () -> startNow(player), 1L);
    }

    private static void startNow(Player player) {
        if (!player.isOnline()) {
            return;
        }

        stop(player.getUniqueId(), false);

        PlayerWAILA nativeWaila = getNativeWaila(player.getUniqueId());
        JEGPlayerWAILA controller = new JEGPlayerWAILA(player, nativeWaila);
        CONTROLLERS.put(player.getUniqueId(), controller);

        long tickRate = Math.max(1L, SlimeHUD.getInstance().getConfig().getLong("waila.tick-rate", 5L));
        controller.runTaskTimer(JustEnoughGuide.getInstance(), 0L, tickRate);
    }

    private static @Nullable PlayerWAILA getNativeWaila(UUID uuid) {
        try {
            return WAILAManager.getInstance().getWailas().get(uuid);
        } catch (RuntimeException | LinkageError error) {
            JustEnoughGuide.getInstance().getLogger().log(
                Level.WARNING,
                "Could not access SlimeHUD's native WAILA for " + uuid
                    + ". JEG will continue without replacing the native HUD.",
                error
            );
            return null;
        }
    }

    public static void remove(Player player) {
        stop(player.getUniqueId(), false);
    }

    public static void onDisable() {
        for (UUID uuid : CONTROLLERS.keySet().toArray(UUID[]::new)) {
            stop(uuid, true);
        }
    }

    private static void stop(UUID uuid, boolean restoreNative) {
        JEGPlayerWAILA old = CONTROLLERS.remove(uuid);
        if (old == null) {
            return;
        }

        old.cancelController();
        if (restoreNative && old.nativeWaila != null) {
            try {
                old.nativeWaila.setPaused(old.nativeWasPaused);
            } catch (RuntimeException | LinkageError error) {
                JustEnoughGuide.getInstance().getLogger().log(
                    Level.FINE,
                    "Could not restore SlimeHUD WAILA state for " + uuid,
                    error
                );
            }
        }
    }

    @Override
    public void run() {
        if (!player.isOnline()) {
            remove(player);
            return;
        }

        if (isDisabledInCurrentWorld()) {
            clearDisplay();
            return;
        }

        updateFacing();

        HUDLocation hudLocation = HUDMachineInfoLocationGuideOption.getSelectedOption(player);
        String location = hudLocation == HUDLocation.DEFAULT
            ? configuredLocation
            : hudLocation == HUDLocation.BOSSBAR ? "bossbar" : "actionbar";

        switch (location) {
            case "bossbar" -> showBossBar();
            case "hotbar", "actionbar" -> showActionBar();
            default -> clearDisplay();
        }
    }

    private boolean isDisabledInCurrentWorld() {
        if (SlimeHUD.getInstance().getConfig().getBoolean("waila.disabled", false)) {
            return true;
        }

        return SlimeHUD.getInstance()
            .getConfig()
            .getStringList("waila.disabled-in")
            .contains(player.getWorld().getName());
    }

    private void updateFacing() {
        Block targetBlock = player.getTargetBlockExact(HUDReachBlockGuideOption.getReachBlock(player));
        if (targetBlock == null || targetBlock.getType().isAir()) {
            facing = "";
            return;
        }

        SlimefunItem item = StorageCacheUtils.getSfItem(targetBlock.getLocation());
        if (item == null) {
            if (VanillaBlockHUDDisplayGuideOption.isEnabled(player)) {
                facing = SlimeHUDIntegrationMain.getVanillaBlockName(player, targetBlock);
            } else {
                facing = "";
            }
            return;
        }

        Location target = targetBlock.getLocation();
        HudRequest request = new HudRequest(item, target, player);
        String facingBlock = SlimeHUD.getTranslationManager().getItemName(player, item);
        String facingBlockInfo = SlimeHUD.getHudController().processRequest(request);

        facing = ChatColor.translateAlternateColorCodes(
            '&',
            facingBlock + (facingBlockInfo.isEmpty() ? "" : " &7| " + facingBlockInfo)
        );
    }

    private void showBossBar() {
        if (facing.isEmpty()) {
            bossBar.setVisible(false);
            return;
        }

        bossBar.setTitle(keepTextColors ? facing : ChatColor.stripColor(facing));
        if (useAutoBossBarColor) {
            bossBar.setColor(Util.pickBarColorFromName(facing));
        }
        bossBar.setVisible(true);
    }

    private void showActionBar() {
        bossBar.setVisible(false);
        if (facing.isEmpty()) {
            return;
        }

        if (PlatformUtil.isPaper()) {
            player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(facing));
        } else {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(facing));
        }
    }

    private void clearDisplay() {
        facing = "";
        bossBar.setVisible(false);
    }

    private void cancelController() {
        try {
            super.cancel();
        } catch (IllegalStateException ignored) {
            // The task may not have started yet.
        }

        bossBar.setVisible(false);
        bossBar.removeAll();
    }

    private static BarColor parseBarColor(String value) {
        if ("inherit".equals(value) || "default".equals(value)) {
            return BarColor.WHITE;
        }

        try {
            return BarColor.valueOf(value.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException error) {
            return BarColor.WHITE;
        }
    }
}
