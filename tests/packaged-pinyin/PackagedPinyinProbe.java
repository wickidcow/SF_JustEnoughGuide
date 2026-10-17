package io.github.wickidcow.jegtest;

import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Tests the real shaded JAR with no server, dependency classpath or network fallback. */
public final class PackagedPinyinProbe {
    private static final String PINYIN = "com.github.houbb.pinyin.util.PinyinHelper";
    private static final String STYLE = "com.github.houbb.pinyin.constant.enums.PinyinStyleEnum";
    private static final String OPENCC = "com.github.houbb.opencc4j.util.ZhConverterUtil";
    private static final String HEAVEN = "com.github.houbb.heaven.util.io.StreamUtil";
    private static final String NLP = "com.github.houbb.nlp.common.constant.NlpConst";

    public static void main(String[] arguments) throws Exception {
        require(arguments.length == 2, "Expected candidate JAR and dictionary checksums");
        Path jar = Path.of(arguments[0]).toRealPath();
        Map<String, String> dictionaries = new LinkedHashMap<>();
        for (String line : Files.readAllLines(Path.of(arguments[1]))) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] row = line.split("  ", 2);
            require(row.length == 2 && row[0].matches("[0-9a-f]{64}"), "Malformed dictionary checksum");
            require(dictionaries.put(row[1], row[0]) == null, "Duplicate dictionary checksum");
        }
        require(dictionaries.size() == 20, "Expected all 20 pinned search dictionaries");
        AtomicInteger acquisitionAttempts = new AtomicInteger();
        URL.setURLStreamHandlerFactory(protocol -> {
            if (!protocol.equals("http") && !protocol.equals("https")) return null;
            return new URLStreamHandler() {
                @Override
                protected URLConnection openConnection(URL url) throws IOException {
                    acquisitionAttempts.incrementAndGet();
                    throw new IOException("Packaged search runtime attempted network acquisition: " + url);
                }
            };
        });
        verify(jar, dictionaries);

        // An incomplete shaded JAR must fail this same verifier.
        Path temporary = Files.createTempDirectory("jeg-packaged-pinyin-negative-");
        try {
            for (String broken : new String[] {
                    "pinyin_dict_char.txt", "data/dictionary/STCharacters.txt",
                    "nlp/chinese_ts_char.txt", OPENCC.replace('.', '/') + ".class"}) {
                Path mutant = Files.createTempFile(temporary, "incomplete-", ".jar");
                copyWithout(jar, mutant, broken);
                boolean refused = false;
                try {
                    verify(mutant, dictionaries);
                } catch (IllegalStateException | ClassNotFoundException expected) {
                    refused = true;
                }
                require(refused, "Verifier accepted missing packaged dependency: " + broken);
            }
        } finally {
            try (var files = Files.list(temporary)) {
                for (Path file : files.toList()) Files.delete(file);
            }
            Files.delete(temporary);
        }
        require(acquisitionAttempts.get() == 0, "Packaged runtime attempted HTTP(S) acquisition");
        System.out.println("JEG_PACKAGED_PINYIN_PASS dictionaries=20 negative_controls=4 acquisition_attempts=0");
    }

    private static void verify(Path jar, Map<String, String> dictionaries) throws Exception {
        URL source = jar.toUri().toURL();
        try (URLClassLoader loader = new URLClassLoader(new URL[] {source}, ClassLoader.getPlatformClassLoader())) {
            for (String name : new String[] {PINYIN, STYLE, OPENCC, HEAVEN, NLP}) {
                Class<?> type = Class.forName(name, false, loader);
                require(type.getClassLoader() == loader, "Dependency escaped isolated candidate classloader: " + name);
                require(source.equals(type.getProtectionDomain().getCodeSource().getLocation()),
                        "Dependency came from a peer JAR or runtime cache: " + name);
            }
            for (var entry : dictionaries.entrySet()) {
                URL resource = loader.getResource(entry.getKey());
                require(resource != null, "Missing dictionary: " + entry.getKey());
                URLConnection connection = resource.openConnection();
                connection.setUseCaches(false);
                require(connection instanceof JarURLConnection jarConnection
                                && source.equals(jarConnection.getJarFileURL()),
                        "Dictionary came from outside the candidate: " + entry.getKey());
                try (var input = connection.getInputStream()) {
                    String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()));
                    require(entry.getValue().equals(digest), "Dictionary bytes changed: " + entry.getKey());
                }
            }
            Class<?> style = loader.loadClass(STYLE);
            Object firstLetter = style.getField("FIRST_LETTER").get(null);
            var pinyin = loader.loadClass(PINYIN).getMethod("toPinyin", String.class, style, String.class);
            require("zg".equals(pinyin.invoke(null, "中国", firstLetter, "")), "Simplified Pinyin failed");
            require("zg".equals(pinyin.invoke(null, "中國", firstLetter, "")), "Traditional Pinyin failed");
            Class<?> opencc = loader.loadClass(OPENCC);
            require("中国".equals(opencc.getMethod("toSimple", String.class).invoke(null, "中國")), "OpenCC simplified conversion failed");
            require("中國".equals(opencc.getMethod("toTraditional", String.class).invoke(null, "中国")), "OpenCC traditional conversion failed");
            try (ZipFile archive = new ZipFile(jar.toFile())) {
                byte[] group = archive.getInputStream(archive.getEntry("com/balugaq/jeg/api/groups/SearchGroup.class")).readAllBytes();
                String constants = new String(group, StandardCharsets.ISO_8859_1);
                require(constants.contains("(Ljava/lang/String;Lcom/github/houbb/pinyin/constant/enums/PinyinStyleEnum;)Ljava/lang/String;"),
                        "Existing public PinyinStyleEnum method descriptor was changed");
            }
        }
    }

    private static void copyWithout(Path source, Path destination, String omitted) throws IOException {
        try (ZipFile input = new ZipFile(source.toFile()); ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(destination))) {
            for (var entries = input.entries(); entries.hasMoreElements();) {
                ZipEntry entry = entries.nextElement();
                if (entry.getName().equals(omitted)) continue;
                output.putNextEntry(new ZipEntry(entry.getName()));
                try (var bytes = input.getInputStream(entry)) {
                    bytes.transferTo(output);
                }
                output.closeEntry();
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
