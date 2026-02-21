package me.jackstar.drakesworlds;

import me.jackstar.drakesworlds.command.DrakesWorldsCommand;
import me.jackstar.drakesworlds.config.WorldsConfig;
import me.jackstar.drakesworlds.service.WorldBootstrapService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class DrakesWorldsPlugin extends JavaPlugin {

    private WorldsConfig worldsConfig;
    private WorldBootstrapService worldBootstrapService;

    @Override
    public void onEnable() {
        saveDefaultWorldsConfig();

        this.worldsConfig = new WorldsConfig(this);
        this.worldsConfig.reload();

        this.worldBootstrapService = new WorldBootstrapService(this, worldsConfig);

        registerCommands();
        this.worldBootstrapService.createStartupWorlds();

        getLogger().info("DrakesWorlds enabled. Loaded profiles: " + worldsConfig.getProfiles().keySet());
    }

    @Override
    public void onDisable() {
        getLogger().info("DrakesWorlds disabled.");
    }

    public WorldsConfig getWorldsConfig() {
        return worldsConfig;
    }

    public WorldBootstrapService getWorldBootstrapService() {
        return worldBootstrapService;
    }

    private void saveDefaultWorldsConfig() {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().warning("Could not create plugin data folder.");
        }
        saveResource("worlds.yml", false);
    }

    private void registerCommands() {
        DrakesWorldsCommand commandHandler = new DrakesWorldsCommand(this);
        PluginCommand command = getCommand("drakesworlds");
        if (command == null) {
            getLogger().severe("Command 'drakesworlds' is missing in plugin.yml");
            return;
        }
        command.setExecutor(commandHandler);
        command.setTabCompleter(commandHandler);
    }
}

