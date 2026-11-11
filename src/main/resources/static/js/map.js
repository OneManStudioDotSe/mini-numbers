/**
 * Geographic Map Visualization
 * Countries as an icon bar chart, with a toggle to the globe (globe.js)
 */

const MapManager = {
  instance: null,
  currentView: 'chart', // 'chart' or 'globe'

  // Country name to ISO code mapping (common variations)
  countryCodeMap: {
    // Full names
    'United States': 'USA',
    'United Kingdom': 'GBR',
    'United Arab Emirates': 'ARE',
    'South Korea': 'KOR',
    'North Korea': 'PRK',
    'Czech Republic': 'CZE',
    'Dominican Republic': 'DOM',
    'New Zealand': 'NZL',
    'South Africa': 'ZAF',
    'Saudi Arabia': 'SAU',
    'Costa Rica': 'CRI',
    'Puerto Rico': 'PRI',
    'Sri Lanka': 'LKA',
    'Hong Kong': 'HKG',
    'Vatican City': 'VAT',

    // Common abbreviations
    US: 'USA',
    UK: 'GBR',
    UAE: 'ARE',

    // Alternative names
    Russia: 'RUS',
    'Russian Federation': 'RUS',
    Iran: 'IRN',
    Syria: 'SYR',
    Venezuela: 'VEN',
    Bolivia: 'BOL',
    Tanzania: 'TZA',
    'Congo (Democratic Republic)': 'COD',
    'Congo (Republic)': 'COG',
    Vietnam: 'VNM',
    Laos: 'LAO',
    Moldova: 'MDA',
    Macedonia: 'MKD',
    'North Macedonia': 'MKD',
    Burma: 'MMR',
    Myanmar: 'MMR',
    'Ivory Coast': 'CIV',
    "Cote d'Ivoire": 'CIV',
    Eswatini: 'SWZ',
    Swaziland: 'SWZ',
  },

  /**
   * Convert country name to ISO 3-letter code
   * @param {string} countryName - Country name
   * @returns {string} ISO 3-letter code or original name
   */
  getCountryCode(countryName) {
    if (!countryName) return null;

    // Check if it's already a 3-letter code
    if (countryName.length === 3 && countryName === countryName.toUpperCase()) {
      return countryName;
    }

    // Check mapping
    if (this.countryCodeMap[countryName]) {
      return this.countryCodeMap[countryName];
    }

    // Try to get standard 3-letter code (this is a best-effort approach)
    // In a production app, you'd use a comprehensive library like country-list
    const standardCodes = {
      Afghanistan: 'AFG',
      Albania: 'ALB',
      Algeria: 'DZA',
      Argentina: 'ARG',
      Armenia: 'ARM',
      Australia: 'AUS',
      Austria: 'AUT',
      Azerbaijan: 'AZE',
      Bangladesh: 'BGD',
      Belarus: 'BLR',
      Belgium: 'BEL',
      Bosnia: 'BIH',
      'Bosnia and Herzegovina': 'BIH',
      Brazil: 'BRA',
      Bulgaria: 'BGR',
      Cambodia: 'KHM',
      Cameroon: 'CMR',
      Canada: 'CAN',
      Chile: 'CHL',
      China: 'CHN',
      Colombia: 'COL',
      Croatia: 'HRV',
      Cuba: 'CUB',
      Cyprus: 'CYP',
      Denmark: 'DNK',
      Ecuador: 'ECU',
      Egypt: 'EGY',
      Estonia: 'EST',
      Ethiopia: 'ETH',
      Finland: 'FIN',
      France: 'FRA',
      Georgia: 'GEO',
      Germany: 'DEU',
      Ghana: 'GHA',
      Greece: 'GRC',
      Hungary: 'HUN',
      Iceland: 'ISL',
      India: 'IND',
      Indonesia: 'IDN',
      Iraq: 'IRQ',
      Ireland: 'IRL',
      Israel: 'ISR',
      Italy: 'ITA',
      Jamaica: 'JAM',
      Japan: 'JPN',
      Jordan: 'JOR',
      Kazakhstan: 'KAZ',
      Kenya: 'KEN',
      Kuwait: 'KWT',
      Latvia: 'LVA',
      Lebanon: 'LBN',
      Libya: 'LBY',
      Lithuania: 'LTU',
      Luxembourg: 'LUX',
      Malaysia: 'MYS',
      Malta: 'MLT',
      Mexico: 'MEX',
      Morocco: 'MAR',
      Nepal: 'NPL',
      Netherlands: 'NLD',
      Nigeria: 'NGA',
      Norway: 'NOR',
      Oman: 'OMN',
      Pakistan: 'PAK',
      Palestine: 'PSE',
      Panama: 'PAN',
      Paraguay: 'PRY',
      Peru: 'PER',
      Philippines: 'PHL',
      Poland: 'POL',
      Portugal: 'PRT',
      Qatar: 'QAT',
      Romania: 'ROU',
      Rwanda: 'RWA',
      Senegal: 'SEN',
      Serbia: 'SRB',
      Singapore: 'SGP',
      Slovakia: 'SVK',
      Slovenia: 'SVN',
      Somalia: 'SOM',
      Spain: 'ESP',
      Sudan: 'SDN',
      Sweden: 'SWE',
      Switzerland: 'CHE',
      Taiwan: 'TWN',
      Thailand: 'THA',
      Tunisia: 'TUN',
      Turkey: 'TUR',
      Uganda: 'UGA',
      Ukraine: 'UKR',
      Uruguay: 'URY',
      Uzbekistan: 'UZB',
      Yemen: 'YEM',
      Zimbabwe: 'ZWE',
    };

    return standardCodes[countryName] || countryName;
  },

  /**
   * Create choropleth map
   * @param {string} canvasId - Canvas element ID
   * @param {Array} countryData - Array of {label: country, value: count}
   * @returns {Promise<Chart>} Chart instance
   */
  async createMap(canvasId, countryData) {
    // Destroy existing Chart.js instance
    if (this.instance) {
      this.instance.destroy();
      this.instance = null;
    }

    try {
      const canvas = document.getElementById(canvasId);
      if (!canvas) {
        console.error(`Canvas #${canvasId} not found`);
        return null;
      }

      // Limit to top 15 countries for better visualization
      const topCountries = countryData.slice(0, 15);
      this._countryData = topCountries;

      // Use icon bar chart with country flags
      const container = canvas.parentElement;
      container.style.height = `${Math.max(300, topCountries.length * 36 + 16)}px`;
      ChartManager.createIconBarChart(canvasId, topCountries, 'country');

      return null;
    } catch (error) {
      console.error('Failed to create geographic chart:', error);

      const container = document.getElementById(canvasId)?.parentElement;
      if (container) {
        container.innerHTML = `
          <div class="empty-state">
            <div class="empty-state__icon">&#x1f5fa;&#xfe0f;</div>
            <div class="empty-state__message">Geographic visualization unavailable</div>
            <div class="empty-state__suggestion">${error.message}</div>
          </div>
        `;
      }

      return null;
    }
  },

  /**
   * Update map with new data
   * @param {Array} countryData - Array of {label: country, value: count}
   */
  async updateMap(countryData) {
    const topCountries = countryData.slice(0, 15);
    this._countryData = topCountries;

    const canvas = document.getElementById('chart-countries');
    if (canvas) {
      const container = canvas.parentElement;
      container.style.height = `${Math.max(300, topCountries.length * 36 + 16)}px`;
      ChartManager.createIconBarChart('chart-countries', topCountries, 'country');
    }
  },

  /**
   * Initialize view toggle functionality
   * @param {Array} countryData - Initial country data
   */
  initViewToggle(countryData) {
    const toggle = document.getElementById('geo-view-toggle');
    if (!toggle) return;

    // Load saved preference — migrate old 'map' preference to 'chart'
    const savedView = localStorage.getItem('geo-view-preference') || 'chart';
    this.currentView = (savedView === 'map') ? 'chart' : savedView;

    const buttons = toggle.querySelectorAll('.view-toggle__btn');

    // Set initial state
    buttons.forEach((btn) => {
      const view = btn.dataset.view;
      if (view === this.currentView) {
        btn.classList.add('active');
      } else {
        btn.classList.remove('active');
      }
    });

    // Apply initial view
    if (this.currentView === 'globe') {
      this.switchView('globe', countryData);
    }

    // Add click handlers
    buttons.forEach((btn) => {
      btn.addEventListener('click', () => {
        const view = btn.dataset.view;
        this.switchView(view, countryData);

        // Update button states
        buttons.forEach((b) => b.classList.remove('active'));
        btn.classList.add('active');

        // Save preference
        localStorage.setItem('geo-view-preference', view);
      });
    });
  },

  /**
   * Switch between chart and globe views
   * @param {string} view - 'chart' or 'globe'
   * @param {Array} countryData - Country data
   */
  switchView(view, countryData) {
    const chartCanvas = document.getElementById('chart-countries');
    const chartContainer = chartCanvas?.parentElement;
    const iconBarChart = chartContainer?.querySelector('.icon-bar-chart');
    const mapContainer = document.getElementById('map-countries');
    const globeContainer = document.getElementById('globe-container');

    if (view === 'globe') {
      // Hide chart (canvas + icon bar chart) and old map, show globe
      chartCanvas.style.display = 'none';
      if (iconBarChart) iconBarChart.style.display = 'none';
      mapContainer.classList.add('hidden');
      if (globeContainer) globeContainer.classList.remove('hidden');

      // Initialize globe lazily
      if (!GlobeManager.instance) {
        GlobeManager.init('globe-canvas', []).then(() => {
          const projectId = typeof Dashboard !== 'undefined' && Dashboard.state
            ? Dashboard.state.currentProjectId : null;
          if (projectId) {
            GlobeManager.startPolling(projectId, 'realtime');
          }
        });
      } else {
        // Globe already initialized — ensure polling
        const projectId = typeof Dashboard !== 'undefined' && Dashboard.state
          ? Dashboard.state.currentProjectId : null;
        if (projectId && !GlobeManager.pollInterval) {
          GlobeManager.startPolling(projectId, GlobeManager.currentRange);
        }
      }

      // Setup range selector event handlers
      this.setupGlobeRangeSelector();
    } else {
      // Show chart (icon bar chart), hide globe and old map
      if (iconBarChart) iconBarChart.style.display = '';
      mapContainer.classList.add('hidden');
      if (globeContainer) globeContainer.classList.add('hidden');

      // Stop globe polling when not visible
      GlobeManager.stopPolling();
    }

    this.currentView = view;
  },

  /**
   * Setup globe range selector button handlers
   */
  setupGlobeRangeSelector() {
    const selector = document.getElementById('globe-range-selector');
    if (!selector || selector.dataset.initialized) return;
    selector.dataset.initialized = 'true';

    const rangeButtons = selector.querySelectorAll('.globe-range-btn:not(#globe-test-markers-btn)');
    rangeButtons.forEach(btn => {
      btn.addEventListener('click', () => {
        rangeButtons.forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        // Deactivate test button
        const testBtn = document.getElementById('globe-test-markers-btn');
        if (testBtn) testBtn.classList.remove('active');

        const range = btn.dataset.range;
        const projectId = typeof Dashboard !== 'undefined' && Dashboard.state
          ? Dashboard.state.currentProjectId : null;
        if (projectId) {
          GlobeManager.startPolling(projectId, range);
        }
      });
    });

    // Test markers button
    const testBtn = document.getElementById('globe-test-markers-btn');
    if (testBtn) {
      testBtn.addEventListener('click', () => {
        rangeButtons.forEach(b => b.classList.remove('active'));
        testBtn.classList.toggle('active');

        if (testBtn.classList.contains('active')) {
          GlobeManager.stopPolling();
          GlobeManager.showTestMarkers();
        } else {
          // Resume real-time polling
          const realtimeBtn = selector.querySelector('[data-range="realtime"]');
          if (realtimeBtn) realtimeBtn.classList.add('active');
          const projectId = typeof Dashboard !== 'undefined' && Dashboard.state
            ? Dashboard.state.currentProjectId : null;
          if (projectId) {
            GlobeManager.startPolling(projectId, 'realtime');
          }
        }
      });
    }
  },

  /**
   * Destroy map instance
   */
  destroy() {
    if (this.instance) {
      this.instance.destroy();
      this.instance = null;
    }
  },
};

