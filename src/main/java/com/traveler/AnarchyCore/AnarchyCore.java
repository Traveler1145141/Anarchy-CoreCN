package com.traveler.AnarchyCore;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AnarchyCore extends JavaPlugin implements Listener, CommandExecutor {

    private FileConfiguration config;
    private final HashMap<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, Integer> shulkerBreakCounts = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = getConfig();
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("dupe").setExecutor(this);
        Bukkit.getScheduler().runTaskTimer(this, this::clearOldCounts, 0L, 1200L);
    }

    private void clearOldCounts() {
        shulkerBreakCounts.entrySet().removeIf(entry -> 
            Bukkit.getPlayer(entry.getKey()) == null
        );
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        if (!config.getBoolean("shulker_dupe.enable")) return;
        Material type = e.getBlock().getType();
        if (type.toString().endsWith("SHULKER_BOX")) {
            Player player = e.getPlayer();
            UUID uuid = player.getUniqueId();
            shulkerBreakCounts.remove(uuid);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("只有玩家可以使用此命令.");
            return true;
        }

        if (!config.getBoolean("dupe.enable")) {
            player.sendMessage("复制被管理员关闭.");
            return true;
        }

        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        long cooldownTime = config.getLong("dupe.cooldown") * 1000L;

        if (cooldowns.containsKey(playerId) && currentTime < cooldowns.get(playerId) + cooldownTime) {
            long remaining = (cooldowns.get(playerId) + cooldownTime - currentTime) / 1000;
            player.sendMessage("冷却: " + remaining + " 后再试.");
            return true;
        }

        ItemStack handItem = player.getInventory().getItemInMainHand();
        if (handItem.getType() == Material.AIR) {
            player.sendMessage("你手上没有可以供复制的物品.");
            return true;
        }

        ItemStack duped = handItem.clone();
        player.getWorld().dropItemNaturally(player.getLocation(), duped);
        player.sendMessage("Item duplicated!");
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
        cooldowns.put(playerId, currentTime);
        return true;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        if (!config.getBoolean("shulker_dupe.enable")) return;
        Material type = e.getBlock().getType();
        if (!type.toString().endsWith("SHULKER_BOX")) return;

        Player player = e.getPlayer();
        UUID uuid = player.getUniqueId();
        int requiredBreaks = config.getInt("shulker_dupe.break", 10);

        int count = shulkerBreakCounts.getOrDefault(uuid, 0) + 1;
        shulkerBreakCounts.put(uuid, count);
        
        if (count < requiredBreaks) {
            player.sendMessage("n+1复制进度: " + count + "/" + requiredBreaks);
            return;
        }
        

        shulkerBreakCounts.remove(uuid);
        

        new BukkitRunnable() {
            @Override
            public void run() {
                ItemStack newBox = new ItemStack(type, 1);
                if (newBox.getItemMeta() instanceof BlockStateMeta meta) {
                    meta.setBlockState(meta.getBlockState());
                    newBox.setItemMeta(meta);
                }
                

                player.getWorld().dropItemNaturally(e.getBlock().getLocation(), newBox);
                player.sendMessage("Shulker duplicated! (Progress reset)");
                player.playSound(e.getBlock().getLocation(), Sound.BLOCK_SHULKER_BOX_OPEN, 1.0f, 1.0f);
            }
        }.runTaskLater(this, 1L);
    }
}
