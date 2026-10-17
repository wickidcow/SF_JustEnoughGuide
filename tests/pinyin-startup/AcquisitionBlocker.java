package io.github.wickidcow.jegtest;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Set;

/** Test-only agent: refuse exactly the optional Houbb acquisition used by released JEG. */
public final class AcquisitionBlocker {
    private static final Set<String> ARTIFACTS = Set.of("pinyin", "opencc4j", "heaven", "nlp-common");

    public static void premain(String argument, Instrumentation instrumentation) throws Exception {
        if (!"disposable-only".equals(System.getProperty("jeg.pinyin.probe"))) {
            throw new IllegalStateException("Pinyin acquisition blocker is only for the disposable fixture");
        }
        Path root = Path.of(argument).toRealPath();
        if (!root.equals(Path.of("").toRealPath())
                || !Files.readString(root.resolve("pinyin-fixture.marker")).trim().equals(root.toString())) {
            throw new IllegalStateException("Unauthorized fixture path");
        }
        Path attempts = root.resolve("acquisition-attempts.txt");
        if (Files.exists(attempts) || Files.exists(root.resolve("acquisition-blocker-armed.txt"))) {
            throw new IllegalStateException("Acquisition fixture must start fresh");
        }
        URLStreamHandler http = nativeHandler("http");
        URLStreamHandler https = nativeHandler("https");
        Files.createFile(attempts);
        URL.setURLStreamHandlerFactory(protocol -> switch (protocol) {
            case "http" -> wrapper(http, attempts, 80);
            case "https" -> wrapper(https, attempts, 443);
            default -> null;
        });
        Files.writeString(root.resolve("acquisition-blocker-armed.txt"), "ARMED\n");
    }

    private static URLStreamHandler nativeHandler(String protocol) throws Exception {
        return (URLStreamHandler) Class.forName("sun.net.www.protocol." + protocol + ".Handler")
                .getConstructor().newInstance();
    }

    private static URLStreamHandler wrapper(URLStreamHandler delegate, Path attempts, int port) {
        return new URLStreamHandler() {
            @Override
            protected URLConnection openConnection(URL url) throws IOException {
                refuseAcquisition(url, attempts);
                return new URL(null, url.toExternalForm(), delegate).openConnection();
            }

            @Override
            protected URLConnection openConnection(URL url, Proxy proxy) throws IOException {
                refuseAcquisition(url, attempts);
                return new URL(null, url.toExternalForm(), delegate).openConnection(proxy);
            }

            @Override
            protected int getDefaultPort() {
                return port;
            }
        };
    }

    private static synchronized void refuseAcquisition(URL url, Path attempts) throws IOException {
        for (String artifact : ARTIFACTS) {
            if (url.getPath().contains("/com/github/houbb/" + artifact + "/")) {
                Files.writeString(attempts, url.toExternalForm() + "\n", StandardOpenOption.APPEND);
                // Libby suppresses the IOException cause; record the actual interception
                // independently so the native control cannot pass for an unrelated failure.
                System.err.println("JEG_OFFLINE_ACQUISITION_BLOCKED " + url);
                throw new IOException("JEG_OFFLINE_ACQUISITION_BLOCKED " + url);
            }
        }
    }
}
