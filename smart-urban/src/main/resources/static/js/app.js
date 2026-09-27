const API_ROOT = '/api/kinetic';
const numberFormat = new Intl.NumberFormat(undefined, { maximumFractionDigits: 0 });
const decimalFormat = new Intl.NumberFormat(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const kwhFormat = new Intl.NumberFormat(undefined, { minimumFractionDigits: 6, maximumFractionDigits: 6 });
const wattFormat = new Intl.NumberFormat(undefined, { minimumFractionDigits: 3, maximumFractionDigits: 3 });
let sectors = [];
let toastTimer;

const byId = (id) => document.getElementById(id);

async function api(path, options = {}) {
  const response = await fetch(`${API_ROOT}${path}`, {
    ...options,
    headers: { 'Content-Type': 'application/json', ...options.headers }
  });
  const body = await response.json().catch(() => null);
  if (!response.ok) {
    throw new Error(body?.message || `Request failed (${response.status})`);
  }
  return body;
}

function showToast(message, isError = false) {
  const toast = byId('toast');
  toast.textContent = message;
  toast.classList.toggle('is-error', isError);
  toast.classList.add('is-visible');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => toast.classList.remove('is-visible'), 3200);
}

function setConnection(online) {
  byId('connection-dot').className = `status-dot ${online ? 'is-online' : 'is-offline'}`;
  byId('connection-label').textContent = online ? 'API connected' : 'API unavailable';
}

function makeCell(value, className = '') {
  const cell = document.createElement('td');
  cell.textContent = value;
  if (className) cell.className = className;
  return cell;
}

function makeEmptyRow(columnCount, message) {
  const row = document.createElement('tr');
  const cell = makeCell(message, 'empty-cell');
  cell.colSpan = columnCount;
  row.append(cell);
  return row;
}

function fillSectorSelect(selectId) {
  const select = byId(selectId);
  const previousValue = select.value;
  const hasSectors = sectors.length > 0;
  select.replaceChildren(new Option(hasSectors ? 'Select a sector' : 'No sectors yet — create one first', ''));
  for (const sector of sectors) {
    select.add(new Option(`${sector.name} · ${sector.stationZone}`, sector.id));
  }
  if (sectors.some((sector) => sector.id === previousValue)) select.value = previousValue;
  select.disabled = !hasSectors;
  byId(`${selectId}-help`).hidden = hasSectors;
}

function renderSectors() {
  for (const id of ['telemetry-sector', 'telemetry-sector-full']) fillSectorSelect(id);
  byId('metric-sectors').textContent = numberFormat.format(sectors.length);
  byId('sector-count-label').textContent = `${sectors.length} ${sectors.length === 1 ? 'sector' : 'sectors'}`;
  byId('telemetry-sector-count').textContent = `${sectors.length} ${sectors.length === 1 ? 'sector' : 'sectors'}`;

  const summary = byId('sector-summary');
  summary.replaceChildren();
  if (sectors.length === 0) {
    const empty = document.createElement('p');
    empty.className = 'empty-message';
    empty.textContent = 'No sectors registered yet. Add the first floor area to begin tracking.';
    summary.append(empty);
  } else {
    for (const sector of sectors.slice(0, 6)) {
      const item = document.createElement('article');
      item.className = 'sector-item';
      const name = document.createElement('strong');
      name.textContent = sector.name;
      const zone = document.createElement('span');
      zone.textContent = sector.stationZone;
      const yieldValue = document.createElement('code');
      yieldValue.textContent = `${kwhFormat.format(sector.totalKwh)} kWh`;
      item.append(name, zone, yieldValue);
      summary.append(item);
    }
  }

  const sectorBody = byId('sectors-table-body');
  sectorBody.replaceChildren();
  const telemetryBody = byId('telemetry-table-body');
  telemetryBody.replaceChildren();
  if (sectors.length === 0) {
    sectorBody.append(makeEmptyRow(4, 'No floor sectors configured.'));
    telemetryBody.append(makeEmptyRow(5, 'No telemetry has been recorded.'));
    return;
  }
  for (const sector of sectors) {
    const row = document.createElement('tr');
    row.append(makeCell(sector.name), makeCell(sector.stationZone),
      makeCell(kwhFormat.format(sector.conversionRateKwhPerStep), 'numeric'),
      makeCell(`${kwhFormat.format(sector.totalKwh)} kWh`, 'numeric'));
    sectorBody.append(row);

    const telemetryRow = document.createElement('tr');
    const lastReading = sector.lastTelemetryAt
      ? new Date(sector.lastTelemetryAt).toLocaleString()
      : 'No readings';
    telemetryRow.append(makeCell(sector.name), makeCell(sector.stationZone),
      makeCell(numberFormat.format(sector.totalSteps), 'numeric'),
      makeCell(`${kwhFormat.format(sector.totalKwh)} kWh`, 'numeric'), makeCell(lastReading));
    telemetryBody.append(telemetryRow);
  }
}

function renderDailyReport(rows) {
  const body = byId('daily-table-body');
  body.replaceChildren();
  if (!rows.length) {
    body.append(makeEmptyRow(4, 'No telemetry recorded for this UTC date.'));
  } else {
    for (const item of rows) {
      const row = document.createElement('tr');
      row.append(makeCell(item.sectorName), makeCell(item.stationZone),
        makeCell(numberFormat.format(item.steps), 'numeric'),
        makeCell(`${wattFormat.format(item.averagePowerWatts)} W`, 'numeric'));
      body.append(row);
    }
  }
  const totalWatts = rows.reduce((sum, item) => sum + Number(item.averagePowerWatts), 0);
  const totalSteps = rows.reduce((sum, item) => sum + Number(item.steps), 0);
  byId('metric-today').innerHTML = `${wattFormat.format(totalWatts)} <small>W</small>`;
  byId('today-sector-count').textContent = `${rows.length} ${rows.length === 1 ? 'sector' : 'sectors'} reporting · ${numberFormat.format(totalSteps)} steps`;
}

function renderHistory(rows) {
  const body = byId('history-table-body');
  body.replaceChildren();
  if (!rows.length) {
    body.append(makeEmptyRow(5, 'No saved readings for this date.'));
    return;
  }
  for (const item of rows) {
    const row = document.createElement('tr');
    const recordedAt = new Date(item.recordedAt).toLocaleString(undefined, {
      dateStyle: 'medium', timeStyle: 'short', timeZone: 'UTC'
    });
    row.append(makeCell(recordedAt), makeCell(item.sectorName), makeCell(item.stationZone),
      makeCell(numberFormat.format(item.steps), 'numeric'),
      makeCell(`${kwhFormat.format(item.generatedKwh)} kWh`, 'numeric'));
    body.append(row);
  }
}

async function loadDailyReport(date) {
  const rows = await api(`/reports/daily-generation?date=${encodeURIComponent(date)}`);
  renderDailyReport(rows);
}

async function loadHistory(date = '') {
  const query = date ? `?date=${encodeURIComponent(date)}` : '';
  renderHistory(await api(`/telemetry${query}`));
}

async function refreshData() {
  try {
    sectors = await api('/sectors');
    setConnection(true);
    renderSectors();
    const totalKwh = sectors.reduce((sum, sector) => sum + Number(sector.totalKwh), 0);
    const totalSteps = sectors.reduce((sum, sector) => sum + Number(sector.totalSteps), 0);
    byId('metric-kwh').innerHTML = `${kwhFormat.format(totalKwh)} <small>kWh</small>`;
    byId('metric-steps').textContent = numberFormat.format(totalSteps);
    await loadDailyReport(byId('overview-date').value);
    await loadHistory(byId('history-date').value);
  } catch (error) {
    setConnection(false);
    showToast(error.message || 'Unable to load network data.', true);
  }
}

function showView(viewName) {
  document.querySelectorAll('.view').forEach((view) => {
    const active = view.id === `view-${viewName}`;
    view.hidden = !active;
    view.classList.toggle('is-visible', active);
  });
  document.querySelectorAll('.nav-item').forEach((item) => {
    const active = item.dataset.view === viewName;
    item.classList.toggle('is-active', active);
    item.setAttribute('aria-current', active ? 'page' : 'false');
  });
  const activeNav = document.querySelector(`.nav-item[data-view="${viewName}"]`);
  if (activeNav) byId('page-title').textContent = activeNav.textContent.replace(/^\d+/, '').trim() === 'Overview'
    ? 'Network overview'
    : activeNav.textContent.replace(/^\d+/, '').trim();
}

function renderReport(containerId, items) {
  const container = byId(containerId);
  const grid = document.createElement('div');
  grid.className = 'report-result';
  for (const [label, value, tone] of items) {
    const item = document.createElement('div');
    item.className = `result-item ${tone || ''}`;
    const caption = document.createElement('span');
    caption.textContent = label;
    const result = document.createElement('strong');
    result.textContent = value;
    item.append(caption, result);
    grid.append(item);
  }
  container.replaceChildren(grid);
}

function bindForms() {
  byId('sector-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    const button = form.querySelector('button[type="submit"]');
    button.disabled = true;
    try {
      await api('/sectors', {
        method: 'POST',
        body: JSON.stringify({
          name: byId('sector-name').value.trim(),
          stationZone: byId('sector-zone').value.trim(),
          conversionRateKwhPerStep: Number(byId('sector-rate').value)
        })
      });
      form.reset();
      await refreshData();
      showToast('Floor sector added to the network.');
    } catch (error) {
      showToast(error.message || 'Could not create the sector.', true);
    } finally {
      button.disabled = false;
    }
  });

  const submitTelemetry = async (event, selectId, stepsId) => {
    event.preventDefault();
    const form = event.currentTarget;
    const button = form.querySelector('button[type="submit"]');
    button.disabled = true;
    try {
      await api('/telemetry', {
        method: 'POST',
        body: JSON.stringify({
          sectorId: byId(selectId).value,
          steps: Number(byId(stepsId).value),
          recordedAt: new Date().toISOString()
        })
      });
      form.reset();
      await refreshData();
      showToast('Telemetry recorded and generation totals updated.');
    } catch (error) {
      showToast(error.message || 'Could not record telemetry.', true);
    } finally {
      button.disabled = false;
    }
  };
  byId('telemetry-form').addEventListener('submit', (event) => submitTelemetry(event, 'telemetry-sector', 'telemetry-steps'));
  byId('telemetry-form-full').addEventListener('submit', (event) => submitTelemetry(event, 'telemetry-sector-full', 'telemetry-steps-full'));

  byId('pnl-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    try {
      const report = await api('/reports/p-and-l', {
        method: 'POST',
        body: JSON.stringify({
          energyPricePerKwh: Number(byId('pnl-price').value),
          transducerRepairs: Number(byId('pnl-repairs').value),
          inverterMaintenance: Number(byId('pnl-inverter').value)
        })
      });
      renderReport('pnl-output', [
        ['Energy generated', `${kwhFormat.format(report.totalKwh)} kWh`],
        ['Energy revenue', decimalFormat.format(report.energyRevenue)],
        ['Transducer repairs', decimalFormat.format(report.transducerRepairs)],
        ['Inverter maintenance', decimalFormat.format(report.inverterMaintenance)],
        ['Total expenses', decimalFormat.format(report.totalExpenses)],
        ['Net income', decimalFormat.format(report.netIncome), report.netIncome >= 0 ? 'is-positive' : 'is-negative']
      ]);
    } catch (error) {
      showToast(error.message || 'Could not generate the P&L report.', true);
    }
  });

  byId('budget-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    try {
      const report = await api('/reports/budget', {
        method: 'POST',
        body: JSON.stringify({
          plannedEnergyKwh: Number(byId('budget-energy').value),
          plannedMaintenance: Number(byId('budget-maintenance').value)
        })
      });
      renderReport('budget-output', [
        ['Planned energy', `${kwhFormat.format(report.plannedEnergyKwh)} kWh`],
        ['Actual energy', `${kwhFormat.format(report.actualEnergyKwh)} kWh`],
        ['Energy variance', `${kwhFormat.format(report.energyVarianceKwh)} kWh`, report.energyVarianceKwh >= 0 ? 'is-positive' : 'is-negative'],
        ['Planned maintenance', decimalFormat.format(report.plannedMaintenance)]
      ]);
    } catch (error) {
      showToast(error.message || 'Could not generate the budget report.', true);
    }
  });
}

function initialize() {
  const today = new Date().toISOString().slice(0, 10);
  byId('today-label').textContent = new Date().toLocaleDateString(undefined, { weekday: 'short', month: 'short', day: 'numeric', year: 'numeric' });
  byId('overview-date').value = today;
  byId('overview-date').addEventListener('change', (event) => {
    loadDailyReport(event.currentTarget.value).catch((error) => showToast(error.message, true));
  });
  byId('history-date').addEventListener('change', (event) => {
    loadHistory(event.currentTarget.value).catch((error) => showToast(error.message, true));
  });
  document.querySelectorAll('.nav-item').forEach((item) => item.addEventListener('click', () => showView(item.dataset.view)));
  document.querySelectorAll('[data-open-view]').forEach((button) => button.addEventListener('click', () => showView(button.dataset.openView)));
  byId('refresh-overview').addEventListener('click', refreshData);
  bindForms();
  refreshData();
}

document.addEventListener('DOMContentLoaded', initialize);