package com.project.smart_urban.repository;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.project.smart_urban.entity.KineticSector;
import com.project.smart_urban.entity.KineticTelemetry;

import tools.jackson.databind.ObjectMapper;

@Repository
public class InMemoryKineticSectorRepository implements KineticSectorRepository {

    private final ConcurrentMap<UUID, KineticSector> sectors = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final Path storageFile;

    @Autowired
    public InMemoryKineticSectorRepository(ObjectMapper objectMapper,
            @Value("${app.storage.file:data/kinetic-data.json}") String storageFile) {
        this(objectMapper, Path.of(storageFile));
    }

    public InMemoryKineticSectorRepository(ObjectMapper objectMapper, Path storageFile) {
        this.objectMapper = objectMapper;
        this.storageFile = storageFile;
        load();
    }

    private void load() {
        if (!Files.exists(storageFile)) {
            return;
        }
        try {
            PersistenceSnapshot snapshot = objectMapper.readValue(storageFile.toFile(), PersistenceSnapshot.class);
            if (snapshot != null && snapshot.sectors() != null) {
                for (PersistedSector persistedSector : snapshot.sectors()) {
                    KineticSector sector = KineticSector.restore(persistedSector.id(), persistedSector.name(),
                                persistedSector.stationZone(),
                                persistedSector.telemetry() == null ? List.of() : persistedSector.telemetry().stream()
                                    .map(reading -> new KineticTelemetry(persistedSector.id(), reading.steps(),
                                        Instant.parse(reading.recordedAt())))
                                    .toList());
                    sectors.put(sector.getId(), sector);
                }
            }
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Could not load kinetic data from " + storageFile, exception);
        }
    }

    @Override
    public synchronized KineticSector save(KineticSector sector) {
        List<KineticSector> updatedSectors = new ArrayList<>(sectors.values());
        updatedSectors.removeIf(existing -> existing.getId().equals(sector.getId()));
        updatedSectors.add(sector);
        persist(updatedSectors);
        sectors.put(sector.getId(), sector);
        return sector;
    }

    private void persist(List<KineticSector> currentSectors) {
        Path absoluteFile = storageFile.toAbsolutePath();
        Path temporaryFile = null;
        try {
            Files.createDirectories(absoluteFile.getParent());
            temporaryFile = Files.createTempFile(absoluteFile.getParent(), "kinetic-data-", ".tmp");
            List<PersistedSector> persistedSectors = currentSectors.stream()
                    .map(sector -> new PersistedSector(sector.getId(), sector.getName(), sector.getStationZone(),
                            sector.getConversionRateKwhPerStep(), sector.getTelemetry().stream()
                                .map(reading -> new PersistedTelemetry(reading.steps(),
                                    reading.recordedAt().toString()))
                                .toList()))
                    .toList();
            objectMapper.writeValue(temporaryFile.toFile(), new PersistenceSnapshot(persistedSectors));
            try {
                Files.move(temporaryFile, absoluteFile, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporaryFile, absoluteFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save kinetic data to " + absoluteFile, exception);
        } finally {
            if (temporaryFile != null) {
                try {
                    Files.deleteIfExists(temporaryFile);
                } catch (IOException ignored) {
                }
            }
        }
    }

    @Override
    public Optional<KineticSector> findById(UUID id) {
        return Optional.ofNullable(sectors.get(id));
    }

    @Override
    public List<KineticSector> findAll() {
        return new ArrayList<>(sectors.values());
    }

    public record PersistenceSnapshot(List<PersistedSector> sectors) {
    }

    public record PersistedSector(UUID id, String name, String stationZone, BigDecimal conversionRateKwhPerStep,
                                  List<PersistedTelemetry> telemetry) {
    }

    public record PersistedTelemetry(long steps, String recordedAt) {
    }
}