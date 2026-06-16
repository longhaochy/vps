package me.meji.kapakloader;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.logging.Logger;

public final class DisablePluginAgentV4 {
    private DisablePluginAgentV4() {
    }

    public static void agentmain(String args, Instrumentation instrumentation) {
        String pluginName = args == null || args.isBlank() ? "KapakWeaponOverpower" : args;
        try {
            ClassLoader bukkitLoader = findBukkitClassLoader(instrumentation);
            Class<?> bukkitClass = Class.forName("org.bukkit.Bukkit", false, bukkitLoader);
            Object pluginManager = bukkitClass.getMethod("getPluginManager").invoke(null);
            Object scheduler = bukkitClass.getMethod("getScheduler").invoke(null);
            Logger logger = (Logger) bukkitClass.getMethod("getLogger").invoke(null);
            Object hostPlugin = findHostPlugin(pluginManager);
            Class<?> pluginType = Class.forName("org.bukkit.plugin.Plugin", false, bukkitLoader);
            Method runTask = scheduler.getClass().getMethod("runTask", pluginType, Runnable.class);
            runTask.invoke(scheduler, hostPlugin, (Runnable) () -> disable(pluginManager, pluginType, pluginName));
            logger.info("[DisablePluginAgent] Scheduled disable for " + pluginName);
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

    private static void disable(Object pluginManager, Class<?> pluginType, String pluginName) {
        try {
            Object plugin = pluginManager.getClass().getMethod("getPlugin", String.class).invoke(pluginManager, pluginName);
            if (plugin == null) {
                Logger.getLogger("Minecraft").info("[DisablePluginAgent] " + pluginName + " is not loaded.");
                return;
            }
            pluginManager.getClass().getMethod("disablePlugin", pluginType).invoke(pluginManager, plugin);
            Logger.getLogger("Minecraft").info("[DisablePluginAgent] " + pluginName + " disabled.");
        } catch (Throwable error) {
            error.printStackTrace();
        }
    }
}
