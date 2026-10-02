package com.balugaq.jeg.utils;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/** Standalone JDK regression; no Bukkit substitute or extra runtime dependency. */
public final class GuideRuntimeClassesTest {
    public static class Fixture {
        static { System.setProperty("jeg.fixture.initialized", "true"); }
        public static class Child {
            static { System.setProperty("jeg.fixture.child.initialized", "true"); }
            public static class Grandchild {}
        }
    }
    private static final String ROOT = "com.balugaq.jeg.utils.GuideRuntimeClassesTest$Fixture";
    public static void main(String[] args) throws Exception {
        URL classes = Path.of(args[0]).toUri().toURL();
        System.clearProperty("jeg.fixture.initialized");
        System.clearProperty("jeg.fixture.child.initialized");
        try (var loader = new RecordingLoader(classes, null)) {
            require(GuideRuntimeClasses.preloadFamilies(loader, ROOT, ROOT) == 3, "Nested family count");
            require(loader.loaded.contains(ROOT + "$Child$Grandchild"), "Nested class not preloaded");
            require(System.getProperty("jeg.fixture.initialized") == null, "Parent initialized too early");
            require(System.getProperty("jeg.fixture.child.initialized") == null, "Child initialized too early");
            require(GuideRuntimeClasses.preloadFamilies(loader, ROOT) == 3, "Repeated validation changed result");
        }
        try (var loader = new RecordingLoader(classes, ROOT + "$Child")) {
            try {
                GuideRuntimeClasses.preloadFamilies(loader, ROOT);
                throw new AssertionError("Missing nested class accepted");
            } catch (NoClassDefFoundError expected) {
                require(expected.getMessage().contains("Fixture$Child"), "Wrong linkage failure");
            }
        }
        try (var loader = new RecordingLoader(classes, ROOT)) {
            try {
                GuideRuntimeClasses.preloadFamilies(loader, ROOT);
                throw new AssertionError("Missing root accepted");
            } catch (ClassNotFoundException expected) {
                require(expected.getMessage().equals(ROOT), "Wrong missing root");
            }
        }
        try (var loader = new URLClassLoader(new URL[0], GuideRuntimeClassesTest.class.getClassLoader())) {
            try {
                GuideRuntimeClasses.preloadFamilies(loader, ROOT);
                throw new AssertionError("Foreign class loader accepted");
            } catch (LinkageError expected) {
                require(expected.getMessage().contains("another class loader"), "Wrong ownership failure");
            }
        }
        System.out.println("Guide renderer preload: 8 regression assertions passed");
    }
    private static final class RecordingLoader extends URLClassLoader {
        final String blocked;
        final Set<String> loaded = new HashSet<>();
        RecordingLoader(URL classes, String blocked) { super(new URL[] {classes}, null); this.blocked = blocked; }
        @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (name.equals(blocked)) throw new ClassNotFoundException(name);
            loaded.add(name);
            return super.findClass(name);
        }
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
