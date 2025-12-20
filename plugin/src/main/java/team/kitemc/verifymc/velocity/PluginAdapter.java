package team.kitemc.verifymc.velocity;

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
    private Object config; // Use Object to avoid compile-time dependency on FileConfiguration
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
            // This is expected and normal - create a compatibility proxy
            velocityPlugin.getLogger().debug("Bukkit Plugin interface not available in Velocity environment, creating compatibility proxy");
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

    /**
     * Get config using reflection to avoid compile-time dependency
     * Returns a proxy object that implements FileConfiguration interface methods
     */
    public Object getConfig() {
        if (config == null) {
            reloadConfig();
        }
        
        // If config is a HashMap (Bukkit classes not available), create a proxy
        if (config instanceof java.util.HashMap) {
            return createConfigProxy((java.util.HashMap<String, Object>) config);
        }
        
        // If config is already a FileConfiguration object, return it
        // Try to check if it's a FileConfiguration using reflection
        try {
            Class<?> fileConfigClass = Class.forName("org.bukkit.configuration.file.FileConfiguration");
            if (fileConfigClass.isInstance(config)) {
                return config;
            }
        } catch (ClassNotFoundException e) {
            // FileConfiguration class not available, create proxy from HashMap
            if (config instanceof java.util.HashMap) {
                return createConfigProxy((java.util.HashMap<String, Object>) config);
            }
            // If config is not a HashMap, create a new one
            return createConfigProxy(new java.util.HashMap<String, Object>());
        }
        
        return config;
    }
    
    /**
     * Create a proxy for FileConfiguration interface
     * Uses reflection to avoid compile-time dependency
     */
    private Object createConfigProxy(java.util.HashMap<String, Object> configMap) {
        try {
            // Try to load FileConfiguration class using reflection
            Class<?> fileConfigClass = Class.forName("org.bukkit.configuration.file.FileConfiguration");
            return Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class[]{fileConfigClass},
                new ConfigInvocationHandler(configMap)
            );
        } catch (ClassNotFoundException e) {
            // FileConfiguration not available in Velocity environment
            // Return a wrapper object that implements the methods we need
            return new ConfigWrapper(configMap);
        }
    }
    
    /**
     * Wrapper class for config when FileConfiguration interface is not available
     */
    private static class ConfigWrapper {
        private final java.util.HashMap<String, Object> configMap;
        
        public ConfigWrapper(java.util.HashMap<String, Object> configMap) {
            this.configMap = configMap;
        }
        
        public String getString(String key) {
            return getString(key, "");
        }
        
        public String getString(String key, String defaultValue) {
            Object value = getNestedValue(configMap, key);
            return value != null ? value.toString() : defaultValue;
        }
        
        public int getInt(String key) {
            return getInt(key, 0);
        }
        
        public int getInt(String key, int defaultValue) {
            Object value = getNestedValue(configMap, key);
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            return defaultValue;
        }
        
        public boolean getBoolean(String key) {
            return getBoolean(key, false);
        }
        
        public boolean getBoolean(String key, boolean defaultValue) {
            Object value = getNestedValue(configMap, key);
            if (value instanceof Boolean) {
                return (Boolean) value;
            }
            if (value instanceof String) {
                return Boolean.parseBoolean((String) value);
            }
            return defaultValue;
        }
        
        public java.util.List<String> getStringList(String key) {
            Object value = getNestedValue(configMap, key);
            if (value instanceof java.util.List) {
                return (java.util.List<String>) value;
            }
            return java.util.Collections.emptyList();
        }
        
        private Object getNestedValue(java.util.Map<String, Object> map, String key) {
            if (key.contains(".")) {
                String[] parts = key.split("\\.", 2);
                Object value = map.get(parts[0]);
                if (value instanceof java.util.Map) {
                    return getNestedValue((java.util.Map<String, Object>) value, parts[1]);
                }
                return null;
            }
            return map.get(key);
        }
    }
    
    /**
     * Invocation handler for FileConfiguration proxy
     */
    private class ConfigInvocationHandler implements InvocationHandler {
        private final java.util.HashMap<String, Object> configMap;
        
        public ConfigInvocationHandler(java.util.HashMap<String, Object> configMap) {
            this.configMap = configMap;
        }
        
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();
            
            // Handle common FileConfiguration methods
            if (methodName.equals("getString") && args != null && args.length > 0) {
                String key = (String) args[0];
                String defaultValue = args.length > 1 ? (String) args[1] : null;
                Object value = getNestedValue(configMap, key);
                return value != null ? value.toString() : (defaultValue != null ? defaultValue : "");
            }
            
            if (methodName.equals("getInt") && args != null && args.length > 0) {
                String key = (String) args[0];
                int defaultValue = args.length > 1 ? (Integer) args[1] : 0;
                Object value = getNestedValue(configMap, key);
                if (value instanceof Number) {
                    return ((Number) value).intValue();
                }
                return defaultValue;
            }
            
            if (methodName.equals("getBoolean") && args != null && args.length > 0) {
                String key = (String) args[0];
                boolean defaultValue = args.length > 1 ? (Boolean) args[1] : false;
                Object value = getNestedValue(configMap, key);
                if (value instanceof Boolean) {
                    return (Boolean) value;
                }
                if (value instanceof String) {
                    return Boolean.parseBoolean((String) value);
                }
                return defaultValue;
            }
            
            if (methodName.equals("getStringList") && args != null && args.length > 0) {
                String key = (String) args[0];
                Object value = getNestedValue(configMap, key);
                if (value instanceof java.util.List) {
                    return value;
                }
                return java.util.Collections.emptyList();
            }
            
            if (methodName.equals("save") && args != null && args.length > 0) {
                // Save config to file
                try {
                    Path configFile = dataDirectory.resolve("config.yml");
                    org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml();
                    try (FileWriter writer = new FileWriter(configFile.toFile())) {
                        yaml.dump(configMap, writer);
                    }
                } catch (Exception e) {
                    getLogger().warning("Failed to save config: " + e.getMessage());
                }
                return null;
            }
            
            // Return default value based on return type
            Class<?> returnType = method.getReturnType();
            if (returnType.isPrimitive()) {
                if (returnType == boolean.class) return false;
                if (returnType == int.class) return 0;
                if (returnType == long.class) return 0L;
                if (returnType == double.class) return 0.0;
                if (returnType == float.class) return 0.0f;
            }
            return null;
        }
        
        /**
         * Get nested value from map using dot notation (e.g., "storage.mysql.host")
         */
        private Object getNestedValue(java.util.Map<String, Object> map, String key) {
            if (key.contains(".")) {
                String[] parts = key.split("\\.", 2);
                Object value = map.get(parts[0]);
                if (value instanceof java.util.Map) {
                    return getNestedValue((java.util.Map<String, Object>) value, parts[1]);
                }
                return null;
            }
            return map.get(key);
        }
    }

    public void saveConfig() {
        try {
            Path configFile = dataDirectory.resolve("config.yml");
            if (config != null) {
                // If config is a HashMap, save using SnakeYAML
                if (config instanceof java.util.HashMap) {
                    org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml();
                    try (FileWriter writer = new FileWriter(configFile.toFile())) {
                        yaml.dump(config, writer);
                    }
                } else {
                    // Use reflection to call save method for FileConfiguration
                    try {
                        Method saveMethod = config.getClass().getMethod("save", File.class);
                        saveMethod.invoke(config, configFile.toFile());
                    } catch (NoSuchMethodException e) {
                        // If save method doesn't exist, try using SnakeYAML
                        org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml();
                        try (FileWriter writer = new FileWriter(configFile.toFile())) {
                            // Try to convert config to Map
                            if (config instanceof java.util.Map) {
                                yaml.dump(config, writer);
                            }
                        }
                    }
                }
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
        
        // In Velocity environment, Bukkit configuration classes are not available
        // Always use SnakeYAML directly to avoid ClassNotFoundException
        java.util.HashMap<String, Object> configMap = new java.util.HashMap<>();
        
        if (Files.exists(configFile)) {
            try (InputStream inputStream = Files.newInputStream(configFile)) {
                org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml();
                Object loaded = yaml.load(inputStream);
                if (loaded instanceof java.util.Map) {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> loadedMap = (java.util.Map<String, Object>) loaded;
                    configMap.putAll(loadedMap);
                }
            } catch (Exception ex) {
                getLogger().warning("Failed to load config file: " + ex.getMessage());
            }
        } else {
            saveDefaultConfig();
            // Try to load default config
            try (InputStream defaultConfig = getClass().getClassLoader().getResourceAsStream("config.yml")) {
                if (defaultConfig != null) {
                    org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml();
                    Object loaded = yaml.load(defaultConfig);
                    if (loaded instanceof java.util.Map) {
                        @SuppressWarnings("unchecked")
                        java.util.Map<String, Object> loadedMap = (java.util.Map<String, Object>) loaded;
                        configMap.putAll(loadedMap);
                    }
                }
            } catch (Exception ex) {
                getLogger().warning("Failed to load default config: " + ex.getMessage());
            }
        }
        
        config = configMap;
        
        // Try to create FileConfiguration proxy if Bukkit classes are available (for Bukkit servers)
        // This is optional and won't cause errors if classes are not available
        try {
            Class<?> yamlConfigClass = Class.forName("org.bukkit.configuration.file.YamlConfiguration");
            if (Files.exists(configFile)) {
                try (InputStream inputStream = Files.newInputStream(configFile)) {
                    Method loadMethod = yamlConfigClass.getMethod("loadConfiguration", Reader.class);
                    Object bukkitConfig = loadMethod.invoke(null, new InputStreamReader(inputStream, java.nio.charset.StandardCharsets.UTF_8));
                    if (bukkitConfig != null) {
                        config = bukkitConfig; // Use Bukkit config if available
                    }
                } catch (Exception e) {
                    // Ignore, use HashMap config
                }
            }
        } catch (ClassNotFoundException e) {
            // Bukkit classes not available, use HashMap config (already set above)
        } catch (Exception e) {
            // Ignore, use HashMap config
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
