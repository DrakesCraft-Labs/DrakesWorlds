package me.jackstar.drakesworlds.generation;

import me.jackstar.drakesworlds.domain.DecorationSettings;
import me.jackstar.drakesworlds.domain.WorldProfile;
import org.bukkit.Axis;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Orientable;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;

import javax.annotation.Nonnull;
import java.util.Locale;
import java.util.Random;

public final class DrakesFloraPopulator extends BlockPopulator {

    private final WorldProfile profile;
    private final DrakesBiomeProvider biomeProvider;

    public DrakesFloraPopulator(WorldProfile profile, DrakesBiomeProvider biomeProvider) {
        this.profile = profile;
        this.biomeProvider = biomeProvider;
    }

    @Override
    public void populate(@Nonnull WorldInfo worldInfo, @Nonnull Random random, int chunkX, int chunkZ, @Nonnull LimitedRegion region) {
        DecorationSettings deco = profile.decorationSettings();
        int startX = region.getCenterChunkX() << 4;
        int startZ = region.getCenterChunkZ() << 4;
        int minY = worldInfo.getMinHeight();
        int maxY = worldInfo.getMaxHeight() - 1;

        int attempts = Math.max(4, deco.baseTreesPerChunk());
        for (int i = 0; i < attempts; i++) {
            int x = startX + random.nextInt(16);
            int z = startZ + random.nextInt(16);
            int y = findSurfaceY(region, x, z, minY, maxY);
            if (y <= minY) {
                continue;
            }

            Biome biome = biomeProvider.getBiome(worldInfo, x, y, z);
            if (!canGrowAtBiome(biome)) {
                continue;
            }

            double biomeMultiplier = biomeTreeMultiplier(biome, deco);
            if (random.nextDouble() > Math.min(0.97d, 0.48d * biomeMultiplier)) {
                continue;
            }

            if (isClearingBiome(biome) && random.nextDouble() < 0.75d) {
                if (random.nextDouble() < deco.bushChance()) {
                    placeBush(region, random, x, y + 1, z);
                }
                continue;
            }

            if (shouldSpawnDeadTree(random, biome, deco)) {
                placeDeadTree(region, random, x, y + 1, z);
            } else if (deco.enableCustomPines() && isPineBiome(biome)) {
                placePine(region, random, x, y + 1, z, deco.pineMinHeight(), deco.pineMaxHeight());
            } else {
                placeRoundTree(region, random, x, y + 1, z, biome);
            }
        }

        // Extra pass for fallen logs and undergrowth.
        if (random.nextDouble() < deco.fallenLogChance()) {
            int count = 1 + random.nextInt(3);
            for (int i = 0; i < count; i++) {
                int x = startX + random.nextInt(16);
                int z = startZ + random.nextInt(16);
                int y = findSurfaceY(region, x, z, minY, maxY);
                if (y > minY) {
                    placeFallenLog(region, random, x, y + 1, z);
                }
            }
        }

        if (random.nextDouble() < deco.bushChance()) {
            int bushCount = 3 + random.nextInt(7);
            for (int i = 0; i < bushCount; i++) {
                int x = startX + random.nextInt(16);
                int z = startZ + random.nextInt(16);
                int y = findSurfaceY(region, x, z, minY, maxY);
                if (y > minY) {
                    placeBush(region, random, x, y + 1, z);
                }
            }
        }
    }

    private int findSurfaceY(LimitedRegion region, int x, int z, int minY, int maxY) {
        for (int y = maxY; y > minY + 1; y--) {
            Material floor = region.getType(x, y, z);
            Material above = region.getType(x, y + 1, z);
            if (isGround(floor) && above.isAir()) {
                return y;
            }
        }
        return minY;
    }

    private void placePine(LimitedRegion region, Random random, int baseX, int baseY, int baseZ, int minHeight, int maxHeight) {
        int height = minHeight + random.nextInt(Math.max(1, (maxHeight - minHeight) + 1));
        int topY = baseY + height;

        for (int y = baseY; y <= topY; y++) {
            setIfReplaceable(region, baseX, y, baseZ, Material.SPRUCE_LOG);
        }

        int crownStart = topY - Math.max(4, height / 3);
        for (int y = crownStart; y <= topY; y++) {
            int distanceFromTop = topY - y;
            int radius = Math.max(1, 3 - (distanceFromTop / 2));
            fillLeavesRing(region, baseX, y, baseZ, radius, Material.SPRUCE_LEAVES, random);
        }

        if (random.nextDouble() < 0.7d) {
            patchGround(region, random, baseX, baseY - 1, baseZ, Material.PODZOL, 2);
        }
    }

    private void placeRoundTree(LimitedRegion region, Random random, int baseX, int baseY, int baseZ, Biome biome) {
        int trunk = 4 + random.nextInt(3);
        Material log = isPineBiome(biome) ? Material.SPRUCE_LOG : Material.OAK_LOG;
        Material leaves = selectLeavesMaterial(biome);

        for (int y = 0; y < trunk; y++) {
            setIfReplaceable(region, baseX, baseY + y, baseZ, log);
        }

        int topY = baseY + trunk;
        fillLeavesRing(region, baseX, topY, baseZ, 2, leaves, random);
        fillLeavesRing(region, baseX, topY + 1, baseZ, 2, leaves, random);
        fillLeavesRing(region, baseX, topY + 2, baseZ, 1, leaves, random);
        setIfReplaceable(region, baseX, topY + 3, baseZ, leaves);
    }

    private void placeDeadTree(LimitedRegion region, Random random, int baseX, int baseY, int baseZ) {
        int height = 4 + random.nextInt(4);
        for (int y = 0; y < height; y++) {
            setIfReplaceable(region, baseX, baseY + y, baseZ, Material.DARK_OAK_LOG);
        }

        if (height > 4) {
            if (random.nextBoolean()) {
                setIfReplaceable(region, baseX + 1, baseY + height - 2, baseZ, Material.DARK_OAK_LOG);
            } else {
                setIfReplaceable(region, baseX, baseY + height - 2, baseZ + 1, Material.DARK_OAK_LOG);
            }
        }
    }

    private void placeFallenLog(LimitedRegion region, Random random, int x, int y, int z) {
        Material log = random.nextBoolean() ? Material.SPRUCE_LOG : Material.OAK_LOG;
        Axis axis = random.nextBoolean() ? Axis.X : Axis.Z;
        int length = 2 + random.nextInt(4);

        for (int i = 0; i < length; i++) {
            int dx = axis == Axis.X ? x + i : x;
            int dz = axis == Axis.Z ? z + i : z;
            if (region.getType(dx, y, dz).isAir()) {
                setAxisLog(region, dx, y, dz, log, axis);
            }
        }
    }

    private void placeBush(LimitedRegion region, Random random, int x, int y, int z) {
        Material leaves = random.nextBoolean() ? Material.SPRUCE_LEAVES : Material.OAK_LEAVES;
        setIfReplaceable(region, x, y, z, leaves);
        if (random.nextBoolean()) {
            setIfReplaceable(region, x + 1, y, z, leaves);
        }
        if (random.nextBoolean()) {
            setIfReplaceable(region, x - 1, y, z, leaves);
        }
        if (random.nextBoolean()) {
            setIfReplaceable(region, x, y, z + 1, leaves);
        }
        if (random.nextBoolean()) {
            setIfReplaceable(region, x, y, z - 1, leaves);
        }
    }

    private void fillLeavesRing(LimitedRegion region, int centerX, int y, int centerZ, int radius, Material leaves, Random random) {
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                int dx = x - centerX;
                int dz = z - centerZ;
                if ((dx * dx) + (dz * dz) > (radius * radius)) {
                    continue;
                }
                if (random.nextDouble() < 0.92d) {
                    setIfReplaceable(region, x, y, z, leaves);
                }
            }
        }
    }

    private void patchGround(LimitedRegion region, Random random, int centerX, int y, int centerZ, Material material, int radius) {
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                int dx = x - centerX;
                int dz = z - centerZ;
                if ((dx * dx) + (dz * dz) > (radius * radius)) {
                    continue;
                }
                if (random.nextDouble() < 0.75d && isGround(region.getType(x, y, z))) {
                    region.setType(x, y, z, material);
                }
            }
        }
    }

    private void setIfReplaceable(LimitedRegion region, int x, int y, int z, Material material) {
        Material current = region.getType(x, y, z);
        if (current.isAir() || current == Material.SHORT_GRASS || current == Material.TALL_GRASS || current == Material.FERN) {
            region.setType(x, y, z, material);
        }
    }

    private void setAxisLog(LimitedRegion region, int x, int y, int z, Material logType, Axis axis) {
        BlockData blockData = Bukkit.createBlockData(logType);
        if (blockData instanceof Orientable orientable) {
            orientable.setAxis(axis);
        }
        region.setBlockData(x, y, z, blockData);
    }

    private static boolean isGround(Material material) {
        return switch (material) {
            case GRASS_BLOCK, DIRT, COARSE_DIRT, PODZOL, ROOTED_DIRT, MUD, SAND, RED_SAND, SNOW_BLOCK, TERRACOTTA,
                    ORANGE_TERRACOTTA, BROWN_TERRACOTTA, RED_TERRACOTTA, STONE -> true;
            default -> false;
        };
    }

    private static boolean canGrowAtBiome(Biome biome) {
        if (biome == Biome.JAGGED_PEAKS || biome == Biome.STONY_PEAKS) {
            return false;
        }
        String name = biome.name().toUpperCase(Locale.ROOT);
        return name.contains("FOREST")
                || name.contains("TAIGA")
                || name.contains("SWAMP")
                || name.contains("GROVE")
                || name.contains("PLAINS")
                || name.contains("MEADOW")
                || biome == Biome.CHERRY_GROVE
                || biome == Biome.WOODED_BADLANDS;
    }

    private static boolean isPineBiome(Biome biome) {
        String name = biome.name().toUpperCase(Locale.ROOT);
        return name.contains("TAIGA") || biome == Biome.GROVE;
    }

    private static boolean isClearingBiome(Biome biome) {
        return biome == Biome.MEADOW || biome == Biome.PLAINS;
    }

    private static Material selectLeavesMaterial(Biome biome) {
        return switch (biome) {
            case CHERRY_GROVE -> Material.CHERRY_LEAVES;
            case MANGROVE_SWAMP -> Material.MANGROVE_LEAVES;
            default -> isPineBiome(biome) ? Material.SPRUCE_LEAVES : Material.OAK_LEAVES;
        };
    }

    private static boolean shouldSpawnDeadTree(Random random, Biome biome, DecorationSettings settings) {
        double chance = settings.deadTreeChance();
        if (biome == Biome.SWAMP || biome == Biome.MANGROVE_SWAMP || biome == Biome.WOODED_BADLANDS) {
            chance += 0.04d;
        }
        return random.nextDouble() < chance;
    }

    private static double biomeTreeMultiplier(Biome biome, DecorationSettings settings) {
        if (isPineBiome(biome)) {
            return settings.taigaTreeMultiplier();
        }
        if (biome == Biome.FOREST || biome == Biome.DARK_FOREST || biome == Biome.CHERRY_GROVE) {
            return settings.forestTreeMultiplier();
        }
        if (biome == Biome.SWAMP || biome == Biome.MANGROVE_SWAMP) {
            return settings.swampTreeMultiplier();
        }
        if (isClearingBiome(biome)) {
            return 0.35d;
        }
        return 1.0d;
    }
}

