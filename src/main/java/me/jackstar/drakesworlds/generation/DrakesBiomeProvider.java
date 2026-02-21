package me.jackstar.drakesworlds.generation;

import me.jackstar.drakesworlds.domain.WorldProfile;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;
import org.bukkit.util.noise.SimplexOctaveGenerator;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class DrakesBiomeProvider extends BiomeProvider {

    private final WorldProfile profile;
    private final List<Biome> availableBiomes;

    private volatile boolean initialized;
    private volatile SimplexOctaveGenerator temperatureNoise;
    private volatile SimplexOctaveGenerator humidityNoise;
    private volatile SimplexOctaveGenerator mountainNoise;
    private volatile SimplexOctaveGenerator clearingNoise;

    public DrakesBiomeProvider(WorldProfile profile) {
        this.profile = profile;
        this.availableBiomes = buildAvailableBiomes(profile);
    }

    @Nonnull
    @Override
    public Biome getBiome(@Nonnull WorldInfo worldInfo, int x, int y, int z) {
        ensureInit(worldInfo);

        double temperature = temperatureNoise.noise(x, z, 0.35d, 0.5d, true);
        double humidity = humidityNoise.noise(x, z, 0.45d, 0.5d, true);
        double mountain = Math.abs(mountainNoise.noise(x, z, 0.5d, 0.5d, true));
        double clearings = clearingNoise.noise(x, z, 0.45d, 0.5d, true);

        Biome biome = selectPrimaryBiome(worldInfo.getSeed(), x, z, temperature, humidity, mountain);

        if (isWoodland(biome) && clearings > profile.clearingThreshold()) {
            biome = pickWeighted(worldInfo.getSeed(), x, z, List.of(Biome.MEADOW, Biome.PLAINS, Biome.CHERRY_GROVE));
        }

        return biome;
    }

    @Nonnull
    @Override
    public List<Biome> getBiomes(@Nonnull WorldInfo worldInfo) {
        return availableBiomes;
    }

    private Biome selectPrimaryBiome(long seed, int x, int z, double temperature, double humidity, double mountain) {
        if (mountain > 0.68d) {
            if (temperature < -0.15d) {
                return pickWeighted(seed, x, z, List.of(Biome.JAGGED_PEAKS, Biome.SNOWY_SLOPES, Biome.GROVE));
            }
            if (temperature < 0.15d) {
                return pickWeighted(seed, x, z, List.of(Biome.GROVE, Biome.STONY_PEAKS, Biome.WINDSWEPT_HILLS));
            }
            return pickWeighted(seed, x, z, List.of(Biome.STONY_PEAKS, Biome.WINDSWEPT_HILLS, Biome.CHERRY_GROVE));
        }

        if (humidity > 0.52d && temperature > 0.08d) {
            return pickWeighted(seed, x, z, List.of(Biome.SWAMP, Biome.MANGROVE_SWAMP, Biome.SWAMP));
        }

        if (temperature > 0.22d && humidity > 0.05d && humidity < 0.42d && mountain > 0.28d) {
            return pickWeighted(seed, x, z, List.of(Biome.CHERRY_GROVE, Biome.FOREST, Biome.MEADOW));
        }

        if (humidity > 0.16d) {
            if (temperature < -0.14d) {
                return pickWeighted(seed, x, z, List.of(Biome.SNOWY_TAIGA, Biome.TAIGA, Biome.GROVE));
            }
            return pickWeighted(seed, x, z, List.of(Biome.OLD_GROWTH_PINE_TAIGA, Biome.TAIGA, Biome.FOREST, Biome.DARK_FOREST));
        }

        if (humidity < -0.34d && temperature > 0.12d) {
            return pickWeighted(seed, x, z, List.of(Biome.BADLANDS, Biome.WOODED_BADLANDS, Biome.BADLANDS));
        }

        return pickWeighted(seed, x, z, List.of(
                Biome.TAIGA,
                Biome.OLD_GROWTH_PINE_TAIGA,
                Biome.FOREST,
                Biome.GROVE,
                Biome.CHERRY_GROVE,
                Biome.SWAMP,
                Biome.PLAINS
        ));
    }

    private Biome pickWeighted(long seed, int x, int z, List<Biome> options) {
        double total = 0.0d;
        for (Biome option : options) {
            total += Math.max(0.0001d, profile.weightFor(option));
        }
        if (total <= 0.0d) {
            return options.get((int) (Math.abs(hash(seed, x, z)) % options.size()));
        }

        double target = normalized(seed, x, z) * total;
        double running = 0.0d;
        for (Biome option : options) {
            running += Math.max(0.0001d, profile.weightFor(option));
            if (target <= running) {
                return option;
            }
        }
        return options.get(options.size() - 1);
    }

    private void ensureInit(WorldInfo worldInfo) {
        if (initialized) {
            return;
        }
        synchronized (this) {
            if (initialized) {
                return;
            }
            this.temperatureNoise = new SimplexOctaveGenerator(worldInfo.getSeed(), 8);
            this.temperatureNoise.setScale(0.0012d);

            this.humidityNoise = new SimplexOctaveGenerator(worldInfo.getSeed() ^ 0x9E3779B97F4A7C15L, 8);
            this.humidityNoise.setScale(0.0010d);

            this.mountainNoise = new SimplexOctaveGenerator(worldInfo.getSeed() ^ 0xC2B2AE3D27D4EB4FL, 8);
            this.mountainNoise.setScale(0.0017d);

            this.clearingNoise = new SimplexOctaveGenerator(worldInfo.getSeed() ^ 0x165667B19E3779F9L, 6);
            this.clearingNoise.setScale(profile.clearingScale());
            this.initialized = true;
        }
    }

    private static List<Biome> buildAvailableBiomes(WorldProfile profile) {
        Set<Biome> biomeSet = new LinkedHashSet<>(profile.biomeWeights().keySet());
        biomeSet.add(Biome.FOREST);
        biomeSet.add(Biome.TAIGA);
        biomeSet.add(Biome.OLD_GROWTH_PINE_TAIGA);
        biomeSet.add(Biome.CHERRY_GROVE);
        biomeSet.add(Biome.SWAMP);
        biomeSet.add(Biome.MANGROVE_SWAMP);
        biomeSet.add(Biome.GROVE);
        biomeSet.add(Biome.SNOWY_SLOPES);
        biomeSet.add(Biome.JAGGED_PEAKS);
        biomeSet.add(Biome.BADLANDS);
        biomeSet.add(Biome.WOODED_BADLANDS);
        return new ArrayList<>(biomeSet);
    }

    private static boolean isWoodland(Biome biome) {
        String name = biome.name().toUpperCase(Locale.ROOT);
        return name.contains("FOREST")
                || name.contains("TAIGA")
                || biome == Biome.CHERRY_GROVE;
    }

    private static double normalized(long seed, int x, int z) {
        long hash = hash(seed, x, z);
        long positive = hash & Long.MAX_VALUE;
        return positive / (double) Long.MAX_VALUE;
    }

    private static long hash(long seed, int x, int z) {
        long h = seed;
        h ^= mix64(0x9E3779B97F4A7C15L * x);
        h ^= mix64(0xC2B2AE3D27D4EB4FL * z);
        return mix64(h);
    }

    private static long mix64(long z) {
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return z ^ (z >>> 33);
    }
}

