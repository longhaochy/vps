package me.meji.runtime;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.util.logging.Logger;

public final class RuntimeCommandAgent {
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
        Object plugin = pluginManager.getClass().getMethod("getPlugin", String.class).invoke(pluginManager, "MejiAdvancedV6");
        if (plugin == null) {
            Object[] plugins = (Object[]) pluginManager.getClass().getMethod("getPlugins").invoke(pluginManager);
            if (plugins.length == 0) {
                throw new IllegalStateException("No Bukkit plugins available for scheduling");
            }
            plugin = plugins[0];
        }

        Object scheduler = bukkitClass.getMethod("getScheduler").invoke(null);
        Method runTask = scheduler.getClass().getMethod("runTask", pluginClass, Runnable.class);
        Object selectedPlugin = plugin;
        String commands = args == null ? "" : args;
        runTask.invoke(scheduler, selectedPlugin, (Runnable) () -> dispatch(commands));
    }

    private static void dispatch(String args) {
        try {
            ClassLoader bukkitLoader = findBukkitClassLoader();
            Class<?> bukkitClass = Class.forName("org.bukkit.Bukkit", false, bukkitLoader);
            Object sender = bukkitClass.getMethod("getConsoleSender").invoke(null);
            Method dispatch = bukkitClass.getMethod("dispatchCommand", Class.forName("org.bukkit.command.CommandSender", false, bukkitLoader), String.class);
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
        return RuntimeCommandAgent.class.getClassLoader();
    }
}
