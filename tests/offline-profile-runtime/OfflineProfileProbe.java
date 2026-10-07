package io.github.wickidcow.jegtest;

import io.github.thebusybiscuit.slimefun4.api.events.AsyncProfileLoadEvent;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerProfile;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

/** Disposable CI probe, not a distributable addon. Uses real server events and profiles. */
public final class OfflineProfileProbe extends JavaPlugin implements Listener {
    private static final String HISTORY = "com.balugaq.jeg.api.patches.JEGGuideHistory";
    private final AtomicInteger events = new AtomicInteger();
    private final AtomicReference<PlayerProfile> observed = new AtomicReference<>();
    private final AtomicReference<Throwable> eventFailure = new AtomicReference<>();
    private volatile UUID ownerId;
    private String phase;
    private boolean started;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void observe(AsyncProfileLoadEvent event) {
        if (ownerId == null || !ownerId.equals(event.getPlayerUUID())) {
            return;
        }
        try {
            require(event.isAsynchronous() && !Bukkit.isPrimaryThread(), "Profile event must be asynchronous");
            require(event.getProfile().getPlayer() == null, "Probe must use an offline profile");
            observed.set(event.getProfile());
            events.incrementAndGet();
        } catch (Throwable failure) {
            eventFailure.compareAndSet(null, failure);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] arguments) {
        try {
            require(sender instanceof ConsoleCommandSender && !started, "Console-only single execution");
            require(Bukkit.isPrimaryThread(), "Command must execute on primary thread");
            require("disposable-only".equals(System.getProperty("jeg.offline.probe")), "Missing JVM authorization");
            require("127.0.0.1".equals(Bukkit.getIp()) && !Bukkit.getOnlineMode(), "Disposable loopback offline server required");
            require(Bukkit.getOnlinePlayers().isEmpty(), "Real online players are forbidden");
            require("jeg-offline-fixture".equals(Bukkit.getWorlds().getFirst().getName()), "Wrong fixture world");
            require(arguments.length == 1, "Exactly one phase is required");
            phase = arguments[0];
            require(phase.equals("control") || phase.equals("candidate-create") || phase.equals("candidate-reload"), "Unknown phase");
            require(Files.readString(Path.of("probe-phase.txt")).trim().equals(phase), "Phase authorization mismatch");
            require("4.1.70".equals(Slimefun.getVersion()), "Wrong published core");
            for (String line : Files.readAllLines(Path.of("expected-plugins.tsv"))) {
                String[] row = line.split("\t", -1);
                require(row.length == 2, "Malformed plugin evidence");
                var plugin = Bukkit.getPluginManager().getPlugin(row[0]);
                require(plugin != null && plugin.isEnabled(), "Missing enabled plugin: " + row[0]);
                require(row[1].equals(plugin.getPluginMeta().getVersion()), "Wrong addon version: " + row[0]);
            }
            require(Files.readAllLines(Path.of("expected-plugins.tsv")).size() == 45, "Must test all 45 addons");
            String ownerName = phase.equals("control") ? "JEGControlProbe" : "JEGPatchedProbe";
            ownerId = UUID.nameUUIDFromBytes(("OfflinePlayer:" + ownerName).getBytes(StandardCharsets.UTF_8));
            require(Files.readString(Path.of("probe-owner.txt")).trim().equals(ownerId.toString()), "Owner authorization mismatch");
            var owner = Bukkit.getOfflinePlayer(ownerName);
            require(ownerId.equals(owner.getUniqueId()) && ownerName.equals(owner.getName()), "Synthetic owner mismatch");
            started = true;
            var controller = Slimefun.getDatabaseManager().getProfileDataController();
            var loading = phase.equals("candidate-reload") ? controller.getProfileAsync(owner) : controller.getOrCreateProfileAsync(owner);
            loading.whenComplete((profile, failure) -> Bukkit.getScheduler().runTask(this, () -> {
                try {
                    if (failure != null) {
                        throw new IllegalStateException("Profile load failed", failure);
                    }
                    require(profile != null, "Persisted candidate profile was not found");
                    require(eventFailure.get() == null && events.get() == 1 && observed.get() == profile, "Real profile-load event was not observed exactly once");
                    require(profile.getPlayer() == null && ownerId.equals(profile.getUUID()), "Offline profile identity changed");
                    var history = profile.getGuideHistory();
                    boolean patched = HISTORY.equals(history.getClass().getName());
                    if (phase.equals("control")) {
                        require(!patched, "Published negative control no longer reproduces the unpatched history");
                        result("CONTROL_REPRODUCED", false, false);
                        return;
                    }
                    require(patched, "JEG callback did not install its actual guide-history class");
                    require(history.getClass().getClassLoader() == Bukkit.getPluginManager().getPlugin("JustEnoughGuide").getClass().getClassLoader(), "Unexpected history classloader");
                    history.setMainMenuPage(7);
                    history.add("offline-profile-probe-sentinel");
                    int historySize = history.size();
                    int backpackCount = profile.getBackpackCount();
                    Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                        try {
                            Bukkit.getPluginManager().callEvent(new AsyncProfileLoadEvent(profile));
                            Bukkit.getPluginManager().callEvent(new AsyncProfileLoadEvent(profile));
                            Bukkit.getScheduler().runTask(this, () -> {
                                try {
                                    require(eventFailure.get() == null && events.get() == 3 && observed.get() == profile, "Repeated profile events were not delivered");
                                    require(profile.getGuideHistory() == history && history.getMainMenuPage() == 7 && history.size() == historySize, "Repeated callback replaced or reset guide history");
                                    require(profile.getPlayer() == null && profile.getBackpackCount() == backpackCount, "Profile identity/count changed");
                                    result("PASS", true, true);
                                } catch (Throwable error) {
                                    fail(error);
                                }
                            });
                        } catch (Throwable error) {
                            fail(error);
                        }
                    });
                } catch (Throwable error) {
                    fail(error);
                }
            }));
        } catch (Throwable failure) {
            fail(failure);
        }
        return true;
    }

    private void result(String status, boolean patched, boolean preserved) throws Exception {
        String content = "status=" + status + "\nphase=" + phase + "\nevents=" + events.get()
                + "\npatched=" + patched + "\nhistory_preserved=" + preserved + "\naddons_enabled=45\nowner=" + ownerId + "\n";
        Path destination = Path.of("probe-" + phase + ".txt");
        Path temporary = Path.of(destination + ".tmp");
        Files.writeString(temporary, content);
        Files.move(temporary, destination, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        getLogger().info("JEG_OFFLINE_PROBE_" + status + " phase=" + phase);
    }

    private void fail(Throwable failure) {
        getLogger().log(Level.SEVERE, "JEG_OFFLINE_PROBE_FAIL", failure);
        try {
            Files.writeString(Path.of("probe-" + phase + ".txt"), "status=FAIL\nphase=" + phase + "\n");
        } catch (Exception writeFailure) {
            getLogger().log(Level.SEVERE, "Could not write failed probe result", writeFailure);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
