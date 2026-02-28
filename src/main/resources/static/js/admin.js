/**
 * Main Admin Dashboard Logic
 * Handles project selection, data fetching, and UI updates
 */

/**
 * Animate a number counting up from 0 to target
 * Respects prefers-reduced-motion
 */
function animateCountUp(element, target, duration = 800, formatter) {
  if (!element) return;
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    element.textContent = formatter ? formatter(target) : target.toLocaleString();
    return;
  }
  const start = performance.now();
  function update(now) {
    const elapsed = now - start;
    const progress = Math.min(elapsed / duration, 1);
    const eased = 1 - Math.pow(1 - progress, 3); // ease-out cubic
    const current = Math.round(target * eased);
    element.textContent = formatter ? formatter(current) : current.toLocaleString();
    if (progress < 1) {
      requestAnimationFrame(update);
    }
  }
  requestAnimationFrame(update);
}

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
    // Initialize global error handling
    Utils.errors.init();

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

    // Set up filters
    this.setupFilters();

    // Set up collapsible dashboard sections
    this.setupSectionToggles();

    // Set up demo data generator
    this.setupDemoDataGenerator();

    // Set up realtime demo toggle
    this.setupRealtimeDemo();

    // New features: UI/UX improvements
    this.setupModals();
    this.setupSettingsPanel();
    this.setupRawEventsViewer();
    this.setupGeographicDrillDown();
    this.updateTimeFilterLabels();

    // Initialize segments modal
    this.setupSegmentsInit();

    // Initialize onboarding for new users
    this.setupOnboarding();

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
      ? '<i class="ri-moon-line"></i>'
      : '<i class="ri-sun-line"></i>';
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
   * Setup sign out button with styled modal
   */
  setupSignOut() {
    const signOutBtn = document.getElementById('sign-out-btn');
    const modal = document.getElementById('logout-modal');
    const confirmBtn = document.getElementById('confirm-logout');
    const cancelBtn = document.getElementById('cancel-logout');

    if (!signOutBtn || !modal) return;

    signOutBtn.addEventListener('click', () => {
      modal.classList.add('show');
    });

    cancelBtn?.addEventListener('click', () => {
      modal.classList.remove('show');
    });

    confirmBtn?.addEventListener('click', async () => {
      // Stop live feed updates
      this.stopLiveFeed();
      Utils.cache.clearAll();

      try {
        await fetch('/api/logout', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
        });
        window.location.href = '/login';
      } catch (error) {
        console.error('Logout error:', error);
        window.location.href = '/login';
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
      const section = document.getElementById('projects-section');
      if (!menu) return;

      // Handle empty state (no projects)
      if (!projects || projects.length === 0) {
        // Hide the entire projects section when empty
        if (section) section.style.display = 'none';
        menu.innerHTML = '';

        // Hide dashboard content when no projects
        const dashboardContent = document.getElementById('dashboard-content');
        if (dashboardContent) {
          Utils.dom.hide(dashboardContent);
        }
        return;
      }

      // Show the projects section
      if (section) section.style.display = '';

      // Show projects list with domain info
      menu.innerHTML = projects
        .map(
          (project) => `
        <div
          class="project-menu__item"
          data-id="${project.id}"
          onclick="Dashboard.selectProject('${project.id}', '${Utils.escapeHtml(project.name).replace(/'/g, "\\'")}')"
        >
          <div class="project-menu__info">
            <div class="project-menu__name">${Utils.escapeHtml(project.name)}</div>
            <div class="project-menu__domain">${Utils.escapeHtml(project.domain)}</div>
          </div>
          <button class="project-menu__delete" onclick="event.stopPropagation(); Dashboard.confirmDeleteProject('${project.id}', '${Utils.escapeHtml(project.name).replace(/'/g, "\\'")}')" title="Delete project" aria-label="Delete ${Utils.escapeHtml(project.name)}">
            <i class="ri-delete-bin-line"></i>
          </button>
        </div>
      `
        )
        .join('');


      // Restore active state if a project is selected
      if (this.state.currentProjectId) {
        document.querySelectorAll('.project-menu__item').forEach((item) => {
          if (item.dataset.id === this.state.currentProjectId) {
            item.classList.add('active');
          }
        });
      }
    } catch (error) {
      console.error('Failed to load projects:', error);
      Utils.toast.error('Failed to load projects');
    }
  },

  /**
   * Show confirmation modal before deleting a project
   */
  confirmDeleteProject(projectId, projectName) {
    const modal = document.getElementById('delete-project-modal');
    if (!modal) return;

    document.getElementById('delete-project-name').textContent = projectName;
    modal.dataset.projectId = projectId;
    modal.classList.add('show');
  },

  /**
   * Delete a project after confirmation
   */
  async deleteProject(projectId) {
    try {
      await fetch(`/admin/projects/${projectId}`, {
        method: 'DELETE',
        headers: Utils.auth.getHeader(),
      });

      const modal = document.getElementById('delete-project-modal');
      if (modal) modal.classList.remove('show');

      // If we deleted the currently selected project, clear the view
      if (this.state.currentProjectId === projectId) {
        this.state.currentProjectId = null;
        const dashboardContent = document.getElementById('dashboard-content');
        if (dashboardContent) Utils.dom.hide(dashboardContent);
      }

      Utils.cache.clear('/admin/projects');
      await this.loadProjects();
      Utils.toast.success('Project deleted');
    } catch (error) {
      console.error('Failed to delete project:', error);
      Utils.toast.error('Failed to delete project');
    }
  },

  /**
   * Generate a UUID-style API key (32-char hex, matching server format)
   */
  generateApiKey() {
    const hex = '0123456789abcdef';
    let key = '';
    const array = new Uint8Array(16);
    crypto.getRandomValues(array);
    array.forEach(b => {
      key += hex[b >> 4] + hex[b & 0x0f];
    });
    return key;
  },

  /**
   * Normalize and validate a domain string
   * @param {string} input - Raw domain input
   * @returns {{ domain: string, valid: boolean, error: string }}
   */
  validateDomain(input) {
    let domain = input.trim();

    // Strip protocol
    domain = domain.replace(/^https?:\/\//i, '');
    // Strip www.
    domain = domain.replace(/^www\./i, '');
    // Strip trailing slashes and paths
    domain = domain.replace(/\/.*$/, '');
    // Strip port
    domain = domain.replace(/:\d+$/, '');

    if (!domain) {
      return { domain: '', valid: false, error: 'Domain is required' };
    }

    // Basic domain pattern: something.tld or sub.something.tld (also allow localhost)
    const domainPattern = /^(localhost|([a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?\.)+[a-zA-Z]{2,})$/;
    if (!domainPattern.test(domain)) {
      return { domain, valid: false, error: 'Enter a valid domain (e.g. example.com)' };
    }

    return { domain, valid: true, error: '' };
  },

  /**
   * Show enhanced two-step modal to create a new project
   */
  async showCreateProjectDialog() {
    const modal = document.getElementById('create-project-modal');
    if (!modal) return;

    const nameInput = document.getElementById('project-name');
    const domainInput = document.getElementById('project-domain');
    const confirmBtn = document.getElementById('confirm-create-project');
    const cancelBtn = document.getElementById('cancel-create-project');
    const step1 = document.getElementById('create-step-1');
    const step2 = document.getElementById('create-step-2');
    const titleEl = document.getElementById('create-project-title');
    const stepDots = document.querySelectorAll('#create-step-indicator .step-dot');

    // Reset to step 1
    if (nameInput) nameInput.value = '';
    if (domainInput) domainInput.value = '';
    if (step1) step1.style.display = '';
    if (step2) step2.style.display = 'none';
    if (confirmBtn) confirmBtn.textContent = 'Create project';
    if (titleEl) titleEl.textContent = 'Create new project';
    stepDots.forEach((dot, i) => dot.classList.toggle('active', i === 0));

    // Clear validation errors
    document.querySelectorAll('.field-error').forEach(el => el.textContent = '');
    document.querySelectorAll('.input--error').forEach(el => el.classList.remove('input--error'));

    // Pre-generate API key
    const preGeneratedKey = this.generateApiKey();
    const apiKeyEl = document.getElementById('generated-api-key');
    if (apiKeyEl) apiKeyEl.textContent = preGeneratedKey;

    this._createProjectStep = 1;
    this._createProjectKey = preGeneratedKey;
    this._createProjectId = null;

    modal.classList.add('show');

    // Clean up old handlers
    if (this._createProjectHandlers) {
      if (confirmBtn && this._createProjectHandlers.confirm) {
        confirmBtn.removeEventListener('click', this._createProjectHandlers.confirm);
      }
      if (cancelBtn && this._createProjectHandlers.cancel) {
        cancelBtn.removeEventListener('click', this._createProjectHandlers.cancel);
      }
    }
    this._createProjectHandlers = {};

    // Domain input: live normalization feedback
    const domainHandler = () => {
      const raw = domainInput.value;
      const errorEl = document.getElementById('project-domain-error');
      if (raw.trim()) {
        const result = this.validateDomain(raw);
        if (!result.valid) {
          domainInput.classList.add('input--error');
          if (errorEl) errorEl.textContent = result.error;
        } else {
          domainInput.classList.remove('input--error');
          if (errorEl) errorEl.textContent = '';
          // Show normalized hint if different
          if (result.domain !== raw.trim()) {
            if (errorEl) {
              errorEl.textContent = '';
              errorEl.innerHTML = `<span class="field-hint-normalized">Will be saved as: ${Utils.escapeHtml(result.domain)}</span>`;
            }
          }
        }
      } else {
        domainInput.classList.remove('input--error');
        if (errorEl) errorEl.textContent = '';
      }
    };
    domainInput?.addEventListener('input', domainHandler);

    // Cancel handler
    const cancelHandler = () => {
      modal.classList.remove('show');
      domainInput?.removeEventListener('input', domainHandler);
    };
    if (cancelBtn) {
      cancelBtn.addEventListener('click', cancelHandler);
      this._createProjectHandlers.cancel = cancelHandler;
    }

    // Copy handlers
    document.getElementById('copy-api-key')?.addEventListener('click', () => {
      navigator.clipboard.writeText(preGeneratedKey);
      Utils.toast.success('API key copied!');
    });

    document.getElementById('copy-api-key-final')?.addEventListener('click', () => {
      navigator.clipboard.writeText(preGeneratedKey);
      Utils.toast.success('API key copied!');
    });

    document.getElementById('copy-snippet')?.addEventListener('click', () => {
      const snippetEl = document.getElementById('tracking-snippet-code');
      if (snippetEl) {
        navigator.clipboard.writeText(snippetEl.textContent);
        Utils.toast.success('Tracking snippet copied!');
      }
    });

    // Confirm handler
    const confirmHandler = async () => {
      if (this._createProjectStep === 2) {
        // Step 2: Go to dashboard
        modal.classList.remove('show');
        domainInput?.removeEventListener('input', domainHandler);
        if (this._createProjectId) {
          const name = nameInput?.value.trim() || 'New project';
          this.selectProject(this._createProjectId, name);
        }
        return;
      }

      // Step 1: Validate and create
      const name = nameInput?.value.trim() || '';
      const domainResult = this.validateDomain(domainInput?.value || '');
      const nameErrorEl = document.getElementById('project-name-error');
      const domainErrorEl = document.getElementById('project-domain-error');

      let hasError = false;

      if (!name) {
        nameInput?.classList.add('input--error');
        if (nameErrorEl) nameErrorEl.textContent = 'Project name is required';
        hasError = true;
      } else {
        nameInput?.classList.remove('input--error');
        if (nameErrorEl) nameErrorEl.textContent = '';
      }

      if (!domainResult.valid) {
        domainInput?.classList.add('input--error');
        if (domainErrorEl) domainErrorEl.textContent = domainResult.error;
        hasError = true;
      } else {
        domainInput?.classList.remove('input--error');
        if (domainErrorEl) domainErrorEl.textContent = '';
      }

      if (hasError) return;

      // Disable button during creation
      confirmBtn.disabled = true;
      confirmBtn.textContent = 'Creating...';

      try {
        await Utils.api.post('/admin/projects', {
          name,
          domain: domainResult.domain,
          apiKey: this._createProjectKey,
        });

        // Refresh projects sidebar immediately
        Utils.cache.clear('/admin/projects');
        await this.loadProjects();

        // Find the new project ID
        const projects = await Utils.api.fetch('/admin/projects');
        const newProject = projects?.find(p => p.apiKey === this._createProjectKey);
        this._createProjectId = newProject?.id || null;

        // Transition to step 2
        this._createProjectStep = 2;
        if (step1) step1.style.display = 'none';
        if (step2) step2.style.display = '';
        if (titleEl) titleEl.textContent = 'Project ready!';
        if (confirmBtn) {
          confirmBtn.textContent = 'Go to dashboard';
          confirmBtn.disabled = false;
        }
        stepDots.forEach((dot, i) => dot.classList.toggle('active', i === 1));

        // Fill in tracking snippet
        const origin = window.location.origin;
        const snippet = `<script async src="${origin}/tracker/tracker.min.js"\n  data-project-key="${this._createProjectKey}"><\/script>`;
        const snippetEl = document.getElementById('tracking-snippet-code');
        if (snippetEl) snippetEl.textContent = snippet;

        // Fill in final API key
        const finalKeyEl = document.getElementById('generated-api-key-final');
        if (finalKeyEl) finalKeyEl.textContent = this._createProjectKey;

        Utils.toast.success('Project created!');
          } catch (error) {
        console.error('Failed to create project:', error);
        Utils.toast.error('Failed to create project');
        confirmBtn.disabled = false;
        confirmBtn.textContent = 'Create project';
      }
    };

    if (confirmBtn) {
      confirmBtn.addEventListener('click', confirmHandler);
      this._createProjectHandlers.confirm = confirmHandler;
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

    // If globe view is active, restart polling for the new project
    if (MapManager.currentView === 'globe' && GlobeManager.instance) {
      GlobeManager.startPolling(this.state.currentProjectId, GlobeManager.currentRange);
    }
  },

  /**
   * Setup time filter dropdown
   */
  setupTimeFilter() {
    const filterEl = document.getElementById('time-filter');
    if (!filterEl) return;

    const debouncedRefresh = Utils.debounce(() => {
      this.refreshReport();
    }, 300);

    filterEl.addEventListener('change', (e) => {
      this.state.currentFilter = e.target.value;
      this.updateDateRangeDisplay();
      debouncedRefresh();
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

      // Update primary UI immediately
      this.updateStats(data.current);
      this.updateComparisons(data.current, data.previous);
      this.updateSparklines(data.timeSeries);
      this.updateCharts(data.current);

      // Hide loading state after primary data renders
      this.hideLoadingState();

      // Load secondary data in parallel (non-blocking)
      Promise.allSettled([
        this.loadContributionCalendar(),
        this.loadGoalsAndFunnels(),
        this.loadSegments(),
        this.loadWebhooks(),
        this.loadEmailReports(),
        this.loadRevenue()
      ]);
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
    // Show skeletons on stat card values
    document.querySelectorAll('.stat-card__value').forEach(el => {
      el.dataset.originalText = el.textContent;
      el.innerHTML = '<span class="skeleton skeleton-text" style="width: 80px; display: inline-block;">&nbsp;</span>';
    });

    // Show skeletons on chart containers
    document.querySelectorAll('.chart-card__container').forEach(el => {
      if (!el.querySelector('.skeleton-chart')) {
        const skeleton = document.createElement('div');
        skeleton.className = 'skeleton skeleton-chart';
        skeleton.style.width = '100%';
        el.prepend(skeleton);
      }
    });

    // Dim comparisons
    document.querySelectorAll('.stat-card__comparison').forEach(el => {
      el.style.opacity = '0.3';
    });
  },

  /**
   * Hide loading states
   */
  hideLoadingState() {
    // Remove chart skeletons
    document.querySelectorAll('.skeleton-chart').forEach(el => el.remove());

    // Restore comparison opacity
    document.querySelectorAll('.stat-card__comparison').forEach(el => {
      el.style.opacity = '';
    });
  },

  /**
   * Update stat cards
   * @param {Object} data - Report data
   */
  updateStats(data) {
    // Animate total views
    const viewsEl = document.getElementById('total-views');
    animateCountUp(viewsEl, data.totalViews || 0, 800, v => Utils.format.number(v));

    // Animate unique visitors
    const visitorsEl = document.getElementById('unique-visitors');
    animateCountUp(visitorsEl, data.uniqueVisitors || 0, 800, v => Utils.format.number(v));

    // Animate bounce rate
    const bounceEl = document.getElementById('bounce-rate');
    const bounceVal = data.bounceRate != null ? Math.round(data.bounceRate * 10) : 0;
    animateCountUp(bounceEl, bounceVal, 800, v => (v / 10).toFixed(1) + '%');

    // Animate total sessions
    const sessionsEl = document.getElementById('total-sessions');
    if (sessionsEl) animateCountUp(sessionsEl, data.totalSessions || 0, 800, v => Utils.format.number(v));

    // Animate avg session duration
    const durationEl = document.getElementById('avg-session-duration');
    if (durationEl) {
      const durSec = Math.round(data.avgSessionDuration || 0);
      animateCountUp(durationEl, durSec, 800, v => Utils.format.duration(v));
    }

    // Animate conversion rate
    const convRateEl = document.getElementById('overall-conversion-rate');
    if (convRateEl) {
      const convVal = data.conversionRate != null ? Math.round(data.conversionRate * 10) : 0;
      animateCountUp(convRateEl, convVal, 800, v => (v / 10).toFixed(1) + '%');
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

    // Sessions comparison
    const sessionsCompEl = document.getElementById('sessions-comparison');
    if (sessionsCompEl && current.totalSessions != null && previous.totalSessions != null) {
      const sessionsChange = this.calculatePercentChange(current.totalSessions, previous.totalSessions);
      const { text, className } = Utils.format.percentageChange(sessionsChange);
      sessionsCompEl.innerHTML = `${text} vs previous period`;
      sessionsCompEl.className = `stat-card__comparison ${className}`;
    }

    // Session duration comparison
    const durationCompEl = document.getElementById('session-duration-comparison');
    if (durationCompEl && current.avgSessionDuration != null && previous.avgSessionDuration != null) {
      const durationChange = this.calculatePercentChange(current.avgSessionDuration, previous.avgSessionDuration);
      const { text, className } = Utils.format.percentageChange(durationChange);
      durationCompEl.innerHTML = `${text} vs previous period`;
      durationCompEl.className = `stat-card__comparison ${className}`;
    }

    // Conversion rate comparison
    const convCompEl = document.getElementById('conversion-rate-comparison');
    if (convCompEl && current.conversionRate != null && previous.conversionRate != null) {
      const convChange = this.calculatePercentChange(current.conversionRate, previous.conversionRate);
      const { text, className } = Utils.format.percentageChange(convChange);
      convCompEl.innerHTML = `${text} vs previous period`;
      convCompEl.className = `stat-card__comparison ${className}`;
    }
  },

  /**
   * Update all charts
   * @param {Object} data - Report data
   */
  updateCharts(data) {
    // Top Pages - Bar Chart with show more
    const pagesEl = document.getElementById('chart-pages');
    if (data.topPages?.length && pagesEl) {
      const allPages = Utils.aggregate.groupTopN(data.topPages, 10);
      this.renderBarChartWithShowMore('chart-pages', allPages, 5);
    } else if (pagesEl) {
      Utils.dom.showEmptyState(pagesEl.parentElement, { icon: '&#128196;', message: 'No page views yet', hint: 'Page views will appear once visitors arrive' });
    }

    // Referrers - Icon Bar Chart with show more
    const referrersEl = document.getElementById('chart-referrers');
    if (data.referrers?.length && referrersEl) {
      const allReferrers = Utils.aggregate.groupTopN(data.referrers, 10);
      this.renderIconBarChartWithShowMore('chart-referrers', allReferrers, 'referrer', 5);
    } else if (referrersEl) {
      Utils.dom.showEmptyState(referrersEl.parentElement, { icon: '&#128279;', message: 'No referrer data', hint: 'Referrer data appears when visitors come from external sites' });
    }

    // Browsers, OS, Devices — charts with view toggles
    const chartConfigs = [
      { key: 'browsers', dataField: 'browsers', stateKey: 'browsersData', iconType: 'browser', emptyIcon: '&#127760;', emptyMsg: 'No browser data' },
      { key: 'os', dataField: 'oss', stateKey: 'osData', iconType: 'os', emptyIcon: '&#128187;', emptyMsg: 'No OS data' },
      { key: 'devices', dataField: 'devices', stateKey: 'devicesData', iconType: 'device', emptyIcon: '&#128241;', emptyMsg: 'No device data' },
    ];

    chartConfigs.forEach(({ key, dataField, stateKey, iconType, emptyIcon, emptyMsg }) => {
      const el = document.getElementById(`chart-${key}`);
      if (data[dataField]?.length && el) {
        this.state[stateKey] = data[dataField];
        const topItems = Utils.aggregate.groupTopN(data[dataField], 8);
        const chartType = localStorage.getItem(`chart-view-${key}`) || 'doughnut';
        this.renderChartByType(`chart-${key}`, topItems, chartType, iconType);
      } else if (el) {
        Utils.dom.showEmptyState(el.parentElement, { icon: emptyIcon, message: emptyMsg });
      }
    });

    // Custom Events - Bar Chart (show section only when data exists)
    const customEventsSection = document.getElementById('custom-events-section');
    if (data.customEvents?.length && document.getElementById('chart-custom-events')) {
      if (customEventsSection) customEventsSection.style.display = '';
      ChartManager.createBarChart('chart-custom-events', data.customEvents);

      // Update annotation with total count and top event
      const annotation = document.getElementById('custom-events-annotation');
      if (annotation) {
        const totalEvents = data.customEvents.reduce((sum, e) => sum + e.value, 0);
        const topEvent = data.customEvents[0];
        annotation.textContent = `${Utils.format.number(totalEvents)} total \u00B7 Top: ${topEvent.label}`;
      }
    } else {
      if (customEventsSection) customEventsSection.style.display = 'none';
    }

    // Countries - Geographic visualization
    const countriesEl = document.getElementById('chart-countries');
    if (data.countries?.length && countriesEl) {
      if (this.state.geoState?.view === 'cities') {
        return;
      }

      MapManager.createMap('chart-countries', data.countries);

      if (!MapManager._toggleInitialized) {
        MapManager.initViewToggle(data.countries);
        MapManager._toggleInitialized = true;
      } else if (MapManager.currentView === 'map' && MapManager.leafletMap) {
        MapManager.updateLeafletMap(data.countries);
      }

      // Attach drill-down listener only once
      if (!this._geoDrillDownListenerAttached) {
        this._geoDrillDownListenerAttached = true;
        window.addEventListener('chartBarClick', (e) => {
          if (e.detail.chartId === 'chart-countries' && this.state.geoState?.view === 'countries') {
            this.drillDownToCountry(e.detail.label);
          }
        });
      }
    } else if (countriesEl) {
      Utils.dom.showEmptyState(countriesEl.parentElement, { icon: '&#127758;', message: 'No geographic data', hint: 'Geographic data requires a GeoIP database' });
    }

    // Time Series - Line Chart (uses pre-aggregated time series from comparison report)
    const timeseriesEl = document.getElementById('chart-timeseries');
    const tsData = this.state.data?.timeSeries;
    if (tsData?.length && timeseriesEl) {
      const timeData = tsData.map(point => ({ timestamp: point.timestamp, count: point.views }));
      ChartManager.createLineChart('chart-timeseries', timeData);
    } else if (timeseriesEl) {
      Utils.dom.showEmptyState(timeseriesEl.parentElement, { icon: '&#128200;', message: 'No time series data' });
    }

    // Activity Heatmap
    const heatmapEl = document.getElementById('chart-heatmap');
    if (data.activityHeatmap?.length && heatmapEl) {
      ChartManager.createHeatmap('chart-heatmap', data.activityHeatmap);
    } else if (heatmapEl) {
      Utils.dom.showEmptyState(heatmapEl.parentElement, { icon: '&#128197;', message: 'No activity data yet' });
    }

    // Peak times analysis
    if (data.peakTimeAnalysis) {
      this.updatePeakTimes(data.peakTimeAnalysis);
    }

    // UTM Campaigns
    const utmSection = document.getElementById('utm-section');
    const hasUtm = data.utmSources?.length || data.utmMediums?.length || data.utmCampaigns?.length;
    if (utmSection) {
      utmSection.style.display = hasUtm ? '' : 'none';
      if (hasUtm) {
        if (data.utmSources?.length) ChartManager.createBarChart('chart-utm-sources', Utils.aggregate.groupTopN(data.utmSources, 8));
        if (data.utmMediums?.length) ChartManager.createBarChart('chart-utm-mediums', Utils.aggregate.groupTopN(data.utmMediums, 8));
        if (data.utmCampaigns?.length) ChartManager.createBarChart('chart-utm-campaigns', Utils.aggregate.groupTopN(data.utmCampaigns, 8));
      }
    }

    // Scroll Depth
    const scrollSection = document.getElementById('scroll-depth-section');
    if (scrollSection) {
      if (data.scrollDepthDistribution?.length) {
        scrollSection.style.display = '';
        ChartManager.createBarChart('chart-scroll-depth', data.scrollDepthDistribution);
      } else {
        scrollSection.style.display = 'none';
      }
    }

    // Entry & Exit Pages
    const entryExitSection = document.getElementById('entry-exit-section');
    if (entryExitSection) {
      if (data.entryPages?.length || data.exitPages?.length) {
        entryExitSection.style.display = '';
        if (data.entryPages?.length) {
          this.renderBarChartWithShowMore('chart-entry-pages', data.entryPages, 5);
        }
        if (data.exitPages?.length) {
          this.renderBarChartWithShowMore('chart-exit-pages', data.exitPages, 5);
        }
      } else {
        entryExitSection.style.display = 'none';
      }
    }

    // Outbound Links & File Downloads
    const outboundSection = document.getElementById('outbound-section');
    if (outboundSection) {
      if (data.outboundLinks?.length || data.fileDownloads?.length) {
        outboundSection.style.display = '';
        if (data.outboundLinks?.length) {
          this.renderBarChartWithShowMore('chart-outbound-links', data.outboundLinks, 5);
        }
        if (data.fileDownloads?.length) {
          this.renderBarChartWithShowMore('chart-file-downloads', data.fileDownloads, 5);
        }
      } else {
        outboundSection.style.display = 'none';
      }
    }

    // Regions / States — with country flags extracted from "Region, Country" labels
    const regionsSection = document.getElementById('regions-section');
    if (regionsSection) {
      if (data.regions?.length) {
        regionsSection.style.display = '';
        // Labels are "Region, Country" — pass to icon bar chart with country type
        // so flags are resolved from the country portion
        const regionsWithFlags = data.regions.map(r => {
          const parts = r.label.split(', ');
          const country = parts.length > 1 ? parts[parts.length - 1] : '';
          return { ...r, _country: country };
        });
        this.renderRegionsChart('chart-regions', regionsWithFlags, 5);
      } else {
        regionsSection.style.display = 'none';
      }
    }
  },

  /**
   * Update peak times annotation in heatmap header
   * @param {Object} peakData - Peak time analysis data
   */
  updatePeakTimes(peakData) {
    if (!peakData) return;

    // Store peak data for heatmap
    this.state.peakHour = peakData.peakHour;
    this.state.peakDay = peakData.peakDay;
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
    this.updateRealtimeCount();

    // Update every 5 seconds
    this.state.liveInterval = setInterval(() => {
      this.updateLiveFeed();
      this.updateRealtimeCount();
    }, 5000);
  },

  async updateRealtimeCount() {
    if (this.state.realtimeDemo) return; // Skip API call in demo mode
    if (!this.state.currentProjectId) return;
    try {
      const data = await Utils.api.fetch(
        `/admin/projects/${this.state.currentProjectId}/realtime-count`,
        { useCache: false }
      );
      const el = document.getElementById('realtime-number');
      if (el) {
        animateCountUp(el, data.activeVisitors || 0, 400, v => v.toString());
      }
    } catch (e) { /* silent fail for realtime */ }
  },

  setupRealtimeDemo() {
    const btn = document.getElementById('realtime-demo-btn');
    if (!btn) return;

    btn.addEventListener('click', () => {
      this.state.realtimeDemo = !this.state.realtimeDemo;
      btn.classList.toggle('active', this.state.realtimeDemo);

      const el = document.getElementById('realtime-number');
      if (this.state.realtimeDemo) {
        // Show random demo count between 5-42
        const demoCount = Math.floor(Math.random() * 38) + 5;
        if (el) animateCountUp(el, demoCount, 400, v => v.toString());
      } else {
        // Resume real data
        this.updateRealtimeCount();
      }
    });
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
          (visit) => {
            const flag = visit.country ? Utils.icons.countryFlag(visit.country) : '';
            const location = Utils.escapeHtml(visit.city || 'Unknown');
            return `
        <div class="live-feed__item">
          <div class="live-feed__path">${Utils.escapeHtml(visit.path)}</div>
          <div class="live-feed__meta">
            ${flag ? `<span class="flag-emoji">${flag}</span> ` : ''}${location} • ${Utils.time.relative(visit.timestamp)}
          </div>
        </div>`;
          }
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
    GlobeManager.stopPolling();
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
   * Render a chart by type, cleaning up previous DOM artifacts first.
   * Used by both initial render and view toggle handlers.
   * @param {string} chartId - Canvas element ID (e.g. 'chart-browsers')
   * @param {Array} items - Array of {label, value} objects
   * @param {string} type - 'doughnut', 'bar', or 'radar'
   * @param {string} iconType - Icon category: 'browser', 'os', 'device'
   */
  renderChartByType(chartId, items, type, iconType) {
    const canvas = document.getElementById(chartId);
    if (!canvas) return;

    // Clean up custom DOM elements from any previous chart type
    const container = canvas.parentElement;
    const card = container.parentElement;
    const iconBar = container.querySelector('.icon-bar-chart');
    if (iconBar) iconBar.remove();
    // Legend may be in container (old) or card (new placement)
    [container, card].forEach(el => {
      const legend = el?.querySelector('.icon-legend');
      if (legend) legend.remove();
    });
    canvas.style.display = '';

    if (type === 'bar') {
      ChartManager.createIconBarChart(chartId, items, iconType);
    } else if (type === 'radar') {
      ChartManager.createRadarChart(chartId, items);
    } else {
      ChartManager.createIconDoughnutChart(chartId, items, iconType);
    }
  },

  /**
   * Update chart type (doughnut, bar, or radar) — called by toggle click handler
   * @param {string} chartName - 'browsers', 'os', or 'devices'
   * @param {string} type - 'doughnut', 'bar', or 'radar'
   */
  updateChartType(chartName, type) {
    if (!this.state.currentProjectId) return;

    const dataKey = `${chartName}Data`;
    const data = this.state[dataKey];
    if (!data || !data.length) return;

    const iconTypeMap = { browsers: 'browser', os: 'os', devices: 'device' };
    const topItems = Utils.aggregate.groupTopN(data, 8);
    this.renderChartByType(`chart-${chartName}`, topItems, type, iconTypeMap[chartName]);
  },

  /**
   * Setup table search and sorting
   */
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
   * Download a composed report CSV based on selected checkboxes in settings
   */
  downloadReport() {
    if (!this.state.data.current) {
      Utils.toast.error('No data to export');
      return;
    }

    const data = this.state.data.current;
    const projectName = document.getElementById('active-title')?.textContent || 'project';
    const timestamp = new Date().toISOString().split('T')[0];
    const lines = [];

    // Check which sections are selected (checkboxes use class + value)
    const checkedValues = new Set(
      Array.from(document.querySelectorAll('.export-section-cb:checked')).map(cb => cb.value)
    );
    const isChecked = (val) => checkedValues.has(val);

    if (isChecked('views-visitors')) {
      lines.push('PAGE VIEWS & VISITORS');
      lines.push(`Total views,${data.totalViews || 0}`);
      lines.push(`Unique visitors,${data.uniqueVisitors || 0}`);
      lines.push(`Bounce rate,${data.bounceRate != null ? data.bounceRate.toFixed(1) + '%' : 'N/A'}`);
      lines.push(`Total sessions,${data.totalSessions || 0}`);
      lines.push('');
    }

    if (isChecked('top-pages') && data.topPages?.length) {
      lines.push('TOP PAGES');
      lines.push('Path,Views');
      data.topPages.forEach(p => lines.push(`"${p.label}",${p.value}`));
      lines.push('');
    }

    if (isChecked('referrers') && data.referrers?.length) {
      lines.push('REFERRERS');
      lines.push('Referrer,Count');
      data.referrers.forEach(r => lines.push(`"${r.label}",${r.value}`));
      lines.push('');
    }

    if (isChecked('utm-campaigns') && (data.utmSources?.length || data.utmMediums?.length || data.utmCampaigns?.length)) {
      lines.push('UTM CAMPAIGNS');
      if (data.utmSources?.length) {
        lines.push('Source,Count');
        data.utmSources.forEach(s => lines.push(`"${s.label}",${s.value}`));
      }
      if (data.utmMediums?.length) {
        lines.push('Medium,Count');
        data.utmMediums.forEach(m => lines.push(`"${m.label}",${m.value}`));
      }
      if (data.utmCampaigns?.length) {
        lines.push('Campaign,Count');
        data.utmCampaigns.forEach(c => lines.push(`"${c.label}",${c.value}`));
      }
      lines.push('');
    }

    if (isChecked('tech')) {
      if (data.browsers?.length) {
        lines.push('BROWSERS');
        lines.push('Browser,Count');
        data.browsers.forEach(b => lines.push(`"${b.label}",${b.value}`));
        lines.push('');
      }
      if (data.oss?.length) {
        lines.push('OPERATING SYSTEMS');
        lines.push('OS,Count');
        data.oss.forEach(o => lines.push(`"${o.label}",${o.value}`));
        lines.push('');
      }
      if (data.devices?.length) {
        lines.push('DEVICES');
        lines.push('Device,Count');
        data.devices.forEach(d => lines.push(`"${d.label}",${d.value}`));
        lines.push('');
      }
    }

    if (isChecked('countries') && data.countries?.length) {
      lines.push('COUNTRIES & REGIONS');
      lines.push('Country,Visits');
      data.countries.forEach(c => lines.push(`"${c.label}",${c.value}`));
      if (data.regions?.length) {
        lines.push('');
        lines.push('Region,Visits');
        data.regions.forEach(r => lines.push(`"${r.label}",${r.value}`));
      }
      lines.push('');
    }

    if (isChecked('custom-events') && data.customEvents?.length) {
      lines.push('CUSTOM EVENTS');
      lines.push('Event,Count');
      data.customEvents.forEach(e => lines.push(`"${e.label}",${e.value}`));
      lines.push('');
    }

    if (isChecked('revenue') && typeof RevenueManager !== 'undefined' && RevenueManager.data) {
      lines.push('REVENUE');
      const rev = RevenueManager.data;
      if (rev.totalRevenue != null) lines.push(`Total revenue,${rev.totalRevenue}`);
      if (rev.revenueByEvent?.length) {
        lines.push('Event,Revenue');
        rev.revenueByEvent.forEach(r => lines.push(`"${r.label}",${r.value}`));
      }
      lines.push('');
    }

    if (lines.length === 0) {
      Utils.toast.error('Select at least one section to export');
      return;
    }

    // Handle raw events separately (async)
    if (isChecked('raw-events')) {
      Utils.toast.info('Downloading report with raw events...');
      this.exportRawEventsForReport(lines, projectName, timestamp);
      return;
    }

    const csvData = lines.join('\n');
    Utils.export.downloadCSV(csvData, `${projectName}_report_${timestamp}.csv`);
    Utils.toast.success('Report downloaded');
  },

  /**
   * Export raw events and append to report lines
   */
  async exportRawEventsForReport(lines, projectName, timestamp) {
    try {
      const params = new URLSearchParams({ page: '0', limit: '10000', sortBy: 'timestamp', order: 'desc' });
      const data = await Utils.api.fetch(
        `/admin/projects/${this.state.currentProjectId}/events?${params}`
      );

      lines.push('RAW EVENTS');
      lines.push('Timestamp,Type,Path,Country,City,Browser,Device,Duration');
      (data.events || []).forEach(e => {
        lines.push(`"${e.timestamp}","${e.eventType}","${e.path}","${e.country || ''}","${e.city || ''}","${e.browser || ''}","${e.device || ''}",${e.duration || 0}`);
      });

      const csvData = lines.join('\n');
      Utils.export.downloadCSV(csvData, `${projectName}_report_${timestamp}.csv`);
      Utils.toast.success('Report downloaded');
    } catch (error) {
      console.error('Failed to export raw events:', error);
      // Download without raw events
      const csvData = lines.join('\n');
      Utils.export.downloadCSV(csvData, `${projectName}_report_${timestamp}.csv`);
      Utils.toast.success('Report downloaded (without raw events)');
    }
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
   * Load goals and funnels data
   */
  async loadGoalsAndFunnels() {
    if (!this.state.currentProjectId) return;

    try {
      const [goalStats, funnels] = await Promise.all([
        GoalsManager.loadGoalStats(this.state.currentProjectId, this.state.currentFilter),
        GoalsManager.loadFunnels(this.state.currentProjectId)
      ]);

      GoalsManager.renderGoals(goalStats);
      GoalsManager.renderFunnels(funnels, this.state.currentProjectId, this.state.currentFilter);
    } catch (error) {
      console.error('Failed to load goals and funnels:', error);
    }
  },

  /**
   * Initialize segments modal when project is available
   */
  setupSegmentsInit() {
    // Will be initialized per-project in loadSegments
  },

  /**
   * Load and render segments for current project
   */
  async loadSegments() {
    if (!this.state.currentProjectId) return;

    try {
      // Initialize modal once per project
      if (typeof SegmentsManager !== 'undefined') {
        SegmentsManager.initModal(this.state.currentProjectId);
        const segments = await SegmentsManager.loadSegments(this.state.currentProjectId);
        SegmentsManager.renderSegments(segments, this.state.currentProjectId, this.state.currentFilter);
      }
    } catch (error) {
      console.error('Failed to load segments:', error);
    }
  },

  /**
   * Load and render webhooks for current project
   */
  async loadWebhooks() {
    if (!this.state.currentProjectId) return;

    try {
      if (typeof WebhooksManager !== 'undefined') {
        WebhooksManager.initModal(this.state.currentProjectId);
        const webhooks = await WebhooksManager.loadWebhooks(this.state.currentProjectId);
        WebhooksManager.renderWebhooks(webhooks, this.state.currentProjectId);
      }
    } catch (error) {
      console.error('Failed to load webhooks:', error);
    }
  },

  /**
   * Load and render email reports for current project
   */
  async loadEmailReports() {
    if (!this.state.currentProjectId) return;

    try {
      if (typeof EmailReportsManager !== 'undefined') {
        EmailReportsManager.initModal(this.state.currentProjectId);
        const reports = await EmailReportsManager.loadReports(this.state.currentProjectId);
        EmailReportsManager.renderReports(reports, this.state.currentProjectId);
      }
    } catch (error) {
      console.error('Failed to load email reports:', error);
    }
  },

  /**
   * Load and render revenue data for current project
   */
  async loadRevenue() {
    if (!this.state.currentProjectId) return;

    try {
      if (typeof RevenueManager !== 'undefined') {
        RevenueManager.initGuide();
        await RevenueManager.loadRevenue(this.state.currentProjectId, this.state.currentFilter);
        RevenueManager.render(this.state.currentProjectId);
      }
    } catch (error) {
      console.error('Failed to load revenue data:', error);
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
               data-tooltip="${this.formatContributionTooltip(dateStr, visits, uniqueVisitors)}">
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
   * Create or browse an existing demo project
   */
  async createOrBrowseDemoProject() {
    try {
      // Check if a demo project already exists
      const projects = await Utils.api.fetch('/admin/projects');
      const demoProject = projects?.find(p => p.name === 'Demo project');

      if (demoProject) {
        // Navigate to existing demo project
        this.selectProject(demoProject.id, demoProject.name);
        return;
      }

      // Create a new demo project
      Utils.toast.info('Creating demo project...');
      await Utils.api.post('/admin/projects', {
        name: 'Demo project',
        domain: 'demo.example.com',
      });

      // Reload and find the new demo project
      Utils.cache.clear('/admin/projects');
      const updatedProjects = await Utils.api.fetch('/admin/projects');
      const newDemo = updatedProjects?.find(p => p.name === 'Demo project');

      if (newDemo) {
        // Generate demo data
        await fetch(`/admin/projects/${newDemo.id}/demo-data`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ count: 500, timeScope: 30 }),
        });

        await this.loadProjects();
        this.selectProject(newDemo.id, newDemo.name);
        Utils.toast.success('Demo project created with sample data!');
      }
    } catch (error) {
      console.error('Failed to create demo project:', error);
      Utils.toast.error('Failed to create demo project');
    }
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

    // Download report button
    const downloadReportBtn = document.getElementById('download-report-btn');
    if (downloadReportBtn) {
      downloadReportBtn.addEventListener('click', () => {
        this.downloadReport();
      });
    }

    // Manage webhooks button (in settings modal)
    const manageWebhooksBtn = document.getElementById('manage-webhooks-btn');
    if (manageWebhooksBtn) {
      manageWebhooksBtn.addEventListener('click', () => {
        modal.classList.remove('show');
        this.loadWebhooks();
        const webhooksModal = document.getElementById('webhooks-modal');
        if (webhooksModal) webhooksModal.classList.add('show');
      });
    }

    // Manage email reports button (in settings modal)
    const manageEmailReportsBtn = document.getElementById('manage-email-reports-btn');
    if (manageEmailReportsBtn) {
      manageEmailReportsBtn.addEventListener('click', () => {
        modal.classList.remove('show');
        this.loadEmailReports();
        const emailReportsModal = document.getElementById('email-reports-modal');
        if (emailReportsModal) emailReportsModal.classList.add('show');
      });
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
      // Update contribution calendar colors
      const calendar = document.getElementById('contribution-calendar');
      if (calendar) {
        calendar.className = `heatmap-${e.detail.heatmapColors}`;
      }

      // Rebuild activity heatmap with new color
      if (this.state.data?.current?.activityHeatmap?.length) {
        ChartManager.createHeatmap('chart-heatmap', this.state.data.current.activityHeatmap);
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
        tbody.innerHTML = data.events.map(e => {
          const badgeClass = e.eventType === 'pageview' ? 'primary' : e.eventType === 'custom' ? 'accent' : 'secondary';
          const typeLabel = e.eventType === 'custom' && e.eventName ? `custom: ${e.eventName}` : e.eventType;
          return `
          <tr>
            <td>${Utils.time.formatTime(e.timestamp)}</td>
            <td><span class="badge badge-${badgeClass}">${typeLabel}</span></td>
            <td>${e.path}</td>
            <td>${e.city || 'Unknown'}, ${e.country || 'Unknown'}</td>
            <td>${e.browser || 'Unknown'}</td>
            <td>${e.device || 'Unknown'}</td>
            <td>${e.duration || 0}s</td>
          </tr>
        `}).join('');
      }

      modal.dataset.page = page.toString();
      modal.dataset.totalEvents = data.total.toString();

      document.getElementById('raw-events-info').textContent =
        `Showing ${page * limit + 1}-${Math.min((page + 1) * limit, data.total)} of ${data.total}`;

      document.getElementById('raw-events-prev').disabled = page === 0;
      document.getElementById('raw-events-next').disabled = (page + 1) * limit >= data.total;

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

      // Restore countries view using icon bar chart (same as initial render)
      if (this.state.data.current?.countries) {
        MapManager.createMap('chart-countries', this.state.data.current.countries);
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

    // Remove icon bar chart and restore canvas before rendering
    const container = document.getElementById('chart-countries')?.parentElement;
    const existingIconChart = container?.querySelector('.icon-bar-chart');
    if (existingIconChart) existingIconChart.remove();
    const canvas = document.getElementById('chart-countries');
    if (canvas) canvas.style.display = '';

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

  /**
   * Get heatmap date labels (day name + most recent date for each day of week)
   * @returns {Array} Array of 7 labels like "Mon Feb 17"
   */
  getHeatmapDateLabels() {
    const labels = [];
    const now = new Date();
    const dayNames = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

    for (let dow = 0; dow < 7; dow++) {
      for (let offset = 0; offset < 7; offset++) {
        const d = new Date(now);
        d.setDate(d.getDate() - offset);
        if (d.getDay() === dow) {
          const month = d.toLocaleDateString('en-US', { month: 'short' });
          const day = d.getDate();
          labels[dow] = `${dayNames[dow]} ${month} ${day}`;
          break;
        }
      }
    }

    return labels;
  },

  /**
   * Render a bar chart with "show more" button if items exceed visible count
   * @param {string} chartId - Canvas element ID
   * @param {Array} allItems - All items to display
   * @param {number} visibleCount - Number of items to show initially
   */
  renderBarChartWithShowMore(chartId, allItems, visibleCount = 5) {
    const canvas = document.getElementById(chartId);
    if (!canvas) return;

    const container = canvas.parentElement;
    const card = container.parentElement;

    // Remove existing show more button
    const existingBtn = card.querySelector('.show-more-btn');
    if (existingBtn) existingBtn.remove();

    const needsShowMore = allItems.length > visibleCount;
    const itemsToShow = needsShowMore ? allItems.slice(0, visibleCount) : allItems;

    // Set dynamic height: 40px per item (20px bar + 20px gap) + 40px padding
    container.style.height = `${Math.max(200, itemsToShow.length * 40 + 40)}px`;
    ChartManager.createBarChart(chartId, itemsToShow);

    if (needsShowMore) {
      const btn = document.createElement('button');
      btn.className = 'show-more-btn';
      btn.textContent = `Show ${allItems.length - visibleCount} more`;
      btn.dataset.expanded = 'false';

      btn.addEventListener('click', () => {
        const expanded = btn.dataset.expanded === 'true';
        if (expanded) {
          container.style.height = `${Math.max(200, visibleCount * 40 + 40)}px`;
          ChartManager.createBarChart(chartId, allItems.slice(0, visibleCount));
          btn.textContent = `Show ${allItems.length - visibleCount} more`;
          btn.dataset.expanded = 'false';
        } else {
          container.style.height = `${Math.max(200, allItems.length * 40 + 40)}px`;
          ChartManager.createBarChart(chartId, allItems);
          btn.textContent = 'Show less';
          btn.dataset.expanded = 'true';
        }
      });

      card.appendChild(btn);
    }
  },

  /**
   * Render an icon bar chart with "show more" button if items exceed visible count
   * @param {string} chartId - Canvas element ID
   * @param {Array} allItems - All items to display
   * @param {string} iconType - Icon category: 'browser', 'os', 'device', 'referrer', 'country'
   * @param {number} visibleCount - Number of items to show initially
   */
  renderIconBarChartWithShowMore(chartId, allItems, iconType, visibleCount = 5) {
    const canvas = document.getElementById(chartId);
    if (!canvas) return;

    const container = canvas.parentElement;
    const card = container.parentElement;

    // Remove existing show more button
    const existingBtn = card.querySelector('.show-more-btn');
    if (existingBtn) existingBtn.remove();

    const needsShowMore = allItems.length > visibleCount;
    const itemsToShow = needsShowMore ? allItems.slice(0, visibleCount) : allItems;

    // Set dynamic height for icon bar chart (36px per item + 8px padding)
    container.style.height = `${Math.max(200, itemsToShow.length * 36 + 16)}px`;
    ChartManager.createIconBarChart(chartId, itemsToShow, iconType);

    if (needsShowMore) {
      const btn = document.createElement('button');
      btn.className = 'show-more-btn';
      btn.textContent = `Show ${allItems.length - visibleCount} more`;
      btn.dataset.expanded = 'false';

      btn.addEventListener('click', () => {
        const expanded = btn.dataset.expanded === 'true';
        if (expanded) {
          container.style.height = `${Math.max(200, visibleCount * 36 + 16)}px`;
          ChartManager.createIconBarChart(chartId, allItems.slice(0, visibleCount), iconType);
          btn.textContent = `Show ${allItems.length - visibleCount} more`;
          btn.dataset.expanded = 'false';
        } else {
          container.style.height = `${Math.max(200, allItems.length * 36 + 16)}px`;
          ChartManager.createIconBarChart(chartId, allItems, iconType);
          btn.textContent = 'Show less';
          btn.dataset.expanded = 'true';
        }
      });

      card.appendChild(btn);
    }
  },

  /**
   * Render regions chart with country flags using icon bar chart
   * @param {string} chartId - Canvas element ID
   * @param {Array} allItems - Items with _country field for flag lookup
   * @param {number} visibleCount - Number of items to show initially
   */
  renderRegionsChart(chartId, allItems, visibleCount = 5) {
    const canvas = document.getElementById(chartId);
    if (!canvas) return;

    const container = canvas.parentElement;
    const card = container.parentElement;

    // Remove existing show more button
    const existingBtn = card.querySelector('.show-more-btn');
    if (existingBtn) existingBtn.remove();

    const renderItems = (items) => {
      // Destroy Chart.js instance and hide canvas
      if (ChartManager.instances[chartId]) {
        ChartManager.instances[chartId].destroy();
        delete ChartManager.instances[chartId];
      }
      canvas.style.display = 'none';

      const existingChart = container.querySelector('.icon-bar-chart');
      if (existingChart) existingChart.remove();

      const maxValue = Math.max(...items.map(d => d.value));
      const total = items.reduce((sum, d) => sum + d.value, 0);

      const chartEl = document.createElement('div');
      chartEl.className = 'icon-bar-chart';

      chartEl.innerHTML = items.map(item => {
        const pct = maxValue > 0 ? (item.value / maxValue) * 100 : 0;
        const pctOfTotal = total > 0 ? ((item.value / total) * 100).toFixed(1) : '0.0';
        const flag = item._country ? Utils.icons.countryFlag(item._country) : '';
        const iconHtml = flag ? `<span class="icon-bar-chart__flag">${flag}</span>` : '';

        return `
          <div class="icon-bar-chart__item">
            <div class="icon-bar-chart__label">
              ${iconHtml}
              <span class="icon-bar-chart__name">${Utils.escapeHtml(item.label)}</span>
            </div>
            <div class="icon-bar-chart__bar-wrap">
              <div class="icon-bar-chart__bar" style="width: ${pct}%"></div>
            </div>
            <div class="icon-bar-chart__value">
              ${Utils.format.compact(item.value)}<span class="icon-bar-chart__pct">${pctOfTotal}%</span>
            </div>
          </div>`;
      }).join('');

      container.appendChild(chartEl);
    };

    const needsShowMore = allItems.length > visibleCount;
    const itemsToShow = needsShowMore ? allItems.slice(0, visibleCount) : allItems;

    container.style.height = `${Math.max(200, itemsToShow.length * 36 + 16)}px`;
    renderItems(itemsToShow);

    if (needsShowMore) {
      const btn = document.createElement('button');
      btn.className = 'show-more-btn';
      btn.textContent = `Show ${allItems.length - visibleCount} more`;
      btn.dataset.expanded = 'false';

      btn.addEventListener('click', () => {
        const expanded = btn.dataset.expanded === 'true';
        if (expanded) {
          container.style.height = `${Math.max(200, visibleCount * 36 + 16)}px`;
          renderItems(allItems.slice(0, visibleCount));
          btn.textContent = `Show ${allItems.length - visibleCount} more`;
          btn.dataset.expanded = 'false';
        } else {
          container.style.height = `${Math.max(200, allItems.length * 36 + 16)}px`;
          renderItems(allItems);
          btn.textContent = 'Show less';
          btn.dataset.expanded = 'true';
        }
      });

      card.appendChild(btn);
    }
  },

  /**
   * Setup onboarding flow for new users
   */
  setupOnboarding() {
    if (localStorage.getItem('mini-numbers-onboarding-dismissed')) return;

    const modal = document.getElementById('onboarding-modal');
    if (!modal) return;

    // Close handlers
    ['close-onboarding', 'dismiss-onboarding', 'close-onboarding-done'].forEach(id => {
      document.getElementById(id)?.addEventListener('click', () => {
        modal.classList.remove('show');
        localStorage.setItem('mini-numbers-onboarding-dismissed', 'true');
      });
    });

    // Check project count to determine if we should show onboarding
    this._checkOnboardingState = async () => {
      try {
        const projects = await Utils.api.fetch('/admin/projects');
        const hasProjects = projects && projects.length > 0;

        if (hasProjects) {
          document.querySelector('[data-step="create-project"]')?.classList.add('completed');
        }

        // Show onboarding if no projects exist
        if (!hasProjects) {
          modal.classList.add('show');
        }
      } catch (e) {
        // Don't show onboarding if we can't determine state
      }
    };

    this._checkOnboardingState();
  },

  /**
   * Setup collapsible dashboard section toggles with localStorage persistence
   */
  setupSectionToggles() {
    const sections = document.querySelectorAll('.dashboard-section[data-section]');
    const storageKey = 'mini-numbers-section-state';

    // Load saved state
    let savedState = {};
    try {
      savedState = JSON.parse(localStorage.getItem(storageKey) || '{}');
    } catch (e) { /* ignore */ }

    sections.forEach(section => {
      const sectionName = section.dataset.section;
      const header = section.querySelector('.dashboard-section__header');
      if (!header) return;

      // Apply saved state (override HTML default)
      if (savedState[sectionName] !== undefined) {
        section.setAttribute('aria-expanded', savedState[sectionName] ? 'true' : 'false');
      }

      // Set initial max-height for expanded sections
      const content = section.querySelector('.dashboard-section__content');
      if (content && section.getAttribute('aria-expanded') === 'true') {
        content.style.maxHeight = 'none';
      }

      header.addEventListener('click', () => {
        const isExpanded = section.getAttribute('aria-expanded') === 'true';
        const newState = !isExpanded;
        section.setAttribute('aria-expanded', newState.toString());

        if (content) {
          if (newState) {
            content.style.maxHeight = 'none';
          } else {
            content.style.maxHeight = '';
          }
        }

        // Persist state
        savedState[sectionName] = newState;
        localStorage.setItem(storageKey, JSON.stringify(savedState));
      });
    });
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
