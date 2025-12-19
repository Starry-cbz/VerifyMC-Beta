package team.kitemc.verifymc.velocity;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Adapter class to make Velocity plugin compatible with Bukkit Plugin interface
 * This allows Velocity plugin to work with existing service classes that expect Bukkit Plugin
 */
public class PluginAdapter implements org.bukkit.plugin.Plugin {
    private final VerifyMCVelocity velocityPlugin;
    private final com.velocitypowered.api.proxy.ProxyServer server;
    private FileConfiguration config;
    private final Path dataFolder;

    public PluginAdapter(VerifyMCVelocity velocityPlugin) {
        this.velocityPlugin = velocityPlugin;
        this.server = velocityPlugin.getServer();
        this.dataFolder = velocityPlugin.getDataFolder();
    }

    @Override
    public File getDataFolder() {
        return dataFolder.toFile();
    }

    @Override
    public FileConfiguration getConfig() {
        if (config == null) {
            reloadConfig();
        }
        return config;
    }

    @Override
    public void saveConfig() {
        try {
            Path configFile = dataFolder.resolve("config.yml");
            if (config != null) {
                config.save(configFile.toFile());
            }
        } catch (Exception e) {
            getLogger().warning("Failed to save config: " + e.getMessage());
        }
    }

    @Override
    public void saveDefaultConfig() {
        Path configFile = dataFolder.resolve("config.yml");
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

    @Override
    public void saveResource(String resourcePath, boolean replace) {
        Path targetFile = dataFolder.resolve(resourcePath);
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

    @Override
    public void reloadConfig() {
        Path configFile = dataFolder.resolve("config.yml");
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

    @Override
    public org.bukkit.plugin.PluginLoader getPluginLoader() {
        return null; // Not used in Velocity
    }

    @Override
    public org.bukkit.Server getServer() {
        return null; // Not used in Velocity
    }

    @Override
    public boolean isEnabled() {
        return true; // Velocity plugin is always enabled when instantiated
    }

    @Override
    public void onDisable() {
        // Handled by Velocity lifecycle
    }

    @Override
    public void onLoad() {
        // Handled by Velocity lifecycle
    }

    @Override
    public void onEnable() {
        // Handled by Velocity lifecycle
    }

    @Override
    public boolean isNaggable() {
        return false;
    }

    @Override
    public void setNaggable(boolean canNag) {
        // Not used
    }

    @Override
    public org.bukkit.plugin.PluginDescriptionFile getDescription() {
        return new org.bukkit.plugin.PluginDescriptionFile(
            "VerifyMC",
            "1.2.4",
            "team.kitemc.verifymc.velocity.VerifyMCVelocity"
        );
    }

    @Override
    public java.util.logging.Logger getLogger() {
        return new BukkitLoggerAdapter(velocityPlugin.getLogger());
    }

    @Override
    public String getName() {
        return "VerifyMC";
    }

    public org.bukkit.scheduler.BukkitScheduler getScheduler() {
        return new VelocitySchedulerAdapter(server);
    }

    public org.bukkit.command.CommandExecutor getCommand(String name) {
        return null; // Commands handled by Velocity
    }

    public java.util.List<org.bukkit.command.Command> getCommands() {
        return java.util.Collections.emptyList();
    }

    public java.io.File getFile() {
        return null; // Not applicable for Velocity
    }

    // CommandExecutor interface implementation
    @Override
    public boolean onCommand(org.bukkit.command.CommandSender sender, org.bukkit.command.Command command, String label, String[] args) {
        return false; // Commands handled by Velocity
    }

    // TabCompleter interface implementation
    @Override
    public java.util.List<String> onTabComplete(org.bukkit.command.CommandSender sender, org.bukkit.command.Command command, String alias, String[] args) {
        return java.util.Collections.emptyList(); // Commands handled by Velocity
    }

    @Override
    public java.io.InputStream getResource(String filename) {
        return getClass().getClassLoader().getResourceAsStream(filename);
    }

    @Override
    public org.bukkit.generator.ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        return null; // Not applicable for Velocity
    }

    @Override
    public org.bukkit.generator.BiomeProvider getDefaultBiomeProvider(String worldName, String id) {
        return null; // Not applicable for Velocity
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
    private static class VelocitySchedulerAdapter implements org.bukkit.scheduler.BukkitScheduler {
        private final com.velocitypowered.api.proxy.ProxyServer server;

        public VelocitySchedulerAdapter(com.velocitypowered.api.proxy.ProxyServer server) {
            this.server = server;
        }

        @Override
        public int scheduleSyncDelayedTask(org.bukkit.plugin.Plugin plugin, Runnable task, long delay) {
            server.getScheduler().buildTask(plugin, task).delay(delay * 50, java.util.concurrent.TimeUnit.MILLISECONDS).schedule();
            return 0;
        }

        @Override
        public int scheduleSyncDelayedTask(org.bukkit.plugin.Plugin plugin, org.bukkit.scheduler.BukkitRunnable task, long delay) {
            return scheduleSyncDelayedTask(plugin, (Runnable) task, delay);
        }

        @Override
        public int scheduleSyncDelayedTask(org.bukkit.plugin.Plugin plugin, Runnable task) {
            return scheduleSyncDelayedTask(plugin, task, 0);
        }

        @Override
        public int scheduleSyncDelayedTask(org.bukkit.plugin.Plugin plugin, org.bukkit.scheduler.BukkitRunnable task) {
            return scheduleSyncDelayedTask(plugin, task, 0);
        }

        @Override
        public int scheduleSyncRepeatingTask(org.bukkit.plugin.Plugin plugin, Runnable task, long delay, long period) {
            server.getScheduler().buildTask(plugin, task)
                .delay(delay * 50, java.util.concurrent.TimeUnit.MILLISECONDS)
                .repeat(period * 50, java.util.concurrent.TimeUnit.MILLISECONDS)
                .schedule();
            return 0;
        }

        @Override
        public int scheduleSyncRepeatingTask(org.bukkit.plugin.Plugin plugin, org.bukkit.scheduler.BukkitRunnable task, long delay, long period) {
            return scheduleSyncRepeatingTask(plugin, (Runnable) task, delay, period);
        }

        @Override
        public int scheduleAsyncDelayedTask(org.bukkit.plugin.Plugin plugin, Runnable task, long delay) {
            server.getScheduler().buildTask(plugin, task).delay(delay * 50, java.util.concurrent.TimeUnit.MILLISECONDS).schedule();
            return 0;
        }

        @Override
        public int scheduleAsyncDelayedTask(org.bukkit.plugin.Plugin plugin, Runnable task) {
            return scheduleAsyncDelayedTask(plugin, task, 0);
        }

        @Override
        public int scheduleAsyncRepeatingTask(org.bukkit.plugin.Plugin plugin, Runnable task, long delay, long period) {
            server.getScheduler().buildTask(plugin, task)
                .delay(delay * 50, java.util.concurrent.TimeUnit.MILLISECONDS)
                .repeat(period * 50, java.util.concurrent.TimeUnit.MILLISECONDS)
                .schedule();
            return 0;
        }

        @Override
        public <T> java.util.concurrent.Future<T> callSyncMethod(org.bukkit.plugin.Plugin plugin, java.util.concurrent.Callable<T> task) {
            java.util.concurrent.CompletableFuture<T> future = new java.util.concurrent.CompletableFuture<>();
            server.getScheduler().buildTask(plugin, () -> {
                try {
                    future.complete(task.call());
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            }).schedule();
            return future;
        }

        @Override
        public void cancelTask(int taskId) {
            // Velocity tasks are managed differently
        }

        @Override
        public void cancelTasks(org.bukkit.plugin.Plugin plugin) {
            // Velocity tasks are managed differently
        }

        public void cancelAllTasks() {
            // Velocity tasks are managed differently
        }

        @Override
        public boolean isCurrentlyRunning(int taskId) {
            return false;
        }

        @Override
        public boolean isQueued(int taskId) {
            return false;
        }

        @Override
        public java.util.List<org.bukkit.scheduler.BukkitWorker> getActiveWorkers() {
            return java.util.Collections.emptyList();
        }

        @Override
        public java.util.List<org.bukkit.scheduler.BukkitTask> getPendingTasks() {
            return java.util.Collections.emptyList();
        }

        @Override
        public org.bukkit.scheduler.BukkitTask runTask(org.bukkit.plugin.Plugin plugin, Runnable task) throws IllegalArgumentException {
            server.getScheduler().buildTask(plugin, task).schedule();
            return null;
        }

        @Override
        public org.bukkit.scheduler.BukkitTask runTask(org.bukkit.plugin.Plugin plugin, org.bukkit.scheduler.BukkitRunnable task) throws IllegalArgumentException {
            return runTask(plugin, (Runnable) task);
        }

        @Override
        public org.bukkit.scheduler.BukkitTask runTaskAsynchronously(org.bukkit.plugin.Plugin plugin, Runnable task) throws IllegalArgumentException {
            server.getScheduler().buildTask(plugin, task).schedule();
            return null;
        }

        @Override
        public org.bukkit.scheduler.BukkitTask runTaskAsynchronously(org.bukkit.plugin.Plugin plugin, org.bukkit.scheduler.BukkitRunnable task) throws IllegalArgumentException {
            return runTaskAsynchronously(plugin, (Runnable) task);
        }

        @Override
        public org.bukkit.scheduler.BukkitTask runTaskLater(org.bukkit.plugin.Plugin plugin, Runnable task, long delay) throws IllegalArgumentException {
            server.getScheduler().buildTask(plugin, task).delay(delay * 50, java.util.concurrent.TimeUnit.MILLISECONDS).schedule();
            return null;
        }

        @Override
        public org.bukkit.scheduler.BukkitTask runTaskLater(org.bukkit.plugin.Plugin plugin, org.bukkit.scheduler.BukkitRunnable task, long delay) throws IllegalArgumentException {
            return runTaskLater(plugin, (Runnable) task, delay);
        }

        @Override
        public org.bukkit.scheduler.BukkitTask runTaskLaterAsynchronously(org.bukkit.plugin.Plugin plugin, Runnable task, long delay) throws IllegalArgumentException {
            server.getScheduler().buildTask(plugin, task).delay(delay * 50, java.util.concurrent.TimeUnit.MILLISECONDS).schedule();
            return null;
        }

        @Override
        public org.bukkit.scheduler.BukkitTask runTaskLaterAsynchronously(org.bukkit.plugin.Plugin plugin, org.bukkit.scheduler.BukkitRunnable task, long delay) throws IllegalArgumentException {
            return runTaskLaterAsynchronously(plugin, (Runnable) task, delay);
        }

        @Override
        public org.bukkit.scheduler.BukkitTask runTaskTimer(org.bukkit.plugin.Plugin plugin, Runnable task, long delay, long period) throws IllegalArgumentException {
            server.getScheduler().buildTask(plugin, task)
                .delay(delay * 50, java.util.concurrent.TimeUnit.MILLISECONDS)
                .repeat(period * 50, java.util.concurrent.TimeUnit.MILLISECONDS)
                .schedule();
            return null;
        }

        @Override
        public org.bukkit.scheduler.BukkitTask runTaskTimer(org.bukkit.plugin.Plugin plugin, org.bukkit.scheduler.BukkitRunnable task, long delay, long period) throws IllegalArgumentException {
            return runTaskTimer(plugin, (Runnable) task, delay, period);
        }

        @Override
        public org.bukkit.scheduler.BukkitTask runTaskTimerAsynchronously(org.bukkit.plugin.Plugin plugin, Runnable task, long delay, long period) throws IllegalArgumentException {
            server.getScheduler().buildTask(plugin, task)
                .delay(delay * 50, java.util.concurrent.TimeUnit.MILLISECONDS)
                .repeat(period * 50, java.util.concurrent.TimeUnit.MILLISECONDS)
                .schedule();
            return null;
        }

        @Override
        public org.bukkit.scheduler.BukkitTask runTaskTimerAsynchronously(org.bukkit.plugin.Plugin plugin, org.bukkit.scheduler.BukkitRunnable task, long delay, long period) throws IllegalArgumentException {
            return runTaskTimerAsynchronously(plugin, (Runnable) task, delay, period);
        }

        // Consumer-based methods (Bukkit API 1.13+)
        @Override
        public void runTask(org.bukkit.plugin.Plugin plugin, java.util.function.Consumer<org.bukkit.scheduler.BukkitTask> task) throws IllegalArgumentException {
            runTask(plugin, (Runnable) () -> {
                // Create a dummy task for Consumer
                org.bukkit.scheduler.BukkitTask dummyTask = new org.bukkit.scheduler.BukkitTask() {
                    @Override
                    public int getTaskId() { return 0; }
                    @Override
                    public org.bukkit.plugin.Plugin getOwner() { return plugin; }
                    @Override
                    public boolean isSync() { return true; }
                    @Override
                    public boolean isCancelled() { return false; }
                    @Override
                    public void cancel() {}
                };
                task.accept(dummyTask);
            });
        }

        @Override
        public void runTaskAsynchronously(org.bukkit.plugin.Plugin plugin, java.util.function.Consumer<org.bukkit.scheduler.BukkitTask> task) throws IllegalArgumentException {
            runTaskAsynchronously(plugin, (Runnable) () -> {
                org.bukkit.scheduler.BukkitTask dummyTask = new org.bukkit.scheduler.BukkitTask() {
                    @Override
                    public int getTaskId() { return 0; }
                    @Override
                    public org.bukkit.plugin.Plugin getOwner() { return plugin; }
                    @Override
                    public boolean isSync() { return false; }
                    @Override
                    public boolean isCancelled() { return false; }
                    @Override
                    public void cancel() {}
                };
                task.accept(dummyTask);
            });
        }

        @Override
        public void runTaskLater(org.bukkit.plugin.Plugin plugin, java.util.function.Consumer<org.bukkit.scheduler.BukkitTask> task, long delay) throws IllegalArgumentException {
            runTaskLater(plugin, (Runnable) () -> {
                org.bukkit.scheduler.BukkitTask dummyTask = new org.bukkit.scheduler.BukkitTask() {
                    @Override
                    public int getTaskId() { return 0; }
                    @Override
                    public org.bukkit.plugin.Plugin getOwner() { return plugin; }
                    @Override
                    public boolean isSync() { return true; }
                    @Override
                    public boolean isCancelled() { return false; }
                    @Override
                    public void cancel() {}
                };
                task.accept(dummyTask);
            }, delay);
        }

        @Override
        public void runTaskLaterAsynchronously(org.bukkit.plugin.Plugin plugin, java.util.function.Consumer<org.bukkit.scheduler.BukkitTask> task, long delay) throws IllegalArgumentException {
            runTaskLaterAsynchronously(plugin, (Runnable) () -> {
                org.bukkit.scheduler.BukkitTask dummyTask = new org.bukkit.scheduler.BukkitTask() {
                    @Override
                    public int getTaskId() { return 0; }
                    @Override
                    public org.bukkit.plugin.Plugin getOwner() { return plugin; }
                    @Override
                    public boolean isSync() { return false; }
                    @Override
                    public boolean isCancelled() { return false; }
                    @Override
                    public void cancel() {}
                };
                task.accept(dummyTask);
            }, delay);
        }

        @Override
        public void runTaskTimer(org.bukkit.plugin.Plugin plugin, java.util.function.Consumer<org.bukkit.scheduler.BukkitTask> task, long delay, long period) throws IllegalArgumentException {
            runTaskTimer(plugin, (Runnable) () -> {
                org.bukkit.scheduler.BukkitTask dummyTask = new org.bukkit.scheduler.BukkitTask() {
                    @Override
                    public int getTaskId() { return 0; }
                    @Override
                    public org.bukkit.plugin.Plugin getOwner() { return plugin; }
                    @Override
                    public boolean isSync() { return true; }
                    @Override
                    public boolean isCancelled() { return false; }
                    @Override
                    public void cancel() {}
                };
                task.accept(dummyTask);
            }, delay, period);
        }

        @Override
        public void runTaskTimerAsynchronously(org.bukkit.plugin.Plugin plugin, java.util.function.Consumer<org.bukkit.scheduler.BukkitTask> task, long delay, long period) throws IllegalArgumentException {
            runTaskTimerAsynchronously(plugin, (Runnable) () -> {
                org.bukkit.scheduler.BukkitTask dummyTask = new org.bukkit.scheduler.BukkitTask() {
                    @Override
                    public int getTaskId() { return 0; }
                    @Override
                    public org.bukkit.plugin.Plugin getOwner() { return plugin; }
                    @Override
                    public boolean isSync() { return false; }
                    @Override
                    public boolean isCancelled() { return false; }
                    @Override
                    public void cancel() {}
                };
                task.accept(dummyTask);
            }, delay, period);
        }
    }
}

