package com.project.smart_urban.entity;

import java.time.Instant;
import java.util.UUID;

public record KineticTelemetry(UUID sectorId, long steps, Instant recordedAt) {
}