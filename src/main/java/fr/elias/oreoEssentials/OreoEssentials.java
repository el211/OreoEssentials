// File: src/main/java/fr/elias/oreoEssentials/OreoEssentials.java
package fr.elias.oreoEssentials;

import com.mongodb.client.MongoClients;
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
import fr.elias.oreoEssentials.rabbitmq.packet.impl.HomeTeleportRequestPacket;
import fr.elias.oreoEssentials.services.*;
import fr.elias.oreoEssentials.util.Lang;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClient;

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

    private fr.elias.oreoEssentials.homes.HomeTeleportBroker homeTpBroker;




    @Override
    public void onEnable() {
        // -------- Boot & base singletons --------
        instance = this;
        saveDefaultConfig();

        // Skins init (your utilities)
        fr.elias.oreoEssentials.util.SkinRefresherBootstrap.init(this);
        fr.elias.oreoEssentials.util.SkinDebug.init(this);

        // Locales
        Lang.init(this);

        // -------- Proxy plugin messaging (server switching) --------
        // Works with BungeeCord & Velocity; do not remove.
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        getServer().getMessenger().registerOutgoingPluginChannel(this, "bungeecord:main");
        ProxyMessenger proxyMessenger = new ProxyMessenger(this);

        // -------- UI/Managers created early --------
        this.invManager = new InventoryManager(this);
        this.invManager.init();

        // Discord moderation notifier (separate config)
        this.discordMod = new fr.elias.oreoEssentials.integration.DiscordModerationNotifier(this);

        // Kits & Tab
        this.kitsManager = new fr.elias.oreoEssentials.kits.KitsManager(this);
        new fr.elias.oreoEssentials.kits.KitCommands(this, kitsManager);
        this.tabListManager = new fr.elias.oreoEssentials.tab.TabListManager(this);

        // -------- Moderation core needed by chat --------
        muteService = new MuteService(this);
        getServer().getPluginManager().registerEvents(new fr.elias.oreoEssentials.listeners.MuteListener(muteService), this);

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
        } catch (Exception e) {
            getLogger().severe("ChatSync init failed: " + e.getMessage());
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

        // -------- Core config service & internal economy bootstrap --------
        this.configService = new ConfigService(this);
        this.ecoBootstrap  = new EconomyBootstrap(this);
        this.ecoBootstrap.enable();

        // -------- Feature toggles --------
        final String essentialsStorage = getConfig().getString("essentials.storage", "yaml").toLowerCase();
        final String economyType       = getConfig().getString("economy.type", "none").toLowerCase();
        this.economyEnabled = getConfig().getBoolean("economy.enabled", !economyType.equals("none"));
        this.redisEnabled   = getConfig().getBoolean("redis.enabled", false);
        this.rabbitEnabled  = getConfig().getBoolean("rabbitmq.enabled", false);

        // -------- Essentials storage selection (Homes/Warps/Spawn/Back) --------
        // Also sets up cross-server HomeDirectory when using MongoDB
        switch (essentialsStorage) {
            case "mongodb" -> {
                String uri    = getConfig().getString("storage.mongo.uri", "mongodb://localhost:27017");
                String dbName = getConfig().getString("storage.mongo.database", "oreo");
                String prefix = getConfig().getString("storage.mongo.collectionPrefix", "oreo_");
                String localServerName = getConfig().getString("server.name", getServer().getName());

                // Keep a client reference to close on disable
                this.homesMongoClient = com.mongodb.client.MongoClients.create(uri);

                // Mongo-backed StorageApi (handles spawn/warps/homes/back)
                this.storage = new fr.elias.oreoEssentials.services.MongoHomesStorage(
                        this.homesMongoClient, dbName, prefix, localServerName
                );

                // Cross-server home owner map
                this.homeDirectory = new fr.elias.oreoEssentials.services.MongoHomeDirectory(
                        this.homesMongoClient, dbName, prefix + "home_directory"
                );

                getLogger().info("Using MongoDB (MongoHomesStorage + MongoHomeDirectory) for essentials data.");
            }
            case "json" -> {
                this.storage       = new fr.elias.oreoEssentials.services.JsonStorage(this);
                this.homeDirectory = null; // not supported on JSON
                getLogger().info("Using JSON storage for essentials.");
            }
            default -> {
                this.storage       = new fr.elias.oreoEssentials.services.YamlStorage(this);
                this.homeDirectory = null; // not supported on YAML
                getLogger().info("Using YAML storage for essentials.");
            }
        }

        // -------- Redis cache (optional; for economy caches, etc.) --------
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
            // Dummy instance prevents null checks in your economy classes
            this.redis = new RedisManager("", 6379, "");
            getLogger().info("Redis disabled.");
        }

        // Offline player cache
        this.offlinePlayerCache = new OfflinePlayerCache();

        // -------- Economy (DB backends) & Vault registration --------
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
                    boolean ok = mgr.connect("", "", ""); // JSON ignores args
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

                // Listeners for economy player data + join/quit packets
                Bukkit.getPluginManager().registerEvents(new PlayerDataListener(this), this);
                Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);

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
                    getLogger().warning("Command 'pay' not found in plugin.yml; skipping registration.");
                }

                if (getCommand("cheque") != null) {
                    getCommand("cheque").setExecutor(new fr.elias.oreoEssentials.commands.ecocommands.ChequeCommand(this));
                    getCommand("cheque").setTabCompleter(
                            new fr.elias.oreoEssentials.commands.ecocommands.completion.ChequeTabCompleter()
                    );
                } else {
                    getLogger().warning("Command 'cheque' not found in plugin.yml; skipping registration.");
                }
            } else {
                getLogger().warning("Economy enabled but no database selected/connected; economy commands will be unavailable.");
            }
        } else {
            getLogger().info("Economy disabled. Skipping Vault, DB, and economy commands.");
        }

        // -------- RabbitMQ (optional cross-server signaling) --------
        if (rabbitEnabled) {
            RabbitMQSender rabbit = new RabbitMQSender(getConfig().getString("rabbitmq.uri"));
            this.packetManager = new PacketManager(this, rabbit);
            if (rabbit.connect()) {
                packetManager.init();
                packetManager.subscribeChannel(PacketChannels.GLOBAL);

                // Cross-server home teleport request (coordinate with proxy switch)
                packetManager.subscribe(
                        fr.elias.oreoEssentials.rabbitmq.packet.impl.HomeTeleportRequestPacket.class,
                        (channel, pkt) -> {
                            String thisServer = getConfig().getString("server.name", getServer().getName());
                            if (!thisServer.equalsIgnoreCase(pkt.getTargetServer())) return;

                            var player = getServer().getPlayer(pkt.getPlayerId());
                            if (player != null && player.isOnline()) {
                                var loc = getHomeService().getHome(pkt.getPlayerId(), pkt.getHomeName());
                                if (loc != null) player.teleport(loc);
                            } else {
                                // Optionally store a pending teleport to complete on PlayerJoin
                            }
                        }
                );




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

                getLogger().info("RabbitMQ connected and subscriptions active.");
            } else {
                getLogger().severe("RabbitMQ connect failed; continuing without messaging.");
                this.packetManager = null;
            }
        } else {
            getLogger().info("RabbitMQ disabled.");
        }

        // -------- Essentials Services --------
        this.spawnService = new SpawnService(storage);
        this.warpService  = new WarpService(storage);
        this.homeService  = new HomeService(this.storage, this.configService, this.homeDirectory);
        // Create the cross-server home broker AFTER homeService is ready and ONLY if RabbitMQ is up
        if (packetManager != null) {
            this.homeTpBroker = new fr.elias.oreoEssentials.homes.HomeTeleportBroker(this, homeService, packetManager);

            // (Optional but recommended) run pending teleports when player joins this server
// after creating homeTpBroker
            org.bukkit.Bukkit.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
                @org.bukkit.event.EventHandler
                public void onJoin(org.bukkit.event.player.PlayerJoinEvent e) {
                    homeTpBroker.onJoin(e); // <-- pass the event, not the UUID
                }
            }, this);

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

        // Admin tphere (with tab)
        var tphere = new fr.elias.oreoEssentials.commands.core.admins.TphereCommand();
        this.commands.register(tphere);
        if (getCommand("tphere") != null) {
            getCommand("tphere").setTabCompleter(tphere);
        }

        // Mute/Unmute (need chatSyncManager)
        var muteCmd   = new MuteCommand(muteService, chatSyncManager);
        var unmuteCmd = new UnmuteCommand(muteService, chatSyncManager);

        // Nick (has completer)
        var nickCmd = new fr.elias.oreoEssentials.commands.core.playercommands.NickCommand();
        this.commands.register(nickCmd);

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
                .register(new HeadCommand());

        // -------- Tab completion wiring --------
        if (getCommand("oeserver") != null) {
            getCommand("oeserver").setTabCompleter(new ServerProxyCommand(proxyMessenger));
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

        // -------- PlaceholderAPI hook (optional; reflection) --------
        tryRegisterPlaceholderAPI();

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
        try { if (this.homesMongoClient != null) this.homesMongoClient.close(); } catch (Exception ignored) {}

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
