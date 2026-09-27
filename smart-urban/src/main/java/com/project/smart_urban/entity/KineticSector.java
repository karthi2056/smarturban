package com.project.smart_urban.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class KineticSector {

    public static final BigDecimal CONVERSION_RATE_KWH_PER_STEP = new BigDecimal("0.000001");

    private final UUID id;
    private final String name;
    private final String stationZone;
    private final BigDecimal conversionRateKwhPerStep;
    private long totalSteps;
    private BigDecimal totalKwh;
    private Instant lastTelemetryAt;
    private final List<KineticTelemetry> telemetry = new ArrayList<>();

    public KineticSector(String name, String stationZone) {
        this(UUID.randomUUID(), name, stationZone);
    }

    private KineticSector(UUID id, String name, String stationZone) {
        this.id = id;
        this.name = name;
        this.stationZone = stationZone;
        this.conversionRateKwhPerStep = CONVERSION_RATE_KWH_PER_STEP;
        this.totalKwh = BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
    }

    public static KineticSector restore(UUID id, String name, String stationZone,
                                        List<KineticTelemetry> telemetry) {
        KineticSector sector = new KineticSector(id, name, stationZone);
        for (KineticTelemetry reading : telemetry) {
            sector.recordTelemetry(reading.steps(), reading.recordedAt());
        }
        return sector;
    }

    public synchronized void recordTelemetry(long steps, Instant recordedAt) {
        totalSteps += steps;
        totalKwh = totalKwh.add(conversionRateKwhPerStep.multiply(BigDecimal.valueOf(steps)))
                .setScale(6, RoundingMode.HALF_UP);
        lastTelemetryAt = recordedAt;
        telemetry.add(new KineticTelemetry(id, steps, recordedAt));
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getStationZone() {
        return stationZone;
    }

    public BigDecimal getConversionRateKwhPerStep() {
        return conversionRateKwhPerStep;
    }

    public long getTotalSteps() {
        return totalSteps;
    }

    public BigDecimal getTotalKwh() {
        return totalKwh;
    }

    public Instant getLastTelemetryAt() {
        return lastTelemetryAt;
    }

    public synchronized List<KineticTelemetry> getTelemetry() {
        return List.copyOf(telemetry);
    }
}