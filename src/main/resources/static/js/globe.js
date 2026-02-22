/**
 * 3D Globe Visualization
 * Interactive 3D earth using Globe.gl for visitor location display
 */

const GlobeManager = {
  instance: null,
  loaded: false,
  loading: false,
  pollInterval: null,
  currentRange: 'realtime',
  container: null,
  idleTimer: null,
  resizeObserver: null,

  /**
   * Lazy-load Globe.gl library from CDN
   * @returns {Promise} Resolves when library is loaded
   */
  async loadLibrary() {
    if (this.loaded) return;
    if (this.loading) {
      return new Promise((resolve) => {
        const check = setInterval(() => {
          if (this.loaded) { clearInterval(check); resolve(); }
        }, 100);
      });
    }

    this.loading = true;
    return new Promise((resolve, reject) => {
      const script = document.createElement('script');
      script.src = 'https://cdn.jsdelivr.net/npm/globe.gl';
      script.onload = () => {
        this.loaded = true;
        this.loading = false;
        resolve();
      };
      script.onerror = () => {
        this.loading = false;
        reject(new Error('Failed to load Globe.gl library'));
      };
      document.head.appendChild(script);
    });
  },

  /**
   * Initialize the 3D globe in a container
   * @param {string} containerId - DOM element ID for the globe canvas
   * @param {Array} data - Initial visitor data array
   */
  async init(containerId, data) {
    try {
      await this.loadLibrary();
    } catch (e) {
      const container = document.getElementById(containerId);
      if (container) {
        container.innerHTML = `
          <div class="empty-state">
            <div class="empty-state__icon"><i class="ri-earth-line"></i></div>
            <div class="empty-state__message">Failed to load 3D globe</div>
            <div class="empty-state__suggestion">Check your internet connection or use Chart view</div>
          </div>`;
      }
      return;
    }

    this.container = document.getElementById(containerId);
    if (!this.container) return;

    // Check WebGL support
    const canvas = document.createElement('canvas');
    const gl = canvas.getContext('webgl') || canvas.getContext('experimental-webgl');
    if (!gl) {
      this.container.innerHTML = `
        <div class="empty-state">
          <div class="empty-state__icon"><i class="ri-earth-line"></i></div>
          <div class="empty-state__message">3D globe requires WebGL</div>
          <div class="empty-state__suggestion">Your browser doesn't support WebGL. Use Chart view instead.</div>
        </div>`;
      return;
    }

    // Clear container
    this.container.innerHTML = '';

    const isDark = ThemeManager.isDark();
    const width = this.container.offsetWidth;
    const height = this.container.offsetHeight || 400;

    this.instance = Globe()(this.container)
      .width(width)
      .height(height)
      .globeImageUrl(isDark
        ? 'https://cdn.jsdelivr.net/npm/three-globe/example/img/earth-night.jpg'
        : 'https://cdn.jsdelivr.net/npm/three-globe/example/img/earth-blue-marble.jpg')
      .backgroundColor(isDark ? '#0f172a' : '#f8fafc')
      .showAtmosphere(true)
      .atmosphereColor(isDark ? '#818cf8' : '#6366f1')
      .atmosphereAltitude(0.2)
      // Rings layer — pulsing ripple animations at visitor locations
      .ringsData(data || [])
      .ringLat(d => d.lat)
      .ringLng(d => d.lng)
      .ringColor(() => isDark
        ? (t) => `rgba(129, 140, 248, ${1 - t})`
        : (t) => `rgba(99, 102, 241, ${1 - t})`)
      .ringMaxRadius(d => 2 + Math.min(d.count, 10) * 0.3)
      .ringPropagationSpeed(2)
      .ringRepeatPeriod(1500)
      // Points layer — persistent 3D markers rising from surface
      .pointsData(data || [])
      .pointLat(d => d.lat)
      .pointLng(d => d.lng)
      .pointAltitude(d => Math.min(d.count * 0.008, 0.25))
      .pointRadius(d => 0.25 + Math.min(d.count * 0.04, 0.6))
      .pointColor(() => isDark ? '#a78bfa' : '#6366f1')
      // Labels layer — city/country name for significant locations
      .labelsData((data || []).filter(d => d.count >= 3))
      .labelLat(d => d.lat)
      .labelLng(d => d.lng)
      .labelText(d => `${d.city || d.country || 'Unknown'} (${d.count})`)
      .labelSize(0.6)
      .labelColor(() => isDark ? '#e2e8f0' : '#1e293b')
      .labelDotRadius(0.2)
      .labelAltitude(0.01);

    // Auto-rotate
    const controls = this.instance.controls();
    controls.autoRotate = true;
    controls.autoRotateSpeed = 0.5;
    controls.enableDamping = true;

    // Pause rotation on interaction, resume after 3 seconds idle
    const pauseRotation = () => {
      controls.autoRotate = false;
      clearTimeout(this.idleTimer);
    };
    const resumeRotation = () => {
      this.idleTimer = setTimeout(() => {
        controls.autoRotate = true;
      }, 3000);
    };

    this.container.addEventListener('mousedown', pauseRotation);
    this.container.addEventListener('mouseup', resumeRotation);
    this.container.addEventListener('touchstart', pauseRotation);
    this.container.addEventListener('touchend', resumeRotation);

    // Responsive sizing
    this.resizeObserver = new ResizeObserver(() => {
      if (this.instance && this.container.offsetWidth > 0) {
        this.instance.width(this.container.offsetWidth);
        this.instance.height(this.container.offsetHeight || 400);
      }
    });
    this.resizeObserver.observe(this.container);

    return this.instance;
  },

  /**
   * Update globe with new visitor data
   * @param {Array} data - Visitor data array
   */
  update(data) {
    if (!this.instance) return;

    this.instance
      .ringsData(data)
      .pointsData(data)
      .labelsData(data.filter(d => d.count >= 3));
  },

  /**
   * Start polling for globe data
   * @param {string} projectId - Project UUID
   * @param {string} range - Time range: 'realtime', '1m', '1h', '1d'
   */
  startPolling(projectId, range) {
    this.stopPolling();
    this.currentRange = range;

    // Fetch immediately
    this.fetchAndUpdate(projectId, range);

    // Only continuously poll for realtime mode
    if (range === 'realtime') {
      this.pollInterval = setInterval(() => {
        this.fetchAndUpdate(projectId, range);
      }, 5000);
    }
  },

  /**
   * Fetch globe data from API and update visualization
   */
  async fetchAndUpdate(projectId, range) {
    try {
      const data = await Utils.api.fetch(
        `/admin/projects/${projectId}/globe?range=${range}`,
        { useCache: false }
      );
      if (data && data.visitors) {
        this.update(data.visitors);
        // Update the total count display if available
        const countEl = document.getElementById('globe-visitor-count');
        if (countEl) {
          countEl.textContent = data.totalActive;
        }
      }
    } catch (error) {
      console.error('Failed to fetch globe data:', error);
    }
  },

  /**
   * Stop polling for data
   */
  stopPolling() {
    if (this.pollInterval) {
      clearInterval(this.pollInterval);
      this.pollInterval = null;
    }
  },

  /**
   * Handle theme change — update globe textures and colors
   */
  updateTheme() {
    if (!this.instance) return;
    const isDark = ThemeManager.isDark();

    this.instance
      .globeImageUrl(isDark
        ? 'https://cdn.jsdelivr.net/npm/three-globe/example/img/earth-night.jpg'
        : 'https://cdn.jsdelivr.net/npm/three-globe/example/img/earth-blue-marble.jpg')
      .backgroundColor(isDark ? '#0f172a' : '#f8fafc')
      .atmosphereColor(isDark ? '#818cf8' : '#6366f1')
      .ringColor(() => isDark
        ? (t) => `rgba(129, 140, 248, ${1 - t})`
        : (t) => `rgba(99, 102, 241, ${1 - t})`)
      .pointColor(() => isDark ? '#a78bfa' : '#6366f1')
      .labelColor(() => isDark ? '#e2e8f0' : '#1e293b');
  },

  /**
   * Destroy globe instance and clean up resources
   */
  destroy() {
    this.stopPolling();
    clearTimeout(this.idleTimer);

    if (this.resizeObserver) {
      this.resizeObserver.disconnect();
      this.resizeObserver = null;
    }

    if (this.instance) {
      // Globe.gl renders into a canvas inside the container
      // Clear the container to dispose of the WebGL context
      if (this.container) {
        this.container.innerHTML = '';
      }
      this.instance = null;
    }
  }
};

// Listen for theme changes
window.addEventListener('themeChange', () => {
  GlobeManager.updateTheme();
});
