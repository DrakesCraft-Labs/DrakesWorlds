package me.jackstar.drakesworlds.command;

import me.jackstar.drakesworlds.DrakesWorldsPlugin;
import me.jackstar.drakesworlds.config.WorldsConfig;
import me.jackstar.drakesworlds.domain.WorldProfile;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class DrakesWorldsCommand implements CommandExecutor, TabCompleter {

    private final DrakesWorldsPlugin plugin;

    public DrakesWorldsCommand(DrakesWorldsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "create" -> handleCreate(sender, args, label);
            case "listprofiles" -> handleListProfiles(sender);
            case "reload" -> handleReload(sender);
            case "worldinfo" -> handleWorldInfo(sender, args, label);
            case "listworlds" -> handleListWorlds(sender);
            case "tp", "teleport" -> handleTeleport(sender, args, label);
            case "spawn", "hub" -> handleSpawn(sender, args, label);
            default -> {
                sendHelp(sender);
                yield true;
            }
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> values = new ArrayList<>();
            if (sender.hasPermission("drakesworlds.admin")) {
                values.addAll(Arrays.asList("create", "listprofiles", "reload", "worldinfo"));
            }
            if (sender.hasPermission("drakesworlds.teleport")) {
                values.addAll(Arrays.asList("listworlds", "tp", "spawn"));
            }
            return filterByPrefix(values, args[0]);
        }
        if (args.length == 2 && "create".equalsIgnoreCase(args[0])) {
            return List.of("<world_name>");
        }
        if (args.length == 3 && "create".equalsIgnoreCase(args[0])) {
            return filterByPrefix(new ArrayList<>(plugin.getWorldsConfig().getProfiles().keySet()), args[2]);
        }
        if (args.length == 2 && "worldinfo".equalsIgnoreCase(args[0])) {
            return filterByPrefix(Bukkit.getWorlds().stream().map(World::getName).collect(Collectors.toList()), args[1]);
        }
        if (args.length == 2 && ("tp".equalsIgnoreCase(args[0]) || "teleport".equalsIgnoreCase(args[0]))) {
            return filterByPrefix(Bukkit.getWorlds().stream().map(World::getName).collect(Collectors.toList()), args[1]);
        }
        if (args.length == 3 && ("tp".equalsIgnoreCase(args[0]) || "teleport".equalsIgnoreCase(args[0]))) {
            return filterByPrefix(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), args[2]);
        }
        if (args.length == 2 && ("spawn".equalsIgnoreCase(args[0]) || "hub".equalsIgnoreCase(args[0]))) {
            return filterByPrefix(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), args[1]);
        }
        return List.of();
    }

    private boolean handleCreate(CommandSender sender, String[] args, String label) {
        if (!requirePermission(sender, "drakesworlds.admin")) {
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " create <world_name> [profile] [seed]");
            return true;
        }

        String worldName = args[1];
        String profileId = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : plugin.getWorldsConfig().getDefaultProfileId();
        Long seed = null;
        if (args.length >= 4) {
            try {
                seed = Long.parseLong(args[3]);
            } catch (NumberFormatException ex) {
                sender.sendMessage(ChatColor.RED + "Invalid seed: " + args[3]);
                return true;
            }
        }

        try {
            World world = plugin.getWorldBootstrapService()
                    .createWorld(worldName, profileId, World.Environment.NORMAL, seed, true);
            sender.sendMessage(ChatColor.GREEN + "World ready: " + world.getName() + " | profile=" + profileId);
        } catch (Exception ex) {
            sender.sendMessage(ChatColor.RED + "Could not create world: " + ex.getMessage());
            plugin.getLogger().severe("World creation failed: " + ex.getMessage());
        }
        return true;
    }

    private boolean handleListProfiles(CommandSender sender) {
        if (!requirePermission(sender, "drakesworlds.admin")) {
            return true;
        }
        sender.sendMessage(ChatColor.GOLD + "DrakesWorlds profiles:");
        for (WorldProfile profile : plugin.getWorldsConfig().getProfiles().values()) {
            sender.sendMessage(ChatColor.GRAY + "- " + ChatColor.AQUA + profile.id());
        }
        sender.sendMessage(ChatColor.GRAY + "Default profile: " + ChatColor.AQUA + plugin.getWorldsConfig().getDefaultProfileId());
        sender.sendMessage(ChatColor.GRAY + "Default world: " + ChatColor.AQUA + plugin.getWorldsConfig().getDefaultWorldName());
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!requirePermission(sender, "drakesworlds.admin")) {
            return true;
        }
        plugin.getWorldsConfig().reload();
        WorldsConfig config = plugin.getWorldsConfig();
        plugin.getWorldBootstrapService().ensureConfiguredDefaultWorldLoaded();
        plugin.getWorldBootstrapService().syncLevelNameWithConfiguredDefaultWorld();
        sender.sendMessage(ChatColor.GREEN + "DrakesWorlds reloaded. Profiles: " + config.getProfiles().keySet());
        sender.sendMessage(ChatColor.GREEN + "Default world: " + config.getDefaultWorldName());
        return true;
    }

    private boolean handleWorldInfo(CommandSender sender, String[] args, String label) {
        if (!requirePermission(sender, "drakesworlds.admin")) {
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " worldinfo <world>");
            return true;
        }
        World world = Bukkit.getWorld(args[1]);
        if (world == null) {
            sender.sendMessage(ChatColor.RED + "World not loaded: " + args[1]);
            return true;
        }
        sender.sendMessage(ChatColor.GOLD + "World info: " + world.getName());
        sender.sendMessage(ChatColor.GRAY + "- Seed: " + world.getSeed());
        sender.sendMessage(ChatColor.GRAY + "- Environment: " + world.getEnvironment().name());
        sender.sendMessage(ChatColor.GRAY + "- MinY/MaxY: " + world.getMinHeight() + "/" + world.getMaxHeight());
        String generator = world.getGenerator() == null ? "Vanilla/Default" : world.getGenerator().getClass().getSimpleName();
        sender.sendMessage(ChatColor.GRAY + "- Generator: " + generator);
        return true;
    }

    private boolean handleListWorlds(CommandSender sender) {
        if (!requirePermission(sender, "drakesworlds.teleport")) {
            return true;
        }
        sender.sendMessage(ChatColor.GOLD + "Loaded worlds:");
        for (World world : Bukkit.getWorlds()) {
            sender.sendMessage(ChatColor.GRAY + "- " + ChatColor.AQUA + world.getName()
                    + ChatColor.DARK_GRAY + " (" + world.getEnvironment().name() + ")");
        }
        return true;
    }

    private boolean handleTeleport(CommandSender sender, String[] args, String label) {
        if (!requirePermission(sender, "drakesworlds.teleport")) {
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " tp <world> [player]");
            return true;
        }

        World world = Bukkit.getWorld(args[1]);
        if (world == null) {
            sender.sendMessage(ChatColor.RED + "World not loaded: " + args[1]);
            return true;
        }

        Player target;
        if (args.length >= 3) {
            target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player not found: " + args[2]);
                return true;
            }
        } else if (sender instanceof Player playerSender) {
            target = playerSender;
        } else {
            sender.sendMessage(ChatColor.RED + "Console must specify a player: /" + label + " tp <world> <player>");
            return true;
        }

        target.teleport(world.getSpawnLocation());
        sender.sendMessage(ChatColor.GREEN + "Teleported " + target.getName() + " to " + world.getName());
        if (!sender.getName().equalsIgnoreCase(target.getName())) {
            target.sendMessage(ChatColor.GREEN + "Teleported to world: " + ChatColor.YELLOW + world.getName());
        }
        return true;
    }

    private boolean handleSpawn(CommandSender sender, String[] args, String label) {
        if (!requirePermission(sender, "drakesworlds.teleport")) {
            return true;
        }

        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player not found: " + args[1]);
                return true;
            }
        } else if (sender instanceof Player playerSender) {
            target = playerSender;
        } else {
            sender.sendMessage(ChatColor.RED + "Console must specify a player: /" + label + " spawn <player>");
            return true;
        }

        World world = plugin.getWorldBootstrapService().ensureConfiguredDefaultWorldLoaded();
        target.teleport(world.getSpawnLocation());
        sender.sendMessage(ChatColor.GREEN + "Teleported " + target.getName() + " to default world: " + world.getName());
        if (!sender.getName().equalsIgnoreCase(target.getName())) {
            target.sendMessage(ChatColor.GREEN + "Teleported to default world: " + ChatColor.YELLOW + world.getName());
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "DrakesWorlds commands:");
        if (sender.hasPermission("drakesworlds.admin")) {
            sender.sendMessage(ChatColor.YELLOW + "/drakesworlds create <world_name> [profile] [seed]");
            sender.sendMessage(ChatColor.YELLOW + "/drakesworlds listprofiles");
            sender.sendMessage(ChatColor.YELLOW + "/drakesworlds worldinfo <world>");
            sender.sendMessage(ChatColor.YELLOW + "/drakesworlds reload");
        }
        if (sender.hasPermission("drakesworlds.teleport")) {
            sender.sendMessage(ChatColor.YELLOW + "/drakesworlds listworlds");
            sender.sendMessage(ChatColor.YELLOW + "/drakesworlds tp <world> [player]");
            sender.sendMessage(ChatColor.YELLOW + "/drakesworlds spawn [player]");
        }
    }

    private boolean requirePermission(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        sender.sendMessage(ChatColor.RED + "No permission.");
        return false;
    }

    private static List<String> filterByPrefix(List<String> values, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower))
                .collect(Collectors.toList());
    }
}
