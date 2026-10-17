package io.github.wickidcow.jegtest;

import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideImplementation;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideMode;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/** Real-server fixture; only core, JEG and this probe are installed in its fresh world. */
public final class PinyinStartupProbe extends JavaPlugin implements SlimefunAddon {
    private static final String SEARCH = "com.balugaq.jeg.api.groups.SearchGroup";
    private static final String STYLE = "com.github.houbb.pinyin.constant.enums.PinyinStyleEnum";
    private SlimefunItem chineseItem;
    private String phase;
    private boolean started;

    @Override
    public void onEnable() {
        require("disposable-only".equals(System.getProperty("jeg.pinyin.probe")), "Missing fixture authorization");
        Plugin jeg = Bukkit.getPluginManager().getPlugin("JustEnoughGuide");
        if (jeg != null && jeg.isEnabled()) {
            // This item exists only in the disposable test plugin, so actual
            // initialized search caches can prove opt-in transliteration.
            ItemGroup group = new ItemGroup(new NamespacedKey(this, "pinyin_fixture"), new ItemStack(Material.BOOK));
            ItemStack[] recipe = new ItemStack[9];
            recipe[0] = new ItemStack(Material.PAPER);
            chineseItem = new SlimefunItem(group,
                    new SlimefunItemStack("JEG_PINYIN_FIXTURE", Material.PAPER, "中国"),
                    RecipeType.ENHANCED_CRAFTING_TABLE, recipe);
            chineseItem.register(this);
        }
    }

    @Override
    public void onDisable() {
        try {
            require(started && phase != null && Bukkit.getServer().isStopping(), "Probe did not finish during normal server shutdown");
            Files.writeString(Path.of("probe-shutdown.txt"), "status=PASS\nphase=" + phase + "\nserver_stopping=true\n");
        } catch (Exception failure) {
            getLogger().log(Level.SEVERE, "JEG_PINYIN_PROBE_FAIL during shutdown", failure);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] arguments) {
        try {
            require(sender instanceof ConsoleCommandSender && !started, "Console-only single execution");
            require(arguments.length == 1, "Exactly one phase is required");
            phase = arguments[0];
            require(Set.of("control", "candidate-off", "candidate-on").contains(phase), "Unknown phase");
            require(Files.readString(Path.of("probe-phase.txt")).trim().equals(phase), "Phase authorization mismatch");
            require(Bukkit.isPrimaryThread() && "127.0.0.1".equals(Bukkit.getIp()) && !Bukkit.getOnlineMode(),
                    "Disposable loopback offline server required");
            require("jeg-pinyin-fixture".equals(Bukkit.getWorlds().getFirst().getName()), "Wrong fixture world");
            require(Bukkit.getMinecraftVersion().equals(System.getProperty("jeg.pinyin.minecraft")), "Wrong native Minecraft version");
            require(Files.readString(Path.of("acquisition-blocker-armed.txt")).trim().equals("ARMED"), "Blocker was not armed");
            require("4.1.75".equals(Slimefun.getVersion()) && Slimefun.instance().isEnabled(), "Wrong published core");
            require(Bukkit.getPluginManager().getPlugins().length == 3, "A peer plugin could mask missing dependencies");
            Plugin jeg = Bukkit.getPluginManager().getPlugin("JustEnoughGuide");
            require(jeg != null, "JEG was not discovered");
            started = true;
            if (phase.equals("control")) {
                require(Bukkit.getOnlinePlayers().isEmpty(), "Control must not have online players");
                require("2.1.72".equals(jeg.getPluginMeta().getVersion()) && !jeg.isEnabled(),
                        "Published negative control did not reproduce JEG startup failure");
                require(Files.readAllLines(Path.of("acquisition-attempts.txt")).stream()
                        .anyMatch(url -> url.contains("/com/github/houbb/pinyin/0.4.0/")),
                        "Control failed without reaching the real Pinyin acquisition path");
                result("CONTROL_REPRODUCED", "jeg_enabled=false\n");
                return true;
            }
            require(jeg.isEnabled() && jeg.getPluginMeta().getVersion().equals(System.getProperty("jeg.pinyin.candidate")),
                    "Candidate JEG is not enabled at the expected version");
            require(chineseItem != null && SlimefunItem.getById("JEG_PINYIN_FIXTURE") == chineseItem,
                    "Native search fixture item is not registered");
            Class<?> search = Class.forName(SEARCH, true, jeg.getClass().getClassLoader());
            search.getMethod("load").invoke(null);
            SlimefunItem motor = SlimefunItem.getById("ELECTRIC_MOTOR");
            require(motor != null, "Expected real core item is missing");
            new BukkitRunnable() {
                private int polls;
                @Override
                public void run() {
                    try {
                        Collection<?> available = (Collection<?>) search.getField("AVAILABLE_ITEMS").get(null);
                        Map<?, ?> cache = (Map<?, ?>) search.getField("KEYWORD_CACHE").get(null);
                        Object entries = cache.get('m');
                        if (available.contains(motor) && available.contains(chineseItem)
                                && entries instanceof Collection<?> matching && matching.contains(motor)) {
                            cancel();
                            verifyCandidate(jeg, search, motor);
                        } else {
                            require(++polls <= 120, "Actual JEG search caches never became ready");
                        }
                    } catch (Throwable failure) {
                        cancel();
                        fail(failure);
                    }
                }
            }.runTaskTimer(this, 10L, 10L);
        } catch (Throwable failure) {
            fail(failure);
        }
        return true;
    }

    private void verifyCandidate(Plugin jeg, Class<?> search, SlimefunItem motor) throws Exception {
        boolean connectedLane = !Bukkit.getMinecraftVersion().equals("26.3");
        Player player = Bukkit.getPlayerExact("JEGPinyinBot");
        if (connectedLane) {
            require(player != null && player.isOnline() && Bukkit.getOnlinePlayers().size() == 1, "Real connected fixture player required");
        } else {
            require(Bukkit.getOnlinePlayers().isEmpty(), "Milestone cache/API lane must not claim a connected player");
        }
        boolean pinyin = phase.equals("candidate-on");
        ClassLoader loader = jeg.getClass().getClassLoader();
        URL source = jeg.getClass().getProtectionDomain().getCodeSource().getLocation();
        for (String name : List.of("com.github.houbb.pinyin.util.PinyinHelper", STYLE,
                "com.github.houbb.opencc4j.support.data.impl.OpenccDatas",
                "com.github.houbb.heaven.util.io.StreamUtil", "com.github.houbb.nlp.common.constant.NlpConst")) {
            Class<?> type = Class.forName(name, true, loader);
            require(type.getClassLoader() == loader && source.equals(type.getProtectionDomain().getCodeSource().getLocation()),
                    "Dependency came from outside the candidate JAR: " + name);
        }
        int dictionaries = 0;
        for (String line : Files.readAllLines(Path.of("dictionaries.sha256"))) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] row = line.split("  ", 2);
            require(row.length == 2, "Malformed dictionary evidence");
            URL resource = loader.getResource(row[1]);
            require(resource != null && resource.openConnection() instanceof JarURLConnection connection
                    && source.equals(connection.getJarFileURL()), "Dictionary came from outside the candidate: " + row[1]);
            try (var input = resource.openStream()) {
                require(row[0].equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()))),
                        "Dictionary bytes changed: " + row[1]);
            }
            dictionaries++;
        }
        require(dictionaries == 20, "Incomplete dictionary evidence");
        Object manager = jeg.getClass().getMethod("getConfigManager").invoke(null);
        require(Boolean.valueOf(pinyin).equals(manager.getClass().getMethod("isPinyinSearch").invoke(manager)), "Wrong Pinyin configuration");
        List<?> shared = (List<?>) manager.getClass().getMethod("getSharedChars").invoke(manager);
        require(shared.size() > 100 && shared.stream().anyMatch(pair -> pair.toString().contains("国") && pair.toString().contains("國")),
                "OpenCC shared-character dictionaries failed to initialize");
        Class<?> style = loader.loadClass(STYLE);
        Object firstLetter = style.getField("FIRST_LETTER").get(null);
        var transliterate = search.getMethod("getPinyin", String.class, style);
        require("zg".equals(transliterate.invoke(null, "中国", firstLetter)), "Public simplified Pinyin API failed");
        require("zg".equals(transliterate.invoke(null, "中國", firstLetter)), "Public traditional Pinyin API failed");

        for (SlimefunGuideMode mode : SlimefunGuideMode.values()) {
            SlimefunGuideImplementation guide = Slimefun.getRegistry().getSlimefunGuide(mode);
            String expected = "com.balugaq.jeg.implementation.guide."
                    + (mode == SlimefunGuideMode.SURVIVAL_MODE ? "SurvivalGuideImplementation" : "CheatGuideImplementation");
            require(guide.getClass().getName().equals(expected) && guide.getClass().getClassLoader() == loader,
                    "JEG does not own the actual " + mode + " guide");
            String ordinary = ChatColor.stripColor(motor.getItemName()).toLowerCase(Locale.ROOT);
            if (connectedLane) {
                require(results(search, guide, player, ordinary, pinyin).contains(motor), "Actual ordinary guide search lost the core item");
                require(results(search, guide, player, "中国", pinyin).contains(chineseItem), "Actual Chinese-name search failed");
                require(results(search, guide, player, "zg", pinyin).contains(chineseItem) == pinyin,
                        "Actual Pinyin search did not respect opt-in configuration");
            } else {
                // No pinned protocol-777 client is available. This lane proves
                // the actual native cache and API, without claiming GUI/player coverage.
                var match = search.getMethod("isSearchFilterApplicable", String.class, String.class, boolean.class);
                require(Boolean.TRUE.equals(match.invoke(null, ordinary, ordinary, false)), "Native ordinary search API failed");
                require(Boolean.valueOf(pinyin).equals(match.invoke(null, "中国", "zg", pinyin)), "Native Pinyin search API ignored configuration");
                Map<?, ?> cache = (Map<?, ?>) search.getField("KEYWORD_CACHE").get(null);
                for (char letter : ordinary.toCharArray()) {
                    require(cache.get(letter) instanceof Collection<?> matching && matching.contains(motor), "Native ordinary item cache is incomplete");
                }
                require(cache.get('国') instanceof Collection<?> chinese && chinese.contains(chineseItem), "Native Chinese cache is incomplete");
                for (char letter : new char[] {'z', 'g'}) {
                    boolean present = cache.get(letter) instanceof Collection<?> matching && matching.contains(chineseItem);
                    require(present == pinyin, "Native Pinyin cache ignored configuration");
                }
            }
        }
        require(Files.size(Path.of("acquisition-attempts.txt")) == 0, "Candidate still attempted optional library acquisition");
        result("PASS", "jeg_enabled=true\npinyin=" + pinyin
                + "\nguides_owned=2\nordinary_search=true\npinyin_search_respects_config=true\n"
                + "shared_chars=" + shared.size() + "\ndictionaries=20\nclass_origin=candidate\nconnected_players="
                + (connectedLane ? "1" : "0") + "\nsearch_coverage="
                + (connectedLane ? "connected-player" : "native-cache-and-api") + "\n");
    }

    private List<?> results(Class<?> search, SlimefunGuideImplementation guide, Player player, String term, boolean pinyin) throws Exception {
        Object group = search.getConstructor(SlimefunGuideImplementation.class, Player.class, String.class, boolean.class)
                .newInstance(guide, player, term, pinyin);
        return (List<?>) search.getField("slimefunItemList").get(group);
    }

    private void result(String status, String details) throws Exception {
        Path destination = Path.of("probe-result.txt");
        Path temporary = Path.of("probe-result.tmp");
        Files.writeString(temporary, "status=" + status + "\nphase=" + phase + "\n" + details);
        Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
        getLogger().info("JEG_PINYIN_PROBE_" + status + " phase=" + phase);
    }

    private void fail(Throwable failure) {
        getLogger().log(Level.SEVERE, "JEG_PINYIN_PROBE_FAIL", failure);
        try {
            Files.writeString(Path.of("probe-result.txt"), "status=FAIL\nphase=" + phase + "\n");
        } catch (Exception writingFailure) {
            getLogger().log(Level.SEVERE, "Could not write failed Pinyin probe result", writingFailure);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    @Override
    public JavaPlugin getJavaPlugin() {
        return this;
    }

    @Override
    public String getBugTrackerURL() {
        return "https://github.com/wickidcow/SF_JustEnoughGuide/issues";
    }
}
