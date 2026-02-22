/**
 * Geographic Map Visualization
 * Interactive world map using Chart.js Chart.Geo plugin
 */

const MapManager = {
  instance: null,
  topologyData: null,
  leafletMap: null,
  currentView: 'chart', // 'chart' or 'globe'

  // Country coordinates (approximate center points)
  countryCoordinates: {
    'United States': [37.0902, -95.7129],
    'United Kingdom': [55.3781, -3.4360],
    'Canada': [56.1304, -106.3468],
    'Germany': [51.1657, 10.4515],
    'France': [46.2276, 2.2137],
    'Italy': [41.8719, 12.5674],
    'Spain': [40.4637, -3.7492],
    'Australia': [-25.2744, 133.7751],
    'Japan': [36.2048, 138.2529],
    'China': [35.8617, 104.1954],
    'India': [20.5937, 78.9629],
    'Brazil': [-14.2350, -51.9253],
    'Mexico': [23.6345, -102.5528],
    'Russia': [61.5240, 105.3188],
    'South Korea': [35.9078, 127.7669],
    'Netherlands': [52.1326, 5.2913],
    'Sweden': [60.1282, 18.6435],
    'Norway': [60.4720, 8.4689],
    'Denmark': [56.2639, 9.5018],
    'Finland': [61.9241, 25.7482],
    'Poland': [51.9194, 19.1451],
    'Belgium': [50.5039, 4.4699],
    'Austria': [47.5162, 14.5501],
    'Switzerland': [46.8182, 8.2275],
    'Greece': [39.0742, 21.8243],
    'Portugal': [39.3999, -8.2245],
    'Ireland': [53.4129, -8.2439],
    'Turkey': [38.9637, 35.2433],
    'Argentina': [-38.4161, -63.6167],
    'Chile': [-35.6751, -71.5430],
    'Colombia': [4.5709, -74.2973],
    'Peru': [-9.1900, -75.0152],
    'South Africa': [-30.5595, 22.9375],
    'Egypt': [26.8206, 30.8025],
    'Nigeria': [9.0820, 8.6753],
    'Kenya': [-0.0236, 37.9062],
    'Saudi Arabia': [23.8859, 45.0792],
    'UAE': [23.4241, 53.8478],
    'Israel': [31.0461, 34.8516],
    'Singapore': [1.3521, 103.8198],
    'Malaysia': [4.2105, 101.9758],
    'Thailand': [15.8700, 100.9925],
    'Vietnam': [14.0583, 108.2772],
    'Philippines': [12.8797, 121.7740],
    'Indonesia': [-0.7893, 113.9213],
    'New Zealand': [-40.9006, 174.8860],
    'Pakistan': [30.3753, 69.3451],
    'Bangladesh': [23.6850, 90.3563],
    'Taiwan': [23.6978, 120.9605],
    'Hong Kong': [22.3193, 114.1694],
    'Ukraine': [48.3794, 31.1656],
    'Czech Republic': [49.8175, 15.4730],
    'Romania': [45.9432, 24.9668],
    'Hungary': [47.1625, 19.5033],
  },

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
   * Load TopoJSON world data
   * @returns {Promise} Topology data
   */
  async loadTopology() {
    if (this.topologyData) {
      return this.topologyData;
    }

    try {
      const response = await fetch(
        'https://unpkg.com/world-atlas@2.0.2/countries-50m.json'
      );
      this.topologyData = await response.json();
      return this.topologyData;
    } catch (error) {
      console.error('Failed to load map topology:', error);
      Utils.toast.error('Failed to load world map data');
      throw error;
    }
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
   * Create interactive Leaflet map
   * @param {string} mapId - Map container ID
   * @param {Array} countryData - Array of {label: country, value: count}
   * @returns {Object} Leaflet map instance
   */
  createLeafletMap(mapId, countryData) {
    // Destroy existing map
    if (this.leafletMap) {
      this.leafletMap.remove();
      this.leafletMap = null;
    }

    const mapContainer = document.getElementById(mapId);
    if (!mapContainer) {
      console.error(`Map container #${mapId} not found`);
      return null;
    }

    // Create map centered on world view
    this.leafletMap = L.map(mapId, {
      center: [20, 0],
      zoom: 2,
      minZoom: 2,
      maxZoom: 6,
      worldCopyJump: true,
    });

    // Add tile layer (OpenStreetMap)
    const isDark = ThemeManager.isDark();
    const tileUrl = isDark
      ? 'https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png'
      : 'https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png';

    L.tileLayer(tileUrl, {
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
      subdomains: 'abcd',
      maxZoom: 19,
    }).addTo(this.leafletMap);

    // Add markers for each country
    this.updateLeafletMap(countryData);

    return this.leafletMap;
  },

  /**
   * Update markers on Leaflet map
   * @param {Array} countryData - Array of {label: country, value: count}
   */
  updateLeafletMap(countryData) {
    if (!this.leafletMap) return;

    // Clear existing markers
    this.leafletMap.eachLayer((layer) => {
      if (layer instanceof L.Marker) {
        this.leafletMap.removeLayer(layer);
      }
    });

    // Find max value for sizing
    const maxValue = Math.max(...countryData.map((c) => c.value));

    // Add markers for each country
    countryData.forEach((country) => {
      const coords = this.countryCoordinates[country.label];
      if (!coords) {
        console.warn(`No coordinates found for: ${country.label}`);
        return;
      }

      // Calculate marker size based on visitor count
      const intensity = country.value / maxValue;
      const radius = 5 + intensity * 20; // 5-25px radius

      // Create circle marker
      const marker = L.circleMarker(coords, {
        radius: radius,
        fillColor: '#2563eb',
        color: '#ffffff',
        weight: 2,
        opacity: 1,
        fillOpacity: 0.6,
      });

      // Add popup with country info
      marker.bindPopup(`
        <div style="text-align: center;">
          <strong>${country.label}</strong><br>
          <span style="color: #2563eb; font-size: 1.2em; font-weight: bold;">
            ${Utils.format.number(country.value)}
          </span> visitors
        </div>
      `);

      // Add tooltip on hover
      marker.bindTooltip(country.label, {
        permanent: false,
        direction: 'top',
      });

      marker.addTo(this.leafletMap);
    });
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

    const buttons = selector.querySelectorAll('.globe-range-btn');
    buttons.forEach(btn => {
      btn.addEventListener('click', () => {
        buttons.forEach(b => b.classList.remove('active'));
        btn.classList.add('active');

        const range = btn.dataset.range;
        const projectId = typeof Dashboard !== 'undefined' && Dashboard.state
          ? Dashboard.state.currentProjectId : null;
        if (projectId) {
          GlobeManager.startPolling(projectId, range);
        }
      });
    });
  },

  /**
   * Destroy map instance
   */
  destroy() {
    if (this.instance) {
      this.instance.destroy();
      this.instance = null;
    }

    if (this.leafletMap) {
      this.leafletMap.remove();
      this.leafletMap = null;
    }
  },
};

// Listen for theme changes
window.addEventListener('themeChange', () => {
  // Update chart colors if chart view is active
  if (MapManager.instance) {
    const colors = ChartManager.getColors();
    if (MapManager.instance.options?.scales?.projection) {
      MapManager.instance.options.scales.projection.backgroundColor = colors.bgSecondary;
      MapManager.instance.data.datasets[0].borderColor = colors.border;
      MapManager.instance.update();
    }
  }

  // Globe theme is handled by GlobeManager's own themeChange listener
});
