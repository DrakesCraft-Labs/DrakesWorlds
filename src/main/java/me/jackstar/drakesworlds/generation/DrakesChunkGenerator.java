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
    private volatile SimplexOctaveGenerator caveNoiseA;
    private volatile SimplexOctaveGenerator caveNoiseB;
    private volatile SimplexOctaveGenerator caveNoiseC;

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
        int[][] surfaceHeights = new int[16][16];

        for (int localX = 0; localX < 16; localX++) {
            int worldX = (chunkX << 4) + localX;
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldZ = (chunkZ << 4) + localZ;

                Biome biome = biomeProvider.getBiome(worldInfo, worldX, seaLevel, worldZ);
                int surfaceY = computeSurfaceY(worldX, worldZ, biome, minY, maxY);
                surfaceHeights[localX][localZ] = surfaceY;

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

        carveCaves(chunkX, chunkZ, chunkData, minY, seaLevel, surfaceHeights);
        generateOres(worldInfo, chunkX, chunkZ, chunkData, minY, maxY);
    }

    private int computeSurfaceY(int worldX, int worldZ, Biome biome, int minY, int maxY) {
        // 3x3 smoothing to avoid needle-like terrain and abrupt checker patterns.
        double center = computeRawHeight(worldX, worldZ, biome) * 0.32d;
        double north = computeRawHeight(worldX, worldZ - 1, biome) * 0.12d;
        double south = computeRawHeight(worldX, worldZ + 1, biome) * 0.12d;
        double east = computeRawHeight(worldX + 1, worldZ, biome) * 0.12d;
        double west = computeRawHeight(worldX - 1, worldZ, biome) * 0.12d;
        double nw = computeRawHeight(worldX - 1, worldZ - 1, biome) * 0.08d;
        double ne = computeRawHeight(worldX + 1, worldZ - 1, biome) * 0.08d;
        double sw = computeRawHeight(worldX - 1, worldZ + 1, biome) * 0.08d;
        double se = computeRawHeight(worldX + 1, worldZ + 1, biome) * 0.08d;
        double clearings = clearingNoise.noise(worldX, worldZ, 0.50d, 0.5d, true);

        double height = center + north + south + east + west + nw + ne + sw + se;

        if ((isWoodlandBiome(biome) || biome == Biome.MEADOW || biome == Biome.PLAINS) && clearings > profile.clearingThreshold()) {
            double flattened = profile.baseHeight() + 3.0d;
            height = lerp(height, flattened, profile.clearingFlattening());
        }

        int clamped = (int) Math.round(height);
        clamped = Math.max(minY + 6, clamped);
        clamped = Math.min(maxY - 8, clamped);
        return clamped;
    }

    private double computeRawHeight(int worldX, int worldZ, Biome biome) {
        double continental = continentalNoise.noise(worldX, worldZ, 0.35d, 0.5d, true);
        double mountain = Math.max(0.0d, mountainNoise.noise(worldX, worldZ, 0.45d, 0.5d, true));
        mountain = Math.pow(mountain, 1.6d);

        double ridges = ridgeNoise.noise(worldX, worldZ, 0.55d, 0.5d, true);
        ridges = 1.0d - Math.abs(ridges);
        ridges = Math.pow(Math.max(0.0d, ridges), 1.75d);

        double valleys = Math.max(0.0d, valleyNoise.noise(worldX, worldZ, 0.30d, 0.5d, true));
        double detail = detailNoise.noise(worldX, worldZ, 0.35d, 0.5d, true) * 0.35d;

        double height = profile.baseHeight()
                + (continental * profile.hillAmplitude())
                + (mountain * (profile.mountainAmplitude() * 0.58d))
                + (ridges * (profile.mountainAmplitude() * 0.16d))
                - (valleys * (profile.valleyDepth() * 0.48d))
                + (detail * profile.detailAmplitude());

        if (isSwampBiome(biome)) {
            height -= 4.0d;
        } else if (isMountainBiome(biome)) {
            height += 4.0d;
        } else if (biome == Biome.BADLANDS || biome == Biome.WOODED_BADLANDS) {
            height += 2.0d;
        }
        return height;
    }

    private void carveCaves(int chunkX, int chunkZ, ChunkData chunkData, int minY, int seaLevel, int[][] surfaceHeights) {
        for (int localX = 0; localX < 16; localX++) {
            int worldX = (chunkX << 4) + localX;
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldZ = (chunkZ << 4) + localZ;
                int surfaceY = surfaceHeights[localX][localZ];
                int topLimit = Math.min(surfaceY - 10, seaLevel - 2);
                if (topLimit <= minY + 10) {
                    continue;
                }
                for (int y = minY + 8; y <= topLimit; y++) {
                    Material current = chunkData.getType(localX, y, localZ);
                    if (current == Material.AIR || current == Material.WATER || current == Material.BEDROCK) {
                        continue;
                    }
                    if (shouldCarveCave(worldX, y, worldZ, seaLevel)) {
                        chunkData.setBlock(localX, y, localZ, y <= (minY + 12) ? Material.LAVA : Material.AIR);
                    }
                }
            }
        }
    }

    private boolean shouldCarveCave(int worldX, int y, int worldZ, int seaLevel) {
        double nA = caveNoiseA.noise(worldX, y * 0.85d, worldZ, 0.70d, 0.5d, true);
        double nB = caveNoiseB.noise(worldX, y * 1.05d, worldZ, 0.60d, 0.5d, true);
        double nC = caveNoiseC.noise(worldX, y * 0.55d, worldZ, 0.55d, 0.5d, true);

        double chamber = Math.abs(nA);
        double tunnel = Math.abs(nB);
        double worm = Math.abs(nC);

        double depthBias = y < seaLevel ? 0.04d : -0.02d;
        boolean chamberCut = chamber > (0.63d + depthBias) && worm < 0.30d;
        boolean tunnelCut = chamber > (0.52d + depthBias) && tunnel < 0.14d;
        return chamberCut || tunnelCut;
    }

    private void generateOres(WorldInfo worldInfo, int chunkX, int chunkZ, ChunkData chunkData, int minY, int maxY) {
        Random oreRandom = new Random(worldInfo.getSeed()
                ^ (chunkX * 341873128712L)
                ^ (chunkZ * 132897987541L)
                ^ 0x9E3779B97F4A7C15L);

        placeOreVeins(oreRandom, chunkData, Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE, 22, 14, Math.max(minY + 8, 20), Math.min(maxY - 8, 190));
        placeOreVeins(oreRandom, chunkData, Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE, 20, 10, Math.max(minY + 6, -40), Math.min(maxY - 8, 130));
        placeOreVeins(oreRandom, chunkData, Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE, 18, 11, Math.max(minY + 6, -20), Math.min(maxY - 8, 110));
        placeOreVeins(oreRandom, chunkData, Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE, 14, 8, Math.max(minY + 6, -58), Math.min(maxY - 8, 16));
        placeOreVeins(oreRandom, chunkData, Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE, 8, 7, Math.max(minY + 6, -48), Math.min(maxY - 8, 32));
        placeOreVeins(oreRandom, chunkData, Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE, 9, 8, Math.max(minY + 6, -52), Math.min(maxY - 8, 36));
        placeOreVeins(oreRandom, chunkData, Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE, 8, 6, Math.max(minY + 6, -58), Math.min(maxY - 8, 20));
        placeOreVeins(oreRandom, chunkData, Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE, 4, 4, Math.max(minY + 6, -16), Math.min(maxY - 8, 180));
    }

    private void placeOreVeins(Random oreRandom, ChunkData chunkData, Material ore, Material deepOre,
                               int attempts, int veinSize, int minOreY, int maxOreY) {
        if (minOreY >= maxOreY) {
            return;
        }
        for (int attempt = 0; attempt < attempts; attempt++) {
            int originX = oreRandom.nextInt(16);
            int originZ = oreRandom.nextInt(16);
            int originY = minOreY + oreRandom.nextInt(Math.max(1, maxOreY - minOreY));

            for (int i = 0; i < veinSize; i++) {
                int x = originX + oreRandom.nextInt(5) - 2;
                int y = originY + oreRandom.nextInt(5) - 2;
                int z = originZ + oreRandom.nextInt(5) - 2;
                if (x < 0 || x > 15 || z < 0 || z > 15) {
                    continue;
                }
                Material current = chunkData.getType(x, y, z);
                if (!isReplaceableStone(current)) {
                    continue;
                }
                chunkData.setBlock(x, y, z, y <= 0 ? deepOre : ore);
            }
        }
    }

    private static boolean isReplaceableStone(Material type) {
        return type == Material.STONE
                || type == Material.DEEPSLATE
                || type == Material.TUFF
                || type == Material.ANDESITE
                || type == Material.DIORITE
                || type == Material.GRANITE;
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
            this.continentalNoise.setScale(0.00078d);

            this.mountainNoise = new SimplexOctaveGenerator(seed ^ 0x9E3779B97F4A7C15L, 8);
            this.mountainNoise.setScale(0.00098d);

            this.ridgeNoise = new SimplexOctaveGenerator(seed ^ 0xC2B2AE3D27D4EB4FL, 7);
            this.ridgeNoise.setScale(0.00108d);

            this.valleyNoise = new SimplexOctaveGenerator(seed ^ 0x165667B19E3779F9L, 6);
            this.valleyNoise.setScale(0.00074d);

            this.detailNoise = new SimplexOctaveGenerator(seed ^ 0x85EBCA77C2B2AE63L, 5);
            this.detailNoise.setScale(0.00135d);

            this.clearingNoise = new SimplexOctaveGenerator(seed ^ 0x27D4EB2F165667C5L, 6);
            this.clearingNoise.setScale(profile.clearingScale());

            this.caveNoiseA = new SimplexOctaveGenerator(seed ^ 0xBF58476D1CE4E5B9L, 4);
            this.caveNoiseA.setScale(0.018d);

            this.caveNoiseB = new SimplexOctaveGenerator(seed ^ 0x94D049BB133111EBL, 4);
            this.caveNoiseB.setScale(0.024d);

            this.caveNoiseC = new SimplexOctaveGenerator(seed ^ 0xD6E8FEB86659FD93L, 3);
            this.caveNoiseC.setScale(0.011d);

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

    private static boolean isWoodlandBiome(Biome biome) {
        String name = biome.name().toUpperCase(Locale.ROOT);
        return name.contains("FOREST")
                || name.contains("TAIGA")
                || biome == Biome.CHERRY_GROVE;
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
