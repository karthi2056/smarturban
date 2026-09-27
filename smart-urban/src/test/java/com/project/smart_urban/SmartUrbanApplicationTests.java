package com.project.smart_urban;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.project.smart_urban.entity.KineticSector;
import com.project.smart_urban.repository.InMemoryKineticSectorRepository;
import com.project.smart_urban.service.KineticEnergyService;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "app.storage.file=target/test-kinetic-data.json")
class SmartUrbanApplicationTests {

	@Autowired
	private KineticEnergyService kineticEnergyService;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void contextLoads() {
	}

	@Test
	void dailyGenerationAggregatesTelemetryByUtcDate() {
		LocalDate date = LocalDate.now(ZoneId.systemDefault()).plusDays(3000 + Math.floorMod(System.nanoTime(), 10000));
		Instant dayStart = date.atStartOfDay(ZoneId.systemDefault()).toInstant();
		KineticSector sector = kineticEnergyService.createSector("Daily test", "Test zone");
		kineticEnergyService.recordTelemetry(sector.getId(), 2, dayStart.plusSeconds(10 * 60 * 60));
		kineticEnergyService.recordTelemetry(sector.getId(), 3, dayStart.plusSeconds(23 * 60 * 60 + 59 * 60));
		kineticEnergyService.recordTelemetry(sector.getId(), 4, dayStart.plusSeconds(24 * 60 * 60 + 60));

		List<KineticEnergyService.DailyGenerationReport> report =
			kineticEnergyService.buildDailyGenerationReport(date);

		assertEquals(1, report.size());
		assertEquals(5, report.get(0).steps());
		assertEquals(new BigDecimal("0.000005"), report.get(0).generatedKwh());
		assertEquals(new BigDecimal("0.000208"), report.get(0).averagePowerWatts());
		assertEquals(2, kineticEnergyService.listTelemetryHistory(date).size());
	}

	@Test
	void reloadsSavedSectorsAndTelemetryAfterRestart(@TempDir Path temporaryDirectory) {
		Path storageFile = temporaryDirectory.resolve("kinetic-data.json");
		KineticEnergyService firstRun = new KineticEnergyService(
				new InMemoryKineticSectorRepository(objectMapper, storageFile));
		KineticSector created = firstRun.createSector("Persistent test", "Archive zone");
		Instant recordedAt = Instant.parse("2026-09-26T12:00:00Z");
		firstRun.recordTelemetry(created.getId(), 125, recordedAt);

		InMemoryKineticSectorRepository afterRestart = new InMemoryKineticSectorRepository(objectMapper, storageFile);
		KineticSector restored = afterRestart.findById(created.getId()).orElseThrow();

		assertEquals(created.getId(), restored.getId());
		assertEquals(125, restored.getTotalSteps());
		assertEquals(new BigDecimal("0.000125"), restored.getTotalKwh());
		assertEquals(1, restored.getTelemetry().size());
		assertEquals(recordedAt, restored.getLastTelemetryAt());
	}

	@Test
	void buildsDailyMonthlyAndSectorReportsFromTelemetry(@TempDir Path temporaryDirectory) {
		KineticEnergyService service = new KineticEnergyService(new InMemoryKineticSectorRepository(objectMapper,
				temporaryDirectory.resolve("reports.json")));
		KineticSector sector = service.createSector("Report test", "Report zone");
		LocalDate today = LocalDate.now(ZoneId.systemDefault());
		Instant startOfToday = today.atStartOfDay(ZoneId.systemDefault()).toInstant();
		service.recordTelemetry(sector.getId(), 10, startOfToday.plusSeconds(60));
		service.recordTelemetry(sector.getId(), 20, startOfToday.minusSeconds(24 * 60 * 60));

		KineticEnergyService.ReportsDashboard reports = service.buildReportsDashboard();

		assertEquals(today, reports.reportDate());
		assertEquals(10, reports.todaySteps());
		assertEquals(new BigDecimal("0.000010"), reports.todayGeneratedKwh());
		assertEquals(30, reports.totalSteps());
		assertEquals(new BigDecimal("0.000030"), reports.totalGeneratedKwh());
		assertEquals(1, reports.todayBySector().size());
		assertEquals(2, reports.dailyReports().size());
		assertEquals(1, reports.monthlyReports().size());
		assertEquals(YearMonth.from(today).toString(), reports.monthlyReports().get(0).month());
		assertEquals(30, reports.sectorReports().get(0).totalSteps());
	}

}
