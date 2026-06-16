package me.meji.runtime;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;

public final class PluginSwapAgent {
    public static void agentmain(String args, Instrumentation instrumentation) throws Exception {
        ClassLoader loader = findBukkitClassLoader(instrumentation);
        Class<?> bukkitClass = Class.forName("org.bukkit.Bukkit", false, loader);
        Class<?> pluginClass = Class.forName("org.bukkit.plugin.Plugin", false, loader);

        Object pluginManager = bukkitClass.getMethod("getPluginManager").invoke(null);
        Method getPlugin = pluginManager.getClass().getMethod("getPlugin", String.class);
        Method disablePlugin = pluginManager.getClass().getMethod("disablePlugin", pluginClass);
        Method loadPlugin = pluginManager.getClass().getMethod("loadPlugin", File.class);
        Method enablePlugin = pluginManager.getClass().getMethod("enablePlugin", pluginClass);

        for (String name : new String[] {"MejiAdvancedV6", "MejiAdvancedV7"}) {
            Object plugin = getPlugin.invoke(pluginManager, name);
            if (plugin != null) {
                disablePlugin.invoke(pluginManager, plugin);
            }
        }

        Object newPlugin = loadPlugin.invoke(pluginManager, new File(args));
        enablePlugin.invoke(pluginManager, newPlugin);
        Object logger = bukkitClass.getMethod("getLogger").invoke(null);
        logger.getClass().getMethod("info", String.class).invoke(logger, "[MejiRuntime] Loaded " + args + " without server restart");
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
