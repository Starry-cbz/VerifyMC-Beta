package team.kitemc.verifymc.velocity;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Adapter class to make Velocity plugin compatible with Bukkit Plugin interface
 * Uses dynamic proxy to avoid compile-time dependency on Bukkit Plugin interface
 * This prevents ClassNotFoundException in Velocity environment
 */
public class PluginAdapter {
    private final VerifyMCVelocity velocityPlugin;
    private final com.velocitypowered.api.proxy.ProxyServer server;
    private FileConfiguration config;
    private final Path dataDirectory;
    private Object pluginProxy;

    public PluginAdapter(VerifyMCVelocity velocityPlugin) {
        this.velocityPlugin = velocityPlugin;
        this.server = velocityPlugin.getServer();
        this.dataDirectory = velocityPlugin.getDataFolder();
        this.pluginProxy = createPluginProxy();
    }

    /**
     * Create a dynamic proxy for Bukkit Plugin interface using reflection
     * This avoids compile-time dependency and handles ClassNotFoundException gracefully
     */
    private Object createPluginProxy() {
        try {
            // Try to load Bukkit Plugin interface using reflection
            Class<?> pluginInterface = Class.forName("org.bukkit.plugin.Plugin");
            return Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class[]{pluginInterface},
                new PluginInvocationHandler()
            );
        } catch (ClassNotFoundException e) {
            // In Velocity environment, Bukkit classes are not available at runtime
            // Create a minimal proxy that implements the methods we need
            velocityPlugin.getLogger().warn("Bukkit Plugin interface not available, creating compatibility proxy");
            return createCompatibilityProxy();
        }
    }

    /**
     * Create a compatibility proxy that doesn't require Bukkit classes
     */
    private Object createCompatibilityProxy() {
        // Return a proxy that implements common methods
        return Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class[]{}, // Empty interfaces - we'll handle method calls manually
            new PluginInvocationHandler()
        );
    }

    /**
     * Get the Plugin proxy object
     * This method returns Object to avoid compile-time dependency on Bukkit Plugin interface
     */
    public Object getPluginProxy() {
        return pluginProxy;
    }

    /**
     * Cast to Plugin interface (for use with Bukkit-based services)
     * Returns null if Bukkit classes are not available
     */
    @SuppressWarnings("unchecked")
    public <T> T asPlugin(Class<T> pluginClass) {
        if (pluginProxy != null && pluginClass.isInstance(pluginProxy)) {
            return (T) pluginProxy;
        }
        // Try to cast using reflection
        try {
            if (pluginProxy != null) {
                return pluginClass.cast(pluginProxy);
            }
        } catch (ClassCastException e) {
            // Ignore
        }
        return null;
    }

    public File getDataFolder() {
        return dataDirectory.toFile();
    }

    public FileConfiguration getConfig() {
        if (config == null) {
            reloadConfig();
        }
        return config;
    }

    public void saveConfig() {
        try {
            Path configFile = dataDirectory.resolve("config.yml");
            if (config != null) {
                config.save(configFile.toFile());
            }
        } catch (Exception e) {
            getLogger().warning("Failed to save config: " + e.getMessage());
        }
    }

    public void saveDefaultConfig() {
        Path configFile = dataDirectory.resolve("config.yml");
        if (!Files.exists(configFile)) {
            try (InputStream defaultConfig = getClass().getClassLoader().getResourceAsStream("config.yml")) {
                if (defaultConfig != null) {
                    Files.copy(defaultConfig, configFile);
                }
            } catch (IOException e) {
                getLogger().warning("Failed to save default config: " + e.getMessage());
            }
        }
    }

    public void saveResource(String resourcePath, boolean replace) {
        Path targetFile = dataDirectory.resolve(resourcePath);
        if (Files.exists(targetFile) && !replace) {
            return;
        }
        try (InputStream resource = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (resource != null) {
                Files.createDirectories(targetFile.getParent());
                Files.copy(resource, targetFile);
            }
        } catch (IOException e) {
            getLogger().warning("Failed to save resource " + resourcePath + ": " + e.getMessage());
        }
    }

    public void reloadConfig() {
        Path configFile = dataDirectory.resolve("config.yml");
        if (Files.exists(configFile)) {
            try (InputStream inputStream = Files.newInputStream(configFile)) {
                config = YamlConfiguration.loadConfiguration(new InputStreamReader(inputStream, java.nio.charset.StandardCharsets.UTF_8));
            } catch (IOException e) {
                getLogger().warning("Failed to reload config: " + e.getMessage());
                config = new YamlConfiguration();
            }
        } else {
            config = new YamlConfiguration();
            saveDefaultConfig();
            reloadConfig();
        }
    }

    public java.util.logging.Logger getLogger() {
        return new BukkitLoggerAdapter(velocityPlugin.getLogger());
    }

    public Object getScheduler() {
        return new VelocitySchedulerAdapter(server).getSchedulerProxy();
    }

    // Invocation handler for Plugin interface proxy
    private class PluginInvocationHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();
            Class<?> returnType = method.getReturnType();
            
            // Handle common Plugin interface methods
            switch (methodName) {
                case "getDataFolder":
                    return getDataFolder();
                case "getConfig":
                    return getConfig();
                case "saveConfig":
                    saveConfig();
                    return null;
                case "saveDefaultConfig":
                    saveDefaultConfig();
                    return null;
                case "saveResource":
                    if (args != null && args.length >= 2) {
                        saveResource((String) args[0], (Boolean) args[1]);
                    }
                    return null;
                case "reloadConfig":
                    reloadConfig();
                    return null;
                case "getLogger":
                    return getLogger();
                case "getScheduler":
                    return getScheduler();
                case "getName":
                    return "VerifyMC";
                case "isEnabled":
                    return true;
                case "getDescription":
                    try {
                        Class<?> descClass = Class.forName("org.bukkit.plugin.PluginDescriptionFile");
                        return descClass.getConstructor(String.class, String.class, String.class)
                            .newInstance("VerifyMC", "1.2.4", "team.kitemc.verifymc.velocity.VerifyMCVelocity");
                    } catch (Exception e) {
                        return null;
                    }
                case "getResource":
                    if (args != null && args.length > 0) {
                        return getClass().getClassLoader().getResourceAsStream((String) args[0]);
                    }
                    return null;
                case "getPluginLoader":
                case "getServer":
                case "getFile":
                case "getCommand":
                case "getCommands":
                case "getDefaultWorldGenerator":
                case "getDefaultBiomeProvider":
                    return null;
                case "onDisable":
                case "onLoad":
                case "onEnable":
                case "setNaggable":
                    return null;
                case "isNaggable":
                    return false;
                case "toString":
                    return "VerifyMC[Velocity]";
                case "equals":
                    return proxy == (args != null && args.length > 0 ? args[0] : null);
                case "hashCode":
                    return System.identityHashCode(proxy);
                default:
                    // For CommandExecutor and TabCompleter methods
                    if (methodName.equals("onCommand")) {
                        return false;
                    }
                    if (methodName.equals("onTabComplete")) {
                        return java.util.Collections.emptyList();
                    }
                    // Return default value based on return type
                    if (returnType.isPrimitive()) {
                        if (returnType == boolean.class) return false;
                        if (returnType == int.class) return 0;
                        if (returnType == long.class) return 0L;
                        if (returnType == double.class) return 0.0;
                        if (returnType == float.class) return 0.0f;
                        if (returnType == byte.class) return (byte) 0;
                        if (returnType == short.class) return (short) 0;
                        if (returnType == char.class) return '\0';
                    }
                    return null;
            }
        }
    }

    // Logger adapter
    private static class BukkitLoggerAdapter extends java.util.logging.Logger {
        private final org.slf4j.Logger logger;

        public BukkitLoggerAdapter(org.slf4j.Logger logger) {
            super("VerifyMC", null);
            this.logger = logger;
        }

        @Override
        public void info(String msg) {
            logger.info(msg);
        }

        @Override
        public void warning(String msg) {
            logger.warn(msg);
        }

        @Override
        public void severe(String msg) {
            logger.error(msg);
        }
    }

    // Scheduler adapter
    private static class VelocitySchedulerAdapter {
        private final com.velocitypowered.api.proxy.ProxyServer server;

        public VelocitySchedulerAdapter(com.velocitypowered.api.proxy.ProxyServer server) {
            this.server = server;
        }

        // Create a proxy for BukkitScheduler interface
        public Object getSchedulerProxy() {
            try {
                Class<?> schedulerInterface = Class.forName("org.bukkit.scheduler.BukkitScheduler");
                return Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class[]{schedulerInterface},
                    new SchedulerInvocationHandler()
                );
            } catch (ClassNotFoundException e) {
                // Return null if BukkitScheduler is not available
                return null;
            }
        }

        private class SchedulerInvocationHandler implements InvocationHandler {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                String methodName = method.getName();
                Class<?> returnType = method.getReturnType();
                
                if (args == null || args.length == 0) {
                    return returnType.isPrimitive() ? (returnType == boolean.class ? false : 0) : null;
                }
                
                Object plugin = args[0];
                Runnable task = null;
                
                if (args.length > 1) {
                    if (args[1] instanceof Runnable) {
                        task = (Runnable) args[1];
                    } else {
                        try {
                            Class<?> bukkitRunnableClass = Class.forName("org.bukkit.scheduler.BukkitRunnable");
                            if (bukkitRunnableClass.isInstance(args[1])) {
                                task = (Runnable) args[1];
                            }
                        } catch (ClassNotFoundException e) {
                            // Ignore
                        }
                    }
                }
                
                if (task == null) {
                    return returnType.isPrimitive() ? (returnType == boolean.class ? false : 0) : null;
                }
                
                long delay = 0;
                long period = 0;
                
                if (args.length > 2) {
                    delay = ((Number) args[2]).longValue() * 50;
                }
                if (args.length > 3) {
                    period = ((Number) args[3]).longValue() * 50;
                }
                
                if (methodName.contains("Timer") && period > 0) {
                    server.getScheduler().buildTask(plugin, task)
                        .delay(delay, java.util.concurrent.TimeUnit.MILLISECONDS)
                        .repeat(period, java.util.concurrent.TimeUnit.MILLISECONDS)
                        .schedule();
                } else if (delay > 0) {
                    server.getScheduler().buildTask(plugin, task)
                        .delay(delay, java.util.concurrent.TimeUnit.MILLISECONDS)
                        .schedule();
                } else {
                    server.getScheduler().buildTask(plugin, task).schedule();
                }
                
                return null;
            }
        }
    }
}
