package com.traveler.AnarchyCore;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location; // 添加缺失的Location导入
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

    // 其他方法保持不变...
}
