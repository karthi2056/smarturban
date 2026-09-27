package com.project.smart_urban.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.project.smart_urban.entity.KineticSector;

public interface KineticSectorRepository {

    KineticSector save(KineticSector sector);

    Optional<KineticSector> findById(UUID id);

    List<KineticSector> findAll();
}