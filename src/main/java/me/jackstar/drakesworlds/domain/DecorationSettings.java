package me.jackstar.drakesworlds.domain;

public record DecorationSettings(
        int baseTreesPerChunk,
        double taigaTreeMultiplier,
        double forestTreeMultiplier,
        double swampTreeMultiplier,
        double deadTreeChance,
        double fallenLogChance,
        double bushChance,
        boolean enableCustomPines,
        int pineMinHeight,
        int pineMaxHeight
) {
}

