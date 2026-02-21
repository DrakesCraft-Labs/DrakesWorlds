package me.jackstar.drakesworlds.service;

import me.jackstar.drakesworlds.DrakesWorldsPlugin;
import me.jackstar.drakesworlds.config.WorldsConfig;
import me.jackstar.drakesworlds.domain.WorldProfile;
import me.jackstar.drakesworlds.generation.DrakesBiomeProvider;
import me.jackstar.drakesworlds.generation.DrakesChunkGenerator;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;

import java.util.Optional;

public final class WorldBootstrapService {

    private final DrakesWorldsPlugin plugin;
    private final WorldsConfig worldsConfig;

    public WorldBootstrapService(DrakesWorldsPlugin plugin, WorldsConfig worldsConfig) {
        this.plugin = plugin;
        this.worldsConfig = worldsConfig;
    }

    public void createStartupWorlds() {
        if (!worldsConfig.isAutoCreateOnStartup()) {
            plugin.getLogger().info("Startup auto-create is disabled.");
            return;
        }

        for (WorldsConfig.StartupWorldSpec spec : worldsConfig.getStartupWorlds()) {
            if (Bukkit.getWorld(spec.name()) != null) {
                plugin.getLogger().info("Startup world '" + spec.name() + "' already loaded.");
                continue;
            }
            if (!spec.createIfMissing()) {
                plugin.getLogger().info("Startup world '" + spec.name() + "' is configured but create-if-missing=false.");
                continue;
            }
            createWorld(spec.name(), spec.profileId(), spec.environment(), spec.seed(), false);
        }
    }

    public World createWorld(String worldName, String profileId, World.Environment environment, Long seed, boolean forceCreateIfMissing) {
        World loadedWorld = Bukkit.getWorld(worldName);
        if (loadedWorld != null) {
            return loadedWorld;
        }

        Optional<WorldProfile> profileOpt = worldsConfig.getProfile(profileId);
        WorldProfile profile = profileOpt.orElseGet(worldsConfig::getRequiredDefaultProfile);
        if (profileOpt.isEmpty()) {
            plugin.getLogger().warning("Requested profile '" + profileId + "' not found. Using '" + profile.id() + "'");
        }

        WorldCreator creator = new WorldCreator(worldName);
        creator.environment(environment);
        if (seed != null) {
            creator.seed(seed);
        }

        DrakesBiomeProvider biomeProvider = new DrakesBiomeProvider(profile);
        DrakesChunkGenerator chunkGenerator = new DrakesChunkGenerator(profile, biomeProvider);

        creator.biomeProvider(biomeProvider);
        creator.generator(chunkGenerator);

        World world = creator.createWorld();
        if (world == null) {
            throw new IllegalStateException("World '" + worldName + "' could not be created.");
        }

        plugin.getLogger().info(
                "Created world '" + worldName + "' with profile '" + profile.id() + "'" +
                        ", env=" + environment +
                        ", seed=" + (seed == null ? "<random>" : seed) +
                        ", forceCreateIfMissing=" + forceCreateIfMissing
        );
        return world;
    }
}

