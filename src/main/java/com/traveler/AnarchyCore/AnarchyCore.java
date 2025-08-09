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

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class AnarchyCore extends JavaPlugin implements Listener, CommandExecutor {

    private FileConfiguration config;
    private FileConfiguration messages;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, Integer> shulkerBreakCounts = new HashMap<>();
    private final Set<String> bannedCommands = new HashSet<>();
    private final Map<Block, Long> redstoneActivationTimes = new HashMap<>();
    private final Map<UUID, Long> elytraBounceTimes = new HashMap<>();
    private long lastLagCheckTime = 0;
    private double lastTPS = 20.0;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = getConfig();
        
        File messagesFile = new File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(messagesFile);
        
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("dupe").setExecutor(this);
        getCommand("anarchycore").setExecutor(this);
        getCommand("lagclean").setExecutor(this);
        
        Bukkit.getScheduler().runTaskTimer(this, this::cleanupOldData, 0L, 1200L);
        
        if (config.getBoolean("anti-lag.enable", true)) {
            Bukkit.getScheduler().runTaskTimer(this, () -> {
                double currentTPS = getServerTPS();
                lastTPS = currentTPS;
                
                if (currentTPS < config.getDouble("anti-lag.tps-threshold", 15.0)) {
                    performLagCleanup();
                }
            }, 0L, config.getLong("anti-lag.check-interval", 600L));
        }
        
        getLogger().info("插件已成功启用！");
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
        return true;
    }
    
    private boolean handleReloadCommand(CommandSender sender) {
        if (!sender.hasPermission("anarchycore.reload")) {
            sender.sendMessage(getMessage("errors.no-permission"));
            return true;
        }
        
        reloadConfig();
        config = getConfig();
        loadBannedCommands();
        setupMessages();
        sender.sendMessage(getMessage("reload.success"));
        return true;
    }
    
    private boolean handleLagCleanCommand(CommandSender sender) {
        if (!sender.hasPermission("anarchycore.lagclean")) {
            sender.sendMessage(getMessage("errors.no-permission"));
            return true;
        }
        
        int cleaned = performLagCleanup();
        sender.sendMessage(getMessage("lagclean.success").replace("{count}", String.valueOf(cleaned)));
        return true;
    }

    private void loadBannedCommands() {
        bannedCommands.clear();
        for (String cmd : config.getStringList("ban-command")) {
            bannedCommands.add(cmd.toLowerCase());
        }
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
            return;
        }
        
        shulkerBreakCounts.remove(playerId);
        
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
            player.setVelocity(new Vector(velocity.getX(), bounceStrength, velocity.getZ()));
            
            player.playSound(player.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 1.0f, 1.0f);
            
            if (config.getBoolean("elytra.warn-player", true)) {
                player.sendMessage(getMessage("elytra.warning"));
            }
            
            elytraBounceTimes.put(playerId, currentTime);
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
                world.getEntities().forEach(entity -> {
                    if (entity.getTicksLived() > config.getInt("anti-lag.entity-age-threshold", 12000)) {
                        entity.remove();
                        cleanedEntities++;
                    }
                });
            }
        }
        
        if (cleanedEntities > 0 && config.getBoolean("anti-lag.broadcast-cleanup", true)) {
            String message = getMessage("lagclean.broadcast")
                .replace("{count}", String.valueOf(cleanedEntities))
                .replace("{tps}", String.format("%.2f", lastTPS));
            Bukkit.broadcastMessage(message);
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
        getLogger().info("插件已安全关闭");
    }
}
