// File: src/main/java/fr/elias/oreoEssentials/OreoEssentials.java
package fr.elias.oreoEssentials;

import fr.elias.oreoEssentials.clearlag.ClearLagManager;
import fr.elias.oreoEssentials.commands.CommandManager;

// Core commands (essentials-like)
import fr.elias.oreoEssentials.commands.core.playercommands.*;
import fr.elias.oreoEssentials.commands.core.admins.*;
import fr.elias.oreoEssentials.commands.core.moderation.*;
import fr.elias.oreoEssentials.commands.core.admins.FlyCommand;
import fr.elias.oreoEssentials.commands.core.moderation.HealCommand;
import fr.elias.oreoEssentials.commands.core.playercommands.ReplyCommand;
import fr.elias.oreoEssentials.commands.core.playercommands.SetHomeCommand;
import fr.elias.oreoEssentials.commands.core.playercommands.SpawnCommand;
import fr.elias.oreoEssentials.commands.core.playercommands.TpAcceptCommand;
import fr.elias.oreoEssentials.commands.core.playercommands.TpDenyCommand;
import fr.elias.oreoEssentials.commands.core.playercommands.TpaCommand;
import fr.elias.oreoEssentials.commands.core.playercommands.WarpCommand;
import fr.elias.oreoEssentials.homes.TeleportBroker;
import fr.elias.oreoEssentials.services.*;
import fr.elias.oreoEssentials.services.chatservices.MuteService;
import fr.elias.oreoEssentials.services.mongoservices.*;
import fr.elias.oreoEssentials.util.KillallLogger;
import fr.elias.oreoEssentials.util.Lang;
import com.mongodb.client.MongoClient;
import fr.elias.oreoEssentials.scoreboard.ScoreboardConfig;
import fr.elias.oreoEssentials.scoreboard.ScoreboardService;
import fr.elias.oreoEssentials.scoreboard.ScoreboardToggleCommand;

// Tab completion
import fr.elias.oreoEssentials.commands.completion.HomeTabCompleter;
import fr.elias.oreoEssentials.commands.completion.WarpTabCompleter;

// Economy commands
import fr.elias.oreoEssentials.commands.ecocommands.MoneyCommand;
import fr.elias.oreoEssentials.commands.ecocommands.completion.MoneyTabCompleter;

// Databases / Cache
import fr.elias.oreoEssentials.database.JsonEconomyDatabase;
import fr.elias.oreoEssentials.database.MongoDBManager;
import fr.elias.oreoEssentials.database.PlayerEconomyDatabase;
import fr.elias.oreoEssentials.database.PostgreSQLManager;
import fr.elias.oreoEssentials.database.RedisManager;

// Economy bootstrap (internal bridge)
import fr.elias.oreoEssentials.economy.EconomyBootstrap;

// Listeners
import fr.elias.oreoEssentials.listeners.*;

// Offline cache
import fr.elias.oreoEssentials.offline.OfflinePlayerCache;

// RabbitMQ
import fr.elias.oreoEssentials.rabbitmq.PacketChannels;
import fr.elias.oreoEssentials.rabbitmq.handler.PlayerJoinPacketHandler;
import fr.elias.oreoEssentials.rabbitmq.handler.PlayerQuitPacketHandler;
import fr.elias.oreoEssentials.rabbitmq.handler.RemoteMessagePacketHandler;
import fr.elias.oreoEssentials.rabbitmq.packet.PacketManager;
import fr.elias.oreoEssentials.rabbitmq.sender.RabbitMQSender;

// Services
import fr.minuskube.inv.InventoryManager;

// Chat (Afelius merge)
import fr.elias.oreoEssentials.chat.AsyncChatListener;
import fr.elias.oreoEssentials.chat.CustomConfig;
import fr.elias.oreoEssentials.chat.FormatManager;
import fr.elias.oreoEssentials.util.ChatSyncManager;

// Vault
import fr.elias.oreoEssentials.util.ProxyMessenger;
import fr.elias.oreoEssentials.vault.VaultEconomyProvider;
import net.milkbowl.vault.economy.Economy;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class OreoEssentials extends JavaPlugin {

    // Singleton
    private static OreoEssentials instance;
    public static OreoEssentials get() { return instance; }
    private MuteService muteService;
    public MuteService getMuteService() { return muteService; }
    // Economy bridge (internal) — distinct from Vault Economy
    private MongoClient homesMongoClient; // <— add this

    private EconomyBootstrap ecoBootstrap;
    // add near other services
    private fr.elias.oreoEssentials.integration.DiscordModerationNotifier discordMod;
    public fr.elias.oreoEssentials.integration.DiscordModerationNotifier getDiscordMod() { return discordMod; }
    private fr.elias.oreoEssentials.services.HomeDirectory homeDirectory; // cross-server directory
    // Essentials services
    private ConfigService configService;
    private StorageApi storage;
    private SpawnService spawnService;
    private InventoryManager invManager;
    public InventoryManager getInvManager() { return invManager; }
    private WarpService warpService;
    private HomeService homeService;
    private TeleportService teleportService;
    private BackService backService;
    private MessageService messageService;
    private DeathBackService deathBackService;
    private GodService godService;
    private CommandManager commands;
    // Kits + Tab
    private fr.elias.oreoEssentials.kits.KitsManager kitsManager;
    private fr.elias.oreoEssentials.tab.TabListManager tabListManager;
    private WarpDirectory warpDirectory;
    private SpawnDirectory spawnDirectory;
    private TeleportBroker teleportBroker;
    public fr.elias.oreoEssentials.kits.KitsManager getKitsManager() { return kitsManager; }
    public fr.elias.oreoEssentials.tab.TabListManager getTabListManager() { return tabListManager; }
    private fr.elias.oreoEssentials.bossbar.BossBarService bossBarService;
    public fr.elias.oreoEssentials.bossbar.BossBarService getBossBarService() { return bossBarService; }

    // Economy / messaging stack
    private PlayerEconomyDatabase database;
    private RedisManager redis;
    private OfflinePlayerCache offlinePlayerCache;

    // Vault provider reference (optional)
    private Economy vaultEconomy;
    // PlayerVaults
    private fr.elias.oreoEssentials.playervaults.PlayerVaultsService playervaultsService;
    public fr.elias.oreoEssentials.playervaults.PlayerVaultsService getPlayervaultsService() { return playervaultsService; }
    // Cross-server inventory bridge (invsee/ecsee)
    private fr.elias.oreoEssentials.cross.InvBridge invBridge;
    public fr.elias.oreoEssentials.cross.InvBridge getInvBridge() { return invBridge; }
    private KillallLogger killallLogger;


    // Toggles
    private boolean economyEnabled;
    private boolean redisEnabled;
    private boolean rabbitEnabled;

    // RabbitMQ packet manager (optional)
    private PacketManager packetManager;

    // Chat system (Afelius -> merged)
    private CustomConfig chatConfig;
    private FormatManager chatFormatManager;
    private ChatSyncManager chatSyncManager;

    private fr.elias.oreoEssentials.homes.HomeTeleportBroker homeTpBroker;
    // in OreoEssentials.java fields
    private fr.elias.oreoEssentials.config.CrossServerSettings crossServerSettings;
    public fr.elias.oreoEssentials.config.CrossServerSettings getCrossServerSettings() { return crossServerSettings; }

    // RTP + EnderChest
    private fr.elias.oreoEssentials.rtp.RtpConfig rtpConfig;
    public fr.elias.oreoEssentials.rtp.RtpConfig getRtpConfig() { return rtpConfig; }

    private fr.elias.oreoEssentials.enderchest.EnderChestConfig ecConfig;
    private fr.elias.oreoEssentials.enderchest.EnderChestService ecService;
    public fr.elias.oreoEssentials.enderchest.EnderChestService getEnderChestService() { return ecService; }

    private ScoreboardService scoreboardService;
    private fr.elias.oreoEssentials.mobs.HealthBarListener healthBarListener;
    private ClearLagManager clearLag;

    private fr.elias.oreoEssentials.aliases.AliasService aliasService;
    public fr.elias.oreoEssentials.aliases.AliasService getAliasService(){ return aliasService; }


    @Override
    public void onEnable() {
        // -------- Boot & base singletons --------
        instance = this;
        saveDefaultConfig();
        // --- Alias editor boot ---
        this.aliasService = new fr.elias.oreoEssentials.aliases.AliasService(this);
        this.aliasService.load();
        this.aliasService.applyRuntimeRegistration();

        this.crossServerSettings = fr.elias.oreoEssentials.config.CrossServerSettings.load(this);
        this.killallLogger = new KillallLogger(this);

        getLogger().info("[BOOT] OreoEssentials starting up…");

        // Make sure ClearLag config exists very early (so schedulers can start later)
        try {
            java.io.File f = new java.io.File(getDataFolder(), "clearlag.yml");
            if (!f.exists()) {

                saveResource("clearlag.yml", false);
            }
        } catch (Throwable ignored) {}

        fr.elias.oreoEssentials.util.SkinRefresherBootstrap.init(this);
        fr.elias.oreoEssentials.util.SkinDebug.init(this);

        // Locales
        Lang.init(this);

        // -------- Proxy plugin messaging (server switching) --------
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        getServer().getMessenger().registerOutgoingPluginChannel(this, "bungeecord:main");
        ProxyMessenger proxyMessenger = new ProxyMessenger(this);
        getLogger().info("[BOOT] Registered proxy plugin messaging channels.");

        // -------- UI/Managers created early --------
        this.invManager = new InventoryManager(this);
        this.invManager.init();

        // -------- Moderation core needed by chat --------
        muteService = new MuteService(this);
        getServer().getPluginManager().registerEvents(new fr.elias.oreoEssentials.listeners.MuteListener(muteService), this);


        // -------- Core config service & ECONOMY BOOTSTRAP (EARLY, BEFORE ANYONE QUERIES VAULT!) --------
        // (Moved here to ensure our Vault economy provider is registered ASAP)
        this.configService = new ConfigService(this);

        // Feature toggles (read early so we can stand up Redis/Economy first)
        final String essentialsStorage = getConfig().getString("essentials.storage", "yaml").toLowerCase();
        final String economyType       = getConfig().getString("economy.type", "none").toLowerCase();
        this.economyEnabled = getConfig().getBoolean("economy.enabled", !economyType.equals("none"));
        this.redisEnabled   = getConfig().getBoolean("redis.enabled", false);
        this.rabbitEnabled  = getConfig().getBoolean("rabbitmq.enabled", false);
        final String localServerName   = configService.serverName(); // unified server id
        getLogger().info("[BOOT] storage=" + essentialsStorage + " economyType=" + economyType
                + " redis=" + redisEnabled + " rabbit=" + rabbitEnabled
                + " server.name=" + localServerName);

        // -------- Redis cache (optional; for economy caches, etc.) [EARLY] --------
        if (redisEnabled) {
            this.redis = new RedisManager(
                    getConfig().getString("redis.host", "localhost"),
                    getConfig().getInt("redis.port", 6379),
                    getConfig().getString("redis.password", "")
            );
            if (!redis.connect()) {
                getLogger().warning("[REDIS] Enabled but failed to connect. Continuing without cache.");
            } else {
                getLogger().info("[REDIS] Connected.");
            }
        } else {
            // Dummy instance prevents null checks in your economy classes
            this.redis = new RedisManager("", 6379, "");
            getLogger().info("[REDIS] Disabled.");
        }

        // -------- INTERNAL ECONOMY + VAULT REGISTRATION (ASAP) --------
        // Stand up internal bootstrap scaffolding (currencies, services, etc.)
        this.ecoBootstrap = new EconomyBootstrap(this);
        this.ecoBootstrap.enable();

        if (economyEnabled) {
            this.database = null;
            switch (economyType) {
                case "mongodb" -> {
                    MongoDBManager mgr = new MongoDBManager(redis);
                    boolean ok = mgr.connect(
                            getConfig().getString("economy.mongodb.uri"),
                            getConfig().getString("economy.mongodb.database"),
                            getConfig().getString("economy.mongodb.collection")
                    );
                    if (!ok) {
                        getLogger().severe("[ECON] MongoDB connect failed. Disabling plugin.");
                        getServer().getPluginManager().disablePlugin(this);
                        return;
                    }
                    this.database = mgr;
                }
                case "postgresql" -> {
                    PostgreSQLManager mgr = new PostgreSQLManager(this, redis);
                    boolean ok = mgr.connect(
                            getConfig().getString("economy.postgresql.url"),
                            getConfig().getString("economy.postgresql.user"),
                            getConfig().getString("economy.postgresql.password")
                    );
                    if (!ok) {
                        getLogger().severe("[ECON] PostgreSQL connect failed. Disabling plugin.");
                        getServer().getPluginManager().disablePlugin(this);
                        return;
                    }
                    this.database = mgr;
                }
                case "json" -> {
                    JsonEconomyDatabase mgr = new JsonEconomyDatabase(this, redis);
                    boolean ok = mgr.connect("", "", ""); // JSON ignores args
                    if (!ok) {
                        getLogger().severe("[ECON] JSON init failed. Disabling plugin.");
                        getServer().getPluginManager().disablePlugin(this);
                        return;
                    }
                    this.database = mgr;
                }
                case "none" -> this.database = null;
                default -> { /* leave null */ }
            }

            if (this.database != null) {
                if (getServer().getPluginManager().getPlugin("Vault") == null) {
                    getLogger().severe("[ECON] Vault not found but economy.enabled=true. Disabling plugin.");
                    getServer().getPluginManager().disablePlugin(this);
                    return;
                }

                // Register Vault provider wrapper AT HIGHEST PRIORITY so other plugins see it first.
                // Unregister any previous providers just in case a race happened.
                try {
                    getServer().getServicesManager().unregister(net.milkbowl.vault.economy.Economy.class, this);
                } catch (Throwable ignored) {}

                VaultEconomyProvider vaultProvider = new VaultEconomyProvider(this);
                getServer().getServicesManager().register(
                        net.milkbowl.vault.economy.Economy.class,
                        vaultProvider,
                        this,
                        org.bukkit.plugin.ServicePriority.Highest
                );

                var rsp = getServer().getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
                if (rsp == null) {
                    getLogger().severe("[ECON] Failed to hook Vault Economy.");
                } else {
                    this.vaultEconomy = rsp.getProvider();
                    getLogger().info("[ECON] Vault economy integration enabled at HIGHEST priority.");
                }

                // Listeners for economy player data + join/quit packets
                Bukkit.getPluginManager().registerEvents(new PlayerDataListener(this), this);
                Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);

                // Offline player cache
                this.offlinePlayerCache = new OfflinePlayerCache();

                // Preload offline cache & refresh periodically
                this.database.populateCache(offlinePlayerCache);
                Bukkit.getScheduler().runTaskTimerAsynchronously(
                        this,
                        () -> this.database.populateCache(offlinePlayerCache),
                        20L * 60,    // 1 minute after start
                        20L * 300    // every 5 minutes
                );

                // Economy commands
                if (getCommand("money") != null) {
                    getCommand("money").setExecutor(new MoneyCommand(this));
                    getCommand("money").setTabCompleter(new MoneyTabCompleter(this));
                }

                if (getCommand("pay") != null) {
                    var payCmd = new fr.elias.oreoEssentials.commands.ecocommands.PayCommand();
                    getCommand("pay").setExecutor((sender, cmd, label, args) -> payCmd.execute(sender, label, args));
                    getCommand("pay").setTabCompleter(
                            new fr.elias.oreoEssentials.commands.ecocommands.completion.PayTabCompleter(this)
                    );
                } else {
                    getLogger().warning("[ECON] Command 'pay' not found in plugin.yml; skipping registration.");
                }

                if (getCommand("cheque") != null) {
                    getCommand("cheque").setExecutor(new fr.elias.oreoEssentials.commands.ecocommands.ChequeCommand(this));
                    getCommand("cheque").setTabCompleter(
                            new fr.elias.oreoEssentials.commands.ecocommands.completion.ChequeTabCompleter()
                    );
                } else {
                    getLogger().warning("[ECON] Command 'cheque' not found in plugin.yml; skipping registration.");
                }
            } else {
                getLogger().warning("[ECON] Enabled but no database selected/connected; economy commands unavailable.");
            }
        } else {
            getLogger().info("[ECON] Disabled. Skipping Vault, DB, and economy commands.");
        }

        // -------- Discord moderation notifier (separate config)
        this.discordMod = new fr.elias.oreoEssentials.integration.DiscordModerationNotifier(this);

        // Health bar for mobs
        try {
            var hbl = new fr.elias.oreoEssentials.mobs.HealthBarListener(this);
            if (hbl.isEnabled()) {
                this.healthBarListener = hbl;
                getServer().getPluginManager().registerEvents(hbl, this);
                getLogger().info("[MOBS] Health bars enabled.");
            } else {
                getLogger().info("[MOBS] Health bars disabled by config.");
            }
        } catch (Throwable t) {
            getLogger().warning("[MOBS] Failed to init health bars: " + t.getMessage());
        }

        // -------- KILLALL Recorder commands --------
        var killExec = new KillallRecorderCommand(this, killallLogger);
        getCommand("killallr").setExecutor(killExec);
        getCommand("killallr").setTabCompleter(killExec);
        getCommand("killallrlog").setExecutor(new KillallLogViewCommand(killallLogger));

        // -------- ClearLag Module (manager + command) --------
        try {
            // Manager will read clearlag.yml and start auto-removal + TPS meter schedulers (if enabled)
            this.clearLag = new fr.elias.oreoEssentials.clearlag.ClearLagManager(this);
            var olaggCmd = getCommand("olagg");
            if (olaggCmd != null) {
                var olagg = new fr.elias.oreoEssentials.clearlag.ClearLagCommands(clearLag);
                olaggCmd.setExecutor(olagg);
                olaggCmd.setTabCompleter(olagg);
            } else {
                getLogger().warning("[OreoLag] Command 'olagg' not found in plugin.yml; skipping registration.");
            }
            getLogger().info("[OreoLag] ClearLag manager initialized.");
        } catch (Throwable t) {
            getLogger().warning("[OreoLag] Failed to initialize ClearLag manager: " + t.getMessage());
        }

        // -------- Chat system (Afelius merge) --------
        this.chatConfig = new fr.elias.oreoEssentials.chat.CustomConfig(this, "chat-format.yml");
        this.chatFormatManager = new fr.elias.oreoEssentials.chat.FormatManager(chatConfig);

        // Custom join messages
        getServer().getPluginManager().registerEvents(new JoinMessagesListener(this), this);

        // Chat sync via RabbitMQ (optional) — init before listener, pass mute service
        boolean chatSyncEnabled = chatConfig.getCustomConfig().getBoolean("MongoDB_rabbitmq.enabled", false);
        String chatRabbitUri    = chatConfig.getCustomConfig().getString("MongoDB_rabbitmq.rabbitmq.uri", "");
        try {
            this.chatSyncManager = new ChatSyncManager(chatSyncEnabled, chatRabbitUri, muteService);
            if (chatSyncEnabled) this.chatSyncManager.subscribeMessages();
            getLogger().info("[CHAT] ChatSync enabled=" + chatSyncEnabled);
        } catch (Exception e) {
            getLogger().severe("[CHAT] ChatSync init failed: " + e.getMessage());
            this.chatSyncManager = new ChatSyncManager(false, "", muteService);
        }

        // Discord relay (read from chat-format.yml)
        var chatRoot = chatConfig.getCustomConfig().getConfigurationSection("chat");
        boolean discordEnabled = chatRoot != null
                && chatRoot.getConfigurationSection("discord") != null
                && chatRoot.getConfigurationSection("discord").getBoolean("enabled", false);
        String discordWebhookUrl = (chatRoot != null && chatRoot.getConfigurationSection("discord") != null)
                ? chatRoot.getConfigurationSection("discord").getString("webhook_url", "")
                : "";

        // Register async chat listener (mute-aware)
        getServer().getPluginManager().registerEvents(
                new AsyncChatListener(
                        chatFormatManager,
                        chatConfig,
                        chatSyncManager,
                        discordEnabled,
                        discordWebhookUrl,
                        muteService
                ),
                this
        );

        // Conversations / auto messages
        getServer().getPluginManager().registerEvents(new ConversationListener(this), this);
        new fr.elias.oreoEssentials.tasks.AutoMessageScheduler(this).start();

        // -------- Essentials storage selection (Homes/Warps/Spawn/Back) --------
        // Also sets up cross-server directories when using MongoDB
        switch (essentialsStorage) {
            case "mongodb" -> {
                String uri    = getConfig().getString("storage.mongo.uri", "mongodb://localhost:27017");
                String dbName = getConfig().getString("storage.mongo.database", "oreo");
                String prefix = getConfig().getString("storage.mongo.collectionPrefix", "oreo_");

                // Keep a client reference to close on disable
                this.homesMongoClient = com.mongodb.client.MongoClients.create(uri);
                this.playerDirectory = new fr.elias.oreoEssentials.playerdirectory.PlayerDirectory(
                        this.homesMongoClient, dbName, prefix
                );
                // (Optional) migration helper if you used Bukkit server name previously
                try {
                    MongoHomesMigrator.run(
                            this.homesMongoClient,
                            dbName,
                            prefix,
                            org.bukkit.Bukkit.getServer().getName(), // legacy saved value
                            localServerName,
                            getLogger()
                    );
                } catch (Throwable ignored) {
                    getLogger().info("[STORAGE] MongoHomesMigrator skipped.");
                }

                // Mongo-backed StorageApi (handles spawn/warps/homes/back)
                this.storage = new MongoHomesStorage(
                        this.homesMongoClient, dbName, prefix, localServerName
                );

                // Cross-server directories (home/warp/spawn owners)
                this.homeDirectory  = new MongoHomeDirectory(
                        this.homesMongoClient, dbName, prefix + "home_directory"
                );
                try {
                    this.warpDirectory  = new MongoWarpDirectory(
                            this.homesMongoClient, dbName, prefix + "warp_directory"
                    );
                } catch (Throwable ignored) { this.warpDirectory = null; }
                try {
                    this.spawnDirectory = new MongoSpawnDirectory(
                            this.homesMongoClient, dbName, prefix + "spawn_directory"
                    );
                } catch (Throwable ignored) { this.spawnDirectory = null; }

                getLogger().info("[STORAGE] Using MongoDB (MongoHomesStorage + directories).");
            }
            case "json" -> {
                this.storage       = new fr.elias.oreoEssentials.services.JsonStorage(this);
                this.homeDirectory = null;
                this.warpDirectory = null;
                this.spawnDirectory = null;
                getLogger().info("[STORAGE] Using JSON.");
            }
            default -> {
                this.storage       = new fr.elias.oreoEssentials.services.YamlStorage(this);
                this.homeDirectory = null;
                this.warpDirectory = null;
                this.spawnDirectory = null;
                getLogger().info("[STORAGE] Using YAML.");
            }
        }

        // ---- RTP config
        this.rtpConfig = new fr.elias.oreoEssentials.rtp.RtpConfig(this);

        // ---- EnderChest config + storage (respect crossserverec)
        this.ecConfig = new fr.elias.oreoEssentials.enderchest.EnderChestConfig(this);
        // --- PlayerVaults (SmartInvs + YAML/Mongo; cross-server)
        this.playervaultsService = new fr.elias.oreoEssentials.playervaults.PlayerVaultsService(this);
        if (this.playervaultsService.enabled()) {
            getLogger().info("[Vaults] PlayerVaults enabled.");
        } else {
            getLogger().info("[Vaults] PlayerVaults disabled by config or storage unavailable.");
        }
        boolean crossServerEc = getConfig().getBoolean("crossserverec", false);
        fr.elias.oreoEssentials.enderchest.EnderChestStorage ecStorage;

        if ("mongodb".equalsIgnoreCase(getConfig().getString("essentials.storage", "yaml"))
                && crossServerEc
                && this.homesMongoClient != null) {
            String dbName = getConfig().getString("storage.mongo.database", "oreo");
            String prefix = getConfig().getString("storage.mongo.collectionPrefix", "oreo_");
            ecStorage = new fr.elias.oreoEssentials.enderchest.MongoEnderChestStorage(
                    this.homesMongoClient, dbName, prefix, getLogger()
            );
            getLogger().info("[EC] Using MongoDB cross-server ender chest storage.");
        } else {
            ecStorage = new fr.elias.oreoEssentials.enderchest.YamlEnderChestStorage(this);
            getLogger().info("[EC] Using local YAML ender chest storage.");
        }
        this.ecService = new fr.elias.oreoEssentials.enderchest.EnderChestService(
                this,
                ecConfig,
                ecStorage
        );
        Bukkit.getServicesManager().register(
                fr.elias.oreoEssentials.enderchest.EnderChestService.class,
                this.ecService,
                this,
                org.bukkit.plugin.ServicePriority.Normal
        );

        // --- Player Sync bootstrap ---
        final boolean invSyncEnabled = getConfig().getBoolean("crossserverinv", false);

        fr.elias.oreoEssentials.playersync.PlayerSyncStorage invStorage;
        if (invSyncEnabled
                && "mongodb".equalsIgnoreCase(getConfig().getString("essentials.storage", "yaml"))
                && this.homesMongoClient != null) {
            String dbName = getConfig().getString("storage.mongo.database", "oreo");
            String prefix = getConfig().getString("storage.mongo.collectionPrefix", "oreo_");
            invStorage = new fr.elias.oreoEssentials.playersync.MongoPlayerSyncStorage(this.homesMongoClient, dbName, prefix);
            getLogger().info("[SYNC] Using MongoDB storage.");
        } else {
            invStorage = new fr.elias.oreoEssentials.playersync.YamlPlayerSyncStorage(this);
            getLogger().info("[SYNC] Using local YAML storage.");
        }

        final var syncPrefsStore    = new fr.elias.oreoEssentials.playersync.PlayerSyncPrefsStore(this);
        final var playerSyncService = new fr.elias.oreoEssentials.playersync.PlayerSyncService(this, invStorage, syncPrefsStore);

        getServer().getPluginManager().registerEvents(
                new fr.elias.oreoEssentials.playersync.PlayerSyncListener(playerSyncService, invSyncEnabled),
                this
        );

        // --- expose InventoryService so /invsee works for offline & cross-server ---
        fr.elias.oreoEssentials.services.InventoryService invSvc =
                new fr.elias.oreoEssentials.services.InventoryService() {
                    @Override
                    public Snapshot load(java.util.UUID uuid) {
                        try {
                            var s = invStorage.load(uuid); // uses Mongo or YAML depending on your config
                            if (s == null) return null;
                            Snapshot snap = new Snapshot();
                            snap.contents = s.inventory;
                            snap.armor    = s.armor;
                            snap.offhand  = s.offhand;
                            return snap;
                        } catch (Exception e) {
                            getLogger().warning("[INVSEE] load failed: " + e.getMessage());
                            return null;
                        }
                    }
                    @Override
                    public void save(java.util.UUID uuid, Snapshot snapshot) {
                        try {
                            var s = new fr.elias.oreoEssentials.playersync.PlayerSyncSnapshot();
                            s.inventory = snapshot.contents;
                            s.armor     = snapshot.armor;
                            s.offhand   = snapshot.offhand;
                            invStorage.save(uuid, s);
                        } catch (Exception e) {
                            getLogger().warning("[INVSEE] save failed: " + e.getMessage());
                        }
                    }
                };

        Bukkit.getServicesManager().register(
                fr.elias.oreoEssentials.services.InventoryService.class,
                invSvc,
                this,
                org.bukkit.plugin.ServicePriority.Normal
        );

        getServer().getPluginManager().registerEvents(
                new fr.elias.oreoEssentials.enderchest.EnderChestListener(this, ecService, crossServerEc),
                this
        );

        // -------- Essentials Services --------
        this.spawnService = new SpawnService(storage);
        this.warpService  = new WarpService(storage);
        this.homeService  = new HomeService(this.storage, this.configService, this.homeDirectory);

        // -------- RabbitMQ (optional cross-server signaling) --------
        if (rabbitEnabled) {
            RabbitMQSender rabbit = new RabbitMQSender(getConfig().getString("rabbitmq.uri"));
            this.packetManager = new PacketManager(this, rabbit);
            if (rabbit.connect()) {
                packetManager.init();

                packetManager.subscribeChannel(PacketChannels.GLOBAL);

                packetManager.subscribeChannel(fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel.individual(localServerName));
                getLogger().info("[RABBIT] Subscribed to individual channel for this server: " + localServerName);

                // Generic packets you already use
                packetManager.subscribe(
                        fr.elias.oreoEssentials.rabbitmq.packet.impl.SendRemoteMessagePacket.class,
                        new RemoteMessagePacketHandler()
                );
                packetManager.subscribe(
                        fr.elias.oreoEssentials.rabbitmq.packet.impl.PlayerJoinPacket.class,
                        new PlayerJoinPacketHandler(this)
                );
                packetManager.subscribe(
                        fr.elias.oreoEssentials.rabbitmq.packet.impl.PlayerQuitPacket.class,
                        new PlayerQuitPacketHandler(this)
                );

                getLogger().info("[RABBIT] Connected and subscriptions active.");
            } else {
                getLogger().severe("[RABBIT] Connect failed; continuing without messaging.");
                this.packetManager = null;
            }
        } else {
            getLogger().info("[RABBIT] Disabled.");
        }

        // ---- Cross-server InvBridge (only if Rabbit is available AND feature is enabled) ----
        if (packetManager != null && packetManager.isInitialized() && invSyncEnabled) {
            this.invBridge = new fr.elias.oreoEssentials.cross.InvBridge(this, packetManager, configService.serverName());
            getLogger().info("[INV-BRIDGE] Cross-server bridge ready.");
        } else {
            this.invBridge = null;
            getLogger().info("[INV-BRIDGE] Disabled (PacketManager unavailable or crossserverinv=false).");
        }

        // -------- Cross-server teleport brokers --------
        if (packetManager != null && packetManager.isInitialized()) {

            final var cs = this.getCrossServerSettings();
            final boolean anyCross =
                    cs.homes() || cs.warps() || cs.spawn() || cs.economy();

            // Only bind RabbitMQ channels if at least one cross-server feature is enabled
            if (anyCross) {
                // Global (if you still broadcast some packets there)
                packetManager.subscribeChannel(fr.elias.oreoEssentials.rabbitmq.PacketChannels.GLOBAL);
                // This server’s individual queue (targeted messages)
                packetManager.subscribeChannel(
                        fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel.individual(configService.serverName())
                );
                getLogger().info("[RABBIT] Subscribed channels for cross-server features. server=" + configService.serverName());
            } else {
                getLogger().info("[RABBIT] All cross-server features disabled by config; skipping channel subscriptions.");
            }

            // Spawn/Warp broker (only if either is enabled)
            if (cs.spawn() || cs.warps()) {
                new fr.elias.oreoEssentials.teleport.CrossServerTeleportBroker(
                        this,
                        spawnService,
                        warpService,
                        packetManager,
                        configService.serverName()
                );
                getLogger().info("[BROKER] CrossServerTeleportBroker ready (spawn=" + cs.spawn() + ", warps=" + cs.warps() + ").");
            } else {
                getLogger().info("[BROKER] CrossServerTeleportBroker disabled by config (spawn & warps off).");
            }

            // Home broker (only if enabled)
            if (cs.homes()) {
                this.homeTpBroker = new fr.elias.oreoEssentials.homes.HomeTeleportBroker(this, homeService, packetManager);
                getLogger().info("[BROKER] HomeTeleportBroker ready (server=" + configService.serverName() + ").");
            } else {
                getLogger().info("[BROKER] HomeTeleportBroker disabled by config (homes off).");
            }

        } else {
            getLogger().warning("[BROKER] Brokers not started: PacketManager unavailable.");
        }

        this.backService      = new BackService(storage);
        this.messageService   = new MessageService();
        this.teleportService  = new TeleportService(this, backService, configService);
        this.deathBackService = new DeathBackService();
        this.godService       = new GodService();

        // -------- Moderation listeners --------
        FreezeService freezeService = new FreezeService();
        getServer().getPluginManager().registerEvents(new fr.elias.oreoEssentials.listeners.FreezeListener(freezeService), this);
        VanishService vanishService = new VanishService(this);
        getServer().getPluginManager().registerEvents(
                new fr.elias.oreoEssentials.listeners.VanishListener(vanishService, this), this
        );

        // Track locations for /back + /deathback + god protection
        getServer().getPluginManager().registerEvents(new PlayerTrackingListener(backService), this);
        getServer().getPluginManager().registerEvents(new DeathBackListener(deathBackService), this);
        getServer().getPluginManager().registerEvents(new GodListener(godService), this);

        // -------- Portals --------
        var portalsManager = new fr.elias.oreoEssentials.portals.PortalsManager(this);
        getServer().getPluginManager().registerEvents(
                new fr.elias.oreoEssentials.portals.PortalsListener(portalsManager), this
        );
        if (getCommand("portal") != null) {
            var portalCmd = new fr.elias.oreoEssentials.portals.PortalsCommand(portalsManager);
            getCommand("portal").setExecutor(portalCmd);
            getCommand("portal").setTabCompleter(portalCmd);
        }

        // -------- JumpPads --------
        var jumpPadsManager = new fr.elias.oreoEssentials.jumpads.JumpPadsManager(this);
        getServer().getPluginManager().registerEvents(
                new fr.elias.oreoEssentials.jumpads.JumpPadsListener(jumpPadsManager), this
        );
        if (getCommand("jumpad") != null) {
            var jumpCmd = new fr.elias.oreoEssentials.jumpads.JumpPadsCommand(jumpPadsManager);
            getCommand("jumpad").setExecutor(jumpCmd);
            getCommand("jumpad").setTabCompleter(jumpCmd);
        }

        // -------- Commands (manager then registrations) --------
        this.commands = new CommandManager(this);

        // --- BossBar (controlled by config: bossbar.enabled)
        this.bossBarService = new fr.elias.oreoEssentials.bossbar.BossBarService(this);
        this.bossBarService.start();
        this.commands.register(new fr.elias.oreoEssentials.bossbar.BossBarToggleCommand(this.bossBarService));

        // --- Scoreboard (controlled by config: scoreboard.enabled)
        ScoreboardConfig sbCfg = ScoreboardConfig.load(this);
        this.scoreboardService = new ScoreboardService(this, sbCfg);
        this.scoreboardService.start();
        this.commands.register(new ScoreboardToggleCommand(this.scoreboardService));

        var tphere = new fr.elias.oreoEssentials.commands.core.admins.TphereCommand();
        this.commands.register(tphere);
        if (getCommand("tphere") != null) {
            getCommand("tphere").setTabCompleter(tphere);
        }

        var muteCmd   = new MuteCommand(muteService, chatSyncManager);
        var unmuteCmd = new UnmuteCommand(muteService, chatSyncManager);

        // Nick (has completer)
        var nickCmd = new fr.elias.oreoEssentials.commands.core.playercommands.NickCommand();
        this.commands.register(nickCmd);

        // --- AFK service
        var afkService = new fr.elias.oreoEssentials.services.AfkService();
        getServer().getPluginManager().registerEvents(new fr.elias.oreoEssentials.listeners.AfkListener(afkService), this);

        // Register all remaining commands
        this.commands
                .register(new SpawnCommand(spawnService))
                .register(new SetSpawnCommand(spawnService))
                .register(new BackCommand(backService))
                .register(new WarpCommand(warpService))
                .register(new SetWarpCommand(warpService))
                .register(new DelWarpCommand(warpService))
                .register(new HomeCommand(homeService))
                .register(new SetHomeCommand(homeService, configService))
                .register(new DelHomeCommand(homeService))
                .register(new TpaCommand(teleportService))
                .register(new TpAcceptCommand(teleportService))
                .register(new TpDenyCommand(teleportService))
                .register(new FlyCommand())
                .register(new HealCommand())
                .register(new FeedCommand())
                .register(new MsgCommand(messageService))
                .register(new ReplyCommand(messageService))
                .register(new BroadcastCommand())
                .register(new HomesCommand(homeService))
                .register(new DeathBackCommand(deathBackService))
                .register(new GodCommand(godService))
                .register(new AfeliusReloadCommand(this, chatConfig))
                .register(new TpCommand())
                .register(new VanishCommand(vanishService))
                .register(new BanCommand())
                .register(new KickCommand())
                .register(new FreezeCommand(freezeService))
                .register(new EnchantCommand())
                .register(muteCmd)
                .register(new UnbanCommand())
                .register(unmuteCmd)
                .register(new OeCommand())
                .register(new ServerProxyCommand(proxyMessenger))
                .register(new SkinCommand())
                .register(new CloneCommand())
                .register(new fr.elias.oreoEssentials.commands.core.playercommands.RtpCommand())
                .register(new fr.elias.oreoEssentials.playersync.PlayerSyncCommand(this, playerSyncService, invSyncEnabled))
                .register(new fr.elias.oreoEssentials.commands.core.playercommands.EcCommand(this.ecService, crossServerEc))
                .register(new HeadCommand())
                .register(new fr.elias.oreoEssentials.commands.core.playercommands.AfkCommand(afkService))
                .register(new fr.elias.oreoEssentials.commands.core.playercommands.TrashCommand())
                .register(new fr.elias.oreoEssentials.commands.core.playercommands.WorkbenchCommand())
                .register(new fr.elias.oreoEssentials.commands.core.playercommands.AnvilCommand())
                .register(new ClearCommand())
                .register(new fr.elias.oreoEssentials.commands.core.playercommands.SeenCommand())
                .register(new fr.elias.oreoEssentials.commands.core.playercommands.PingCommand())
                .register(new fr.elias.oreoEssentials.commands.core.playercommands.HatCommand())
                .register(new fr.elias.oreoEssentials.commands.core.playercommands.RealNameCommand())
                .register(new fr.elias.oreoEssentials.commands.core.playercommands.FurnaceCommand())
                .register(new fr.elias.oreoEssentials.commands.core.playercommands.NearCommand())
                .register(new fr.elias.oreoEssentials.commands.core.playercommands.KillCommand())
                .register(new fr.elias.oreoEssentials.commands.core.playercommands.InvseeCommand())
                .register(new fr.elias.oreoEssentials.commands.core.playercommands.CookCommand())
                .register(new fr.elias.oreoEssentials.commands.ecocommands.BalanceCommand(this))
                .register(new fr.elias.oreoEssentials.commands.ecocommands.BalTopCommand(this))
                .register(new fr.elias.oreoEssentials.commands.core.playercommands.EcSeeCommand())
                .register(new fr.elias.oreoEssentials.commands.core.admins.ReloadAllCommand())
                .register(new fr.elias.oreoEssentials.commands.core.playercommands.VaultsCommand())
                .register(new fr.elias.oreoEssentials.commands.core.playercommands.UuidCommand());

        // -------- Tab completion wiring --------
        if (getCommand("oeserver") != null) {
            getCommand("oeserver").setTabCompleter(new ServerProxyCommand(proxyMessenger));
        }
        if (getCommand("balance") != null) {
            getCommand("balance").setTabCompleter((sender, cmd, alias, args) -> {
                if (args.length == 1 && sender.hasPermission("oreo.balance.others")) {
                    String partial = args[0].toLowerCase(java.util.Locale.ROOT);
                    return org.bukkit.Bukkit.getOnlinePlayers().stream()
                            .map(org.bukkit.entity.Player::getName)
                            .filter(n -> n.toLowerCase(java.util.Locale.ROOT).startsWith(partial))
                            .sorted(String.CASE_INSENSITIVE_ORDER)
                            .toList();
                }
                return java.util.List.of();
            });
        }
        if (getCommand("otherhomes") != null) {
            var c = new fr.elias.oreoEssentials.commands.core.admins.OtherHomesListCommand(this, homeService);
            getCommand("otherhomes").setExecutor(c);
            getCommand("otherhomes").setTabCompleter(c);
        }
        // /aliaseditor registration
        if (getCommand("aliaseditor") != null) {
            getCommand("aliaseditor").setExecutor(new fr.elias.oreoEssentials.aliases.AliasEditorCommand(aliasService));
        }

        if (getCommand("otherhome") != null) {
            var otherHome = new fr.elias.oreoEssentials.commands.core.admins.OtherHomeCommand(this, homeService);
            this.commands.register(otherHome);                 // uses your CommandManager (OreoCommand)
            getCommand("otherhome").setTabCompleter(otherHome); // TabCompleter is fine to set directly
        }

        // After other services:
        var visitorService = new fr.elias.oreoEssentials.services.VisitorService();
        getServer().getPluginManager().registerEvents(
                new fr.elias.oreoEssentials.listeners.VisitorGuardListener(visitorService), this
        );

        // One GamemodeCommand instance for both executor (via your CommandManager) and tab-complete:
        var gmCmd = new fr.elias.oreoEssentials.commands.core.admins.GamemodeCommand(visitorService);
        this.getCommands().register(gmCmd);
        if (getCommand("gamemode") != null) {
            getCommand("gamemode").setTabCompleter(gmCmd);
        }

        if (getCommand("skin") != null)   getCommand("skin").setTabCompleter(new SkinCommand());
        if (getCommand("clone") != null)  getCommand("clone").setTabCompleter(new CloneCommand());
        if (getCommand("head") != null)   getCommand("head").setTabCompleter(new HeadCommand());
        if (getCommand("home") != null)   getCommand("home").setTabCompleter(new HomeTabCompleter(homeService));
        if (getCommand("warp") != null)   getCommand("warp").setTabCompleter(new WarpTabCompleter(warpService));
        if (getCommand("enchant") != null)
            getCommand("enchant").setTabCompleter(new fr.elias.oreoEssentials.commands.completion.EnchantTabCompleter());
        if (getCommand("mute") != null)   getCommand("mute").setTabCompleter(muteCmd);
        if (getCommand("unban") != null)  getCommand("unban").setTabCompleter(new UnbanCommand());
        if (getCommand("nick") != null)   getCommand("nick").setTabCompleter(nickCmd);
        if (getCommand("unmute") != null) getCommand("unmute").setTabCompleter(unmuteCmd);
        if (getCommand("invsee") != null)
            getCommand("invsee").setTabCompleter(new fr.elias.oreoEssentials.commands.core.playercommands.InvseeCommand());
        if (getCommand("ecsee") != null)
            getCommand("ecsee").setTabCompleter(new fr.elias.oreoEssentials.commands.core.playercommands.EcSeeCommand());

        // -------- PlaceholderAPI hook (optional; reflection) --------
        tryRegisterPlaceholderAPI();

        getLogger().info("OreoEssentials enabled.");
    }




    public fr.elias.oreoEssentials.playervaults.PlayerVaultsService getPlayerVaultsService() {
        return playervaultsService;
    }



    public boolean isMessagingAvailable() {
        return packetManager != null && packetManager.isInitialized();
    }

    @Override
    public void onDisable() {
        // SmartInvs: no stop() in your version
        try { if (teleportService != null) teleportService.shutdown(); } catch (Exception ignored) {}
        try { if (storage != null) { storage.flush(); storage.close(); } } catch (Exception ignored) {}
        try { if (database != null) database.close(); } catch (Exception ignored) {}
        try { if (packetManager != null) packetManager.close(); } catch (Exception ignored) {}
        try { if (ecoBootstrap != null) ecoBootstrap.disable(); } catch (Exception ignored) {}
        try { if (chatSyncManager != null) chatSyncManager.close(); } catch (Exception ignored) {}
        try { if (tabListManager != null) tabListManager.stop(); } catch (Exception ignored) {}
        try { if (kitsManager != null) kitsManager.saveData(); } catch (Exception ignored) {}
        try { if (scoreboardService != null) scoreboardService.stop(); } catch (Exception ignored) {}
        try { if (this.homesMongoClient != null) this.homesMongoClient.close(); } catch (Exception ignored) {}
        try { if (bossBarService != null) bossBarService.stop(); } catch (Exception ignored) {}
        try { if (playervaultsService != null) playervaultsService.stop(); } catch (Exception ignored) {}
        try { if (aliasService != null) aliasService.shutdown(); } catch (Exception ignored) {}


        this.healthBarListener = null; // GC will handle the rest



        getLogger().info("OreoEssentials disabled.");
    }


    /* ----------------------------- Helpers ----------------------------- */

    /** Optional PlaceholderAPI hook using reflection. */
    private void tryRegisterPlaceholderAPI() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            getLogger().info("PlaceholderAPI not found; skipping placeholders.");
            return;
        }
        try {
            // NOTE: Using your provided class location:
            Class<?> hookCls = Class.forName("fr.elias.oreoEssentials.PlaceholderAPIHook");
            Object hook = hookCls.getConstructor(OreoEssentials.class).newInstance(this);
            hookCls.getMethod("register").invoke(hook);
            getLogger().info("PlaceholderAPI placeholders registered.");
        } catch (ClassNotFoundException e) {
            getLogger().warning("PlaceholderAPIHook class not found; skipping placeholders.");
        } catch (Throwable t) {
            getLogger().warning("Failed to register PlaceholderAPI placeholders: " + t.getMessage());
        }
    }

    /* ----------------------------- Getters ----------------------------- */

    public ConfigService getConfigService() { return configService; }
    public StorageApi getStorage() { return storage; }
    public SpawnService getSpawnService() { return spawnService; }
    public WarpService getWarpService() { return warpService; }
    public HomeService getHomeService() { return homeService; }
    public TeleportService getTeleportService() { return teleportService; }
    public BackService getBackService() { return backService; }
    public MessageService getMessageService() { return messageService; }
    public DeathBackService getDeathBackService() { return deathBackService; }
    public GodService getGodService() { return godService; }
    public CommandManager getCommands() { return commands; }
    public ChatSyncManager getChatSyncManager() { return chatSyncManager; }
    public fr.elias.oreoEssentials.mobs.HealthBarListener getHealthBarListener() { return healthBarListener; }


    public fr.elias.oreoEssentials.chat.CustomConfig getChatConfig() { return chatConfig; }

    public WarpDirectory getWarpDirectory() { return warpDirectory; }
    public SpawnDirectory getSpawnDirectory() { return spawnDirectory; }
    private fr.elias.oreoEssentials.playerdirectory.PlayerDirectory playerDirectory;
    public fr.elias.oreoEssentials.playerdirectory.PlayerDirectory getPlayerDirectory() { return playerDirectory; }
    public TeleportBroker getTeleportBroker() { return teleportBroker; }
    public RedisManager getRedis() { return redis; }
    public OfflinePlayerCache getOfflinePlayerCache() { return offlinePlayerCache; }
    public PlayerEconomyDatabase getDatabase() { return database; }
    public PacketManager getPacketManager() { return packetManager; }
    public ScoreboardService getScoreboardService() { return scoreboardService; }
    public fr.elias.oreoEssentials.homes.HomeTeleportBroker getHomeTeleportBroker() {
        return homeTpBroker;
    }
    public EconomyBootstrap getEcoBootstrap() { return ecoBootstrap; }
    public Economy getVaultEconomy() { return vaultEconomy; }
}
