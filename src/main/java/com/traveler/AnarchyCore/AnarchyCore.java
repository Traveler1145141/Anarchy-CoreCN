package com.traveler.AnarchyCore;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class AnarchyCore extends JavaPlugin implements Listener, CommandExecutor {

    private FileConfiguration config;
    private FileConfiguration messages;
    private final HashMap<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, Integer> shulkerBreakCounts = new HashMap<>();
    private final Set<String> bannedCommands = new HashSet<>();

    @Override
    public void onEnable() {
        // 加载配置文件
        saveDefaultConfig();
        config = getConfig();
        loadBannedCommands();
        
        // 加载消息文件
        saveMessages();
        messages = getMessagesConfig();
        
        // 注册事件和执行器
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("dupe").setExecutor(this);
        
        // 设置定时任务清理计数
        Bukkit.getScheduler().runTaskTimer(this, this::clearOldCounts, 0L, 1200L); // 每分钟一次
        
        // 注册重载命令
        PluginCommand reloadCmd = getCommand("anarchycore");
        if (reloadCmd != null) {
            reloadCmd.setExecutor(this);
        }
        
        getLogger().info("AnarchyCore插件已启用！");
    }
    
    private void loadBannedCommands() {
        bannedCommands.clear();
        List<String> commands = config.getStringList("ban-command");
        for (String cmd : commands) {
            bannedCommands.add(cmd.toLowerCase());
        }
    }
    
    // 保存默认消息文件
    private void saveMessages() {
        File file = new File(getDataFolder(), "messages.yml");
        if (!file.exists()) {
            saveResource("messages.yml", false);
        }
    }
    
    // 获取消息配置
    private FileConfiguration getMessagesConfig() {
        return YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml"));
    }
    
    // 重载所有配置
    public void reloadConfigs() {
        reloadConfig();
        config = getConfig();
        loadBannedCommands();
        messages = getMessagesConfig();
    }
    
    // 获取带默认值的消息
    private String getMessage(String path, String defaultValue) {
        if (messages == null) {
            saveMessages();
            messages = getMessagesConfig();
        }
        String msg = messages.getString(path, defaultValue);
        return ChatColor.translateAlternateColorCodes('&', msg);
    }
    
    // 获取消息（使用错误提示作为默认值）
    private String getMessage(String path) {
        return getMessage(path, "&c错误: 缺少消息 " + path);
    }

    // 清理离线玩家的计数数据
    private void clearOldCounts() {
        shulkerBreakCounts.entrySet().removeIf(entry -> 
            Bukkit.getPlayer(entry.getKey()) == null
        );
    }

    // 放置潜影盒事件处理
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        if (!config.getBoolean("shulker_dupe.enable")) return;
        Material type = e.getBlock().getType();
        if (type.toString().endsWith("SHULKER_BOX")) {
            UUID uuid = e.getPlayer().getUniqueId();
            shulkerBreakCounts.remove(uuid);
        }
    }

    // 命令处理
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("dupe")) {
            return handleDupeCommand(sender);
        } else if (cmd.getName().equalsIgnoreCase("anarchycore")) {
            return handleReloadCommand(sender);
        }
        return false;
    }
    
    // 处理/dupe命令
    private boolean handleDupeCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(getMessage("errors.player-only", "&c只有玩家可以使用此命令。"));
            return true;
        }

        if (!config.getBoolean("dupe.enable")) {
            player.sendMessage(getMessage("dupe.disabled", "&c复制命令已被禁用。"));
            return true;
        }

        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        long cooldownTime = config.getLong("dupe.cooldown") * 1000L;

        // 检查冷却时间
        if (cooldowns.containsKey(playerId) && currentTime < cooldowns.get(playerId) + cooldownTime) {
            long remaining = (cooldowns.get(playerId) + cooldownTime - currentTime) / 1000;
            String msg = getMessage("dupe.cooldown", "&c冷却中: 剩余{time}秒。")
                    .replace("{time}", String.valueOf(remaining));
            player.sendMessage(msg);
            return true;
        }

        // 检查手上物品
        ItemStack handItem = player.getInventory().getItemInMainHand();
        if (handItem.getType() == Material.AIR) {
            player.sendMessage(getMessage("dupe.no-item", "&c你手上必须持有物品才能复制。"));
            return true;
        }

        // 执行复制
        ItemStack duped = handItem.clone();
        player.getWorld().dropItemNaturally(player.getLocation(), duped);
        player.sendMessage(getMessage("dupe.success", "&a物品已复制！"));
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
        cooldowns.put(playerId, currentTime);
        return true;
    }
    
    // 处理/anarchycore重载命令
    private boolean handleReloadCommand(CommandSender sender) {
        if (!sender.hasPermission("anarchycore.reload")) {
            sender.sendMessage(getMessage("errors.no-permission", "&c你没有权限执行此操作。"));
            return true;
        }
        
        reloadConfigs();
        sender.sendMessage(getMessage("reload.success", "&aAnarchyCore配置已重新载入！"));
        return true;
    }

    // 潜影盒挖掘事件处理
    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        if (!config.getBoolean("shulker_dupe.enable")) return;
        Material type = e.getBlock().getType();
        if (!type.toString().endsWith("SHULKER_BOX")) return;

        Player player = e.getPlayer();
        UUID uuid = player.getUniqueId();
        int requiredBreaks = config.getInt("shulker_dupe.break", 10);
        
        // 更新挖掘计数
        int count = shulkerBreakCounts.getOrDefault(uuid, 0) + 1;
        shulkerBreakCounts.put(uuid, count);
        
        // 未达到要求次数，显示进度
        if (count < requiredBreaks) {
            String msg = getMessage("shulker.progress", "&e复制进度: {current}/{required}")
                    .replace("{current}", String.valueOf(count))
                    .replace("{required}", String.valueOf(requiredBreaks));
            player.sendMessage(msg);
            return;
        }
        
        // 达到要求，重置计数并复制
        shulkerBreakCounts.remove(uuid);
        
        new BukkitRunnable() {
            @Override
            public void run() {
                // 创建新的空潜影盒
                ItemStack newBox = new ItemStack(type, 1);
                if (newBox.getItemMeta() instanceof BlockStateMeta meta) {
                    meta.setBlockState(meta.getBlockState());
                    newBox.setItemMeta(meta);
                }
                
                // 在玩家位置掉落复制品
                player.getWorld().dropItemNaturally(e.getBlock().getLocation(), newBox);
                player.sendMessage(getMessage("shulker.success", "&a潜影盒已复制！"));
                player.playSound(e.getBlock().getLocation(), Sound.BLOCK_SHULKER_BOX_OPEN, 1.0f, 1.0f);
            }
        }.runTaskLater(this, 1L); // 延时1tick执行确保正确触发
    }
    
    // 玩家命令预处理 - 禁止特定命令
    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String fullCommand = event.getMessage().toLowerCase();
        if (fullCommand.startsWith("/")) {
            // 提取命令名称（忽略参数和斜杠）
            String command = fullCommand.split("\\s+")[0].substring(1);
            
            // 检查是否在禁止列表中
            if (bannedCommands.contains(command)) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(getMessage("errors.command-disabled", 
                    "&c此命令已在服务器上禁用。"));
            }
        }
    }
    
    @Override
    public void onDisable() {
        getLogger().info("AnarchyCore插件已禁用！");
    }
}
