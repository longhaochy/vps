package me.meji.runtime;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;

public final class GenericPluginLoadAgent {
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
            throw new IllegalStateException("No enabled plugin available to schedule main-thread load");
        }

        Method runTask = scheduler.getClass().getMethod("runTask", pluginClass, Runnable.class);
        Object finalSchedulerPlugin = schedulerPlugin;
        runTask.invoke(scheduler, finalSchedulerPlugin, (Runnable) () -> load(args, bukkitClass, pluginClass));
    }

    private static void load(String args, Class<?> bukkitClass, Class<?> pluginClass) {
        try {
            String[] parts = args.split("\\|", 2);
            File jar = new File(parts[0]);
            String[] disableNames = parts.length > 1 && !parts[1].isBlank() ? parts[1].split(",") : new String[0];

            Object pluginManager = bukkitClass.getMethod("getPluginManager").invoke(null);
            Method getPlugin = pluginManager.getClass().getMethod("getPlugin", String.class);
            Method disablePlugin = pluginManager.getClass().getMethod("disablePlugin", pluginClass);
            Method loadPlugin = pluginManager.getClass().getMethod("loadPlugin", File.class);
            Method enablePlugin = pluginManager.getClass().getMethod("enablePlugin", pluginClass);

            for (String name : disableNames) {
                Object plugin = getPlugin.invoke(pluginManager, name.trim());
                if (plugin != null) {
                    disablePlugin.invoke(pluginManager, plugin);
                }
            }

            Object plugin = loadPlugin.invoke(pluginManager, jar);
            enablePlugin.invoke(pluginManager, plugin);
            Object logger = bukkitClass.getMethod("getLogger").invoke(null);
            logger.getClass().getMethod("info", String.class).invoke(logger, "[MejiRuntime] Main-thread loaded " + jar.getName() + " without server restart");
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
