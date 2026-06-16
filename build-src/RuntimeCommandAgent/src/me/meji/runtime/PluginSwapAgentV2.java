package me.meji.runtime;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;

public final class PluginSwapAgentV2 {
    public static void agentmain(String args, Instrumentation instrumentation) throws Exception {
        ClassLoader loader = findBukkitClassLoader(instrumentation);
        Class<?> bukkitClass = Class.forName("org.bukkit.Bukkit", false, loader);
        Class<?> pluginClass = Class.forName("org.bukkit.plugin.Plugin", false, loader);
        Object pluginManager = bukkitClass.getMethod("getPluginManager").invoke(null);
        Object scheduler = bukkitClass.getMethod("getScheduler").invoke(null);

        Object schedulerPlugin = null;
        Object[] plugins = (Object[]) pluginManager.getClass().getMethod("getPlugins").invoke(pluginManager);
        Method isEnabled = pluginClass.getMethod("isEnabled");
        for (Object plugin : plugins) {
            if ((Boolean) isEnabled.invoke(plugin)) {
                schedulerPlugin = plugin;
                break;
            }
        }
        if (schedulerPlugin == null) {
            throw new IllegalStateException("No enabled plugin available to schedule main-thread swap");
        }

        Object finalSchedulerPlugin = schedulerPlugin;
        Method runTask = scheduler.getClass().getMethod("runTask", pluginClass, Runnable.class);
        runTask.invoke(scheduler, finalSchedulerPlugin, (Runnable) () -> swap(args, bukkitClass, pluginClass));
    }

    private static void swap(String args, Class<?> bukkitClass, Class<?> pluginClass) {
        try {
            Object pluginManager = bukkitClass.getMethod("getPluginManager").invoke(null);
            Method getPlugin = pluginManager.getClass().getMethod("getPlugin", String.class);
            Method disablePlugin = pluginManager.getClass().getMethod("disablePlugin", pluginClass);
            Method loadPlugin = pluginManager.getClass().getMethod("loadPlugin", File.class);
            Method enablePlugin = pluginManager.getClass().getMethod("enablePlugin", pluginClass);

            Object v6 = getPlugin.invoke(pluginManager, "MejiAdvancedV6");
            if (v6 != null) {
                disablePlugin.invoke(pluginManager, v6);
            }

            Object v7 = getPlugin.invoke(pluginManager, "MejiAdvancedV7");
            if (v7 == null) {
                v7 = loadPlugin.invoke(pluginManager, new File(args));
            }
            enablePlugin.invoke(pluginManager, v7);

            Object logger = bukkitClass.getMethod("getLogger").invoke(null);
            logger.getClass().getMethod("info", String.class).invoke(logger, "[MejiRuntime] Main-thread loaded MejiAdvancedV7 without server restart");
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    private static ClassLoader findBukkitClassLoader(Instrumentation instrumentation) throws ClassNotFoundException {
        for (Class<?> loadedClass : instrumentation.getAllLoadedClasses()) {
            if ("org.bukkit.Bukkit".equals(loadedClass.getName())) {
                return loadedClass.getClassLoader();
            }
        }
        throw new ClassNotFoundException("org.bukkit.Bukkit");
    }
}
