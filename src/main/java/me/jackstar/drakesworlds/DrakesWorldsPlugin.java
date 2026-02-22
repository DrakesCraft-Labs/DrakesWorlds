package me.jackstar.drakesworlds;

import me.jackstar.drakesworlds.command.DrakesWorldsCommand;
import me.jackstar.drakesworlds.config.WorldsConfig;
import me.jackstar.drakesworlds.domain.WorldProfile;
import me.jackstar.drakesworlds.generation.DrakesBiomeProvider;
import me.jackstar.drakesworlds.generation.DrakesChunkGenerator;
import me.jackstar.drakesworlds.listener.DefaultWorldRoutingListener;
import me.jackstar.drakesworlds.service.WorldBootstrapService;
import org.bukkit.command.PluginCommand;
import org.bukkit.generator.ChunkGenerator;
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
        this.worldBootstrapService.syncBukkitDefaultWorldGenerator();
        this.worldBootstrapService.syncLevelNameWithConfiguredDefaultWorld();
        boolean startupPhase = getServer().getWorlds().isEmpty();
        if (startupPhase) {
            getLogger().info("Startup phase detected: deferring world bootstrap to first server tick.");
            getServer().getScheduler().runTask(this, () -> {
                this.worldBootstrapService.createStartupWorlds();
                this.worldBootstrapService.ensureConfiguredDefaultWorldLoaded();
            });
        } else {
            this.worldBootstrapService.createStartupWorlds();
            this.worldBootstrapService.ensureConfiguredDefaultWorldLoaded();
        }
        getServer().getPluginManager().registerEvents(new DefaultWorldRoutingListener(this), this);

        getLogger().info("DrakesWorlds enabled. Loaded profiles: " + worldsConfig.getProfiles().keySet());
    }

    @Override
    public void onDisable() {
        getLogger().info("DrakesWorlds disabled.");
    }

    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String profileId) {
        if (worldsConfig == null) {
            return super.getDefaultWorldGenerator(worldName, profileId);
        }

        WorldProfile profile = worldsConfig.getProfile(profileId)
                .orElseGet(worldsConfig::getRequiredDefaultProfile);

        DrakesBiomeProvider biomeProvider = new DrakesBiomeProvider(profile);
        return new DrakesChunkGenerator(profile, biomeProvider);
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
