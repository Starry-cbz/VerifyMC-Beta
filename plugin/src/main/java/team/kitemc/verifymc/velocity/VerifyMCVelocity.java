package team.kitemc.verifymc.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.slf4j.Logger;
import team.kitemc.verifymc.db.FileUserDao;
import team.kitemc.verifymc.db.FileAuditDao;
import team.kitemc.verifymc.db.UserDao;
import team.kitemc.verifymc.db.AuditDao;
import team.kitemc.verifymc.db.MysqlUserDao;
import team.kitemc.verifymc.db.MysqlAuditDao;
import team.kitemc.verifymc.mail.MailService;
import team.kitemc.verifymc.service.VerifyCodeService;
import team.kitemc.verifymc.service.AuthmeService;
import team.kitemc.verifymc.service.VersionCheckService;
import team.kitemc.verifymc.web.WebServer;
import team.kitemc.verifymc.web.ReviewWebSocketServer;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@Plugin(
    id = "verifymc",
    name = "VerifyMC",
    version = "1.2.4",
    description = "Web-based MC whitelist authentication plugin for Velocity",
    authors = {"KiteMC"}
)
public class VerifyMCVelocity {
    private final ProxyServer server;
    private final Path dataDirectory;
    private final org.slf4j.Logger logger;
    private ResourceBundle messages;
    private WebServer webServer;
    private ReviewWebSocketServer wsServer;
    private UserDao userDao;
    private AuditDao auditDao;
    private VerifyCodeService codeService;
    private MailService mailService;
    private AuthmeService authmeService;
    private VersionCheckService versionCheckService;
    private Map<String, Object> config;
    private String whitelistMode;
    private String webRegisterUrl;
    private boolean debug = false;

    @Inject
    public VerifyMCVelocity(ProxyServer server, @DataDirectory Path dataDirectory, org.slf4j.Logger logger) {
        this.server = server;
        this.dataDirectory = dataDirectory;
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        // Initialize data directory
        try {
            Files.createDirectories(dataDirectory);
        } catch (IOException e) {
            logger.error("Failed to create data directory: " + e.getMessage());
            return;
        }

        // Load configuration
        loadConfig();

        // Load i18n messages
        String language = (String) config.getOrDefault("language", "zh");
        loadMessages(language);

        // Initialize storage
        initializeStorage();

        // Create plugin adapter for compatibility
        PluginAdapter pluginAdapter = new PluginAdapter(this);

        // Initialize services using adapter
        codeService = new VerifyCodeService(pluginAdapter);
        mailService = new MailService(pluginAdapter, this::getMessage);
        authmeService = new AuthmeService(pluginAdapter);
        versionCheckService = new VersionCheckService(pluginAdapter);

        // Start web server
        int port = (Integer) config.getOrDefault("web_port", 8080);
        int wsPort = (Integer) config.getOrDefault("ws_port", port + 1);
        String theme = (String) config.getOrDefault("frontend.theme", "default");
        String staticDir = dataDirectory.resolve("static").resolve(theme).toString();

        wsServer = new ReviewWebSocketServer(wsPort, pluginAdapter);
        try {
            wsServer.start();
            logger.info(getMessage("websocket.start_success") + ": " + wsPort);
        } catch (Exception e) {
            logger.warn(getMessage("websocket.start_failed") + ": " + e.getMessage());
        }

        PluginAdapter pluginAdapter = new PluginAdapter(this);
        webServer = new WebServer(port, staticDir, pluginAdapter, codeService, mailService, userDao, auditDao, authmeService, wsServer, messages);
        try {
            webServer.start();
            logger.info(getMessage("web.start_success") + ": " + port);
        } catch (Exception e) {
            logger.warn(getMessage("web.start_failed") + ": " + e.getMessage());
        }

        // Register commands
        registerCommands();

        // Log plugin enabled
        logger.info(getMessage("plugin.enabled"));
        logger.info(getMessage("server.detected.velocity"));
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (webServer != null) {
            webServer.stop();
        }
        if (wsServer != null) {
            try {
                wsServer.stop();
            } catch (InterruptedException e) {
                logger.warn(getMessage("websocket.stop_interrupted") + ": " + e.getMessage());
                Thread.currentThread().interrupt();
            }
        }
        if (userDao != null) {
            userDao.save();
        }
        if (auditDao != null) {
            auditDao.save();
        }
        logger.info(getMessage("plugin.disabled"));
    }

    @Subscribe
    public void onPlayerLogin(LoginEvent event) {
        com.velocitypowered.api.proxy.Player player = event.getPlayer();
        String username = player.getUsername();
        String ip = player.getRemoteAddress().getAddress().getHostAddress();

        // Check bypass IPs
        @SuppressWarnings("unchecked")
        List<String> bypassIps = (List<String>) config.getOrDefault("whitelist_bypass_ips", Collections.emptyList());
        if (bypassIps.contains(ip)) {
            debugLog("Bypassed whitelist check for IP: " + ip);
            return;
        }

        // Check player in database
        Map<String, Object> user = userDao != null ? userDao.getAllUsers().stream()
            .filter(u -> username.equalsIgnoreCase((String) u.get("username")) && "approved".equals(u.get("status")))
            .findFirst().orElse(null) : null;

        if (user == null) {
            // Player not approved
            String url = webRegisterUrl;
            String msgKey = "velocity.login.not_whitelisted";
            String msg = getMessage(msgKey).replace("{url}", url);
            
            Component kickMessage = LegacyComponentSerializer.legacySection().deserialize(msg);
            event.setResult(ResultedEvent.ComponentResult.denied(kickMessage));
            debugLog("Blocked unregistered player: " + username + " from IP: " + ip);
        } else {
            debugLog("Allowed registered player: " + username + " (Status: approved)");
        }
    }

    private void loadConfig() {
        Path configFile = dataDirectory.resolve("config.yml");
        if (!Files.exists(configFile)) {
            // Create default config
            createDefaultConfig(configFile);
        }

        try (InputStream inputStream = Files.newInputStream(configFile)) {
            Yaml yaml = new Yaml();
            config = yaml.load(inputStream);
            if (config == null) {
                config = new HashMap<>();
            }
        } catch (IOException e) {
            logger.error("Failed to load config: " + e.getMessage());
            config = new HashMap<>();
        }

        whitelistMode = (String) config.getOrDefault("whitelist_mode", "plugin");
        webRegisterUrl = (String) config.getOrDefault("web_register_url", "https://domain.com/");
        debug = (Boolean) config.getOrDefault("debug", false);
    }

    private void createDefaultConfig(Path configFile) {
        try (InputStream defaultConfig = getClass().getClassLoader().getResourceAsStream("config.yml")) {
            if (defaultConfig != null) {
                Files.copy(defaultConfig, configFile);
            }
        } catch (IOException e) {
            logger.error("Failed to create default config: " + e.getMessage());
        }
    }

    private void loadMessages(String language) {
        try {
            InputStream stream = getClass().getClassLoader().getResourceAsStream("i18n/messages_" + language + ".properties");
            if (stream != null) {
                messages = new PropertyResourceBundle(stream);
            } else {
                // Fallback to English
                stream = getClass().getClassLoader().getResourceAsStream("i18n/messages_en.properties");
                if (stream != null) {
                    messages = new PropertyResourceBundle(stream);
                }
            }
        } catch (IOException e) {
            logger.error("Failed to load messages: " + e.getMessage());
            messages = new ResourceBundle() {
                @Override
                protected Object handleGetObject(String key) {
                    return key;
                }

                @Override
                public Enumeration<String> getKeys() {
                    return Collections.emptyEnumeration();
                }
            };
        }
    }

    private void initializeStorage() {
        String storageType = (String) config.getOrDefault("storage.type", "data");
        PluginAdapter pluginAdapter = new PluginAdapter(this);

        if ("mysql".equalsIgnoreCase(storageType)) {
            Properties mysqlConfig = new Properties();
            @SuppressWarnings("unchecked")
            Map<String, Object> mysqlSettings = (Map<String, Object>) config.getOrDefault("storage.mysql", Collections.emptyMap());
            mysqlConfig.setProperty("host", (String) mysqlSettings.getOrDefault("host", "localhost"));
            mysqlConfig.setProperty("port", String.valueOf(mysqlSettings.getOrDefault("port", 3306)));
            mysqlConfig.setProperty("database", (String) mysqlSettings.getOrDefault("database", "verifymc"));
            mysqlConfig.setProperty("user", (String) mysqlSettings.getOrDefault("user", "root"));
            mysqlConfig.setProperty("password", (String) mysqlSettings.getOrDefault("password", ""));

            try {
                userDao = new MysqlUserDao(mysqlConfig, messages, pluginAdapter);
                auditDao = new MysqlAuditDao(mysqlConfig);
                logger.info(getMessage("storage.mysql.enabled"));
            } catch (Exception e) {
                logger.error(getMessage("storage.migrate.fail").replace("{0}", e.getMessage()));
                return;
            }
        } else {
            File userFile = dataDirectory.resolve("data").resolve("users.json").toFile();
            File auditFile = dataDirectory.resolve("data").resolve("audits.json").toFile();
            userFile.getParentFile().mkdirs();
            auditFile.getParentFile().mkdirs();
            userDao = new FileUserDao(userFile, pluginAdapter);
            auditDao = new FileAuditDao(auditFile);
            logger.info(getMessage("storage.file.enabled"));
        }
    }

    private void registerCommands() {
        CommandManager commandManager = server.getCommandManager();
        commandManager.register("vmc", new VelocityCommand());
    }

    public String getMessage(String key) {
        return getMessage(key, (String) config.getOrDefault("language", "zh"));
    }

    public String getMessage(String key, String language) {
        if (messages != null && messages.containsKey(key)) {
            return messages.getString(key);
        }
        return key;
    }

    public void debugLog(String msg) {
        if (debug) {
            logger.info("[DEBUG] " + msg);
        }
    }

    // Velocity-specific adapter methods
    public Map<String, Object> getConfig() {
        return config;
    }

    public Path getDataFolder() {
        return dataDirectory;
    }

    public org.slf4j.Logger getLogger() {
        return logger;
    }

    private class VelocityCommand implements SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            CommandSource source = invocation.source();
            String[] args = invocation.arguments();

            if (args.length == 0) {
                showHelp(source);
                return;
            }

            String subCommand = args[0].toLowerCase();
            String language = (String) config.getOrDefault("language", "zh");

            switch (subCommand) {
                case "help":
                    showHelp(source);
                    break;
                case "port":
                    showPort(source, language);
                    break;
                case "reload":
                    if (!source.hasPermission("verifymc.admin")) {
                        source.sendMessage(Component.text(getMessage("command.no_permission", language)));
                        return;
                    }
                    reloadPlugin(source, language);
                    break;
                case "add":
                    if (args.length < 3) {
                        source.sendMessage(Component.text(getMessage("command.add_usage", language)));
                        return;
                    }
                    if (!source.hasPermission("verifymc.admin")) {
                        source.sendMessage(Component.text(getMessage("command.no_permission", language)));
                        return;
                    }
                    addWhitelist(source, args[1], args[2], language);
                    break;
                case "remove":
                    if (args.length < 2) {
                        source.sendMessage(Component.text(getMessage("command.remove_usage", language)));
                        return;
                    }
                    if (!source.hasPermission("verifymc.admin")) {
                        source.sendMessage(Component.text(getMessage("command.no_permission", language)));
                        return;
                    }
                    removeWhitelist(source, args[1], language);
                    break;
                default:
                    showHelp(source);
                    break;
            }
        }

        @Override
        public List<String> suggest(Invocation invocation) {
            String[] args = invocation.arguments();
            if (args.length == 1) {
                String prefix = args[0].toLowerCase();
                return Arrays.asList("help", "reload", "add", "remove", "port").stream()
                    .filter(cmd -> cmd.startsWith(prefix))
                    .collect(Collectors.toList());
            }
            return Collections.emptyList();
        }

        @Override
        public boolean hasPermission(Invocation invocation) {
            return true;
        }
    }

    private void showHelp(CommandSource sender) {
        String language = (String) config.getOrDefault("language", "zh");
        sender.sendMessage(Component.text("§6=== VerifyMC " + getMessage("command.help.title", language) + " ===\n"));
        sender.sendMessage(Component.text("§e/vmc help §7- " + getMessage("command.help.help", language) + "\n"));
        sender.sendMessage(Component.text("§e/vmc port §7- " + getMessage("command.help.port", language) + "\n"));
        sender.sendMessage(Component.text("§e/vmc reload §7- " + getMessage("command.help.reload", language) + "\n"));
        sender.sendMessage(Component.text("§e/vmc add <" + getMessage("command.help.player", language) + "> §7- " + getMessage("command.help.add", language) + "\n"));
        sender.sendMessage(Component.text("§e/vmc remove <" + getMessage("command.help.player", language) + "> §7- " + getMessage("command.help.remove", language) + "\n"));
    }

    private void showPort(CommandSource sender, String language) {
        int port = (Integer) config.getOrDefault("web_port", 8080);
        sender.sendMessage(Component.text("§a" + getMessage("command.port_info", language).replace("{port}", String.valueOf(port))));
    }

    private void reloadPlugin(CommandSource sender, String language) {
        sender.sendMessage(Component.text("§a" + getMessage("plugin.reload.start", language)));
        // Reload config
        loadConfig();
        String lang = (String) config.getOrDefault("language", "zh");
        loadMessages(lang);
        sender.sendMessage(Component.text("§a" + getMessage("plugin.reload.success", language)));
    }

    private void addWhitelist(CommandSource sender, String username, String email, String language) {
        try {
            String uuid = java.util.UUID.randomUUID().toString(); // Velocity doesn't have offline player UUID
            Map<String, Object> user = userDao.getUserByUsername(username);
            boolean ok;

            if (user != null) {
                ok = userDao.updateUserStatus(user.get("uuid").toString(), "approved");
            } else {
                ok = userDao.registerUser(uuid, username, email, "approved");
            }

            userDao.save();

            if (ok) {
                sender.sendMessage(Component.text("§a" + getMessage("command.add_success", language).replace("{player}", username)));
            } else {
                sender.sendMessage(Component.text("§c" + getMessage("command.add_failed", language)));
            }
        } catch (Exception e) {
            sender.sendMessage(Component.text("§c" + getMessage("command.add_failed", language) + ": " + e.getMessage()));
        }
    }

    private void removeWhitelist(CommandSource sender, String username, String language) {
        try {
            Map<String, Object> user = userDao.getUserByUsername(username);
            if (user == null) {
                sender.sendMessage(Component.text("§c" + getMessage("command.remove_failed", language)));
                return;
            }

            String uuid = user.get("uuid").toString();
            boolean ok = userDao.deleteUser(uuid);
            userDao.save();

            if (ok) {
                sender.sendMessage(Component.text("§a" + getMessage("command.remove_success", language).replace("{player}", username)));
            } else {
                sender.sendMessage(Component.text("§c" + getMessage("command.remove_failed", language)));
            }
        } catch (Exception e) {
            sender.sendMessage(Component.text("§c" + getMessage("command.remove_failed", language) + ": " + e.getMessage()));
        }
    }
}

