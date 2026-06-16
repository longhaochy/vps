package me.meji.runtime;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.util.logging.Logger;

public final class RuntimeCommandAgentV4 {
    private static final String[] SCHEDULER_PLUGIN_NAMES = {
        "squaremap",
        "MejiAdvancedV8",
        "BetterKeepInventory",
        "Geyser-Spigot"
    };

    private static volatile Instrumentation instrumentationRef;

    public static void agentmain(String args, Instrumentation instrumentation) throws Exception {
        instrumentationRef = instrumentation;
        run(args);
    }

    public static void premain(String args, Instrumentation instrumentation) throws Exception {
        instrumentationRef = instrumentation;
        run(args);
    }

    private static void run(String args) throws Exception {
        ClassLoader bukkitLoader = findBukkitClassLoader();
        Class<?> bukkitClass = Class.forName("org.bukkit.Bukkit", false, bukkitLoader);
        Class<?> pluginClass = Class.forName("org.bukkit.plugin.Plugin", false, bukkitLoader);
        Object pluginManager = bukkitClass.getMethod("getPluginManager").invoke(null);
        Object plugin = findEnabledSchedulerPlugin(pluginManager);

        Object scheduler = bukkitClass.getMethod("getScheduler").invoke(null);
        Method runTask = scheduler.getClass().getMethod("runTask", pluginClass, Runnable.class);
        String commands = args == null ? "" : args;
        runTask.invoke(scheduler, plugin, (Runnable) () -> dispatch(commands));
    }

    private static Object findEnabledSchedulerPlugin(Object pluginManager) throws Exception {
        Method getPlugin = pluginManager.getClass().getMethod("getPlugin", String.class);
        Method getPlugins = pluginManager.getClass().getMethod("getPlugins");
        for (String name : SCHEDULER_PLUGIN_NAMES) {
            Object plugin = getPlugin.invoke(pluginManager, name);
            if (isEnabled(plugin)) {
                return plugin;
            }
        }

        Object[] plugins = (Object[]) getPlugins.invoke(pluginManager);
        for (Object plugin : plugins) {
            if (isEnabled(plugin)) {
                return plugin;
            }
        }
        throw new IllegalStateException("No enabled Bukkit plugin available for scheduling");
    }

    private static boolean isEnabled(Object plugin) throws Exception {
        return plugin != null && Boolean.TRUE.equals(plugin.getClass().getMethod("isEnabled").invoke(plugin));
    }

    private static void dispatch(String args) {
        try {
            ClassLoader bukkitLoader = findBukkitClassLoader();
            Class<?> bukkitClass = Class.forName("org.bukkit.Bukkit", false, bukkitLoader);
            Object sender = bukkitClass.getMethod("getConsoleSender").invoke(null);
            Method dispatch = bukkitClass.getMethod(
                "dispatchCommand",
                Class.forName("org.bukkit.command.CommandSender", false, bukkitLoader),
                String.class
            );
            Logger logger = (Logger) bukkitClass.getMethod("getLogger").invoke(null);
            for (String command : args.split("\\n")) {
                String trimmed = command.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                logger.info("[MejiRuntime] Dispatching: " + trimmed);
                dispatch.invoke(null, sender, trimmed);
            }
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    private static ClassLoader findBukkitClassLoader() throws ClassNotFoundException {
        Instrumentation instrumentation = instrumentationRef;
        if (instrumentation != null) {
            for (Class<?> loadedClass : instrumentation.getAllLoadedClasses()) {
                if ("org.bukkit.Bukkit".equals(loadedClass.getName())) {
                    return loadedClass.getClassLoader();
                }
            }
        }
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            ClassLoader loader = thread.getContextClassLoader();
            if (loader == null) {
                continue;
            }
            try {
                Class.forName("org.bukkit.Bukkit", false, loader);
                return loader;
            } catch (ClassNotFoundException ignored) {
                // Keep scanning active server/plugin threads.
            }
        }
        return RuntimeCommandAgentV4.class.getClassLoader();
    }
}
