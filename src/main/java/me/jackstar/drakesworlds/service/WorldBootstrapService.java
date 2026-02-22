package me.jackstar.drakesworlds.service;

import me.jackstar.drakesworlds.DrakesWorldsPlugin;
import me.jackstar.drakesworlds.config.WorldsConfig;
import me.jackstar.drakesworlds.domain.WorldProfile;
import me.jackstar.drakesworlds.generation.DrakesBiomeProvider;
import me.jackstar.drakesworlds.generation.DrakesChunkGenerator;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

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

    public World ensureConfiguredDefaultWorldLoaded() {
        String defaultWorldName = worldsConfig.getDefaultWorldName();
        World loaded = Bukkit.getWorld(defaultWorldName);
        if (loaded != null) {
            return loaded;
        }

        WorldsConfig.StartupWorldSpec startupSpec = worldsConfig.getStartupWorlds().stream()
                .filter(spec -> spec.name().equalsIgnoreCase(defaultWorldName))
                .findFirst()
                .orElse(null);

        if (startupSpec != null) {
            return createWorld(startupSpec.name(), startupSpec.profileId(), startupSpec.environment(), startupSpec.seed(), true);
        }

        plugin.getLogger().warning("Default world '" + defaultWorldName + "' is not in startup-worlds. Creating it with default profile.");
        return createWorld(defaultWorldName, worldsConfig.getDefaultWorldProfileId(), World.Environment.NORMAL, null, true);
    }

    public void syncLevelNameWithConfiguredDefaultWorld() {
        if (!worldsConfig.isSyncLevelNameInServerProperties()) {
            return;
        }

        File serverProperties = new File(plugin.getServer().getWorldContainer(), "server.properties");
        if (!serverProperties.exists()) {
            plugin.getLogger().warning("server.properties not found. Could not sync level-name.");
            return;
        }

        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(serverProperties)) {
            props.load(in);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not read server.properties: " + ex.getMessage());
            return;
        }

        String expected = worldsConfig.getDefaultWorldName();
        String current = props.getProperty("level-name", "world");
        if (Objects.equals(current, expected)) {
            return;
        }

        props.setProperty("level-name", expected);
        try (FileOutputStream out = new FileOutputStream(serverProperties)) {
            props.store(out, "Updated by DrakesWorlds");
            plugin.getLogger().warning("Synchronized server.properties level-name from '" + current + "' to '" + expected + "'. Restart required to fully apply.");
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not write server.properties: " + ex.getMessage());
        }
    }

    public void syncBukkitDefaultWorldGenerator() {
        File bukkitYml = new File(plugin.getServer().getWorldContainer(), "bukkit.yml");
        if (!bukkitYml.exists()) {
            plugin.getLogger().warning("bukkit.yml not found. Could not sync world generator.");
            return;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(bukkitYml);
        String worldName = worldsConfig.getDefaultWorldName();
        String profileId = worldsConfig.getDefaultWorldProfileId();
        String expected = plugin.getName() + ":" + profileId;
        String path = "worlds." + worldName + ".generator";
        String current = yaml.getString(path, "");
        if (expected.equalsIgnoreCase(current)) {
            return;
        }

        yaml.set(path, expected);
        try {
            yaml.save(bukkitYml);
            plugin.getLogger().warning("Synchronized bukkit.yml generator for world '" + worldName + "' to '" + expected + "'. Restart required to fully apply.");
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not write bukkit.yml: " + ex.getMessage());
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
