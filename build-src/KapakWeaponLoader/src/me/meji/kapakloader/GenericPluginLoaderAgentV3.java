package me.meji.kapakloader;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.logging.Logger;

public final class GenericPluginLoaderAgentV3 {
    private GenericPluginLoaderAgentV3() {
    }

    public static void agentmain(String args, Instrumentation instrumentation) {
        String[] parts = args == null ? new String[0] : args.split("\\|", 2);
        String jarPath = parts.length >= 1 && !parts[0].isBlank() ? parts[0] : "/workspaces/vps/plugins/KapakWeaponScare.jar";
        String pluginName = parts.length >= 2 && !parts[1].isBlank() ? parts[1] : "KapakWeaponScare";
        try {
            ClassLoader bukkitLoader = findBukkitClassLoader(instrumentation);
            Class<?> bukkitClass = Class.forName("org.bukkit.Bukkit", false, bukkitLoader);
            Object pluginManager = bukkitClass.getMethod("getPluginManager").invoke(null);
            Object scheduler = bukkitClass.getMethod("getScheduler").invoke(null);
            Logger logger = (Logger) bukkitClass.getMethod("getLogger").invoke(null);
            Object hostPlugin = findHostPlugin(pluginManager);

            Class<?> pluginType = Class.forName("org.bukkit.plugin.Plugin", false, bukkitLoader);
            Method runTask = scheduler.getClass().getMethod("runTask", pluginType, Runnable.class);
            runTask.invoke(scheduler, hostPlugin, (Runnable) () -> loadOnServerThread(pluginManager, jarPath, pluginName));
            logger.info("[GenericPluginLoader] Scheduled plugin load for " + pluginName + " from " + jarPath);
        } catch (Throwable error) {
            error.printStackTrace();
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

    private static Object findHostPlugin(Object pluginManager) throws Exception {
        Object plugins = pluginManager.getClass().getMethod("getPlugins").invoke(pluginManager);
        if (Array.getLength(plugins) == 0) {
            throw new IllegalStateException("No enabled plugins are available to schedule a Bukkit task.");
        }
        return Array.get(plugins, 0);
    }

    private static void loadOnServerThread(Object pluginManager, String jarPath, String pluginName) {
        try {
            Class<?> pluginManagerType = pluginManager.getClass();
            ClassLoader bukkitLoader = pluginManagerType.getClassLoader();
            Class<?> pluginType = Class.forName("org.bukkit.plugin.Plugin", false, bukkitLoader);
            Logger logger = Logger.getLogger("Minecraft");

            Object existing = pluginManagerType.getMethod("getPlugin", String.class).invoke(pluginManager, pluginName);
            if (existing != null) {
                logger.info("[GenericPluginLoader] " + pluginName + " is already loaded.");
                return;
            }

            Object plugin = pluginManagerType.getMethod("loadPlugin", File.class).invoke(pluginManager, new File(jarPath));
            pluginManagerType.getMethod("enablePlugin", pluginType).invoke(pluginManager, plugin);
            logger.info("[GenericPluginLoader] " + pluginName + " loaded without restart.");
        } catch (Throwable error) {
            error.printStackTrace();
        }
    }
}
