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
        if (!sender.hasPermission("drakesworlds.admin")) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "create" -> handleCreate(sender, args);
            case "listprofiles" -> handleListProfiles(sender);
            case "reload" -> handleReload(sender);
            case "worldinfo" -> handleWorldInfo(sender, args);
            default -> {
                sendHelp(sender);
                yield true;
            }
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("drakesworlds.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return filterByPrefix(Arrays.asList("create", "listprofiles", "reload", "worldinfo"), args[0]);
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
        return List.of();
    }

    private boolean handleCreate(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /drakesworlds create <world_name> [profile] [seed]");
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
        sender.sendMessage(ChatColor.GOLD + "DrakesWorlds profiles:");
        for (WorldProfile profile : plugin.getWorldsConfig().getProfiles().values()) {
            sender.sendMessage(ChatColor.GRAY + "- " + ChatColor.AQUA + profile.id());
        }
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        plugin.getWorldsConfig().reload();
        WorldsConfig config = plugin.getWorldsConfig();
        sender.sendMessage(ChatColor.GREEN + "DrakesWorlds reloaded. Profiles: " + config.getProfiles().keySet());
        return true;
    }

    private boolean handleWorldInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /drakesworlds worldinfo <world>");
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

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "DrakesWorlds commands:");
        sender.sendMessage(ChatColor.YELLOW + "/drakesworlds create <world_name> [profile] [seed]");
        sender.sendMessage(ChatColor.YELLOW + "/drakesworlds listprofiles");
        sender.sendMessage(ChatColor.YELLOW + "/drakesworlds worldinfo <world>");
        sender.sendMessage(ChatColor.YELLOW + "/drakesworlds reload");
    }

    private static List<String> filterByPrefix(List<String> values, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower))
                .collect(Collectors.toList());
    }
}
