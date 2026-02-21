package me.jackstar.drakesworlds.config;

import me.jackstar.drakesworlds.DrakesWorldsPlugin;
import me.jackstar.drakesworlds.domain.DecorationSettings;
import me.jackstar.drakesworlds.domain.WorldProfile;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class WorldsConfig {

    private final DrakesWorldsPlugin plugin;
    private final File worldsFile;

    private FileConfiguration config;
    private String defaultProfileId;
    private boolean autoCreateOnStartup;
    private String defaultWorldName;
    private boolean enforceDefaultWorldOnJoin;
    private boolean enforceDefaultWorldOnlyFirstJoin;
    private boolean syncLevelNameInServerProperties;
    private final Map<String, WorldProfile> profiles = new HashMap<>();
    private final List<StartupWorldSpec> startupWorlds = new ArrayList<>();

    public WorldsConfig(DrakesWorldsPlugin plugin) {
        this.plugin = plugin;
        this.worldsFile = new File(plugin.getDataFolder(), "worlds.yml");
    }

    public void reload() {
        this.config = YamlConfiguration.loadConfiguration(worldsFile);

        this.defaultProfileId = config.getString("default-profile", "wild_natural");
        this.autoCreateOnStartup = config.getBoolean("auto-create-on-startup", true);
        this.defaultWorldName = config.getString("default-world.name", "drakes_wild").trim();
        this.enforceDefaultWorldOnJoin = config.getBoolean("default-world.enforce-on-join", true);
        this.enforceDefaultWorldOnlyFirstJoin = config.getBoolean("default-world.only-first-join", false);
        this.syncLevelNameInServerProperties = config.getBoolean("default-world.sync-level-name", true);

        this.profiles.clear();
        loadProfiles();

        this.startupWorlds.clear();
        loadStartupWorlds();
    }

    public Optional<WorldProfile> getProfile(String profileId) {
        if (profileId == null || profileId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(profiles.get(profileId.toLowerCase(Locale.ROOT)));
    }

    public String getDefaultProfileId() {
        return defaultProfileId;
    }

    public WorldProfile getRequiredDefaultProfile() {
        return getProfile(defaultProfileId)
                .orElseGet(() -> {
                    plugin.getLogger().warning("Default profile '" + defaultProfileId + "' not found, using first available.");
                    return profiles.values().stream()
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException("No generation profiles available in worlds.yml"));
                });
    }

    public boolean isAutoCreateOnStartup() {
        return autoCreateOnStartup;
    }

    public String getDefaultWorldName() {
        return defaultWorldName;
    }

    public boolean isEnforceDefaultWorldOnJoin() {
        return enforceDefaultWorldOnJoin;
    }

    public boolean isEnforceDefaultWorldOnlyFirstJoin() {
        return enforceDefaultWorldOnlyFirstJoin;
    }

    public boolean isSyncLevelNameInServerProperties() {
        return syncLevelNameInServerProperties;
    }

    public Map<String, WorldProfile> getProfiles() {
        return Map.copyOf(profiles);
    }

    public List<StartupWorldSpec> getStartupWorlds() {
        return List.copyOf(startupWorlds);
    }

    private void loadProfiles() {
        ConfigurationSection profilesSection = config.getConfigurationSection("profiles");
        if (profilesSection == null) {
            throw new IllegalStateException("Missing 'profiles' section in worlds.yml");
        }

        for (String profileId : profilesSection.getKeys(false)) {
            ConfigurationSection profileSection = profilesSection.getConfigurationSection(profileId);
            if (profileSection == null) {
                continue;
            }

            ConfigurationSection terrain = profileSection.getConfigurationSection("terrain");
            ConfigurationSection biomeWeightsSection = profileSection.getConfigurationSection("biome-weights");
            ConfigurationSection decoration = profileSection.getConfigurationSection("decoration");

            if (terrain == null || biomeWeightsSection == null || decoration == null) {
                plugin.getLogger().warning("Profile '" + profileId + "' is incomplete and was skipped.");
                continue;
            }

            Map<Biome, Double> weights = new EnumMap<>(Biome.class);
            for (String biomeName : biomeWeightsSection.getKeys(false)) {
                String normalized = biomeName.trim().toUpperCase(Locale.ROOT);
                try {
                    Biome biome = Biome.valueOf(normalized);
                    double value = biomeWeightsSection.getDouble(biomeName, 0.0d);
                    if (value > 0.0d) {
                        weights.put(biome, value);
                    }
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().warning("Unknown biome '" + biomeName + "' in profile '" + profileId + "'");
                }
            }

            if (weights.isEmpty()) {
                plugin.getLogger().warning("Profile '" + profileId + "' has no valid biome weights and was skipped.");
                continue;
            }

            DecorationSettings decorationSettings = new DecorationSettings(
                    decoration.getInt("base-trees-per-chunk", 16),
                    decoration.getDouble("taiga-tree-multiplier", 1.6d),
                    decoration.getDouble("forest-tree-multiplier", 1.2d),
                    decoration.getDouble("swamp-tree-multiplier", 1.1d),
                    clampZeroToOne(decoration.getDouble("dead-tree-chance", 0.08d)),
                    clampZeroToOne(decoration.getDouble("fallen-log-chance", 0.12d)),
                    clampZeroToOne(decoration.getDouble("bush-chance", 0.18d)),
                    decoration.getBoolean("enable-custom-pines", true),
                    decoration.getInt("pine-min-height", 8),
                    decoration.getInt("pine-max-height", 14)
            );

            WorldProfile profile = new WorldProfile(
                    profileId.toLowerCase(Locale.ROOT),
                    terrain.getInt("sea-level", 63),
                    terrain.getInt("base-height", 72),
                    terrain.getDouble("hill-amplitude", 20.0d),
                    terrain.getDouble("mountain-amplitude", 42.0d),
                    terrain.getDouble("valley-depth", 14.0d),
                    terrain.getDouble("detail-amplitude", 5.0d),
                    terrain.getDouble("clearing-scale", 0.0035d),
                    terrain.getDouble("clearing-threshold", 0.62d),
                    clampZeroToOne(terrain.getDouble("clearing-flattening", 0.7d)),
                    weights,
                    decorationSettings
            );

            profiles.put(profile.id(), profile);
        }
    }

    private void loadStartupWorlds() {
        List<Map<?, ?>> rawList = config.getMapList("startup-worlds");
        for (Map<?, ?> raw : rawList) {
            String name = valueOrDefault(raw, "name", "").trim();
            String profile = valueOrDefault(raw, "profile", defaultProfileId).trim();
            String environmentRaw = valueOrDefault(raw, "environment", "NORMAL").trim();
            String seedRaw = valueOrDefault(raw, "seed", "").trim();
            boolean createIfMissing = Boolean.parseBoolean(valueOrDefault(raw, "create-if-missing", "true"));

            if (name.isBlank()) {
                continue;
            }

            World.Environment environment;
            try {
                environment = World.Environment.valueOf(environmentRaw.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Invalid environment '" + environmentRaw + "' for startup world '" + name + "'. Using NORMAL.");
                environment = World.Environment.NORMAL;
            }

            Long seed = null;
            if (!seedRaw.isBlank() && !"null".equalsIgnoreCase(seedRaw)) {
                try {
                    seed = Long.parseLong(seedRaw);
                } catch (NumberFormatException ex) {
                    plugin.getLogger().warning("Invalid seed '" + seedRaw + "' for startup world '" + name + "'. Ignored.");
                }
            }

            startupWorlds.add(new StartupWorldSpec(name, profile.toLowerCase(Locale.ROOT), environment, seed, createIfMissing));
        }
    }

    private static double clampZeroToOne(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    private static String valueOrDefault(Map<?, ?> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    public record StartupWorldSpec(
            String name,
            String profileId,
            World.Environment environment,
            Long seed,
            boolean createIfMissing
    ) {
    }
}
