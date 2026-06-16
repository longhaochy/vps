package me.meji.kapakloader;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public final class CommandCleanupAgent {
    private CommandCleanupAgent() {
    }

    public static void agentmain(String args, Instrumentation instrumentation) {
        Set<String> commandNames = parseCommandNames(args);
        try {
            ClassLoader bukkitLoader = findBukkitClassLoader(instrumentation);
            Class<?> bukkitClass = Class.forName("org.bukkit.Bukkit", false, bukkitLoader);
            Object pluginManager = bukkitClass.getMethod("getPluginManager").invoke(null);
            Object scheduler = bukkitClass.getMethod("getScheduler").invoke(null);
            Logger logger = (Logger) bukkitClass.getMethod("getLogger").invoke(null);
            Object hostPlugin = findHostPlugin(pluginManager);
            Class<?> pluginType = Class.forName("org.bukkit.plugin.Plugin", false, bukkitLoader);
            Method runTask = scheduler.getClass().getMethod("runTask", pluginType, Runnable.class);
            runTask.invoke(scheduler, hostPlugin, (Runnable) () -> cleanupCommands(bukkitClass, commandNames));
            logger.info("[CommandCleanupAgent] Scheduled command cleanup for " + commandNames);
        } catch (Throwable error) {
            error.printStackTrace();
        }
    }

    private static Set<String> parseCommandNames(String args) {
        if (args == null || args.isBlank()) {
            return Set.of("kit");
        }
        java.util.HashSet<String> names = new java.util.HashSet<>();
        for (String part : args.split(",")) {
            String trimmed = part.trim().toLowerCase(Locale.ROOT);
            if (!trimmed.isBlank()) {
                names.add(trimmed);
            }
        }
        return names.isEmpty() ? Set.of("kit") : names;
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

    private static void cleanupCommands(Class<?> bukkitClass, Set<String> commandNames) {
        Logger logger = Logger.getLogger("Minecraft");
        try {
            Object server = bukkitClass.getMethod("getServer").invoke(null);
            Object commandMap = getCommandMap(server);
            Field knownCommandsField = findField(commandMap.getClass(), "knownCommands");
            knownCommandsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Object> knownCommands = (Map<String, Object>) knownCommandsField.get(commandMap);
            List<String> removed = new ArrayList<>();
            for (String key : new ArrayList<>(knownCommands.keySet())) {
                String normalized = key.toLowerCase(Locale.ROOT);
                String bareName = normalized.contains(":") ? normalized.substring(normalized.indexOf(':') + 1) : normalized;
                if (commandNames.contains(normalized) || commandNames.contains(bareName)) {
                    knownCommands.remove(key);
                    removed.add(key);
                }
            }
            logger.info("[CommandCleanupAgent] Removed command bindings: " + removed);
        } catch (Throwable error) {
            error.printStackTrace();
        }
    }

    private static Object getCommandMap(Object server) throws Exception {
        try {
            return server.getClass().getMethod("getCommandMap").invoke(server);
        } catch (NoSuchMethodException ignored) {
            Field commandMapField = findField(server.getClass(), "commandMap");
            commandMapField.setAccessible(true);
            return commandMapField.get(server);
        }
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
