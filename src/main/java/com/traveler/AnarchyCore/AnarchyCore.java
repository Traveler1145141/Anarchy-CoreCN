package com.traveler.AnarchyCore;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
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
    public static final String PLUGIN_VERSION = "1.2";
    
    private FileConfiguration config;
    private FileConfiguration messages;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, Integer> shulkerBreakCounts = new HashMap<>();
    private final Set<String> bannedCommands = new HashSet<>();
    private final Map<Block, Long> redstoneActivationTimes = new HashMap<>();
    private final Map<UUID, Long> elytraBounceTimes = new HashMap<>();
    private long lastLagCheckTime = 0;
    private double lastTPS = 20.0;
    
    // 固定更新URL
    private static final String UPDATE_URL = "https://raw.githubusercontent.com/Traveler1145141/FileCloud/refs/heads/main/AnarchyCore-CN/version.txt";
    
    // ANSI颜色代码
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_CYAN = "\u001B[36m";

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
        shulkerBreakCounts.entrySet().removeIf(entry -> 
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
            UUID playerId = event.getPlayer().getUniqueId();
            shulkerBreakCounts.remove(playerId);
            
            if (config.getBoolean("logging.shulker-reset", true)) {
                getLogger().info(ANSI_CYAN + "玩家 " + event.getPlayer().getName() + " 放置了潜影盒，重置计数" + ANSI_RESET);
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
        int requiredBreaks = config.getInt("shulker_dupe.break", 10);
        
        int currentCount = shulkerBreakCounts.getOrDefault(playerId, 0) + 1;
        shulkerBreakCounts.put(playerId, currentCount);
        
        if (currentCount < requiredBreaks) {
            String msg = getMessage("shulker.progress")
                    .replace("{current}", String.valueOf(currentCount))
                    .replace("{required}", String.valueOf(requiredBreaks));
            player.sendMessage(msg);
            
            if (config.getBoolean("logging.shulker-progress", true)) {
                getLogger().info(ANSI_CYAN + "玩家 " + player.getName() + " 挖掘潜影盒进度: " + currentCount + "/" + requiredBreaks + ANSI_RESET);
            }
            return;
        }
        
        // 达到要求，重置计数并复制
        shulkerBreakCounts.put(playerId, 0);
        
        new BukkitRunnable() {
            @Override
            public void run() {
                ItemStack newBox = new ItemStack(type, 1);
                if (newBox.getItemMeta() instanceof BlockStateMeta meta) {
                    meta.setBlockState(meta.getBlockState());
                    newBox.setItemMeta(meta);
                }
                
                player.getWorld().dropItemNaturally(event.getBlock().getLocation(), newBox);
                player.sendMessage(getMessage("shulker.success"));
                player.playSound(event.getBlock().getLocation(), Sound.BLOCK_SHULKER_BOX_OPEN, 1.0f, 1.0f);
                
                if (config.getBoolean("logging.shulker-dupe", true)) {
                    getLogger().info(ANSI_GREEN + "玩家 " + player.getName() + " 成功复制了潜影盒" + ANSI_RESET);
                }
            }
        }.runTaskLater(this, 1L);
    }
    
    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String fullCommand = event.getMessage().toLowerCase();
        if (fullCommand.startsWith("/")) {
            String command = fullCommand.split("\\s+")[0].substring(1);
            if (bannedCommands.contains(command)) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(getMessage("errors.command-disabled"));
                
                if (config.getBoolean("logging.command-block", true)) {
                    getLogger().info(ANSI_YELLOW + "阻止玩家 " + event.getPlayer().getName() + " 使用禁用命令: /" + command + ANSI_RESET);
                }
            }
        }
    }
    
    @EventHandler
    public void onRedstoneActivate(BlockRedstoneEvent event) {
        if (!config.getBoolean("redstone.enable", true)) return;
        
        Block block = event.getBlock();
        
        if (isHighFrequencyRedstone(block)) {
            event.setNewCurrent(0);
            if (config.getBoolean("redstone.warn-player", true)) {
                warnNearbyPlayers(block);
            }
            
            if (config.getBoolean("logging.redstone-block", true)) {
                getLogger().info(ANSI_YELLOW + "在位置 " + block.getLocation() + " 检测并阻止了高频红石" + ANSI_RESET);
            }
        }
    }
    
    private boolean isHighFrequencyRedstone(Block block) {
        long currentTime = System.currentTimeMillis();
        Long lastActivation = redstoneActivationTimes.get(block);
        
        redstoneActivationTimes.put(block, currentTime);
        
        if (lastActivation == null) return false;
        
        long interval = currentTime - lastActivation;
        long minInterval = config.getLong("redstone.min-interval", 100);
        
        return interval < minInterval;
    }
    
    private void warnNearbyPlayers(Block block) {
        String warning = getMessage("redstone.warning");
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(block.getWorld()) && 
                player.getLocation().distanceSquared(block.getLocation()) <= 100) {
                player.sendMessage(warning);
            }
        }
    }
    
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!config.getBoolean("elytra.enable", false)) return;
        
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        
        if (!player.isGliding()) return;
        
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        
        long currentTime = System.currentTimeMillis();
        if (elytraBounceTimes.containsKey(playerId) && 
            currentTime - elytraBounceTimes.get(playerId) < config.getLong("elytra.bounce-cooldown", 1000)) {
            return;
        }
        
        Vector velocity = player.getVelocity();
        double verticalVelocity = velocity.getY();
        double threshold = config.getDouble("elytra.vertical-threshold", 0.05);
        
        if (Math.abs(verticalVelocity) < threshold) {
            double bounceStrength = config.getDouble("elytra.bounce-strength", 0.5);
            
            // 计算水平方向速度
            double horizontalSpeed = Math.sqrt(velocity.getX() * velocity.getX() + velocity.getZ() * velocity.getZ());
            
            if (horizontalSpeed > 0) {
                // 计算水平方向单位向量
                Vector horizontalDirection = new Vector(velocity.getX(), 0, velocity.getZ()).normalize();
                
                // 应用回弹效果（方向相反）
                Vector newVelocity = horizontalDirection.multiply(-horizontalSpeed * bounceStrength);
                
                // 保持垂直速度不变
                newVelocity.setY(velocity.getY());
                
                player.setVelocity(newVelocity);
                
                player.playSound(player.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 1.0f, 1.0f);
                
                if (config.getBoolean("elytra.warn-player", true)) {
                    player.sendMessage(getMessage("elytra.warning"));
                }
                
                elytraBounceTimes.put(playerId, currentTime);
                
                if (config.getBoolean("logging.elytra-bounce", true)) {
                    getLogger().info(ANSI_GREEN + "玩家 " + player.getName() + " 在位置 " + player.getLocation() + " 触发了鞘翅平飞回弹" + ANSI_RESET);
                }
            }
        }
    }
    
    private double getServerTPS() {
        long currentTime = System.currentTimeMillis();
        long timeDiff = currentTime - lastLagCheckTime;
        
        if (timeDiff < 1000) return lastTPS;
        
        lastLagCheckTime = currentTime;
        return Bukkit.getTPS()[0];
    }
    
    private int performLagCleanup() {
        int cleanedEntities = 0;
        
        if (config.getBoolean("anti-lag.clear-dropped-items", true)) {
            for (World world : Bukkit.getWorlds()) {
                for (Entity entity : world.getEntities()) {
                    if (entity instanceof Item) {
                        Item item = (Item) entity;
                        if (item.getPickupDelay() > 10000) {
                            entity.remove();
                            cleanedEntities++;
                        }
                    }
                }
            }
        }
        
        if (config.getBoolean("anti-lag.clear-mobs", true)) {
            List<EntityType> mobTypes = Arrays.asList(
                EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CREEPER, EntityType.SPIDER,
                EntityType.ENDERMAN, EntityType.WITCH, EntityType.BLAZE, EntityType.GHAST
            );
            
            for (World world : Bukkit.getWorlds()) {
                for (Entity entity : world.getEntities()) {
                    if (mobTypes.contains(entity.getType()) && 
                        entity.getTicksLived() > config.getInt("anti-lag.mob-age-threshold", 6000)) {
                        entity.remove();
                        cleanedEntities++;
                    }
                }
            }
        }
        
        if (config.getBoolean("anti-lag.clear-chunk-entities", true)) {
            for (World world : Bukkit.getWorlds()) {
                for (Entity entity : world.getEntities()) {
                    if (entity.getTicksLived() > config.getInt("anti-lag.entity-age-threshold", 12000)) {
                        entity.remove();
                        cleanedEntities++;
                    }
                }
            }
        }
        
        if (cleanedEntities > 0 && config.getBoolean("anti-lag.broadcast-cleanup", true)) {
            String message = getMessage("lagclean.broadcast")
                .replace("{count}", String.valueOf(cleanedEntities))
                .replace("{tps}", String.format("%.2f", lastTPS));
            Bukkit.broadcastMessage(message);
        }
        
        if (cleanedEntities > 0 && config.getBoolean("logging.lagclean", true)) {
            getLogger().info(ANSI_GREEN + "自动清理了 " + cleanedEntities + " 个可能导致卡顿的实体 (当前TPS: " + lastTPS + ")" + ANSI_RESET);
        }
        
        return cleanedEntities;
    }
    
    private String getMessage(String path) {
        return getMessage(path, "&c错误: 缺少消息 " + path);
    }
    
    private String getMessage(String path, String defaultValue) {
        String msg = messages.getString(path, defaultValue);
        return msg.replace('&', '§');
    }
    
    @Override
    public void onDisable() {
        getLogger().info(ANSI_GREEN + "插件已安全关闭" + ANSI_RESET);
    }
}
