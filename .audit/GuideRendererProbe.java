package audit;

import io.github.thebusybiscuit.slimefun4.api.player.PlayerProfile;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuide;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideMode;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import com.balugaq.jeg.api.interfaces.JEGSlimefunGuideImplementation;
import com.balugaq.jeg.api.patches.JEGGuideHistory;
import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

/** Actual server guide handlers with an explicitly synthetic no-network player. */
public final class GuideRendererProbe extends JavaPlugin {
    @Override public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            UUID id = UUID.fromString("acd94d21-c2a1-4c9f-8155-99c880100701");
            try {
                var plugin = Bukkit.getPluginManager().getPlugin("JustEnoughGuide");
                require(plugin != null, "JEG was not discovered");
                if (Boolean.getBoolean("guide.probe.damaged")) {
                    require(!plugin.isEnabled(), "Incomplete addon was not refused");
                    for (var mode : SlimefunGuideMode.values()) {
                        var guide = Slimefun.getRegistry().getSlimefunGuide(mode);
                        require(guide != null && !guide.getClass().getName().startsWith("com.balugaq.jeg."),
                            "Incomplete JEG replaced the working guide");
                    }
                    Files.writeString(Path.of("guide-result.txt"), "GUIDE_DAMAGED_REFUSED_CORE_RETAINED\n");
                } else {
                    require(plugin.isEnabled(), "Complete JEG did not enable");
                    Class<?> renderer = Class.forName("com.balugaq.jeg.utils.clickhandler.OnDisplay$ItemGroup", true, plugin.getClass().getClassLoader());
                    require(renderer.getClassLoader() == plugin.getClass().getClassLoader(), "Renderer from another plugin");
                    Fixture fixture = new Fixture(id);
                    PlayerProfile profile = new PlayerProfile(fixture.player, 0) {
                        @Override public Player getPlayer() { return fixture.player; }
                        @Override public OfflinePlayer getOwner() { return fixture.player; }
                    };
                    var history = PlayerProfile.class.getDeclaredField("guideHistory");
                    history.setAccessible(true);
                    history.set(profile, new JEGGuideHistory(profile));
                    Slimefun.getRegistry().getPlayerProfiles().put(id, profile);
                    for (var mode : new SlimefunGuideMode[] {SlimefunGuideMode.SURVIVAL_MODE, SlimefunGuideMode.CHEAT_MODE}) {
                        int before = fixture.opened;
                        SlimefunGuide.openGuide(fixture.player, mode);
                        require(fixture.opened > before, "Guide history did not open an inventory: " + mode);
                        require(fixture.last != null && Arrays.stream(fixture.last.getContents())
                            .filter(item -> item != null && !item.getType().isAir()).count() > 5, "Empty guide menu");
                        var guide = (JEGSlimefunGuideImplementation) Slimefun.getRegistry().getSlimefunGuide(mode);
                        var groups = guide.getVisibleItemGroups(fixture.player, profile);
                        var group = groups.stream().filter(g -> !g.getItems().isEmpty()
                            && !(g instanceof io.github.thebusybiscuit.slimefun4.api.items.groups.FlexItemGroup)).findFirst().orElseThrow();
                        before = fixture.opened;
                        guide.openItemGroup(profile, group, 1);
                        require(fixture.opened > before, "Item group did not open: " + mode);
                    }
                    Files.writeString(Path.of("guide-result.txt"), "GUIDE_HISTORY_AND_GROUPS_PASS\nmenus=" + fixture.opened + "\n");
                }
                getLogger().info(Files.readString(Path.of("guide-result.txt")).strip());
            } catch (Throwable error) {
                getLogger().log(java.util.logging.Level.SEVERE, "GUIDE_RENDERER_PROBE_FAILED", error);
            } finally {
                Slimefun.getRegistry().getPlayerProfiles().remove(id);
                Bukkit.shutdown();
            }
        }, 80L);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static final class Fixture {
        final Inventory storage = Bukkit.createInventory(null, 36);
        final org.bukkit.persistence.PersistentDataContainer data = new ItemStack(Material.PAPER).getItemMeta().getPersistentDataContainer();
        Inventory last;
        int opened;
        final PlayerInventory inventory = (PlayerInventory) Proxy.newProxyInstance(PlayerInventory.class.getClassLoader(),
            new Class<?>[] {PlayerInventory.class}, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "getItemInMainHand", "getItemInOffHand": return new ItemStack(Material.AIR);
                    case "getArmorContents", "getExtraContents": return new ItemStack[4];
                }
                try { return storage.getClass().getMethod(method.getName(), method.getParameterTypes()).invoke(storage, args); }
                catch (InvocationTargetException error) { throw error.getCause(); }
            });
        final Player player;
        Fixture(UUID id) {
            player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "getName", "getDisplayName" -> "GuideRendererTest";
                    case "getInventory" -> inventory;
                    case "getPersistentDataContainer" -> data;
                    case "getWorld" -> Bukkit.getWorlds().getFirst();
                    case "getLocation" -> Bukkit.getWorlds().getFirst().getSpawnLocation();
                    case "getPlayer" -> proxy;
                    case "getServer" -> Bukkit.getServer();
                    case "isOnline", "isConnected", "isValid", "isOp", "hasPermission", "isPermissionSet" -> true;
                    case "isDead", "isSneaking", "isSprinting" -> false;
                    case "getGameMode" -> GameMode.SURVIVAL;
                    case "getLocale" -> "en_us";
                    case "locale" -> Locale.US;
                    case "getLevel", "getTotalExperience" -> 0;
                    case "getExp" -> 0.0f;
                    case "openInventory" -> { last = (Inventory) args[0]; opened++; yield null; }
                    case "closeInventory", "sendMessage", "playSound", "updateInventory" -> null;
                    case "hashCode" -> id.hashCode();
                    case "equals" -> proxy == args[0];
                    case "toString" -> "GuideRendererTest";
                    default -> throw new UnsupportedOperationException("Unimplemented player fixture method: " + method);
                });
        }
    }
}
