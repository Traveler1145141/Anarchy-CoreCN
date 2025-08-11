package com.traveler.AnarchyCore;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class AnarchyCore extends JavaPlugin implements Listener, CommandExecutor {

    // 插件版本号
    public static final String PLUGIN_VERSION = "1.0.0";
    
    private FileConfiguration config;
    private FileConfiguration messages;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Set<String> bannedCommands = new HashSet<>();
    private final Map<Block, Long> redstoneActivationTimes = new HashMap<>();
    private final Map<UUID, Long> elytraBounceTimes = new HashMap<>();
    private long lastLagCheckTime = 0;
    private double lastTPS = 20.0;
    
    // 固定更新URL
    private static final String UPDATE_URL = "https://raw.githubusercontent.com/Traveler114514/Anarchy-CoreCN/master/version.txt";
    
    // ANSI颜色代码
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_CYAN = "\u001B[36m";
    
    // 潜影盒复制状态跟踪
    private final Map<UUID, Integer> shulkerCycleCounts = new HashMap<>();
    private final Map<UUID, Material> activeShulkerTypes = new HashMap<>();

    @Override
    public void onEnable() {
        // 先初始化配置
        saveDefaultConfig();
        config = getConfig();
        
        // 打印LOGO
        printLogo();
        
        // 检查更新
        if (config.getBoolean("check-updates", true)) {
            checkForUpdates();
        }
        
        // 加载其他配置
        loadBannedCommands();
        setupMessages();
        
        // 注册事件和命令
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("dupe").setExecutor(this);
        getCommand("anarchycore").setExecutor(this);
        getCommand("lagclean").setExecutor(this);
        
        // 启动清理任务
        Bukkit.getScheduler().runTaskTimer(this, this::cleanupOldData, 0L, 1200L);
        
        // 启动反卡服任务
        if (config.getBoolean("anti-lag.enable", true)) {
            Bukkit.getScheduler().runTaskTimer(this, () -> {
                double currentTPS = getServerTPS();
                lastTPS = currentTPS;
                
                if (currentTPS < config.getDouble("anti-lag.tps-threshold", 15.0)) {
                    performLagCleanup();
                }
            }, 0L, config.getLong("anti-lag.check-interval", 600L));
        }
        
        getLogger().info(ANSI_GREEN + "插件已成功启用！版本: " + PLUGIN_VERSION + ANSI_RESET);
    }

    private void printLogo() {
        if (!config.getBoolean("logging.startup-logo", true)) return;
        
        String[] logo = {
            ANSI_GREEN + "    _                                    _                ____                                  ____   _   _ ",
            "   / \\     _ __     __ _   _ __    ___  | |__    _   _   / ___|   ___    _ __    ___           / ___| | \\ | |",
            "  / _ \\   | '_ \\   / _` | | '__|  / __| | '_ \\  | | | | | |      / _ \\  | '__|  / _ \\  _____  | |     |  \\| |",
            " / ___ \\  | | | | | (_| | | |    | (__  | | | | | |_| | | |___  | (_) | | |    |  __/ |_____| | |___  | |\\  |",
            "/_/   \\_\\ |_| |_|  \\__,_| |_|     \\___| |_| |_|  \\__, |  \\____|  \\___/  |_|     \\___|          \\____| |_| \\_|",
            "                                                 |___/                                                       " + ANSI_RESET
        };
        
        for (String line : logo) {
            getLogger().info(line);
        }
    }
    
    private void checkForUpdates() {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                String currentVersion = getDescription().getVersion();
                String latestVersion = getLatestVersion();
                
                if (latestVersion == null) {
                    getLogger().warning(ANSI_YELLOW + "无法获取最新版本信息" + ANSI_RESET);
                    return;
                }
                
                // 比较版本号
                int comparison = compareVersions(latestVersion, PLUGIN_VERSION);
                
                if (comparison > 0) {
                    getLogger().info(ANSI_YELLOW + "发现新版本: " + latestVersion + " (当前版本: " + PLUGIN_VERSION + ")" + ANSI_RESET);
                    getLogger().info(ANSI_YELLOW + "下载地址: https://github.com/Traveler114514/Anarchy-CoreCN/releases" + ANSI_RESET);
                } else if (comparison == 0) {
                    getLogger().info(ANSI_GREEN + "插件已是最新版本 (" + PLUGIN_VERSION + ")" + ANSI_RESET);
                } else {
                    getLogger().info(ANSI_RED + "警告: 当前版本 (" + PLUGIN_VERSION + ") 比最新版本 (" + latestVersion + ") 更新" + ANSI_RESET);
                }
            } catch (Exception e) {
                getLogger().warning(ANSI_YELLOW + "检查更新时出错: " + e.getMessage() + ANSI_RESET);
            }
        });
    }
    
    private int compareVersions(String version1, String version2) {
        String[] parts1 = version1.split("\\.");
        String[] parts2 = version2.split("\\.");
        
        int maxLength = Math.max(parts1.length, parts2.length);
        
        for (int i = 0; i < maxLength; i++) {
            int num1 = i < parts1.length ? Integer.parseInt(parts1[i]) : 0;
            int num2 = i < parts2.length ? Integer.parseInt(parts2[i]) : 0;
            
            if (num1 < num2) return -1;
            if (num1 > num2) return 1;
        }
        
        return 0;
    }
    
    private String getLatestVersion() {
        try {
            URL url = new URL(UPDATE_URL);
            URLConnection connection = url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                return reader.readLine().trim();
            }
        } catch (Exception e) {
            return null;
        }
    }

    private void setupMessages() {
        File messagesFile = new File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(messagesFile);
    }

    private void loadBannedCommands() {
        bannedCommands.clear();
        for (String cmd : config.getStringList("ban-command")) {
            bannedCommands.add(cmd.toLowerCase());
        }
    }

    private void cleanupOldData() {
        shulkerCycleCounts.entrySet().removeIf(entry -> 
            Bukkit.getPlayer(entry.getKey()) == null
        );
        
        long currentTime = System.currentTimeMillis();
        redstoneActivationTimes.entrySet().removeIf(entry -> 
            currentTime - entry.getValue() > config.getLong("redstone.record-expire", 60000)
        );
        
        elytraBounceTimes.entrySet().removeIf(entry -> 
            currentTime - entry.getValue() > config.getLong("elytra.bounce-cooldown", 1000)
        );
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!config.getBoolean("shulker_dupe.enable")) return;
        
        Material type = event.getBlock().getType();
        if (type.toString().endsWith("SHULKER_BOX")) {
            Player player = event.getPlayer();
            UUID playerId = player.getUniqueId();
            
            // 记录玩家放置的潜影盒类型
            activeShulkerTypes.put(playerId, type);
            
            if (config.getBoolean("logging.shulker-placed", true)) {
                getLogger().info(ANSI_CYAN + "玩家 " + player.getName() + " 放置了潜影盒" + ANSI_RESET);
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("dupe")) {
            return handleDupeCommand(sender);
        } else if (cmd.getName().equalsIgnoreCase("anarchycore")) {
            return handleReloadCommand(sender);
        } else if (cmd.getName().equalsIgnoreCase("lagclean")) {
            return handleLagCleanCommand(sender);
        }
        return false;
    }
    
    private boolean handleDupeCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(getMessage("errors.player-only"));
            return true;
        }

        if (!config.getBoolean("dupe.enable")) {
            player.sendMessage(getMessage("dupe.disabled"));
            return true;
        }

        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        long cooldownTime = config.getLong("dupe.cooldown") * 1000L;

        if (cooldowns.containsKey(playerId) && currentTime < cooldowns.get(playerId) + cooldownTime) {
            long remaining = (cooldowns.get(playerId) + cooldownTime - currentTime) / 1000;
            String msg = getMessage("dupe.cooldown").replace("{time}", String.valueOf(remaining));
            player.sendMessage(msg);
            return true;
        }

        ItemStack handItem = player.getInventory().getItemInMainHand();
        if (handItem.getType() == Material.AIR) {
            player.sendMessage(getMessage("dupe.no-item"));
            return true;
        }

        ItemStack duped = handItem.clone();
        player.getWorld().dropItemNaturally(player.getLocation(), duped);
        player.sendMessage(getMessage("dupe.success"));
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
        cooldowns.put(playerId, currentTime);
        
        if (config.getBoolean("logging.dupe", true)) {
            getLogger().info(ANSI_GREEN + "玩家 " + player.getName() + " 复制了物品: " + handItem.getType() + ANSI_RESET);
        }
        
        return true;
    }
    
    private boolean handleReloadCommand(CommandSender sender) {
        if (!sender.hasPermission("anarchycore.reload")) {
            sender.sendMessage(getMessage("errors.no-permission"));
            return true;
        }
        
        reloadPlugin();
        sender.sendMessage(getMessage("reload.success"));
        
        if (config.getBoolean("logging.reload", true)) {
            getLogger().info(ANSI_GREEN + "插件配置已重新加载" + ANSI_RESET);
        }
        
        return true;
    }
    
    private boolean handleLagCleanCommand(CommandSender sender) {
        if (!sender.hasPermission("anarchycore.lagclean")) {
            sender.sendMessage(getMessage("errors.no-permission"));
            return true;
        }
        
        int cleaned = performLagCleanup();
        sender.sendMessage(getMessage("lagclean.success").replace("{count}", String.valueOf(cleaned)));
        
        if (config.getBoolean("logging.lagclean-manual", true)) {
            getLogger().info(ANSI_GREEN + "管理员 " + sender.getName() + " 手动清理了 " + cleaned + " 个实体" + ANSI_RESET);
        }
        
        return true;
    }

    private void reloadPlugin() {
        reloadConfig();
        config = getConfig();
        loadBannedCommands();
        setupMessages();
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!config.getBoolean("shulker_dupe.enable")) return;
        
        Material type = event.getBlock().getType();
        if (!type.toString().endsWith("SHULKER_BOX")) return;

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        int requiredCycles = config.getInt("shulker_dupe.cycles", 10);
        
        // 获取玩家当前使用的潜影盒类型
        Material activeType = activeShulkerTypes.get(playerId);
        
        // 如果玩家没有放置过潜影盒，或者挖掘的不是同类型潜影盒，则忽略
        if (activeType == null || activeType != type) {
            return;
        }
        
        int currentCycle = shulkerCycleCounts.getOrDefault(playerId, 0) + 1;
        shulkerCycleCounts.put(playerId, currentCycle);
        
        if (currentCycle < requiredCycles) {
            String msg = getMessage("shulker.progress")
                    .replace("{current}", String.valueOf(currentCycle))
                    .replace("{required}", String.valueOf(requiredCycles));
            player.sendMessage(msg);
            
            if (config.getBoolean("logging.shulker-progress", true)) {
                getLogger().info(ANSI_CYAN + "玩家 " + player.getName() + " 挖掘潜影盒进度: " + currentCycle + "/" + requiredCycles + ANSI_RESET);
            }
            return;
        }
        
        // 达到要求，重置计数并复制
        shulkerCycleCounts.put(playerId, 0);
        Location location = event.getBlock().getLocation();
        
        new BukkitRunnable() {
            @Override
            public void run() {
                ItemStack newBox = new ItemStack(type, 1);
                if (newBox.getItemMeta() instanceof BlockStateMeta meta) {
                    meta.setBlockState(meta.getBlockState());
                    newBox.setItemMeta(meta);
                }
                
                player.getWorld().dropItemNaturally(location, newBox);
                player.sendMessage(getMessage("shulker.success"));
                player.playSound(location, Sound.BLOCK_SHULKER_BOX_OPEN, 1.0f, 1.0f);
                
                if (config.getBoolean("logging.shulker-dupe", true)) {
                    getLogger().info(ANSI_GREEN + "玩家 " + player.getName() + " 成功复制了潜影盒" + ANSI_RESET);
                }
            }
        }.runTaskLater(this, 1L);
    }
    
    // 其他方法保持不变...
}
