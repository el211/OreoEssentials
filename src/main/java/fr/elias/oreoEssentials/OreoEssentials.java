// File: src/main/java/fr/elias/oreoEssentials/OreoEssentials.java
package fr.elias.oreoEssentials;

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
import fr.elias.oreoEssentials.util.Lang;

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
    private EconomyBootstrap ecoBootstrap;
    // add near other services
    private fr.elias.oreoEssentials.integration.DiscordModerationNotifier discordMod;
    public fr.elias.oreoEssentials.integration.DiscordModerationNotifier getDiscordMod() { return discordMod; }

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

    public fr.elias.oreoEssentials.kits.KitsManager getKitsManager() { return kitsManager; }
    public fr.elias.oreoEssentials.tab.TabListManager getTabListManager() { return tabListManager; }

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
        Lang.init(this);

        // Allow sending plugin messages to the proxy for server switching
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");      // works on Bungee + Velocity
        // Optional extra: some setups also listen to the lowercase ID
        getServer().getMessenger().registerOutgoingPluginChannel(this, "bungeecord:main");
        ProxyMessenger proxyMessenger = new ProxyMessenger(this);
        this.invManager = new InventoryManager(this);
        this.invManager.init();
        // Discord Moderation notifier (separate config: discord-integration.yml)
        this.discordMod = new fr.elias.oreoEssentials.integration.DiscordModerationNotifier(this);
        this.kitsManager = new fr.elias.oreoEssentials.kits.KitsManager(this);
        new fr.elias.oreoEssentials.kits.KitCommands(this, kitsManager);
        this.tabListManager = new fr.elias.oreoEssentials.tab.TabListManager(this);
        // --- Moderation/services (create early so chat can use it) ---
        muteService = new MuteService(this);
        getServer().getPluginManager().registerEvents(new fr.elias.oreoEssentials.listeners.MuteListener(muteService), this);
        // --- Chat merge (Afelius) ---
        this.chatConfig = new fr.elias.oreoEssentials.chat.CustomConfig(this, "chat-format.yml");
        this.chatFormatManager = new fr.elias.oreoEssentials.chat.FormatManager(chatConfig);
        // Register custom join messages (independent of economy)
        getServer().getPluginManager().registerEvents(new JoinMessagesListener(this), this);

        // Chat sync (RabbitMQ) optional — init FIRST
        // BEFORE you register the chat listener, after you create MuteService muteService = new MuteService(this);
        // --- Chat sync (RabbitMQ) optional — init FIRST (now with muteService) ---
        boolean chatSyncEnabled = chatConfig.getCustomConfig().getBoolean("MongoDB_rabbitmq.enabled", false);
        String rabbitUri = chatConfig.getCustomConfig().getString("MongoDB_rabbitmq.rabbitmq.uri", "");
        try {
            this.chatSyncManager = new ChatSyncManager(chatSyncEnabled, rabbitUri, muteService); // <-- pass muteService
            if (chatSyncEnabled) this.chatSyncManager.subscribeMessages();
        } catch (Exception e) {
            getLogger().severe("ChatSync init failed: " + e.getMessage());
            this.chatSyncManager = new ChatSyncManager(false, "", muteService); // <-- still pass it
        }



        // Discord toggle + URL (single source of truth)
        // --- Discord toggle + URL ---
        var chatRoot = chatConfig.getCustomConfig().getConfigurationSection("chat");
        boolean discordEnabled = chatRoot != null
                && chatRoot.getConfigurationSection("discord") != null
                && chatRoot.getConfigurationSection("discord").getBoolean("enabled", false);
        String discordWebhookUrl = (chatRoot != null && chatRoot.getConfigurationSection("discord") != null)
                ? chatRoot.getConfigurationSection("discord").getString("webhook_url", "")
                : "";

        // --- Register chat listener (mute-aware) ---
        getServer().getPluginManager().registerEvents(
                new AsyncChatListener(
                        chatFormatManager,
                        chatConfig,
                        chatSyncManager,
                        discordEnabled,
                        discordWebhookUrl,
                        muteService // <-- IMPORTANT
                ),
                this
        );



        // Conversations (bot replies)
        getServer().getPluginManager().registerEvents(new ConversationListener(this), this);

        // Automatic messages
        new fr.elias.oreoEssentials.tasks.AutoMessageScheduler(this).start();

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
// --- Economy commands ---
                if (getCommand("pay") != null) {
                    // Use the new Vault-backed PayCommand (no ecoBootstrap arg)
                    fr.elias.oreoEssentials.commands.ecocommands.PayCommand payCmd =
                            new fr.elias.oreoEssentials.commands.ecocommands.PayCommand();

                    // Hook executor via your OreoCommand adapter
                    getCommand("pay").setExecutor((sender, cmd, label, args) ->
                            payCmd.execute(sender, label, args));

                    // Use the dedicated completer for cross-server/offline names
                    getCommand("pay").setTabCompleter(
                            new fr.elias.oreoEssentials.commands.ecocommands.completion.PayTabCompleter(this)
                    );
                } else {
                    getLogger().warning("Command 'pay' not found in plugin.yml; skipping registration.");
                }

                if (getCommand("cheque") != null) {
                    getCommand("cheque").setExecutor(new fr.elias.oreoEssentials.commands.ecocommands.ChequeCommand(this));
                    getCommand("cheque").setTabCompleter(new fr.elias.oreoEssentials.commands.ecocommands.completion.ChequeTabCompleter());
                } else {
                    getLogger().warning("Command 'cheque' not found in plugin.yml; skipping registration.");
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
        VanishService vanishService = new VanishService(this);
        // --- Command manager & registrations (init FIRST, then register) ---
        this.commands = new CommandManager(this);
        var muteCmd   = new MuteCommand(muteService, chatSyncManager);
        var unmuteCmd = new UnmuteCommand(muteService, chatSyncManager);
        var nickCmd = new fr.elias.oreoEssentials.commands.core.playercommands.NickCommand();
        this.commands.register(nickCmd);

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
                .register(new AfeliusReloadCommand(this, chatConfig))
                // Admin / utility
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
                .register(new HeadCommand());
        if (getCommand("oeserver") != null) {
            getCommand("oeserver").setTabCompleter(new ServerProxyCommand(proxyMessenger));
        }
        if (getCommand("skin") != null)
            getCommand("skin").setTabCompleter(new SkinCommand());
        if (getCommand("clone") != null)
            getCommand("clone").setTabCompleter(new CloneCommand());
        if (getCommand("head") != null)
            getCommand("head").setTabCompleter(new HeadCommand());

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
            getCommand("mute").setTabCompleter(muteCmd);
        }
        if (getCommand("unban") != null) {
            getCommand("unban").setTabCompleter(new UnbanCommand());
        }
        if (getCommand("nick") != null) {
            getCommand("nick").setTabCompleter(nickCmd);
        }
        if (getCommand("unmute") != null) {
            getCommand("unmute").setTabCompleter(unmuteCmd);
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
        // SmartInvs: no stop() in your version
        try { if (teleportService != null) teleportService.shutdown(); } catch (Exception ignored) {}
        try { if (storage != null) { storage.flush(); storage.close(); } } catch (Exception ignored) {}
        try { if (database != null) database.close(); } catch (Exception ignored) {}
        try { if (packetManager != null) packetManager.close(); } catch (Exception ignored) {}
        try { if (ecoBootstrap != null) ecoBootstrap.disable(); } catch (Exception ignored) {}
        try { if (chatSyncManager != null) chatSyncManager.close(); } catch (Exception ignored) {}
        try { if (tabListManager != null) tabListManager.stop(); } catch (Exception ignored) {}
        try { if (kitsManager != null) kitsManager.saveData(); } catch (Exception ignored) {}
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

    public RedisManager getRedis() { return redis; }
    public OfflinePlayerCache getOfflinePlayerCache() { return offlinePlayerCache; }
    public PlayerEconomyDatabase getDatabase() { return database; }
    public PacketManager getPacketManager() { return packetManager; }
    public EconomyBootstrap getEcoBootstrap() { return ecoBootstrap; }
    public Economy getVaultEconomy() { return vaultEconomy; }
}
