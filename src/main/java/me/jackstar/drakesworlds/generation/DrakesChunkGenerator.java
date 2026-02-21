package me.jackstar.drakesworlds.generation;

import me.jackstar.drakesworlds.domain.WorldProfile;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.util.noise.SimplexOctaveGenerator;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public final class DrakesChunkGenerator extends ChunkGenerator {

    private final WorldProfile profile;
    private final DrakesBiomeProvider biomeProvider;
    private final DrakesFloraPopulator floraPopulator;

    private volatile boolean initialized;
    private volatile SimplexOctaveGenerator continentalNoise;
    private volatile SimplexOctaveGenerator mountainNoise;
    private volatile SimplexOctaveGenerator ridgeNoise;
    private volatile SimplexOctaveGenerator valleyNoise;
    private volatile SimplexOctaveGenerator detailNoise;
    private volatile SimplexOctaveGenerator clearingNoise;

    public DrakesChunkGenerator(WorldProfile profile, DrakesBiomeProvider biomeProvider) {
        this.profile = profile;
        this.biomeProvider = biomeProvider;
        this.floraPopulator = new DrakesFloraPopulator(profile, biomeProvider);
    }

    @Nullable
    @Override
    public BiomeProvider getDefaultBiomeProvider(@Nonnull WorldInfo worldInfo) {
        return biomeProvider;
    }

    @Nonnull
    @Override
    public List<BlockPopulator> getDefaultPopulators(@Nonnull World world) {
        return List.of(floraPopulator);
    }

    @Override
    public void generateBedrock(@Nonnull WorldInfo worldInfo, @Nonnull Random random, int chunkX, int chunkZ, @Nonnull ChunkData chunkData) {
        int minY = worldInfo.getMinHeight();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                chunkData.setBlock(x, minY, z, Material.BEDROCK);
            }
        }
    }

    @Override
    public void generateNoise(@Nonnull WorldInfo worldInfo, @Nonnull Random random, int chunkX, int chunkZ, @Nonnull ChunkData chunkData) {
        ensureInit(worldInfo);

        int minY = worldInfo.getMinHeight();
        int maxY = worldInfo.getMaxHeight();
        int seaLevel = profile.seaLevel();

        for (int localX = 0; localX < 16; localX++) {
            int worldX = (chunkX << 4) + localX;
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldZ = (chunkZ << 4) + localZ;

                Biome biome = biomeProvider.getBiome(worldInfo, worldX, seaLevel, worldZ);
                int surfaceY = computeSurfaceY(worldX, worldZ, biome, minY, maxY);

                for (int y = minY + 1; y <= surfaceY; y++) {
                    Material block = resolveColumnMaterial(biome, y, surfaceY, worldX, worldZ);
                    chunkData.setBlock(localX, y, localZ, block);
                }

                if (surfaceY < seaLevel) {
                    for (int y = surfaceY + 1; y <= seaLevel; y++) {
                        chunkData.setBlock(localX, y, localZ, Material.WATER);
                    }
                }

                if (isSnowBiome(biome) && surfaceY >= seaLevel + 1 && surfaceY + 1 < maxY) {
                    chunkData.setBlock(localX, surfaceY + 1, localZ, Material.SNOW);
                }
            }
        }
    }

    private int computeSurfaceY(int worldX, int worldZ, Biome biome, int minY, int maxY) {
        double continental = continentalNoise.noise(worldX, worldZ, 0.45d, 0.5d, true);
        double mountain = Math.abs(mountainNoise.noise(worldX, worldZ, 0.55d, 0.5d, true));
        double ridges = Math.abs(ridgeNoise.noise(worldX, worldZ, 0.75d, 0.5d, true));
        double valleys = Math.abs(valleyNoise.noise(worldX, worldZ, 0.40d, 0.5d, true));
        double detail = detailNoise.noise(worldX, worldZ, 0.90d, 0.5d, true);
        double clearings = clearingNoise.noise(worldX, worldZ, 0.50d, 0.5d, true);

        double height = profile.baseHeight()
                + (continental * profile.hillAmplitude())
                + (mountain * profile.mountainAmplitude())
                + (ridges * (profile.mountainAmplitude() * 0.45d))
                - (valleys * profile.valleyDepth())
                + (detail * profile.detailAmplitude());

        if (isSwampBiome(biome)) {
            height -= 8.0d;
        } else if (isMountainBiome(biome)) {
            height += 12.0d;
        } else if (biome == Biome.BADLANDS || biome == Biome.WOODED_BADLANDS) {
            height += 4.0d;
        }

        if ((biome == Biome.MEADOW || biome == Biome.PLAINS) && clearings > profile.clearingThreshold()) {
            double flattened = profile.baseHeight() + 4.0d;
            height = lerp(height, flattened, profile.clearingFlattening());
        }

        int clamped = (int) Math.round(height);
        clamped = Math.max(minY + 6, clamped);
        clamped = Math.min(maxY - 8, clamped);
        return clamped;
    }

    private Material resolveColumnMaterial(Biome biome, int y, int surfaceY, int worldX, int worldZ) {
        int depthFromTop = surfaceY - y;
        if (depthFromTop == 0) {
            return topBlockForBiome(biome, worldX, worldZ);
        }
        if (depthFromTop <= 3) {
            return fillerBlockForBiome(biome);
        }
        if (biome == Biome.BADLANDS || biome == Biome.WOODED_BADLANDS || biome == Biome.ERODED_BADLANDS) {
            return badlandsStrata(y, worldX, worldZ);
        }
        return Material.STONE;
    }

    private Material topBlockForBiome(Biome biome, int worldX, int worldZ) {
        return switch (biome) {
            case SWAMP -> Material.GRASS_BLOCK;
            case MANGROVE_SWAMP -> Material.MUD;
            case BADLANDS, WOODED_BADLANDS, ERODED_BADLANDS -> Material.RED_SAND;
            case JAGGED_PEAKS, SNOWY_SLOPES, GROVE, SNOWY_TAIGA -> Material.SNOW_BLOCK;
            case OLD_GROWTH_PINE_TAIGA, TAIGA -> {
                if ((Math.abs(hash(worldX, worldZ)) & 1L) == 0L) {
                    yield Material.PODZOL;
                }
                yield Material.GRASS_BLOCK;
            }
            default -> Material.GRASS_BLOCK;
        };
    }

    private Material fillerBlockForBiome(Biome biome) {
        return switch (biome) {
            case MANGROVE_SWAMP -> Material.MUD;
            case BADLANDS, WOODED_BADLANDS, ERODED_BADLANDS -> Material.ORANGE_TERRACOTTA;
            default -> Material.DIRT;
        };
    }

    private Material badlandsStrata(int y, int worldX, int worldZ) {
        int selector = Math.floorMod(y + (int) (Math.abs(hash(worldX, worldZ)) % 9L), 6);
        return switch (selector) {
            case 0 -> Material.TERRACOTTA;
            case 1 -> Material.ORANGE_TERRACOTTA;
            case 2 -> Material.BROWN_TERRACOTTA;
            case 3 -> Material.RED_TERRACOTTA;
            case 4 -> Material.LIGHT_GRAY_TERRACOTTA;
            default -> Material.TERRACOTTA;
        };
    }

    private void ensureInit(WorldInfo worldInfo) {
        if (initialized) {
            return;
        }
        synchronized (this) {
            if (initialized) {
                return;
            }
            long seed = worldInfo.getSeed();

            this.continentalNoise = new SimplexOctaveGenerator(seed, 8);
            this.continentalNoise.setScale(0.00105d);

            this.mountainNoise = new SimplexOctaveGenerator(seed ^ 0x9E3779B97F4A7C15L, 8);
            this.mountainNoise.setScale(0.00175d);

            this.ridgeNoise = new SimplexOctaveGenerator(seed ^ 0xC2B2AE3D27D4EB4FL, 7);
            this.ridgeNoise.setScale(0.0022d);

            this.valleyNoise = new SimplexOctaveGenerator(seed ^ 0x165667B19E3779F9L, 6);
            this.valleyNoise.setScale(0.0013d);

            this.detailNoise = new SimplexOctaveGenerator(seed ^ 0x85EBCA77C2B2AE63L, 5);
            this.detailNoise.setScale(0.0050d);

            this.clearingNoise = new SimplexOctaveGenerator(seed ^ 0x27D4EB2F165667C5L, 6);
            this.clearingNoise.setScale(profile.clearingScale());

            this.initialized = true;
        }
    }

    private static boolean isSwampBiome(Biome biome) {
        return biome == Biome.SWAMP || biome == Biome.MANGROVE_SWAMP;
    }

    private static boolean isSnowBiome(Biome biome) {
        String name = biome.name().toUpperCase(Locale.ROOT);
        return name.contains("SNOW") || biome == Biome.JAGGED_PEAKS || biome == Biome.GROVE;
    }

    private static boolean isMountainBiome(Biome biome) {
        String name = biome.name().toUpperCase(Locale.ROOT);
        return name.contains("PEAKS")
                || name.contains("SLOPES")
                || biome == Biome.WINDSWEPT_HILLS
                || biome == Biome.GROVE;
    }

    private static double lerp(double from, double to, double factor) {
        return from + (to - from) * factor;
    }

    private static long hash(int x, int z) {
        long h = 1469598103934665603L;
        h ^= x;
        h *= 1099511628211L;
        h ^= z;
        h *= 1099511628211L;
        return h;
    }
}
