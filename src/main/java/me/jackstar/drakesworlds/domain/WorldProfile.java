package me.jackstar.drakesworlds.domain;

import org.bukkit.block.Biome;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class WorldProfile {

    private final String id;
    private final int seaLevel;
    private final int baseHeight;
    private final double hillAmplitude;
    private final double mountainAmplitude;
    private final double valleyDepth;
    private final double detailAmplitude;
    private final double clearingScale;
    private final double clearingThreshold;
    private final double clearingFlattening;
    private final Map<Biome, Double> biomeWeights;
    private final DecorationSettings decorationSettings;

    public WorldProfile(
            String id,
            int seaLevel,
            int baseHeight,
            double hillAmplitude,
            double mountainAmplitude,
            double valleyDepth,
            double detailAmplitude,
            double clearingScale,
            double clearingThreshold,
            double clearingFlattening,
            Map<Biome, Double> biomeWeights,
            DecorationSettings decorationSettings
    ) {
        this.id = id;
        this.seaLevel = seaLevel;
        this.baseHeight = baseHeight;
        this.hillAmplitude = hillAmplitude;
        this.mountainAmplitude = mountainAmplitude;
        this.valleyDepth = valleyDepth;
        this.detailAmplitude = detailAmplitude;
        this.clearingScale = clearingScale;
        this.clearingThreshold = clearingThreshold;
        this.clearingFlattening = clearingFlattening;
        this.biomeWeights = Collections.unmodifiableMap(new EnumMap<>(biomeWeights));
        this.decorationSettings = decorationSettings;
    }

    public String id() {
        return id;
    }

    public int seaLevel() {
        return seaLevel;
    }

    public int baseHeight() {
        return baseHeight;
    }

    public double hillAmplitude() {
        return hillAmplitude;
    }

    public double mountainAmplitude() {
        return mountainAmplitude;
    }

    public double valleyDepth() {
        return valleyDepth;
    }

    public double detailAmplitude() {
        return detailAmplitude;
    }

    public double clearingScale() {
        return clearingScale;
    }

    public double clearingThreshold() {
        return clearingThreshold;
    }

    public double clearingFlattening() {
        return clearingFlattening;
    }

    public Map<Biome, Double> biomeWeights() {
        return biomeWeights;
    }

    public DecorationSettings decorationSettings() {
        return decorationSettings;
    }

    public double weightFor(Biome biome) {
        return biomeWeights.getOrDefault(biome, 0.0d);
    }
}

