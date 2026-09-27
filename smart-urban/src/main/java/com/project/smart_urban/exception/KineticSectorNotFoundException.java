package com.project.smart_urban.exception;

import java.util.UUID;

public class KineticSectorNotFoundException extends RuntimeException {

    public KineticSectorNotFoundException(UUID sectorId) {
        super("Kinetic sector not found: " + sectorId);
    }
}