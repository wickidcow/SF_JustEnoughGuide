package com.balugaq.jeg.utils;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Checks the renderer's own classes before replacing Slimefun's working guide. */
public final class GuideRuntimeClasses {
    private GuideRuntimeClasses() {}

    public static int preload(ClassLoader loader) throws ClassNotFoundException {
        return preloadFamilies(loader,
            "com.balugaq.jeg.utils.clickhandler.OnDisplay",
            "com.balugaq.jeg.utils.clickhandler.OnClick");
    }

    static int preloadFamilies(ClassLoader loader, String... roots) throws ClassNotFoundException {
        Objects.requireNonNull(loader, "loader");
        Set<Class<?>> visited = new HashSet<>();
        for (String root : roots) {
            // Linking only: static actions must not run before configuration and managers exist.
            visit(Class.forName(root, false, loader), loader, visited);
        }
        return visited.size();
    }

    private static void visit(Class<?> type, ClassLoader loader, Set<Class<?>> visited) {
        if (type.getClassLoader() != loader) {
            throw new LinkageError("Guide renderer resolved through another class loader: " + type.getName());
        }
        if (!visited.add(type)) return;
        // This resolves the declared nested types too, including OnDisplay$ItemGroup.
        for (Class<?> nested : type.getDeclaredClasses()) {
            visit(nested, loader, visited);
        }
    }
}
