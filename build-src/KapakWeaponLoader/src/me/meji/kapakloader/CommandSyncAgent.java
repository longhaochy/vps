package me.meji.kapakloader;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.logging.Logger;

public final class CommandSyncAgent {
    private CommandSyncAgent() {
    }

    public static void agentmain(String args, Instrumentation instrumentation) {
        try {
            ClassLoader bukkitLoader = findBukkitClassLoader(instrumentation);
            Class<?> bukkitClass = Class.forName("org.bukkit.Bukkit", false, bukkitLoader);
            Object pluginManager = bukkitClass.getMethod("getPluginManager").invoke(null);
            Object scheduler = bukkitClass.getMethod("getScheduler").invoke(null);
            Logger logger = (Logger) bukkitClass.getMethod("getLogger").invoke(null);
            Object hostPlugin = findHostPlugin(pluginManager);
            Class<?> pluginType = Class.forName("org.bukkit.plugin.Plugin", false, bukkitLoader);
            Method runTask = scheduler.getClass().getMethod("runTask", pluginType, Runnable.class);
            runTask.invoke(scheduler, hostPlugin, (Runnable) () -> syncCommands(bukkitClass));
            logger.info("[CommandSyncAgent] Scheduled server command sync.");
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
            throw new IllegalStateException("No plugins are available to schedule a Bukkit task.");
        }
        return Array.get(plugins, 0);
    }

    private static void syncCommands(Class<?> bukkitClass) {
        try {
            Object server = bukkitClass.getMethod("getServer").invoke(null);
            server.getClass().getMethod("syncCommands").invoke(server);
            Logger.getLogger("Minecraft").info("[CommandSyncAgent] Server commands synced.");
        } catch (Throwable error) {
            error.printStackTrace();
        }
    }
}
