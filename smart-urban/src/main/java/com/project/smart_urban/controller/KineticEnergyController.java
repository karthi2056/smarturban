package com.project.smart_urban.controller;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.project.smart_urban.entity.KineticSector;
import com.project.smart_urban.service.KineticEnergyService;

@RestController
@RequestMapping("/api/kinetic")
public class KineticEnergyController {

    private final KineticEnergyService kineticEnergyService;

    public KineticEnergyController(KineticEnergyService kineticEnergyService) {
        this.kineticEnergyService = kineticEnergyService;
    }

    @PostMapping("/sectors")
    @ResponseStatus(HttpStatus.CREATED)
    public KineticSector createSector(@RequestBody CreateSectorRequest request) {
        return kineticEnergyService.createSector(request.name(), request.stationZone());
    }

    @GetMapping("/sectors")
    public List<KineticSector> listSectors() {
        return kineticEnergyService.listSectors();
    }

    @GetMapping("/sectors/{sectorId}")
    public KineticSector getSector(@PathVariable UUID sectorId) {
        return kineticEnergyService.getSector(sectorId);
    }

    @PostMapping("/telemetry")
    public KineticSector recordTelemetry(@RequestBody TelemetryRequest request) {
        return kineticEnergyService.recordTelemetry(request.sectorId(), request.steps(), request.recordedAt());
    }

    @GetMapping("/telemetry")
    public List<KineticEnergyService.TelemetryHistoryEntry> telemetryHistory(
            @RequestParam(value = "date", required = false) LocalDate date) {
        return kineticEnergyService.listTelemetryHistory(date);
    }

    @GetMapping("/reports/daily-generation")
    public List<KineticEnergyService.DailyGenerationReport> dailyGeneration(
            @RequestParam("date") LocalDate date) {
        return kineticEnergyService.buildDailyGenerationReport(date);
    }

    @GetMapping("/reports/dashboard")
    public KineticEnergyService.ReportsDashboard reportsDashboard() {
        return kineticEnergyService.buildReportsDashboard();
    }

    public record CreateSectorRequest(String name, String stationZone) {
    }

    public record TelemetryRequest(UUID sectorId, long steps, Instant recordedAt) {
    }

    public record ProfitAndLossRequest(BigDecimal energyPricePerKwh, BigDecimal transducerRepairs,
                                       BigDecimal inverterMaintenance) {
    }

    public record BudgetRequest(BigDecimal plannedEnergyKwh, BigDecimal plannedMaintenance) {
    }
}