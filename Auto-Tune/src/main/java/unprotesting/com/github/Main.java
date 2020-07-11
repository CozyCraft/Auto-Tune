package unprotesting.com.github;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.github.stefvanschie.inventoryframework.Gui;
import com.github.stefvanschie.inventoryframework.GuiItem;
import com.github.stefvanschie.inventoryframework.pane.OutlinePane;
import com.github.stefvanschie.inventoryframework.pane.PaginatedPane;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import com.sun.net.httpserver.HttpServer;
import unprotesting.com.github.Commands.AutoTuneCommand;
import unprotesting.com.github.util.Config;
import unprotesting.com.github.util.JoinEventHandler;
import unprotesting.com.github.Commands.AutoTuneGUIShopUserCommand;
import unprotesting.com.github.util.StaticFileHandler;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.mapdb.DB;
import org.mapdb.DBMaker;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.milkbowl.vault.economy.Economy;
import lombok.Getter;
import lombok.Setter;

public final class Main extends JavaPlugin implements Listener {

    JavaPlugin instance = this;

    @Getter
    public static Main INSTANCE;

    private static final Logger log = Logger.getLogger("Minecraft");
    private static Economy econ;
    private static JavaPlugin plugin;
    File playerdata = new File("plugins/Auto-Tune/", "playerdata.yml");
    public static final String BASEDIR = "plugins/Auto-Tune/Trade";
    public static final String BASEDIRMAIN = "plugins/Auto-Tune/data.csv";
    public FileConfiguration playerDataConfig;
    public final String playerdatafilename = "playerdata.yml";

    public static DB db;

    public static ConcurrentMap<String, ConcurrentHashMap<Integer, Double[]>> map;

    public static ConcurrentHashMap<String, ConcurrentHashMap<Integer, Double[]>> tempmap;

    public static ConcurrentMap<Integer, Material> ItemMap;

    @Getter
    private File configf, shopf;

    @Getter
    @Setter
    private FileConfiguration mainConfig, shopConfig;

    @Getter
    @Setter
    public static Integer materialListSize;


    @Override
    public void onDisable() {
        cancelAllTasks(this);
        log.info(String.format("[%s] Disabled Version %s", getDescription().getName(), getDescription().getVersion()));
    }

    private void cancelAllTasks(Main main) {
    }

    @Override
    public void onEnable() {
        Bukkit.getServer().getPluginManager().registerEvents(new JoinEventHandler(), this);
        createFiles();
        INSTANCE = this;
        if (!setupEconomy()) {
            log.severe(
                    String.format("Disabled Auto-Tune due to no Vault dependency found!", getDescription().getName()));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        loadDefaults();
        if (Config.isWebServer()) {
            HttpServer server;
            try {
                server = HttpServer.create(new InetSocketAddress(Config.getPort()), 0);
                server.createContext("/static", new StaticFileHandler(BASEDIR));
                server.setExecutor(null);
                server.start();
                log.info("[Auto Tune] Web server has started on port " + Config.getPort());

            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        DB db = DBMaker.fileDB("data.db").checksumStoreEnable().closeOnJvmShutdown().make();
        map = (ConcurrentMap<String, ConcurrentHashMap<Integer, Double[]>>) db.hashMap("map").createOrOpen();
        playerDataConfig = YamlConfiguration.loadConfiguration(playerdata);
        saveplayerdata();
        loadShopsFile();
        setMaterialListSize(map.size());
        this.getCommand("at").setExecutor(new AutoTuneCommand());
        this.getCommand("shop").setExecutor(new AutoTuneGUIShopUserCommand());

    }


    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }

    public static Economy getEconomy() {
        return econ;
    }

    public static String[] convert(Set<String> setOfString) {

        // Create String[] from setOfString
        String[] arrayOfString = setOfString

                // Convert Set of String
                // to Stream<String>
                .stream()

                // Convert Stream<String>
                // to String[]
                .toArray(String[]::new);

        // return the formed String[]
        return arrayOfString;
    }

    public void createFiles() {

        configf = new File(getDataFolder(), "config.yml");
        shopf = new File(getDataFolder(), "shops.yml");

        if (!configf.exists()) {
            configf.getParentFile().mkdirs();
            saveResource("config.yml", false);
        }

        if (!shopf.exists()) {
            shopf.getParentFile().mkdirs();
            saveResource("shops.yml", false);
        }

        mainConfig = new YamlConfiguration();
        shopConfig = new YamlConfiguration();

        try {
            mainConfig.load(configf);
            shopConfig.load(shopf);

        } catch (InvalidConfigurationException | IOException e) {
            e.printStackTrace();
        }

    }

    public boolean onCommand(CommandSender sender, Command testcmd, String trade, String[] help) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            String hostIP = "";
            try {
                URL url_name = new URL("http://bot.whatismyipaddress.com");

                BufferedReader sc = new BufferedReader(new InputStreamReader(url_name.openStream()));

                // reads system IPAddress
                hostIP = sc.readLine().trim();

                int PORT = Config.getPort();
                InetAddress address = InetAddress.getLocalHost();
                String hostName = address.getHostName();
                this.getCommand("at").setExecutor(new AutoTuneCommand());
                TextComponent message = new TextComponent(ChatColor.YELLOW + "" + ChatColor.BOLD
                        + player.getDisplayName() + ", go to http://" + hostIP + ":" + PORT + "/static/index.html");
                message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new ComponentBuilder("Click to begin trading online").create()));
                message.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL,
                        "http://" + hostIP + ":" + PORT + "/static/index.html"));
                player.spigot().sendMessage(message);
                player.sendMessage(ChatColor.ITALIC + "Hostname : " + hostName);
            } catch (Exception e) {
                hostIP = "Cannot Execute Properly";
            }
            return true;
        }
        return false;
    }

    public void loadDefaults() {
        Config.setWebServer(getMainConfig().getBoolean("web-server-enabled", false));
        Config.setDebugEnabled(getMainConfig().getBoolean("debug-enabled", false));
        Config.setPort(getMainConfig().getInt("port", 8321));
        Config.setTimePeriod(getMainConfig().getInt("time-period", 8321));
        Config.setMenuRows(getMainConfig().getInt("menu-rows", 3));
        Config.setServerName(ChatColor.translateAlternateColorCodes('&',
                getMainConfig().getString("server-name", "Survival Server - (Change this in Config)")));
        Config.setMenuTitle(
                ChatColor.translateAlternateColorCodes('&', getMainConfig().getString("menu-title", "Auto-Tune Shop")));
        Config.setPricingModel(
                ChatColor.translateAlternateColorCodes('&', getMainConfig().getString("pricing-model", "Basic")));
        Config.setBasicVolatilityAlgorithim(ChatColor.translateAlternateColorCodes('&',
                getMainConfig().getString("Basic-Volatility-Algorithim", "Fixed")));
        Config.setNoPermission(ChatColor.translateAlternateColorCodes('&',
                getMainConfig().getString("no-permission", "You do not have permission to perform this command")));
        Config.setBasicMaxFixedVolatility(getMainConfig().getDouble("Basic-Fixed-Max-Volatility", 2.00));
        Config.setBasicMaxVariableVolatility(getMainConfig().getDouble("Basic-Variable-Max-Volatility", 2.00));
        Config.setBasicMinFixedVolatility(getMainConfig().getDouble("Basic-Fixed-Min-Volatility", 0.05));
        Config.setBasicMinVariableVolatility(getMainConfig().getDouble("Basic-Variable-Min-Volatility", 0.05));
    }

    public void saveplayerdata() {
        try {
            YamlConfiguration.loadConfiguration(playerdata);
            playerDataConfig.save(playerdata);
        } catch (IOException e) {
            plugin.getLogger().warning("Unable to save " + playerdatafilename); // shouldn't really happen, but save
                                                                                // throws the
            // exception
        }

    }

    public static void log(String input) {
        Main.getINSTANCE().getLogger().log(Level.WARNING, "[AUTO-TUNE]: " + input);
    }

    public static void debugLog(String input) {
        if (Config.isDebugEnabled()) {
            Main.getINSTANCE().getLogger().log(Level.WARNING, "[AUTO-TUNE][DEBUG]: " + input);
        }
    }

    @Getter
    @Setter
    public static Gui gui;

    public static ConcurrentHashMap<Integer, OutlinePane> pageArray = new ConcurrentHashMap<Integer, OutlinePane>();


    public static void sendMessage(CommandSender commandSender, String message) {
        commandSender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }

    @Getter
    public static Set<String> tempCollection;

    public void loadShopsFile(){
        Set<String> testset = map.keySet();
        if (testset.isEmpty() == true){
            log("No data-file/usable-data found!");
            for (String key : Main.getINSTANCE().getShopConfig().getConfigurationSection("shops").getKeys(false)){
                ConfigurationSection config = Main.getINSTANCE().getShopConfig().getConfigurationSection("shops").getConfigurationSection(key);
                if (config == null){
                    log("Check the section for shop " + key + " in the shops.yml. It was not found.");
                    continue;
                }
                assert config != null;
                Double temp_a = config.getDouble("price");
                Double[] x = { temp_a, 0.0, 0.0 };
                ConcurrentHashMap<Integer, Double[]> start = (new ConcurrentHashMap<Integer, Double[]>());
                start.put(0, x);
                map.put(key, start);
                debugLog("Loaded shop: " + key + " at price: " + Double.toString(temp_a));
                }
            log("Default shops loaded from shop file");
        
        }
        if (testset.isEmpty() == false && getMainConfig().getBoolean("debug-enabled") == true){
            Integer b = testset.size();
                debugLog(b.toString() + " Items Loaded: " + testset.toString());
        }
    }

}

