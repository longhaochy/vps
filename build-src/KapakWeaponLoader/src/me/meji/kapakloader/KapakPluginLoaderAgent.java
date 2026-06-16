package me.meji.kapakloader;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.logging.Logger;

public final class KapakPluginLoaderAgent {
    private KapakPluginLoaderAgent() {
    }

    public static void agentmain(String args, Instrumentation instrumentation) {
        load(args, instrumentation);
    }

    public static void premain(String args, Instrumentation instrumentation) {
        load(args, instrumentation);
    }

    private static void load(String args, Instrumentation instrumentation) {
        String jarPath = args == null || args.isBlank() ? "/workspaces/vps/plugins/KapakWeapon.jar" : args;
        try {
            ClassLoader bukkitLoader = findBukkitClassLoader(instrumentation);
            Class<?> bukkitClass = Class.forName("org.bukkit.Bukkit", false, bukkitLoader);
            Object pluginManager = bukkitClass.getMethod("getPluginManager").invoke(null);
            Object scheduler = bukkitClass.getMethod("getScheduler").invoke(null);
            Logger logger = (Logger) bukkitClass.getMethod("getLogger").invoke(null);
            Object hostPlugin = findHostPlugin(pluginManager);

            Class<?> pluginType = Class.forName("org.bukkit.plugin.Plugin", false, bukkitLoader);
            Class<?> runnableType = Runnable.class;
            Method runTask = scheduler.getClass().getMethod("runTask", pluginType, runnableType);
            runTask.invoke(scheduler, hostPlugin, (Runnable) () -> loadOnServerThread(pluginManager, jarPath));
            logger.info("[KapakWeaponLoader] Scheduled plugin load for " + jarPath);
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
        Method getPlugins = pluginManager.getClass().getMethod("getPlugins");
        Object plugins = getPlugins.invoke(pluginManager);
        int length = Array.getLength(plugins);
        if (length == 0) {
            throw new IllegalStateException("No enabled plugins are available to schedule a Bukkit task.");
        }
        return Array.get(plugins, 0);
    }

    private static void loadOnServerThread(Object pluginManager, String jarPath) {
        try {
            Class<?> pluginManagerType = pluginManager.getClass();
            ClassLoader bukkitLoader = pluginManagerType.getClassLoader();
            Class<?> pluginType = Class.forName("org.bukkit.plugin.Plugin", false, bukkitLoader);
            Logger logger = Logger.getLogger("Minecraft");

            Object existing = pluginManagerType.getMethod("getPlugin", String.class).invoke(pluginManager, "KapakWeapon");
            if (existing != null) {
                logger.info("[KapakWeaponLoader] KapakWeapon is already loaded.");
                return;
            }

            Object plugin = pluginManagerType.getMethod("loadPlugin", File.class).invoke(pluginManager, new File(jarPath));
            pluginManagerType.getMethod("enablePlugin", pluginType).invoke(pluginManager, plugin);
            logger.info("[KapakWeaponLoader] KapakWeapon loaded without restart.");
        } catch (Throwable error) {
            error.printStackTrace();
        }
    }
}
