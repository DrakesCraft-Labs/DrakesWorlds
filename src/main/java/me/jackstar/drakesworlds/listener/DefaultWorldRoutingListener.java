package me.jackstar.drakesworlds.listener;

import me.jackstar.drakesworlds.DrakesWorldsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public final class DefaultWorldRoutingListener implements Listener {

    private final DrakesWorldsPlugin plugin;

    public DefaultWorldRoutingListener(DrakesWorldsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.getWorldsConfig().isEnforceDefaultWorldOnJoin()) {
            return;
        }
        if (plugin.getWorldsConfig().isEnforceDefaultWorldOnlyFirstJoin() && event.getPlayer().hasPlayedBefore()) {
            return;
        }

        teleportToDefaultWorld(event.getPlayer(), false);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (!plugin.getWorldsConfig().isEnforceDefaultWorldOnJoin()) {
            return;
        }
        if (plugin.getWorldsConfig().isEnforceDefaultWorldOnlyFirstJoin() && event.getPlayer().hasPlayedBefore()) {
            return;
        }

        World target = plugin.getWorldBootstrapService().ensureConfiguredDefaultWorldLoaded();
        if (target != null) {
            event.setRespawnLocation(safeSpawn(target));
        }
    }

    private void teleportToDefaultWorld(Player player, boolean sendMessage) {
        World target = plugin.getWorldBootstrapService().ensureConfiguredDefaultWorldLoaded();
        if (target == null) {
            return;
        }
        if (player.getWorld().getName().equalsIgnoreCase(target.getName())) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            player.teleport(safeSpawn(target));
            if (sendMessage) {
                player.sendMessage("§aTeletransportado al mundo principal: §e" + target.getName());
            }
        });
    }

    private Location safeSpawn(World world) {
        Location spawn = world.getSpawnLocation().clone();
        int x = spawn.getBlockX();
        int z = spawn.getBlockZ();
        int y = world.getHighestBlockYAt(x, z) + 1;
        return new Location(world, x + 0.5d, y, z + 0.5d, spawn.getYaw(), spawn.getPitch());
    }
}
