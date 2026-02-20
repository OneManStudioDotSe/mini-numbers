/**
 * Main Admin Dashboard Logic
 * Handles project selection, data fetching, and UI updates
 */

const Dashboard = {
  // Application state
  state: {
    currentProjectId: null,
    currentFilter: '7d',
    liveInterval: null,
    previousData: null, // For comparison metrics

    // Multi-period data storage
    data: {
      current: null,
      previous: null,
      timeSeries: null
    },

    // Filter state
    filters: {
      dimension: null,
      value: null,
      active: false
    },

    // Raw data cache for re-aggregation
    rawData: {
      browsers: null,
      os: null,
      devices: null,
      countries: null,
      referrers: null,
      topPages: null
    }
  },

  /**
   * Initialize dashboard
   */
  async init() {
    // Set up theme toggle
    this.setupThemeToggle();

    // Set up mobile menu
    this.setupMobileMenu();

    // Set up sign out
    this.setupSignOut();

    // Load projects
    await this.loadProjects();

    // Set up time filter
    this.setupTimeFilter();

    // Set up chart type toggles
    this.setupChartToggles();

    // Set up table search and sorting
    this.setupTableFeatures();

    // Set up filters
    this.setupFilters();

    // Set up export buttons
    this.setupExportButtons();

    // Set up demo data generator
    this.setupDemoDataGenerator();

    // New features: UI/UX improvements
    this.setupInspirationalMessage();
    this.setupModals();
    this.setupSettingsPanel();
    this.setupRawEventsViewer();
    this.setupGeographicDrillDown();
    this.updateTimeFilterLabels();

    // Initialize geographic state
    this.state.geoState = {
      view: 'countries',
      selectedCountry: null
    };
  },

  /**
   * Setup theme toggle button
   */
  setupThemeToggle() {
    const toggle = document.getElementById('theme-toggle');
    if (!toggle) return;

    toggle.addEventListener('click', () => {
      ThemeManager.toggle();
    });

    // Update toggle state based on current theme
    this.updateThemeToggleIcon();

    // Listen for theme changes
    window.addEventListener('themeChange', () => {
      this.updateThemeToggleIcon();
    });
  },

  /**
   * Update theme toggle icon
   */
  updateThemeToggleIcon() {
    const slider = document.querySelector('.theme-toggle__slider');
    if (!slider) return;

    const isDark = ThemeManager.isDark();
    slider.innerHTML = isDark
      ? '<i data-feather="moon"></i>'
      : '<i data-feather="sun"></i>';

    // Re-initialize feather icons
    if (window.feather) {
      feather.replace();
    }
  },

  /**
   * Setup mobile menu toggle
   */
  setupMobileMenu() {
    const menuToggle = document.getElementById('menu-toggle');
    const sidebar = document.getElementById('sidebar');

    if (menuToggle && sidebar) {
      menuToggle.addEventListener('click', () => {
        sidebar.classList.toggle('open');
      });

      // Close sidebar when clicking outside on mobile
      document.addEventListener('click', (e) => {
        if (
          window.innerWidth <= 640 &&
          sidebar.classList.contains('open') &&
          !sidebar.contains(e.target) &&
          !menuToggle.contains(e.target)
        ) {
          sidebar.classList.remove('open');
        }
      });
    }
  },

  /**
   * Setup sign out button
   */
  setupSignOut() {
    const signOutBtn = document.getElementById('sign-out-btn');
    if (!signOutBtn) return;

    signOutBtn.addEventListener('click', async () => {
      // Stop live feed updates
      this.stopLiveFeed();

      // Clear cache
      Utils.cache.clearAll();

      // Show confirmation
      if (confirm('Are you sure you want to sign out?')) {
        try {
          // Call logout endpoint
          await fetch('/api/logout', {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json',
            },
          });

          // Redirect to login page
          window.location.href = '/login';
        } catch (error) {
          console.error('Logout error:', error);
          // Fallback: redirect anyway
          window.location.href = '/login';
        }
      } else {
        // Restart live feed if user cancels
        if (this.state.currentProjectId) {
          this.startLiveFeed();
        }
      }
    });
  },

  /**
   * Load projects list
   */
  async loadProjects() {
    try {
      const projects = await Utils.api.fetch('/admin/projects');

      const menu = document.getElementById('project-menu');
      if (!menu) return;

      // Handle empty state (no projects)
      if (!projects || projects.length === 0) {
        menu.innerHTML = `
          <div class="empty-state">
            <div class="empty-state__icon">📊</div>
            <div class="empty-state__message">No projects yet</div>
            <div class="empty-state__hint">Create your first project to start tracking analytics</div>
            <button class="btn btn-primary btn-sm" onclick="Dashboard.showCreateProjectDialog()">
              Create Project
            </button>
          </div>
        `;
        // Hide dashboard content when no projects
        const dashboardContent = document.getElementById('dashboard-content');
        if (dashboardContent) {
          Utils.dom.hide(dashboardContent);
        }
        return;
      }

      // Show projects list
      menu.innerHTML = projects
        .map(
          (project) => `
        <div
          class="project-menu__item"
          data-id="${project.id}"
          onclick="Dashboard.selectProject('${project.id}', '${project.name.replace(/'/g, "\\'")}')"
        >
          ${project.name}
        </div>
      `
        )
        .join('');
    } catch (error) {
      console.error('Failed to load projects:', error);
      Utils.toast.error('Failed to load projects');
    }
  },

  /**
   * Show enhanced modal to create a new project
   */
  async showCreateProjectDialog() {
    const modal = document.getElementById('create-project-modal');
    if (!modal) {
      console.error('Create project modal not found!');
      return;
    }

    const nameInput = document.getElementById('project-name');
    const subtitleInput = document.getElementById('project-subtitle');
    const domainInput = document.getElementById('project-domain');
    const apiKeyPreview = document.getElementById('api-key-preview');
    const confirmBtn = document.getElementById('confirm-create-project');
    const cancelBtn = document.getElementById('cancel-create-project');
    const copyBtn = document.getElementById('copy-api-key');

    // Reset form
    if (nameInput) nameInput.value = '';
    if (subtitleInput) subtitleInput.value = '';
    if (domainInput) domainInput.value = '';
    if (apiKeyPreview) apiKeyPreview.style.display = 'none';
    if (confirmBtn) confirmBtn.textContent = 'Create Project';

    modal.classList.add('show');

    // Store original state for cleanup
    if (!this._createProjectHandlers) {
      this._createProjectHandlers = { confirm: null, cancel: null, copy: null };
    }

    // Clean up old handlers
    if (confirmBtn && this._createProjectHandlers.confirm) {
      confirmBtn.removeEventListener('click', this._createProjectHandlers.confirm);
    }
    if (cancelBtn && this._createProjectHandlers.cancel) {
      cancelBtn.removeEventListener('click', this._createProjectHandlers.cancel);
    }
    if (copyBtn && this._createProjectHandlers.copy) {
      copyBtn.removeEventListener('click', this._createProjectHandlers.copy);
    }

    // Cancel handler
    const cancelHandler = () => modal.classList.remove('show');
    if (cancelBtn) {
      cancelBtn.addEventListener('click', cancelHandler);
      this._createProjectHandlers.cancel = cancelHandler;
    }

    // Create handler
    const confirmHandler = async () => {
      // If already showing API key, close modal
      if (apiKeyPreview && apiKeyPreview.style.display !== 'none') {
        modal.classList.remove('show');
        await this.loadProjects();
        return;
      }

      const name = nameInput?.value.trim() || '';
      const subtitle = subtitleInput?.value.trim() || '';
      const domain = domainInput?.value.trim() || '';

      if (!name || !domain) {
        Utils.toast.error('Name and domain are required');
        return;
      }

      try {
        const displayName = subtitle ? `${name} - ${subtitle}` : name;
        await Utils.api.post('/admin/projects', { name: displayName, domain });

        const projects = await Utils.api.fetch('/admin/projects');
        const newProject = projects[projects.length - 1];

        document.getElementById('generated-api-key').textContent = newProject.apiKey;
        if (apiKeyPreview) apiKeyPreview.style.display = 'block';
        if (confirmBtn) confirmBtn.textContent = 'Done';

        Utils.toast.success('Project created!');
        feather.replace();
      } catch (error) {
        console.error('Failed to create project:', error);
        Utils.toast.error('Failed to create project');
      }
    };
    if (confirmBtn) {
      confirmBtn.addEventListener('click', confirmHandler);
      this._createProjectHandlers.confirm = confirmHandler;
    }

    // Copy API key handler
    const copyHandler = () => {
      const key = document.getElementById('generated-api-key')?.textContent;
      if (key) {
        navigator.clipboard.writeText(key);
        Utils.toast.success('API key copied!');
      }
    };
    if (copyBtn) {
      copyBtn.addEventListener('click', copyHandler);
      this._createProjectHandlers.copy = copyHandler;
    }
  },

  /**
   * Select project and load data
   * @param {string} id - Project ID
   * @param {string} name - Project name
   */
  async selectProject(id, name) {
    this.state.currentProjectId = id;

    // Update UI
    const titleEl = document.getElementById('active-title');
    if (titleEl) titleEl.textContent = name;

    // Update active menu item
    document.querySelectorAll('.project-menu__item').forEach((item) => {
      item.classList.remove('active');
      if (item.dataset.id === id) {
        item.classList.add('active');
      }
    });

    // Show dashboard content
    const dashboardContent = document.getElementById('dashboard-content');
    if (dashboardContent) {
      Utils.dom.show(dashboardContent);
    }

    // Show raw events button
    const rawEventsBtn = document.getElementById('view-raw-events-btn');
    if (rawEventsBtn) {
      rawEventsBtn.style.display = 'flex';
    }

    // Close mobile menu if open
    const sidebar = document.getElementById('sidebar');
    if (sidebar && window.innerWidth <= 640) {
      sidebar.classList.remove('open');
    }

    // Load data
    await this.refreshReport();
    this.startLiveFeed();
  },

  /**
   * Setup time filter dropdown
   */
  setupTimeFilter() {
    const filterEl = document.getElementById('time-filter');
    if (!filterEl) return;

    filterEl.addEventListener('change', (e) => {
      this.state.currentFilter = e.target.value;
      this.updateDateRangeDisplay();
      this.refreshReport();
    });
  },

  /**
   * Refresh report data
   */
  async refreshReport() {
    if (!this.state.currentProjectId) return;

    try {
      // Show loading skeletons
      this.showLoadingState();

      // Fetch comparison report with time series
      const data = await Utils.api.fetch(
        `/admin/projects/${this.state.currentProjectId}/report/comparison?filter=${this.state.currentFilter}`
      );

      // Store both periods and time series
      this.state.data = {
        current: data.current,
        previous: data.previous,
        timeSeries: data.timeSeries
      };

      // Backward compatibility
      this.state.previousData = data.current;

      // Update UI
      this.updateStats(data.current);
      this.updateComparisons(data.current, data.previous);
      this.updateSparklines(data.timeSeries);
      this.updateCharts(data.current);
      this.updateTables(data.current);

      // Load contribution calendar (separate API call)
      this.loadContributionCalendar();

      // Hide loading state
      this.hideLoadingState();
    } catch (error) {
      console.error('Failed to refresh report:', error);
      Utils.toast.error('Failed to load analytics data');
      this.hideLoadingState();
    }
  },

  /**
   * Show loading skeleton states
   */
  showLoadingState() {
    // In a full implementation, this would show skeleton loaders
    // For now, we'll just add a subtle loading indicator
  },

  /**
   * Hide loading states
   */
  hideLoadingState() {
    // Remove loading indicators
  },

  /**
   * Update stat cards
   * @param {Object} data - Report data
   */
  updateStats(data) {
    // Update total views
    const viewsEl = document.getElementById('total-views');
    if (viewsEl) {
      viewsEl.textContent = Utils.format.number(data.totalViews);
    }

    // Update unique visitors
    const visitorsEl = document.getElementById('unique-visitors');
    if (visitorsEl) {
      visitorsEl.textContent = Utils.format.number(data.uniqueVisitors);
    }

    // Update bounce rate
    const bounceEl = document.getElementById('bounce-rate');
    if (bounceEl) {
      bounceEl.textContent = (data.bounceRate != null ? data.bounceRate.toFixed(1) : '0') + '%';
    }
  },

  /**
   * Update sparkline charts with real time series data
   * @param {Array} timeSeries - Time series data points
   */
  updateSparklines(timeSeries) {
    if (!timeSeries || !timeSeries.length) return;

    const viewsData = timeSeries.map(point => point.views);
    const visitorsData = timeSeries.map(point => point.uniqueVisitors);

    if (document.getElementById('sparkline-views')) {
      ChartManager.createSparkline('sparkline-views', viewsData);
    }

    if (document.getElementById('sparkline-visitors')) {
      ChartManager.createSparkline('sparkline-visitors', visitorsData);
    }
  },

  /**
   * Calculate percentage change between two values
   * @param {number} current - Current value
   * @param {number} previous - Previous value
   * @returns {number} Percentage change
   */
  calculatePercentChange(current, previous) {
    if (previous === 0) return current > 0 ? 100 : 0;
    return ((current - previous) / previous) * 100;
  },

  /**
   * Update comparison metrics with previous period data
   * @param {Object} current - Current period data
   * @param {Object} previous - Previous period data
   */
  updateComparisons(current, previous) {
    if (!current || !previous) return;

    // Calculate percentage changes
    const viewsChange = this.calculatePercentChange(current.totalViews, previous.totalViews);
    const visitorsChange = this.calculatePercentChange(current.uniqueVisitors, previous.uniqueVisitors);

    // Update views comparison
    const viewsCompEl = document.getElementById('views-comparison');
    if (viewsCompEl) {
      const { text, className } = Utils.format.percentageChange(viewsChange);
      const previousFormatted = Utils.format.number(previous.totalViews);
      viewsCompEl.innerHTML = `
        ${text} vs previous period
        <span class="comparison-detail">(${previousFormatted} previously)</span>
      `;
      viewsCompEl.className = `stat-card__comparison ${className}`;
    }

    // Update visitors comparison
    const visitorsCompEl = document.getElementById('visitors-comparison');
    if (visitorsCompEl) {
      const { text, className } = Utils.format.percentageChange(visitorsChange);
      const previousFormatted = Utils.format.number(previous.uniqueVisitors);
      visitorsCompEl.innerHTML = `
        ${text} vs previous period
        <span class="comparison-detail">(${previousFormatted} previously)</span>
      `;
      visitorsCompEl.className = `stat-card__comparison ${className}`;
    }

    // Update bounce rate comparison (inverted: lower is better)
    const bounceCompEl = document.getElementById('bounce-comparison');
    if (bounceCompEl && current.bounceRate != null && previous.bounceRate != null) {
      const bounceChange = this.calculatePercentChange(current.bounceRate, previous.bounceRate);
      const { text } = Utils.format.percentageChange(bounceChange);
      // Invert colors: decrease = good (positive), increase = bad (negative)
      const className = bounceChange < 0 ? 'positive' : bounceChange > 0 ? 'negative' : '';
      const previousFormatted = previous.bounceRate.toFixed(1) + '%';
      bounceCompEl.innerHTML = `
        ${text} vs previous period
        <span class="comparison-detail">(${previousFormatted} previously)</span>
      `;
      bounceCompEl.className = `stat-card__comparison ${className}`;
    }
  },

  /**
   * Update all charts
   * @param {Object} data - Report data
   */
  updateCharts(data) {
    // Top Pages - Bar Chart
    if (data.topPages?.length && document.getElementById('chart-pages')) {
      const topPages = Utils.aggregate.groupTopN(data.topPages, 10);
      ChartManager.createBarChart('chart-pages', topPages);
    }

    // Referrers - Bar Chart
    if (data.referrers?.length && document.getElementById('chart-referrers')) {
      const topReferrers = Utils.aggregate.groupTopN(data.referrers, 10);
      ChartManager.createBarChart('chart-referrers', topReferrers);
    }

    // Browsers - Chart with toggle
    if (data.browsers?.length && document.getElementById('chart-browsers')) {
      this.state.browsersData = data.browsers;
      const topBrowsers = Utils.aggregate.groupTopN(data.browsers, 8);
      const chartType = localStorage.getItem('chart-view-browsers') || 'doughnut';

      if (chartType === 'bar') {
        ChartManager.createBarChart('chart-browsers', topBrowsers);
      } else {
        ChartManager.createDoughnutChart('chart-browsers', topBrowsers);
      }
    }

    // Operating Systems - Chart with toggle
    if (data.oss?.length && document.getElementById('chart-os')) {
      this.state.osData = data.oss;
      const topOS = Utils.aggregate.groupTopN(data.oss, 8);
      const chartType = localStorage.getItem('chart-view-os') || 'doughnut';

      if (chartType === 'bar') {
        ChartManager.createBarChart('chart-os', topOS);
      } else {
        ChartManager.createDoughnutChart('chart-os', topOS);
      }
    }

    // Devices - Chart with toggle
    if (data.devices?.length && document.getElementById('chart-devices')) {
      this.state.devicesData = data.devices;
      const topDevices = Utils.aggregate.groupTopN(data.devices, 8);
      const chartType = localStorage.getItem('chart-view-devices') || 'doughnut';

      if (chartType === 'bar') {
        ChartManager.createBarChart('chart-devices', topDevices);
      } else {
        ChartManager.createDoughnutChart('chart-devices', topDevices);
      }
    }

    // Countries - Geographic visualization
    if (data.countries?.length && document.getElementById('chart-countries')) {
      // Check if we're in drill-down mode
      if (this.state.geoState?.view === 'cities') {
        // Already drilled down, chart is showing cities
        return;
      }

      MapManager.createMap('chart-countries', data.countries);

      // Initialize view toggle (only once)
      if (!MapManager._toggleInitialized) {
        MapManager.initViewToggle(data.countries);
        MapManager._toggleInitialized = true;
      } else if (MapManager.currentView === 'map' && MapManager.leafletMap) {
        // Update map if it's the active view
        MapManager.updateLeafletMap(data.countries);
      }

      // Add click handler for bar chart view (drill-down)
      window.addEventListener('chartBarClick', (e) => {
        if (e.detail.chartId === 'chart-countries' && this.state.geoState?.view === 'countries') {
          this.drillDownToCountry(e.detail.label);
        }
      });
    }

    // Time Series - Line Chart (aggregate from lastVisits)
    if (data.lastVisits?.length && document.getElementById('chart-timeseries')) {
      const granularity = this.state.currentFilter === '24h' ? 'hour' : 'day';
      const timeData = Utils.aggregate.groupByTime(data.lastVisits, granularity);
      if (timeData.length) {
        ChartManager.createLineChart('chart-timeseries', timeData);
      }
    }

    // Activity Heatmap with peak highlighting
    if (data.activityHeatmap?.length && document.getElementById('chart-heatmap')) {
      const peakHour = data.peakTimeAnalysis?.peakHour;
      const peakDay = data.peakTimeAnalysis?.peakDay;
      ChartManager.createHeatmap('chart-heatmap', data.activityHeatmap, peakHour, peakDay);
    }

    // Peak times analysis
    if (data.peakTimeAnalysis) {
      this.updatePeakTimes(data.peakTimeAnalysis);
    }
  },

  /**
   * Update peak times display
   * @param {Object} peakData - Peak time analysis data
   */
  updatePeakTimes(peakData) {
    if (!peakData) return;

    // Update peak hours
    const hoursEl = document.getElementById('peak-hours');
    if (hoursEl && peakData.topHours) {
      hoursEl.innerHTML = peakData.topHours.map((hour, index) => `
        <div class="peak-item ${index === 0 ? 'peak-item--top' : ''}">
          <span class="peak-label">${hour.label}</span>
          <span class="peak-value">${Utils.format.number(hour.value)} visits</span>
          ${index === 0 ? '<span class="badge badge-primary">Peak</span>' : ''}
        </div>
      `).join('');
    }

    // Update peak days
    const daysEl = document.getElementById('peak-days');
    if (daysEl && peakData.topDays) {
      daysEl.innerHTML = peakData.topDays.map((day, index) => `
        <div class="peak-item ${index === 0 ? 'peak-item--top' : ''}">
          <span class="peak-label">${day.label}</span>
          <span class="peak-value">${Utils.format.number(day.value)} visits</span>
          ${index === 0 ? '<span class="badge badge-primary">Peak</span>' : ''}
        </div>
      `).join('');
    }

    // Store peak data for heatmap
    this.state.peakHour = peakData.peakHour;
    this.state.peakDay = peakData.peakDay;
  },

  /**
   * Update tables
   * @param {Object} data - Report data
   */
  updateTables(data) {
    // Update recent activity table
    const tableBody = document.querySelector('#table-recent tbody');
    if (tableBody && data.lastVisits?.length) {
      tableBody.innerHTML = data.lastVisits
        .map(
          (visit) => `
        <tr>
          <td>${visit.path}</td>
          <td>${visit.city || 'Unknown'}</td>
          <td>${Utils.time.formatTime(visit.timestamp)}</td>
        </tr>
      `
        )
        .join('');

      // Re-initialize Feather icons
      if (window.feather) {
        feather.replace();
      }
    }
  },

  /**
   * Start live feed updates
   */
  startLiveFeed() {
    // Clear existing interval
    if (this.state.liveInterval) {
      clearInterval(this.state.liveInterval);
    }

    // Initial load
    this.updateLiveFeed();

    // Update every 5 seconds
    this.state.liveInterval = setInterval(() => {
      this.updateLiveFeed();
    }, 5000);
  },

  /**
   * Update live feed
   */
  async updateLiveFeed() {
    if (!this.state.currentProjectId) return;

    try {
      const data = await Utils.api.fetch(
        `/admin/projects/${this.state.currentProjectId}/live`,
        { useCache: false }
      );

      const feedEl = document.getElementById('live-feed');
      if (!feedEl) return;

      if (!data || data.length === 0) {
        feedEl.innerHTML = '<div class="text-muted">No recent activity</div>';
        return;
      }

      feedEl.innerHTML = data
        .map(
          (visit) => `
        <div class="live-feed__item">
          <div class="live-feed__path">${visit.path}</div>
          <div class="live-feed__meta">
            ${visit.city || 'Unknown'} • ${Utils.time.relative(visit.timestamp)}
          </div>
        </div>
      `
        )
        .join('');
    } catch (error) {
      console.error('Failed to update live feed:', error);
      // Silently fail for live feed
    }
  },

  /**
   * Stop live feed updates
   */
  stopLiveFeed() {
    if (this.state.liveInterval) {
      clearInterval(this.state.liveInterval);
      this.state.liveInterval = null;
    }
  },

  /**
   * Setup chart type toggles for browsers, OS, and devices
   */
  setupChartToggles() {
    const toggles = document.querySelectorAll('.view-toggle[data-chart]');

    toggles.forEach((toggle) => {
      const chartName = toggle.dataset.chart;
      const buttons = toggle.querySelectorAll('.view-toggle__btn');

      // Load saved preference
      const savedView =
        localStorage.getItem(`chart-view-${chartName}`) || 'doughnut';

      // Set initial button states
      buttons.forEach((btn) => {
        if (btn.dataset.view === savedView) {
          btn.classList.add('active');
        } else {
          btn.classList.remove('active');
        }
      });

      // Add click handlers
      buttons.forEach((btn) => {
        btn.addEventListener('click', () => {
          const view = btn.dataset.view;

          // Update button states
          buttons.forEach((b) => b.classList.remove('active'));
          btn.classList.add('active');

          // Save preference
          localStorage.setItem(`chart-view-${chartName}`, view);

          // Recreate chart with new type
          this.updateChartType(chartName, view);
        });
      });
    });
  },

  /**
   * Update chart type (doughnut, bar, or radar)
   * @param {string} chartName - 'browsers', 'os', or 'devices'
   * @param {string} type - 'doughnut', 'bar', or 'radar'
   */
  updateChartType(chartName, type) {
    if (!this.state.currentProjectId) return;

    // Get the stored data
    const dataKey = `${chartName}Data`;
    const data = this.state[dataKey];

    if (!data || !data.length) return;

    const chartId = `chart-${chartName}`;
    const topItems = Utils.aggregate.groupTopN(data, 8);

    if (type === 'bar') {
      ChartManager.createBarChart(chartId, topItems);
    } else if (type === 'radar') {
      ChartManager.createRadarChart(chartId, topItems);
    } else {
      ChartManager.createDoughnutChart(chartId, topItems);
    }
  },

  /**
   * Setup table search and sorting
   */
  setupTableFeatures() {
    const searchInput = document.getElementById('table-search');
    const table = document.getElementById('table-recent');

    if (!searchInput || !table) return;

    // Setup search
    searchInput.addEventListener(
      'input',
      Utils.debounce((e) => {
        this.filterTable(e.target.value.toLowerCase());
      }, 300)
    );

    // Setup sorting
    const headers = table.querySelectorAll('th.sortable');
    headers.forEach((header) => {
      header.addEventListener('click', () => {
        this.sortTable(header);
      });
    });
  },

  /**
   * Filter table rows by search term
   * @param {string} searchTerm - Search term
   */
  filterTable(searchTerm) {
    const table = document.getElementById('table-recent');
    if (!table) return;

    const rows = table.querySelectorAll('tbody tr');

    rows.forEach((row) => {
      const text = row.textContent.toLowerCase();
      const matches = text.includes(searchTerm);
      row.style.display = matches ? '' : 'none';
    });
  },

  /**
   * Sort table by column
   * @param {HTMLElement} header - Header element
   */
  sortTable(header) {
    const table = document.getElementById('table-recent');
    if (!table) return;

    const tbody = table.querySelector('tbody');
    const rows = Array.from(tbody.querySelectorAll('tr'));

    // Determine sort direction
    const currentSort = header.classList.contains('asc') ? 'asc' : 'desc';
    const newSort = currentSort === 'asc' ? 'desc' : 'asc';

    // Remove sort classes from all headers
    table.querySelectorAll('th.sortable').forEach((th) => {
      th.classList.remove('asc', 'desc');
    });

    // Add sort class to current header
    header.classList.add(newSort);

    // Get column index
    const columnIndex = Array.from(header.parentElement.children).indexOf(header);

    // Sort rows
    rows.sort((a, b) => {
      const aValue = a.children[columnIndex]?.textContent.trim() || '';
      const bValue = b.children[columnIndex]?.textContent.trim() || '';

      if (newSort === 'asc') {
        return aValue.localeCompare(bValue, undefined, { numeric: true });
      } else {
        return bValue.localeCompare(aValue, undefined, { numeric: true });
      }
    });

    // Reorder rows in DOM
    rows.forEach((row) => tbody.appendChild(row));
  },

  /**
   * Setup filter controls
   */
  setupFilters() {
    const dimensionSelect = document.getElementById('filter-dimension');
    const valueSelect = document.getElementById('filter-value');
    const clearBtn = document.getElementById('clear-filter');

    if (!dimensionSelect || !valueSelect || !clearBtn) return;

    dimensionSelect.addEventListener('change', (e) => {
      const dimension = e.target.value;
      if (!dimension) {
        this.clearFilter();
        return;
      }
      this.populateFilterValues(dimension);
    });

    valueSelect.addEventListener('change', (e) => {
      const value = e.target.value;
      const dimension = dimensionSelect.value;
      if (value && dimension) {
        this.applyFilter(dimension, value);
      }
    });

    clearBtn.addEventListener('click', () => this.clearFilter());
  },

  /**
   * Populate filter value dropdown based on selected dimension
   * @param {string} dimension - The dimension to filter by
   */
  populateFilterValues(dimension) {
    const valueSelect = document.getElementById('filter-value');
    const container = document.getElementById('filter-value-container');

    if (!valueSelect || !container || !this.state.data.current) return;

    // Map dimension to data key
    const dimKey = dimension === 'os' ? 'oss' : dimension === 'browser' ? 'browsers' :
                   dimension === 'device' ? 'devices' : dimension === 'country' ? 'countries' :
                   dimension === 'referrer' ? 'referrers' : null;

    if (!dimKey || !this.state.data.current[dimKey]) return;

    // Clear existing options
    valueSelect.innerHTML = '<option value="">Select value...</option>';

    // Add options from current data
    this.state.data.current[dimKey].forEach(item => {
      const option = document.createElement('option');
      option.value = item.label;
      option.textContent = `${item.label} (${item.count})`;
      valueSelect.appendChild(option);
    });

    // Show filter value container
    container.style.display = 'flex';
  },

  /**
   * Apply filter to dashboard data
   * @param {string} dimension - The dimension to filter by
   * @param {string} value - The value to filter for
   */
  applyFilter(dimension, value) {
    this.state.filters = { dimension, value, active: true };

    // Show filter warning
    const warning = document.querySelector('.filter-warning');
    if (warning) warning.style.display = 'flex';

    // Filter and update charts
    const filteredData = this.filterData(this.state.data.current, dimension, value);
    this.updateCharts(filteredData);
  },

  /**
   * Clear active filter
   */
  clearFilter() {
    this.state.filters = { dimension: null, value: null, active: false };

    // Reset UI
    const dimensionSelect = document.getElementById('filter-dimension');
    const valueContainer = document.getElementById('filter-value-container');
    const warning = document.querySelector('.filter-warning');

    if (dimensionSelect) dimensionSelect.value = '';
    if (valueContainer) valueContainer.style.display = 'none';
    if (warning) warning.style.display = 'none';

    // Restore original data
    if (this.state.data.current) {
      this.updateCharts(this.state.data.current);
    }
  },

  /**
   * Filter data by dimension and value
   * @param {Object} data - The data to filter
   * @param {string} dimension - The dimension to filter by
   * @param {string} value - The value to filter for
   * @returns {Object} Filtered data
   */
  filterData(data, dimension, value) {
    if (!data) return data;

    const filtered = { ...data };

    // Map dimension to data key
    const dimKey = dimension === 'os' ? 'oss' : dimension === 'browser' ? 'browsers' :
                   dimension === 'device' ? 'devices' : dimension === 'country' ? 'countries' :
                   dimension === 'referrer' ? 'referrers' : null;

    if (dimKey && filtered[dimKey]) {
      filtered[dimKey] = filtered[dimKey].filter(item => item.label === value);
    }

    return filtered;
  },

  /**
   * Set up export button handlers
   */
  setupExportButtons() {
    document.querySelectorAll('[data-export]').forEach(btn => {
      btn.addEventListener('click', (e) => {
        const exportType = e.currentTarget.dataset.export;
        this.exportChartData(exportType);
      });
    });
  },

  /**
   * Export chart data to CSV
   * @param {string} chartType - Type of data to export
   */
  exportChartData(chartType) {
    if (!this.state.data.current) {
      Utils.toast.error('No data to export');
      return;
    }

    const data = this.state.data.current;
    const projectName = document.getElementById('active-title')?.textContent || 'project';
    const timestamp = new Date().toISOString().split('T')[0];

    let csvData, filename;

    switch (chartType) {
      case 'full-report':
        csvData = this.formatFullReportCSV(data);
        filename = `${projectName}_full_report_${timestamp}.csv`;
        break;

      case 'top-pages':
        csvData = Utils.export.toCSV(
          data.topPages.map(p => ({ path: p.label, views: p.value })),
          ['path', 'views']
        );
        filename = `${projectName}_top_pages_${timestamp}.csv`;
        break;

      case 'browsers':
        csvData = Utils.export.toCSV(
          data.browsers.map(b => ({ browser: b.label, count: b.value })),
          ['browser', 'count']
        );
        filename = `${projectName}_browsers_${timestamp}.csv`;
        break;

      case 'os':
        csvData = Utils.export.toCSV(
          data.oss.map(o => ({ os: o.label, count: o.value })),
          ['os', 'count']
        );
        filename = `${projectName}_os_${timestamp}.csv`;
        break;

      case 'devices':
        csvData = Utils.export.toCSV(
          data.devices.map(d => ({ device: d.label, count: d.value })),
          ['device', 'count']
        );
        filename = `${projectName}_devices_${timestamp}.csv`;
        break;

      case 'referrers':
        csvData = Utils.export.toCSV(
          data.referrers.map(r => ({ referrer: r.label, count: r.value })),
          ['referrer', 'count']
        );
        filename = `${projectName}_referrers_${timestamp}.csv`;
        break;

      case 'countries':
        csvData = Utils.export.toCSV(
          data.countries.map(c => ({ country: c.label, visits: c.value })),
          ['country', 'visits']
        );
        filename = `${projectName}_countries_${timestamp}.csv`;
        break;

      case 'recent-activity':
        csvData = Utils.export.toCSV(
          data.lastVisits.map(v => ({
            path: v.path,
            city: v.city,
            timestamp: v.timestamp
          })),
          ['path', 'city', 'timestamp']
        );
        filename = `${projectName}_recent_activity_${timestamp}.csv`;
        break;

      default:
        Utils.toast.error('Unknown export type');
        return;
    }

    Utils.export.downloadCSV(csvData, filename);
    Utils.toast.success(`Exported ${filename}`);
  },

  /**
   * Format full report as CSV
   * @param {Object} data - Report data
   * @returns {string} CSV content
   */
  formatFullReportCSV(data) {
    const lines = [];

    // Summary section
    lines.push('ANALYTICS SUMMARY');
    lines.push(`Total Views,${data.totalViews}`);
    lines.push(`Unique Visitors,${data.uniqueVisitors}`);
    lines.push('');

    // Top Pages
    lines.push('TOP PAGES');
    lines.push('Path,Views');
    data.topPages.forEach(p => lines.push(`"${p.label}",${p.value}`));
    lines.push('');

    // Browsers
    lines.push('BROWSERS');
    lines.push('Browser,Count');
    data.browsers.forEach(b => lines.push(`"${b.label}",${b.value}`));
    lines.push('');

    // Operating Systems
    lines.push('OPERATING SYSTEMS');
    lines.push('OS,Count');
    data.oss.forEach(o => lines.push(`"${o.label}",${o.value}`));
    lines.push('');

    // Devices
    lines.push('DEVICES');
    lines.push('Device,Count');
    data.devices.forEach(d => lines.push(`"${d.label}",${d.value}`));
    lines.push('');

    // Countries
    lines.push('COUNTRIES');
    lines.push('Country,Visits');
    data.countries.forEach(c => lines.push(`"${c.label}",${c.value}`));
    lines.push('');

    // Recent Activity
    lines.push('RECENT ACTIVITY');
    lines.push('Path,City,Timestamp');
    data.lastVisits.forEach(v => lines.push(`"${v.path}","${v.city}","${v.timestamp}"`));

    return lines.join('\n');
  },

  /**
   * Load contribution calendar
   */
  async loadContributionCalendar() {
    if (!this.state.currentProjectId) return;

    try {
      const calendar = await Utils.api.fetch(`/admin/projects/${this.state.currentProjectId}/calendar`);
      this.renderContributionCalendar(calendar);
    } catch (error) {
      console.error('Failed to load contribution calendar:', error);
    }
  },

  /**
   * Render contribution calendar
   * @param {Object} calendar - Calendar data
   */
  renderContributionCalendar(calendar) {
    const container = document.getElementById('contribution-calendar');
    if (!container || !calendar.days.length) return;

    // Create date map for quick lookup
    const dateMap = new Map(calendar.days.map(day => [day.date, day]));

    // Calculate weeks needed
    const startDate = new Date(calendar.startDate);
    const endDate = new Date(calendar.endDate);
    const daysDiff = Math.floor((endDate - startDate) / (1000 * 60 * 60 * 24));
    const weeksCount = Math.ceil(daysDiff / 7);

    // Build grid HTML
    let html = '<div class="contribution-grid">';

    // Month labels
    html += '<div class="contribution-months">';
    const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    let currentMonth = -1;
    for (let week = 0; week < weeksCount; week++) {
      const weekDate = new Date(startDate);
      weekDate.setDate(startDate.getDate() + week * 7);
      const month = weekDate.getMonth();
      if (month !== currentMonth && week % 4 === 0) {
        html += `<span style="grid-column: ${week + 2}">${months[month]}</span>`;
        currentMonth = month;
      }
    }
    html += '</div>';

    // Day labels
    const dayLabels = ['Mon', 'Wed', 'Fri'];
    html += '<div class="contribution-days">';
    dayLabels.forEach((day, i) => {
      html += `<span style="grid-row: ${i * 2 + 3}">${day}</span>`;
    });
    html += '</div>';

    // Contribution cells
    html += '<div class="contribution-cells">';
    let currentDate = new Date(startDate);

    for (let week = 0; week < weeksCount; week++) {
      for (let day = 0; day < 7; day++) {
        const dateStr = currentDate.toISOString().split('T')[0];
        const dayData = dateMap.get(dateStr);
        const level = dayData?.level || 0;
        const visits = dayData?.visits || 0;
        const uniqueVisitors = dayData?.uniqueVisitors || 0;

        html += `
          <div class="contribution-cell"
               data-level="${level}"
               data-date="${dateStr}"
               data-visits="${visits}"
               data-unique="${uniqueVisitors}"
               title="${this.formatContributionTooltip(dateStr, visits, uniqueVisitors)}">
          </div>
        `;

        currentDate.setDate(currentDate.getDate() + 1);
        if (currentDate > endDate) break;
      }
      if (currentDate > endDate) break;
    }
    html += '</div></div>';

    container.innerHTML = html;
  },

  /**
   * Format contribution tooltip
   * @param {string} date - Date string
   * @param {number} visits - Number of visits
   * @param {number} uniqueVisitors - Number of unique visitors
   * @returns {string} Tooltip text
   */
  formatContributionTooltip(date, visits, uniqueVisitors) {
    const dateObj = new Date(date);
    const formatted = dateObj.toLocaleDateString('en-US', {
      weekday: 'short',
      month: 'short',
      day: 'numeric',
      year: 'numeric'
    });

    return visits === 0
      ? `${formatted}: No activity`
      : `${formatted}: ${visits} visits, ${uniqueVisitors} unique visitors`;
  },

  /**
   * Set up demo data generator modal and handlers
   */
  setupDemoDataGenerator() {
    const generateBtn = document.getElementById('generate-demo-btn');
    const modal = document.getElementById('demo-data-modal');
    const closeBtn = document.getElementById('close-demo-modal');
    const cancelBtn = document.getElementById('cancel-demo-btn');
    const confirmBtn = document.getElementById('confirm-demo-btn');

    if (!generateBtn || !modal) return;

    // Show button when project is selected
    const observer = new MutationObserver(() => {
      if (this.state.currentProjectId) {
        generateBtn.style.display = 'flex';
      } else {
        generateBtn.style.display = 'none';
      }
    });

    observer.observe(document.getElementById('dashboard-content'), {
      attributes: true,
      attributeFilter: ['class']
    });

    // Open modal
    generateBtn.addEventListener('click', () => {
      modal.classList.add('show');
    });

    // Close modal handlers
    const closeModal = () => {
      modal.classList.remove('show');
    };

    closeBtn.addEventListener('click', closeModal);
    cancelBtn.addEventListener('click', closeModal);

    // Generate demo data
    confirmBtn.addEventListener('click', async () => {
      const count = parseInt(document.getElementById('demo-count').value) || 500;
      await this.generateDemoData(count);
      closeModal();
    });
  },

  /**
   * Generate demo data for current project
   * @param {number} count - Number of events to generate
   */
  async generateDemoData(count) {
    if (!this.state.currentProjectId) {
      Utils.toast.error('No project selected');
      return;
    }

    if (count < 0 || count > 3000) {
      Utils.toast.error('Count must be between 0 and 3000');
      return;
    }

    try {
      Utils.toast.info(`Generating ${count} demo events...`);

      const timeScope = parseInt(document.getElementById('demo-time-scope')?.value) || 30;

      const response = await fetch(`/admin/projects/${this.state.currentProjectId}/demo-data`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ count, timeScope })
      });

      if (!response.ok) {
        throw new Error('Failed to generate demo data');
      }

      const result = await response.json();
      Utils.toast.success(`Generated ${result.generated} demo events`);

      // Reload project data
      await this.refreshReport();
    } catch (error) {
      console.error('Demo data generation failed:', error);
      Utils.toast.error('Failed to generate demo data');
    }
  },

  /**
   * Setup inspirational message display in sidebar
   */
  setupInspirationalMessage() {
    const messages = [
      "Data tells stories. Listen carefully.",
      "Privacy matters. You're doing it right.",
      "Small insights, big impact.",
      "Know your visitors, respect their privacy.",
      "Analytics without compromise.",
      "Understanding beats guessing every time.",
      "Your data, your rules.",
      "Minimal tracking, maximum insight.",
      "Numbers with meaning.",
      "Privacy-first is future-proof.",
    ];

    const container = document.getElementById('sidebar-inspiration');
    if (!container) return;

    const randomIndex = Math.floor(Math.random() * messages.length);
    container.textContent = messages[randomIndex];
  },

  /**
   * Setup modal close handlers
   */
  setupModals() {
    // Generic modal closer for backdrop and X button
    document.querySelectorAll('.modal-backdrop, .modal-close').forEach(el => {
      el.addEventListener('click', (e) => {
        const modal = e.target.closest('.modal');
        if (modal) modal.classList.remove('show');
      });
    });

    // Prevent content clicks from closing modal
    document.querySelectorAll('.modal-content').forEach(el => {
      el.addEventListener('click', (e) => e.stopPropagation());
    });
  },

  /**
   * Setup settings panel with project information
   */
  setupSettingsPanel() {
    const modal = document.getElementById('settings-modal');
    const openBtn = document.getElementById('open-settings-btn');
    const saveBtn = document.getElementById('save-settings');
    const cancelBtn = document.getElementById('cancel-settings');
    const copyProjectKeyBtn = document.getElementById('copy-project-api-key');

    if (!modal || !openBtn) return;

    openBtn.addEventListener('click', () => {
      // Show modal immediately
      modal.classList.add('show');

      // Load dashboard settings (synchronous)
      const settings = SettingsManager.load();
      document.getElementById('setting-time-format').value = settings.timeFormat;
      document.getElementById('setting-date-format').value = settings.dateFormat;
      document.getElementById('setting-heatmap-colors').value = settings.heatmapColors;

      // Load project information asynchronously (don't block modal from showing)
      const projectSection = document.getElementById('project-settings-section');
      if (this.state.currentProjectId && projectSection) {
        projectSection.style.display = 'block';
        document.getElementById('setting-project-name').value = 'Loading...';
        document.getElementById('setting-project-domain').value = 'Loading...';
        document.getElementById('setting-project-api-key').textContent = 'Loading...';

        // Load data in background
        Utils.api.fetch('/admin/projects').then(projects => {
          const currentProject = projects.find(p => p.id === this.state.currentProjectId);

          if (currentProject) {
            document.getElementById('setting-project-name').value = currentProject.name;
            document.getElementById('setting-project-domain').value = currentProject.domain;
            document.getElementById('setting-project-api-key').textContent = currentProject.apiKey;
            feather.replace();
          } else {
            projectSection.style.display = 'none';
          }
        }).catch(error => {
          console.error('Failed to load project info:', error);
          projectSection.style.display = 'none';
        });
      } else if (projectSection) {
        projectSection.style.display = 'none';
      }

      feather.replace();
    });

    saveBtn.addEventListener('click', async () => {
      // Save dashboard settings
      const settings = {
        timeFormat: document.getElementById('setting-time-format').value,
        dateFormat: document.getElementById('setting-date-format').value,
        heatmapColors: document.getElementById('setting-heatmap-colors').value,
      };
      SettingsManager.save(settings);

      // Save project name if changed
      if (this.state.currentProjectId) {
        const newName = document.getElementById('setting-project-name')?.value.trim();
        if (newName) {
          try {
            // Update project name via API
            await Utils.api.post(`/admin/projects/${this.state.currentProjectId}`, {
              name: newName
            });

            // Update the title display
            const titleEl = document.getElementById('active-title');
            if (titleEl) titleEl.textContent = newName;

            // Reload projects list
            await this.loadProjects();
          } catch (error) {
            console.error('Failed to update project name:', error);
            Utils.toast.error('Failed to update project name');
            return;
          }
        }
      }

      modal.classList.remove('show');
      Utils.toast.success('Settings saved');

      // Refresh dashboard to apply settings
      this.refreshReport();
      this.updateTimeFilterLabels();
    });

    if (cancelBtn) {
      cancelBtn.addEventListener('click', () => modal.classList.remove('show'));
    }

    // Copy project API key handler
    if (copyProjectKeyBtn) {
      copyProjectKeyBtn.addEventListener('click', () => {
        const key = document.getElementById('setting-project-api-key')?.textContent;
        if (key) {
          navigator.clipboard.writeText(key);
          Utils.toast.success('API key copied!');
        }
      });
    }

    // Listen for settings changes and update heatmap colors
    window.addEventListener('settingsChanged', (e) => {
      const calendar = document.getElementById('contribution-calendar');
      if (calendar) {
        calendar.className = `heatmap-${e.detail.heatmapColors}`;
      }
    });
  },

  /**
   * Setup raw events viewer with pagination
   */
  setupRawEventsViewer() {
    const modal = document.getElementById('raw-events-modal');
    const openBtn = document.getElementById('view-raw-events-btn');

    if (!modal || !openBtn) return;

    openBtn.addEventListener('click', () => {
      modal.classList.add('show');
      this.loadRawEvents(0);
    });

    document.getElementById('raw-events-next')?.addEventListener('click', () => {
      const page = parseInt(modal.dataset.page || '0');
      this.loadRawEvents(page + 1);
    });

    document.getElementById('raw-events-prev')?.addEventListener('click', () => {
      const page = parseInt(modal.dataset.page || '0');
      if (page > 0) this.loadRawEvents(page - 1);
    });

    ['raw-events-type', 'raw-events-sort'].forEach(id => {
      document.getElementById(id)?.addEventListener('change', () => this.loadRawEvents(0));
    });

    document.getElementById('export-raw-events')?.addEventListener('click', () => {
      this.exportRawEvents();
    });
  },

  /**
   * Load raw events data with pagination and filtering
   * @param {number} page - Page number to load
   */
  async loadRawEvents(page = 0) {
    if (!this.state.currentProjectId) return;

    const modal = document.getElementById('raw-events-modal');
    const tbody = document.getElementById('raw-events-tbody');
    const limit = 50;

    const filterType = document.getElementById('raw-events-type')?.value || '';
    const sortValue = document.getElementById('raw-events-sort')?.value || 'timestamp-desc';
    const [sortBy, order] = sortValue.split('-');

    try {
      const params = new URLSearchParams({
        page: page.toString(),
        limit: limit.toString(),
        sortBy,
        order,
        ...(filterType && { filter: filterType })
      });

      const data = await Utils.api.fetch(
        `/admin/projects/${this.state.currentProjectId}/events?${params}`
      );

      if (!data.events || data.events.length === 0) {
        tbody.innerHTML = '<tr><td colspan="7" style="text-align: center; color: var(--color-text-muted);">No events found</td></tr>';
      } else {
        tbody.innerHTML = data.events.map(e => `
          <tr>
            <td>${Utils.time.formatTime(e.timestamp)}</td>
            <td><span class="badge badge-${e.eventType === 'pageview' ? 'primary' : 'secondary'}">${e.eventType}</span></td>
            <td>${e.path}</td>
            <td>${e.city || 'Unknown'}, ${e.country || 'Unknown'}</td>
            <td>${e.browser || 'Unknown'}</td>
            <td>${e.device || 'Unknown'}</td>
            <td>${e.duration || 0}s</td>
          </tr>
        `).join('');
      }

      modal.dataset.page = page.toString();
      modal.dataset.totalEvents = data.total.toString();

      document.getElementById('raw-events-info').textContent =
        `Showing ${page * limit + 1}-${Math.min((page + 1) * limit, data.total)} of ${data.total}`;

      document.getElementById('raw-events-prev').disabled = page === 0;
      document.getElementById('raw-events-next').disabled = (page + 1) * limit >= data.total;

      feather.replace();
    } catch (error) {
      console.error('Failed to load raw events:', error);
      Utils.toast.error('Failed to load events');
      tbody.innerHTML = '<tr><td colspan="7" style="text-align: center; color: var(--color-error);">Error loading events</td></tr>';
    }
  },

  /**
   * Export raw events to CSV
   */
  async exportRawEvents() {
    if (!this.state.currentProjectId) return;

    try {
      const filterType = document.getElementById('raw-events-type')?.value || '';
      const sortValue = document.getElementById('raw-events-sort')?.value || 'timestamp-desc';
      const [sortBy, order] = sortValue.split('-');

      const params = new URLSearchParams({
        page: '0',
        limit: '10000', // Get more for export
        sortBy,
        order,
        ...(filterType && { filter: filterType })
      });

      const data = await Utils.api.fetch(
        `/admin/projects/${this.state.currentProjectId}/events?${params}`
      );

      const csvData = Utils.export.toCSV(
        data.events.map(e => ({
          timestamp: e.timestamp,
          type: e.eventType,
          path: e.path,
          referrer: e.referrer || '',
          country: e.country || '',
          city: e.city || '',
          browser: e.browser || '',
          os: e.os || '',
          device: e.device || '',
          duration: e.duration || 0
        })),
        ['timestamp', 'type', 'path', 'referrer', 'country', 'city', 'browser', 'os', 'device', 'duration']
      );

      const projectName = document.getElementById('active-title')?.textContent || 'project';
      const timestamp = new Date().toISOString().split('T')[0];
      const filename = `${projectName}_raw_events_${timestamp}.csv`;

      Utils.export.downloadCSV(csvData, filename);
      Utils.toast.success(`Exported ${data.events.length} events`);
    } catch (error) {
      console.error('Failed to export raw events:', error);
      Utils.toast.error('Failed to export events');
    }
  },

  /**
   * Setup geographic drill-down functionality
   */
  setupGeographicDrillDown() {
    const backBtn = document.getElementById('geo-back-btn');
    if (!backBtn) return;

    backBtn.addEventListener('click', () => {
      this.state.geoState.view = 'countries';
      this.state.geoState.selectedCountry = null;
      document.getElementById('geo-title').textContent = 'Geographic distribution';
      backBtn.style.display = 'none';

      // Restore countries view
      if (this.state.data.current?.countries) {
        const topCountries = Utils.aggregate.groupTopN(this.state.data.current.countries, 10);
        ChartManager.createBarChart('chart-countries', topCountries);
      }
    });
  },

  /**
   * Drill down to show cities for a specific country
   * @param {string} countryName - Country name to drill down into
   */
  async drillDownToCountry(countryName) {
    this.state.geoState.view = 'cities';
    this.state.geoState.selectedCountry = countryName;

    // Filter to cities in this country
    const citiesData = this.state.data.current.lastVisits
      .filter(v => v.country === countryName)
      .reduce((acc, v) => {
        const city = v.city || 'Unknown';
        acc[city] = (acc[city] || 0) + 1;
        return acc;
      }, {});

    const citiesArray = Object.entries(citiesData)
      .map(([label, value]) => ({ label, value }))
      .sort((a, b) => b.value - a.value)
      .slice(0, 10);

    document.getElementById('geo-title').textContent = `Cities in ${countryName}`;
    document.getElementById('geo-back-btn').style.display = 'flex';

    ChartManager.createBarChart('chart-countries', citiesArray);
  },

  /**
   * Update time filter labels - now just updates the date range display card
   */
  updateTimeFilterLabels() {
    this.updateDateRangeDisplay();
  },

  /**
   * Update the date range display card below the time picker
   */
  updateDateRangeDisplay() {
    const displayEl = document.getElementById('date-range-display');
    const filterEl = document.getElementById('time-filter');
    if (!displayEl || !filterEl) return;

    const now = new Date();
    const filterValue = filterEl.value || '7d';

    const ranges = {
      '24h': { hours: 24 },
      '3d': { days: 3 },
      '7d': { days: 7 },
      '30d': { days: 30 },
      '365d': { days: 365 }
    };

    const range = ranges[filterValue];
    if (!range) return;

    const start = new Date(now);
    if (range.hours) start.setHours(start.getHours() - range.hours);
    if (range.days) start.setDate(start.getDate() - range.days);

    const settings = typeof SettingsManager !== 'undefined' ? SettingsManager.load() : { dateFormat: 'MM/DD/YYYY' };
    const formatDate = (d) => {
      const day = String(d.getDate()).padStart(2, '0');
      const month = String(d.getMonth() + 1).padStart(2, '0');
      const year = d.getFullYear();

      if (settings.dateFormat === 'DD/MM/YYYY') return `${day}/${month}/${year}`;
      if (settings.dateFormat === 'YYYY-MM-DD') return `${year}-${month}-${day}`;
      return `${month}/${day}/${year}`;
    };

    displayEl.textContent = `${formatDate(start)} — ${formatDate(now)}`;
  },
};

// Initialize dashboard when DOM is ready
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', () => Dashboard.init());
} else {
  Dashboard.init();
}

// Clean up on page unload
window.addEventListener('beforeunload', () => {
  Dashboard.stopLiveFeed();
});
