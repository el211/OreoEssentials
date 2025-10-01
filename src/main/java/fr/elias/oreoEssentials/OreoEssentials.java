// File: src/main/java/fr/elias/oreoEssentials/OreoEssentials.java
package fr.elias.oreoEssentials;

import fr.elias.oreoEssentials.commands.CommandManager;

// Core commands (essentials-like)
import fr.elias.oreoEssentials.commands.core.BackCommand;
import fr.elias.oreoEssentials.commands.core.BroadcastCommand;
import fr.elias.oreoEssentials.commands.core.DelHomeCommand;
import fr.elias.oreoEssentials.commands.core.DelWarpCommand;
import fr.elias.oreoEssentials.commands.core.FeedCommand;
import fr.elias.oreoEssentials.commands.core.FlyCommand;
import fr.elias.oreoEssentials.commands.core.HealCommand;
import fr.elias.oreoEssentials.commands.core.HomeCommand;
import fr.elias.oreoEssentials.commands.core.HomesCommand;
import fr.elias.oreoEssentials.commands.core.MsgCommand;
import fr.elias.oreoEssentials.commands.core.ReplyCommand;
import fr.elias.oreoEssentials.commands.core.SetHomeCommand;
import fr.elias.oreoEssentials.commands.core.SetSpawnCommand;
import fr.elias.oreoEssentials.commands.core.SetWarpCommand;
import fr.elias.oreoEssentials.commands.core.SpawnCommand;
import fr.elias.oreoEssentials.commands.core.TpAcceptCommand;
import fr.elias.oreoEssentials.commands.core.TpDenyCommand;
import fr.elias.oreoEssentials.commands.core.TpaCommand;
import fr.elias.oreoEssentials.commands.core.WarpCommand;
import fr.elias.oreoEssentials.commands.core.DeathBackCommand;
import fr.elias.oreoEssentials.commands.core.GodCommand;

// Tab completion
import fr.elias.oreoEssentials.commands.completion.HomeTabCompleter;
import fr.elias.oreoEssentials.commands.completion.WarpTabCompleter;

// Economy commands
import fr.elias.oreoEssentials.commands.ecocommands.ChequeCommand;
import fr.elias.oreoEssentials.commands.ecocommands.MoneyCommand;
import fr.elias.oreoEssentials.commands.ecocommands.completion.ChequeTabCompleter;
import fr.elias.oreoEssentials.commands.ecocommands.completion.MoneyTabCompleter;
import fr.elias.oreoEssentials.commands.ecocommands.completion.PayTabCompleter;

// Databases / Cache
import fr.elias.oreoEssentials.database.JsonEconomyDatabase;
import fr.elias.oreoEssentials.database.MongoDBManager;
import fr.elias.oreoEssentials.database.PlayerEconomyDatabase;
import fr.elias.oreoEssentials.database.PostgreSQLManager;
import fr.elias.oreoEssentials.database.RedisManager;

// Economy bootstrap (internal bridge)
import fr.elias.oreoEssentials.economy.EconomyBootstrap;

// Listeners
import fr.elias.oreoEssentials.listeners.PlayerDataListener;
import fr.elias.oreoEssentials.listeners.PlayerListener;
import fr.elias.oreoEssentials.listeners.PlayerTrackingListener;
import fr.elias.oreoEssentials.listeners.DeathBackListener;
import fr.elias.oreoEssentials.listeners.GodListener;

// Offline cache
import fr.elias.oreoEssentials.offline.OfflinePlayerCache;

// RabbitMQ
import fr.elias.oreoEssentials.rabbitmq.PacketChannels;
import fr.elias.oreoEssentials.rabbitmq.handler.PlayerJoinPacketHandler;
import fr.elias.oreoEssentials.rabbitmq.handler.PlayerQuitPacketHandler;
import fr.elias.oreoEssentials.rabbitmq.handler.RemoteMessagePacketHandler;
import fr.elias.oreoEssentials.rabbitmq.packet.PacketManager;
import fr.elias.oreoEssentials.rabbitmq.packet.impl.PlayerJoinPacket;
import fr.elias.oreoEssentials.rabbitmq.packet.impl.PlayerQuitPacket;
import fr.elias.oreoEssentials.rabbitmq.packet.impl.SendRemoteMessagePacket;
import fr.elias.oreoEssentials.rabbitmq.sender.RabbitMQSender;

// Services
import fr.elias.oreoEssentials.services.BackService;
import fr.elias.oreoEssentials.services.ConfigService;
import fr.elias.oreoEssentials.services.DeathBackService;
import fr.elias.oreoEssentials.services.FreezeService;
import fr.elias.oreoEssentials.services.GodService;
import fr.elias.oreoEssentials.services.HomeService;
import fr.elias.oreoEssentials.services.MessageService;
import fr.elias.oreoEssentials.services.MuteService;
import fr.elias.oreoEssentials.services.SpawnService;
import fr.elias.oreoEssentials.services.StorageApi;
import fr.elias.oreoEssentials.services.TeleportService;
import fr.elias.oreoEssentials.services.VanishService;
import fr.elias.oreoEssentials.services.WarpService;
import fr.elias.oreoEssentials.services.JsonStorage;
import fr.elias.oreoEssentials.services.MongoStorage;
import fr.elias.oreoEssentials.services.YamlStorage;

// Chat (Afelius merge)
import fr.elias.oreoEssentials.chat.AsyncChatListener;
import fr.elias.oreoEssentials.chat.CustomConfig;
import fr.elias.oreoEssentials.chat.FormatManager;
import fr.elias.oreoEssentials.util.ChatSyncManager;

// Vault
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

    // Economy bridge (internal) — distinct from Vault Economy
    private EconomyBootstrap ecoBootstrap;

    // Essentials services
    private ConfigService configService;
    private StorageApi storage;
    private SpawnService spawnService;
    private WarpService warpService;
    private HomeService homeService;
    private TeleportService teleportService;
    private BackService backService;
    private MessageService messageService;
    private DeathBackService deathBackService;
    private GodService godService;
    private CommandManager commands;

    // Economy / messaging stack
    private PlayerEconomyDatabase database;
    private RedisManager redis;
    private OfflinePlayerCache offlinePlayerCache;

    // Vault provider reference (optional)
    private Economy vaultEconomy;

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

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // --- Chat merge (Afelius) ---
        this.chatConfig = new CustomConfig(this, "chat-format.yml");
        this.chatFormatManager = new FormatManager(chatConfig);

        // Chat sync (RabbitMQ) optional
        boolean chatSyncEnabled = chatConfig.getCustomConfig().getBoolean("MongoDB_rabbitmq.enabled", false);
        String rabbitUri = chatConfig.getCustomConfig().getString("MongoDB_rabbitmq.rabbitmq.uri", "");
        try {
            this.chatSyncManager = new ChatSyncManager(chatSyncEnabled, rabbitUri);
            if (chatSyncEnabled) this.chatSyncManager.subscribeMessages();
        } catch (Exception e) {
            getLogger().severe("ChatSync init failed: " + e.getMessage());
            this.chatSyncManager = new ChatSyncManager(false, "");
        }

        // Discord webhook URL (from chat-format.yml)
        String discordWebhookUrl = chatConfig.getCustomConfig().getString("chat.discord_webhook_url", "");

        // Register chat listener (only affects chat if chat.enabled = true)
        getServer().getPluginManager().registerEvents(
                new AsyncChatListener(chatFormatManager, chatConfig, chatSyncManager, discordWebhookUrl), this
        );

        // Config + internal economy bootstrap
        this.configService = new ConfigService(this);
        this.ecoBootstrap = new EconomyBootstrap(this);
        this.ecoBootstrap.enable();

        // Toggles
        final String essentialsStorage = getConfig().getString("essentials.storage", "yaml").toLowerCase();
        final String economyType       = getConfig().getString("economy.type", "none").toLowerCase();
        this.economyEnabled = getConfig().getBoolean("economy.enabled", !economyType.equals("none"));
        this.redisEnabled    = getConfig().getBoolean("redis.enabled", false);
        this.rabbitEnabled   = getConfig().getBoolean("rabbitmq.enabled", false);

        // Essentials storage selection
        switch (essentialsStorage) {
            case "mongodb" -> {
                this.storage = new MongoStorage(this, configService);
                getLogger().info("Using MongoDB storage for essentials.");
            }
            case "json" -> {
                this.storage = new JsonStorage(this);
                getLogger().info("Using JSON storage for essentials.");
            }
            default -> {
                this.storage = new YamlStorage(this);
                getLogger().info("Using YAML storage for essentials.");
            }
        }

        // Redis (optional)
        if (redisEnabled) {
            this.redis = new RedisManager(
                    getConfig().getString("redis.host", "localhost"),
                    getConfig().getInt("redis.port", 6379),
                    getConfig().getString("redis.password", "")
            );
            if (!redis.connect()) {
                getLogger().warning("Redis enabled but failed to connect. Continuing without cache.");
            } else {
                getLogger().info("Connected to Redis.");
            }
        } else {
            // Create a dummy instance to avoid null checks
            this.redis = new RedisManager("", 6379, "");
            getLogger().info("Redis disabled.");
        }

        this.offlinePlayerCache = new OfflinePlayerCache();

        // Economy stack (DB + Vault provider)
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
                        getLogger().severe("MongoDB economy connect failed. Disabling plugin.");
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
                        getLogger().severe("PostgreSQL economy connect failed. Disabling plugin.");
                        getServer().getPluginManager().disablePlugin(this);
                        return;
                    }
                    this.database = mgr;
                }
                case "json" -> {
                    JsonEconomyDatabase mgr = new JsonEconomyDatabase(this, redis);
                    boolean ok = mgr.connect("", "", ""); // JSON impl ignores args
                    if (!ok) {
                        getLogger().severe("JSON economy init failed. Disabling plugin.");
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
                    getLogger().severe("Vault not found but economy.enabled=true. Disabling plugin.");
                    getServer().getPluginManager().disablePlugin(this);
                    return;
                }

                // Register Vault provider wrapper
                VaultEconomyProvider vaultProvider = new VaultEconomyProvider(this);
                getServer().getServicesManager().register(Economy.class, vaultProvider, this, ServicePriority.High);

                RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
                if (rsp == null) {
                    getLogger().severe("Failed to hook Vault Economy.");
                } else {
                    this.vaultEconomy = rsp.getProvider();
                    getLogger().info("Vault economy integration enabled.");
                }

                // Listeners for economy player data
                Bukkit.getPluginManager().registerEvents(new PlayerDataListener(this), this);
                Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);

                // Preload offline cache & refresh periodically
                this.database.populateCache(offlinePlayerCache);
                Bukkit.getScheduler().runTaskTimerAsynchronously(
                        this,
                        () -> this.database.populateCache(offlinePlayerCache),
                        20L * 60,      // 1 minute after start
                        20L * 300      // every 5 minutes
                );

                // Economy commands
                if (getCommand("money") != null) {
                    getCommand("money").setExecutor(new MoneyCommand(this));
                    getCommand("money").setTabCompleter(new MoneyTabCompleter(this));
                }

                // /pay (core.PayCommand uses ecoBootstrap)
                if (getCommand("pay") != null) {
                    getCommand("pay").setExecutor((sender, cmd, label, args) ->
                            new fr.elias.oreoEssentials.commands.core.PayCommand(ecoBootstrap)
                                    .execute(sender, label, args));
                    getCommand("pay").setTabCompleter(new PayTabCompleter(this));
                }

                if (getCommand("cheque") != null) {
                    getCommand("cheque").setExecutor(new ChequeCommand(this));
                    getCommand("cheque").setTabCompleter(new ChequeTabCompleter());
                }
            } else {
                getLogger().warning("Economy enabled but no database selected/connected; economy commands will be unavailable.");
            }
        } else {
            getLogger().info("Economy disabled. Skipping Vault, DB, and economy commands.");
        }

        // RabbitMQ (optional)
        if (rabbitEnabled) {
            RabbitMQSender rabbit = new RabbitMQSender(getConfig().getString("rabbitmq.uri"));
            this.packetManager = new PacketManager(this, rabbit);
            if (rabbit.connect()) {
                packetManager.init();
                packetManager.subscribeChannel(PacketChannels.GLOBAL);
                packetManager.subscribe(SendRemoteMessagePacket.class, new RemoteMessagePacketHandler());
                packetManager.subscribe(PlayerJoinPacket.class, new PlayerJoinPacketHandler(this));
                packetManager.subscribe(PlayerQuitPacket.class, new PlayerQuitPacketHandler(this));
                getLogger().info("RabbitMQ connected and subscriptions active.");
            } else {
                getLogger().severe("RabbitMQ connect failed; continuing without messaging.");
                this.packetManager = null;
            }
        } else {
            getLogger().info("RabbitMQ disabled.");
        }

        // Essentials services
        this.spawnService     = new SpawnService(storage);
        this.warpService      = new WarpService(storage);
        this.homeService      = new HomeService(storage, configService);
        this.backService      = new BackService(storage);
        this.messageService   = new MessageService();
        this.teleportService  = new TeleportService(this, backService, configService);
        this.deathBackService = new DeathBackService();
        this.godService       = new GodService();

        // Moderation/services
        FreezeService freezeService = new FreezeService();
        getServer().getPluginManager().registerEvents(
                new fr.elias.oreoEssentials.listeners.FreezeListener(freezeService), this);
        VanishService vanishService = new VanishService();
        MuteService muteService = new MuteService(this);
        getServer().getPluginManager().registerEvents(
                new fr.elias.oreoEssentials.listeners.MuteListener(muteService), this);

        // --- Command manager & registrations (init FIRST, then register) ---
        this.commands = new CommandManager(this);

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
                // Afelius chat reload command
                .register(new fr.elias.oreoEssentials.commands.core.AfeliusReloadCommand(this, chatConfig))
                // Admin / utility
                .register(new fr.elias.oreoEssentials.commands.core.TpCommand())
                .register(new fr.elias.oreoEssentials.commands.core.VanishCommand(vanishService))
                .register(new fr.elias.oreoEssentials.commands.core.BanCommand())
                .register(new fr.elias.oreoEssentials.commands.core.KickCommand())
                .register(new fr.elias.oreoEssentials.commands.core.FreezeCommand(freezeService))
                .register(new fr.elias.oreoEssentials.commands.core.EnchantCommand())
                .register(new fr.elias.oreoEssentials.commands.core.MuteCommand(muteService))
                .register(new fr.elias.oreoEssentials.commands.core.UnmuteCommand(muteService));

        // Tab completions for /home and /warp
        if (getCommand("home") != null) {
            getCommand("home").setTabCompleter(new HomeTabCompleter(homeService));
        }
        if (getCommand("warp") != null) {
            getCommand("warp").setTabCompleter(new WarpTabCompleter(warpService));
        }
        if (getCommand("enchant") != null) {
            getCommand("enchant").setTabCompleter(new fr.elias.oreoEssentials.commands.completion.EnchantTabCompleter());
        }
        if (getCommand("mute") != null) {
            getCommand("mute").setTabCompleter(
                    (org.bukkit.command.TabCompleter) new fr.elias.oreoEssentials.commands.core.MuteCommand(muteService)
            );
        }
        if (getCommand("unmute") != null) {
            getCommand("unmute").setTabCompleter(
                    (org.bukkit.command.TabCompleter) new fr.elias.oreoEssentials.commands.core.UnmuteCommand(muteService)
            );
        }

        // PlaceholderAPI expansion (optional; reflection-based to avoid hard dep)
        tryRegisterPlaceholderAPI();

        // Listeners (tracking for /back, death location for /deathback, god mode protection, vanish)
        getServer().getPluginManager().registerEvents(new PlayerTrackingListener(backService), this);
        getServer().getPluginManager().registerEvents(new DeathBackListener(deathBackService), this);
        getServer().getPluginManager().registerEvents(new GodListener(godService), this);
        getServer().getPluginManager().registerEvents(
                new fr.elias.oreoEssentials.listeners.VanishListener(vanishService, this), this
        );

        // --- Portals ---
        fr.elias.oreoEssentials.portals.PortalsManager portalsManager =
                new fr.elias.oreoEssentials.portals.PortalsManager(this);
        getServer().getPluginManager().registerEvents(
                new fr.elias.oreoEssentials.portals.PortalsListener(portalsManager), this);
        if (getCommand("portal") != null) {
            fr.elias.oreoEssentials.portals.PortalsCommand portalCmd =
                    new fr.elias.oreoEssentials.portals.PortalsCommand(portalsManager);
            getCommand("portal").setExecutor(portalCmd);
            getCommand("portal").setTabCompleter(portalCmd);
        }

        // --- JumpPads ---
        fr.elias.oreoEssentials.jumpads.JumpPadsManager jumpPadsManager =
                new fr.elias.oreoEssentials.jumpads.JumpPadsManager(this);
        getServer().getPluginManager().registerEvents(
                new fr.elias.oreoEssentials.jumpads.JumpPadsListener(jumpPadsManager), this);
        if (getCommand("jumpad") != null) {
            fr.elias.oreoEssentials.jumpads.JumpPadsCommand jumpCmd =
                    new fr.elias.oreoEssentials.jumpads.JumpPadsCommand(jumpPadsManager);
            getCommand("jumpad").setExecutor(jumpCmd);
            getCommand("jumpad").setTabCompleter(jumpCmd);
        }

        getLogger().info("OreoEssentials enabled.");
    }

    public boolean isMessagingAvailable() {
        return packetManager != null && packetManager.isInitialized();
    }

    @Override
    public void onDisable() {
        try { if (teleportService != null) teleportService.shutdown(); } catch (Exception ignored) {}
        try { if (storage != null) { storage.flush(); storage.close(); } } catch (Exception ignored) {}
        try { if (database != null) database.close(); } catch (Exception ignored) {}
        try { if (packetManager != null) packetManager.close(); } catch (Exception ignored) {}
        try { if (ecoBootstrap != null) ecoBootstrap.disable(); } catch (Exception ignored) {}
        try { if (chatSyncManager != null) chatSyncManager.close(); } catch (Exception ignored) {}

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

    public RedisManager getRedis() { return redis; }
    public OfflinePlayerCache getOfflinePlayerCache() { return offlinePlayerCache; }
    public PlayerEconomyDatabase getDatabase() { return database; }
    public PacketManager getPacketManager() { return packetManager; }
    public EconomyBootstrap getEcoBootstrap() { return ecoBootstrap; }
    public Economy getVaultEconomy() { return vaultEconomy; }
}
