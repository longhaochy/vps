package me.meji.kapakloader;

import java.io.File;
import java.io.InputStream;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.jar.JarFile;

public final class RedefineEnableAuraAgent {
    private static final String TARGET_CLASS = "me.meji.kapakaura.KapakWeaponAuraPlugin";
    private static final String TARGET_ENTRY = "me/meji/kapakaura/KapakWeaponAuraPlugin.class";

    private RedefineEnableAuraAgent() {
    }

    public static void agentmain(String args, Instrumentation instrumentation) {
        try {
            String[] parts = args == null ? new String[0] : args.split("\\|", 2);
            String patchedJar = parts.length > 0 && !parts[0].isBlank()
                ? parts[0]
                : "/workspaces/vps/plugins/KapakWeaponAuraPulse.jar";
            String[] pluginNames = parts.length > 1 && !parts[1].isBlank()
                ? parts[1].split(",")
                : new String[] {"KapakWeaponAura", "KapakWeaponAuraPulse"};

            byte[] patchedClass = readClassBytes(patchedJar);
            int redefined = 0;
            for (Class<?> loadedClass : instrumentation.getAllLoadedClasses()) {
                if (TARGET_CLASS.equals(loadedClass.getName())) {
                    instrumentation.redefineClasses(new ClassDefinition(loadedClass, patchedClass));
                    redefined++;
                }
            }

            int redefinedCount = redefined;
            ClassLoader bukkitLoader = findBukkitClassLoader(instrumentation);
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit", false, bukkitLoader);
            Object pluginManager = bukkit.getMethod("getPluginManager").invoke(null);
            Object scheduler = bukkit.getMethod("getScheduler").invoke(null);
            Object hostPlugin = findEnabledHostPlugin(pluginManager);
            Class<?> pluginClass = Class.forName("org.bukkit.plugin.Plugin", false, bukkitLoader);
            Method runTask = scheduler.getClass().getMethod("runTask", pluginClass, Runnable.class);
            runTask.invoke(scheduler, hostPlugin, (Runnable) () -> enablePlugins(pluginManager, pluginClass, pluginNames, redefinedCount));
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }

    private static byte[] readClassBytes(String jarPath) throws Exception {
        try (JarFile jar = new JarFile(new File(jarPath));
             InputStream input = jar.getInputStream(jar.getJarEntry(TARGET_ENTRY))) {
            if (input == null) {
                throw new IllegalArgumentException(TARGET_ENTRY + " not found in " + jarPath);
            }
            return input.readAllBytes();
        }
    }

    private static ClassLoader findBukkitClassLoader(Instrumentation instrumentation) {
        for (Class<?> loadedClass : instrumentation.getAllLoadedClasses()) {
            if ("org.bukkit.Bukkit".equals(loadedClass.getName())) {
                return loadedClass.getClassLoader();
            }
        }
        throw new IllegalStateException("org.bukkit.Bukkit is not loaded in the target JVM.");
    }

    private static Object findEnabledHostPlugin(Object pluginManager) throws Exception {
        Method getPlugins = pluginManager.getClass().getMethod("getPlugins");
        Object plugins = getPlugins.invoke(pluginManager);
        Method isEnabled = null;
        for (int i = 0; i < Array.getLength(plugins); i++) {
            Object plugin = Array.get(plugins, i);
            if (isEnabled == null) {
                isEnabled = plugin.getClass().getMethod("isEnabled");
            }
            if (Boolean.TRUE.equals(isEnabled.invoke(plugin))) {
                return plugin;
            }
        }
        throw new IllegalStateException("No enabled plugins are available to schedule a Bukkit task.");
    }

    private static void enablePlugins(Object pluginManager, Class<?> pluginClass, String[] pluginNames, int redefined) {
        try {
            Method getPlugin = pluginManager.getClass().getMethod("getPlugin", String.class);
            Method enablePlugin = pluginManager.getClass().getMethod("enablePlugin", pluginClass);
            Method isEnabled = pluginClass.getMethod("isEnabled");
            for (String rawName : pluginNames) {
                String pluginName = rawName.trim();
                if (pluginName.isEmpty()) {
                    continue;
                }
                Object plugin = getPlugin.invoke(pluginManager, pluginName);
                if (plugin == null) {
                    java.util.logging.Logger.getLogger("Minecraft").warning("[RedefineEnableAuraAgent] Plugin not found: " + pluginName);
                    continue;
                }
                if (!Boolean.TRUE.equals(isEnabled.invoke(plugin))) {
                    enablePlugin.invoke(pluginManager, plugin);
                }
                java.util.logging.Logger.getLogger("Minecraft").info("[RedefineEnableAuraAgent] " + pluginName
                    + " enabled with " + redefined + " redefined aura class(es).");
            }
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }
}
