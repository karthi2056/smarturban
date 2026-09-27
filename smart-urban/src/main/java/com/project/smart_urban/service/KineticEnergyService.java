package com.project.smart_urban.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.project.smart_urban.entity.KineticSector;
import com.project.smart_urban.entity.KineticTelemetry;
import com.project.smart_urban.exception.InvalidKineticRequestException;
import com.project.smart_urban.exception.KineticSectorNotFoundException;
import com.project.smart_urban.repository.KineticSectorRepository;

@Service
public class KineticEnergyService {

    private final KineticSectorRepository sectorRepository;

    public KineticEnergyService(KineticSectorRepository sectorRepository) {
        this.sectorRepository = sectorRepository;
    }

    public KineticSector createSector(String name, String stationZone) {
        requireText(name, "name");
        requireText(stationZone, "stationZone");
        return sectorRepository.save(new KineticSector(name, stationZone));
    }

    public List<KineticSector> listSectors() {
        List<KineticSector> sectors = sectorRepository.findAll();
        sectors.sort((left, right) -> {
            int zoneComparison = left.getStationZone().compareTo(right.getStationZone());
            return zoneComparison != 0 ? zoneComparison : left.getName().compareTo(right.getName());
        });
        return sectors;
    }

    public KineticSector recordTelemetry(UUID sectorId, long steps, Instant recordedAt) {
        if (steps <= 0) {
            throw new InvalidKineticRequestException("steps must be greater than zero");
        }
        if (recordedAt == null) {
            throw new InvalidKineticRequestException("recordedAt is required");
        }
        KineticSector sector = sectorRepository.findById(sectorId)
                .orElseThrow(() -> new KineticSectorNotFoundException(sectorId));
        sector.recordTelemetry(steps, recordedAt);
        return sectorRepository.save(sector);
    }

    public KineticSector getSector(UUID sectorId) {
        return sectorRepository.findById(sectorId)
                .orElseThrow(() -> new KineticSectorNotFoundException(sectorId));
    }

    public List<TelemetryHistoryEntry> listTelemetryHistory(LocalDate date) {
        List<TelemetryHistoryEntry> history = new ArrayList<>();
        for (KineticSector sector : sectorRepository.findAll()) {
            for (KineticTelemetry telemetry : sector.getTelemetry()) {
                if (date != null && !telemetry.recordedAt().atZone(ZoneId.systemDefault()).toLocalDate().equals(date)) {
                    continue;
                }
                BigDecimal generatedKwh = sector.getConversionRateKwhPerStep()
                        .multiply(BigDecimal.valueOf(telemetry.steps())).setScale(6, RoundingMode.HALF_UP);
                history.add(new TelemetryHistoryEntry(sector.getId(), sector.getName(), sector.getStationZone(),
                        telemetry.steps(), telemetry.recordedAt(), generatedKwh));
            }
        }
        history.sort(Comparator.comparing(TelemetryHistoryEntry::recordedAt).reversed()
                .thenComparing(TelemetryHistoryEntry::sectorName));
        return history;
    }

    public List<DailyGenerationReport> buildDailyGenerationReport(LocalDate date) {
        if (date == null) {
            throw new InvalidKineticRequestException("date is required");
        }
        List<DailyGenerationReport> reports = new ArrayList<>();
        for (KineticSector sector : sectorRepository.findAll()) {
            long steps = 0;
            for (KineticTelemetry telemetry : sector.getTelemetry()) {
                if (telemetry.recordedAt().atZone(ZoneId.systemDefault()).toLocalDate().equals(date)) {
                    steps += telemetry.steps();
                }
            }
            if (steps > 0) {
                BigDecimal generatedKwh = kwhForSteps(steps);
                BigDecimal averagePowerWatts = averagePower(generatedKwh);
                reports.add(new DailyGenerationReport(date, sector.getId(), sector.getName(),
                    sector.getStationZone(), steps, generatedKwh, averagePowerWatts));
            }
        }
        reports.sort((left, right) -> {
            int zoneComparison = left.stationZone().compareTo(right.stationZone());
            return zoneComparison != 0 ? zoneComparison : left.sectorName().compareTo(right.sectorName());
        });
        return reports;
    }

        public ReportsDashboard buildReportsDashboard() {
        ZoneId systemZone = ZoneId.systemDefault();
        LocalDate currentDate = LocalDate.now(systemZone);
        YearMonth currentMonth = YearMonth.from(currentDate);
        Map<LocalDate, ReportTotals> dailyTotals = new TreeMap<>(Comparator.reverseOrder());
        Map<YearMonth, ReportTotals> monthlyTotals = new TreeMap<>(Comparator.reverseOrder());
        List<SectorGenerationSummary> sectorSummaries = new ArrayList<>();
        long totalSteps = 0;
        BigDecimal totalKwh = BigDecimal.ZERO;

        for (KineticSector sector : sectorRepository.findAll()) {
            totalSteps += sector.getTotalSteps();
            totalKwh = totalKwh.add(sector.getTotalKwh());
            sectorSummaries.add(new SectorGenerationSummary(sector.getId(), sector.getName(),
                sector.getStationZone(), sector.getTotalSteps(), sector.getTotalKwh()));
            for (KineticTelemetry telemetry : sector.getTelemetry()) {
            LocalDate date = telemetry.recordedAt().atZone(systemZone).toLocalDate();
            dailyTotals.computeIfAbsent(date, ignored -> new ReportTotals()).add(telemetry.steps());
            monthlyTotals.computeIfAbsent(YearMonth.from(date), ignored -> new ReportTotals())
                .add(telemetry.steps());
            }
        }

        sectorSummaries.sort(Comparator.comparing(SectorGenerationSummary::stationZone)
            .thenComparing(SectorGenerationSummary::sectorName));
        List<DailyGenerationSummary> dailyReports = dailyTotals.entrySet().stream()
            .map(entry -> {
                BigDecimal generatedKwh = kwhForSteps(entry.getValue().steps);
                return new DailyGenerationSummary(entry.getKey(), entry.getValue().steps, generatedKwh,
                    averagePower(generatedKwh));
            })
            .toList();
        List<MonthlyGenerationSummary> monthlyReports = monthlyTotals.entrySet().stream()
            .map(entry -> new MonthlyGenerationSummary(entry.getKey().toString(), entry.getValue().steps,
                kwhForSteps(entry.getValue().steps)))
            .toList();
        ReportTotals todayTotals = dailyTotals.getOrDefault(currentDate, new ReportTotals());
        ReportTotals monthTotals = monthlyTotals.getOrDefault(currentMonth, new ReportTotals());

        return new ReportsDashboard(currentDate, todayTotals.steps, kwhForSteps(todayTotals.steps),
            monthTotals.steps, kwhForSteps(monthTotals.steps), totalSteps,
            totalKwh.setScale(6, RoundingMode.HALF_UP), buildDailyGenerationReport(currentDate),
            dailyReports, monthlyReports, sectorSummaries);
        }

        private BigDecimal kwhForSteps(long steps) {
        return KineticSector.CONVERSION_RATE_KWH_PER_STEP.multiply(BigDecimal.valueOf(steps))
            .setScale(6, RoundingMode.HALF_UP);
        }

        private BigDecimal averagePower(BigDecimal generatedKwh) {
        return generatedKwh.multiply(BigDecimal.valueOf(1000))
            .divide(BigDecimal.valueOf(24), 6, RoundingMode.HALF_UP);
        }

    private void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new InvalidKineticRequestException(fieldName + " is required");
        }
    }

    public record DailyGenerationReport(LocalDate date, UUID sectorId, String sectorName,
                                       String stationZone, long steps, BigDecimal generatedKwh,
                                       BigDecimal averagePowerWatts) {
    }

    public record TelemetryHistoryEntry(UUID sectorId, String sectorName, String stationZone, long steps,
                                        Instant recordedAt, BigDecimal generatedKwh) {
    }

    public record ReportsDashboard(LocalDate reportDate, long todaySteps, BigDecimal todayGeneratedKwh,
                                   long monthSteps, BigDecimal monthGeneratedKwh, long totalSteps,
                                   BigDecimal totalGeneratedKwh, List<DailyGenerationReport> todayBySector,
                                   List<DailyGenerationSummary> dailyReports,
                                   List<MonthlyGenerationSummary> monthlyReports,
                                   List<SectorGenerationSummary> sectorReports) {
    }

    public record DailyGenerationSummary(LocalDate date, long steps, BigDecimal generatedKwh,
                                         BigDecimal averagePowerWatts) {
    }

    public record MonthlyGenerationSummary(String month, long steps, BigDecimal generatedKwh) {
    }

    public record SectorGenerationSummary(UUID sectorId, String sectorName, String stationZone,
                                           long totalSteps, BigDecimal totalGeneratedKwh) {
    }

    private static final class ReportTotals {
        private long steps;

        private void add(long steps) {
            this.steps += steps;
        }
    }
}