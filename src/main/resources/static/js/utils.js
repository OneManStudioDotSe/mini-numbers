/**
 * Utility Functions
 * Helper functions for formatting, auth, caching, and common operations
 */

const Utils = {
  // Authentication
  auth: {
    /**
     * Get auth header for API requests
     * @returns {Object} Authorization header
     */
    getHeader() {
      return {
        Authorization: 'Basic ' + btoa('admin:your-password'),
      };
    },
  },

  // Cache management
  cache: {
    data: {},
    timestamps: {},
    TTL: 5 * 60 * 1000, // 5 minutes

    /**
     * Check if cache entry exists and is valid
     * @param {string} key - Cache key
     * @returns {boolean} True if valid cache exists
     */
    has(key) {
      if (!this.data[key] || !this.timestamps[key]) {
        return false;
      }
      const age = Date.now() - this.timestamps[key];
      return age < this.TTL;
    },

    /**
     * Get cached data
     * @param {string} key - Cache key
     * @returns {*} Cached data or null
     */
    get(key) {
      if (this.has(key)) {
        return this.data[key];
      }
      return null;
    },

    /**
     * Set cache data
     * @param {string} key - Cache key
     * @param {*} value - Data to cache
     */
    set(key, value) {
      this.data[key] = value;
      this.timestamps[key] = Date.now();
    },

    /**
     * Clear specific cache entry
     * @param {string} key - Cache key
     */
    clear(key) {
      delete this.data[key];
      delete this.timestamps[key];
    },

    /**
     * Clear all cache
     */
    clearAll() {
      this.data = {};
      this.timestamps = {};
    },
  },

  // Number formatting
  format: {
    /**
     * Format large numbers with commas
     * @param {number} num - Number to format
     * @returns {string} Formatted number
     */
    number(num) {
      if (num === null || num === undefined) return '0';
      return num.toLocaleString();
    },

    /**
     * Format percentage
     * @param {number} value - Percentage value
     * @param {number} decimals - Decimal places
     * @returns {string} Formatted percentage
     */
    percentage(value, decimals = 1) {
      if (value === null || value === undefined) return '0%';
      return value.toFixed(decimals) + '%';
    },

    /**
     * Format percentage change with sign and color class
     * @param {number} change - Percentage change
     * @returns {Object} {text, className}
     */
    percentageChange(change) {
      if (change === null || change === undefined) {
        return { text: 'N/A', className: '' };
      }

      const isPositive = change > 0;
      const sign = isPositive ? '▲' : change < 0 ? '▼' : '';
      const className = isPositive ? 'positive' : change < 0 ? 'negative' : '';
      const text = `${sign} ${Math.abs(change).toFixed(1)}%`;

      return { text, className };
    },

    /**
     * Shorten large numbers (e.g., 1.2K, 3.4M)
     * @param {number} num - Number to shorten
     * @returns {string} Shortened number
     */
    compact(num) {
      if (num === null || num === undefined) return '0';
      if (num < 1000) return num.toString();
      if (num < 1000000) return (num / 1000).toFixed(1) + 'K';
      return (num / 1000000).toFixed(1) + 'M';
    },

    /**
     * Format seconds into human-readable duration (e.g., "2m 15s")
     * @param {number} seconds - Duration in seconds
     * @returns {string} Formatted duration
     */
    duration(seconds) {
      if (seconds === null || seconds === undefined || seconds === 0) return '0s';
      if (seconds < 60) return seconds + 's';
      var m = Math.floor(seconds / 60);
      var s = seconds % 60;
      if (s === 0) return m + 'm';
      return m + 'm ' + s + 's';
    },
  },

  // Date/Time formatting
  time: {
    /**
     * Format ISO timestamp to readable time
     * @param {string} isoString - ISO timestamp
     * @returns {string} Formatted time (HH:MM or HH:MM AM/PM)
     */
    formatTime(isoString) {
      if (!isoString) return '';
      try {
        const date = new Date(isoString);
        const settings = typeof SettingsManager !== 'undefined' ? SettingsManager.load() : { timeFormat: '24h' };

        return date.toLocaleTimeString('en-US', {
          hour: '2-digit',
          minute: '2-digit',
          hour12: settings.timeFormat === '12h',
        });
      } catch (error) {
        return isoString.split('T')[1]?.substring(0, 5) || '';
      }
    },

    /**
     * Format ISO timestamp to readable date
     * @param {string} isoString - ISO timestamp
     * @returns {string} Formatted date
     */
    formatDate(isoString) {
      if (!isoString) return '';
      try {
        const date = new Date(isoString);
        return date.toLocaleDateString('en-US', {
          month: 'short',
          day: 'numeric',
          year: 'numeric',
        });
      } catch (error) {
        return isoString.split('T')[0] || '';
      }
    },

    /**
     * Format ISO timestamp to relative time (e.g., "2m ago")
     * @param {string} isoString - ISO timestamp
     * @returns {string} Relative time
     */
    relative(isoString) {
      if (!isoString) return '';
      try {
        const date = new Date(isoString);
        const now = new Date();
        const diffMs = now - date;
        const diffSec = Math.floor(diffMs / 1000);
        const diffMin = Math.floor(diffSec / 60);
        const diffHour = Math.floor(diffMin / 60);
        const diffDay = Math.floor(diffHour / 24);

        if (diffSec < 60) return 'just now';
        if (diffMin < 60) return `${diffMin}m ago`;
        if (diffHour < 24) return `${diffHour}h ago`;
        if (diffDay < 7) return `${diffDay}d ago`;
        return this.formatDate(isoString);
      } catch (error) {
        return isoString;
      }
    },
  },

  // DOM helpers
  dom: {
    /**
     * Create element with classes and attributes
     * @param {string} tag - HTML tag
     * @param {Object} options - Classes, attributes, content
     * @returns {HTMLElement} Created element
     */
    create(tag, { classes = [], attrs = {}, content = '' } = {}) {
      const el = document.createElement(tag);

      if (classes.length) {
        el.classList.add(...classes);
      }

      Object.entries(attrs).forEach(([key, value]) => {
        el.setAttribute(key, value);
      });

      if (content) {
        el.innerHTML = content;
      }

      return el;
    },

    /**
     * Show element
     * @param {HTMLElement} el - Element to show
     */
    show(el) {
      if (el) el.classList.remove('hidden');
    },

    /**
     * Hide element
     * @param {HTMLElement} el - Element to hide
     */
    hide(el) {
      if (el) el.classList.add('hidden');
    },

    /**
     * Toggle element visibility
     * @param {HTMLElement} el - Element to toggle
     */
    toggle(el) {
      if (el) el.classList.toggle('hidden');
    },

    /**
     * Show empty state in a container
     * @param {HTMLElement} container - Target container
     * @param {Object} options - Icon, message, hint
     */
    showEmptyState(container, { icon, message, hint } = {}) {
      if (!container) return;
      container.innerHTML = `
        <div class="empty-state">
          <div class="empty-state__icon">${icon || '&#128202;'}</div>
          <div class="empty-state__message">${Utils.escapeHtml(message || 'No data available')}</div>
          ${hint ? `<div class="empty-state__suggestion">${Utils.escapeHtml(hint)}</div>` : ''}
        </div>
      `;
    },

    /**
     * Show error state in a container with optional retry
     * @param {HTMLElement} container - Target container
     * @param {string} message - Error message
     * @param {Function} retryCallback - Optional retry function
     */
    showError(container, message, retryCallback) {
      if (!container) return;
      container.innerHTML = `
        <div class="empty-state">
          <div class="empty-state__icon">&#9888;</div>
          <div class="empty-state__message">${Utils.escapeHtml(message)}</div>
          ${retryCallback ? '<button class="btn btn-secondary btn-sm empty-state__retry">Retry</button>' : ''}
        </div>
      `;
      if (retryCallback) {
        container.querySelector('.empty-state__retry')?.addEventListener('click', retryCallback);
      }
    },
  },

  // Toast notifications
  toast: {
    container: null,

    /**
     * Initialize toast container
     */
    init() {
      if (!this.container) {
        this.container = document.createElement('div');
        this.container.className = 'toast-container';
        document.body.appendChild(this.container);
      }
    },

    /**
     * Show toast notification
     * @param {string} message - Toast message
     * @param {string} type - Toast type (success, error, warning, info)
     * @param {number} duration - Duration in ms (0 = don't auto-close)
     */
    show(message, type = 'info', duration = 4000) {
      this.init();

      const toast = document.createElement('div');
      toast.className = `toast toast--${type}`;

      toast.innerHTML = `
        <div class="toast__content">
          <div class="toast__message">${message}</div>
        </div>
        <button class="toast__close" aria-label="Close">×</button>
      `;

      const closeBtn = toast.querySelector('.toast__close');
      closeBtn.addEventListener('click', () => {
        this.remove(toast);
      });

      this.container.appendChild(toast);

      if (duration > 0) {
        setTimeout(() => {
          this.remove(toast);
        }, duration);
      }

      return toast;
    },

    /**
     * Remove toast with animation
     * @param {HTMLElement} toast - Toast element
     */
    remove(toast) {
      toast.style.animation = 'slideInRight 0.3s reverse';
      setTimeout(() => {
        if (toast.parentNode) {
          toast.parentNode.removeChild(toast);
        }
      }, 300);
    },

    success(message, duration) {
      return this.show(message, 'success', duration);
    },

    error(message, duration) {
      return this.show(message, 'error', duration);
    },

    warning(message, duration) {
      return this.show(message, 'warning', duration);
    },

    info(message, duration) {
      return this.show(message, 'info', duration);
    },
  },

  // Global error handling
  errors: {
    init() {
      window.onerror = (message, source, lineno, colno, error) => {
        console.error('Global error:', { message, source, lineno, colno, error });
        return false;
      };

      window.addEventListener('unhandledrejection', (event) => {
        console.error('Unhandled promise rejection:', event.reason);
        if (event.reason && !event.reason._handled) {
          Utils.toast.error('An unexpected error occurred.');
        }
      });
    },
  },

  // API helpers
  api: {
    /**
     * Fetch data with caching, retry, and error handling
     * @param {string} url - API URL
     * @param {Object} options - Fetch options (useCache, retries)
     * @returns {Promise} Response data
     */
    async fetch(url, options = {}) {
      const useCache = options.useCache !== false;
      const maxRetries = options.retries ?? 2;
      const { useCache: _, retries: __, ...fetchOptions } = options;

      if (useCache && Utils.cache.has(url)) {
        return Utils.cache.get(url);
      }

      let lastError;
      for (let attempt = 0; attempt <= maxRetries; attempt++) {
        try {
          const response = await fetch(url, {
            headers: {
              ...Utils.auth.getHeader(),
              ...fetchOptions.headers,
            },
            ...fetchOptions,
          });

          if (!response.ok) {
            // Redirect to login on authentication failure
            if (response.status === 401) {
              window.location.href = '/login';
              const err = new Error('Authentication required');
              err.status = 401;
              err._handled = true;
              throw err;
            }
            const err = new Error(`HTTP ${response.status}: ${response.statusText}`);
            err.status = response.status;
            // Don't retry client errors (4xx)
            if (response.status >= 400 && response.status < 500) {
              err._handled = true;
              throw err;
            }
            throw err;
          }

          const data = await response.json();

          if (useCache) {
            Utils.cache.set(url, data);
          }

          return data;
        } catch (error) {
          lastError = error;
          if (error.status && error.status >= 400 && error.status < 500) throw error;
          if (attempt < maxRetries) {
            await new Promise(r => setTimeout(r, Math.pow(2, attempt) * 500));
          }
        }
      }

      console.error('API fetch error after retries:', lastError);
      Utils.toast.error(`Failed to fetch data: ${lastError.message}`);
      lastError._handled = true;
      throw lastError;
    },

    /**
     * POST data to API endpoint
     * @param {string} url - API URL
     * @param {Object} data - Data to send
     * @returns {Promise} Response data
     */
    async post(url, data) {
      try {
        const response = await fetch(url, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            ...Utils.auth.getHeader(),
          },
          body: JSON.stringify(data),
        });

        if (!response.ok) {
          throw new Error(`HTTP ${response.status}: ${response.statusText}`);
        }

        // Check if response has content
        const contentType = response.headers.get('content-type');
        if (contentType && contentType.includes('application/json')) {
          return await response.json();
        }

        return null;
      } catch (error) {
        console.error('API POST error:', error);
        Utils.toast.error(`Failed to save data: ${error.message}`);
        throw error;
      }
    },
  },

  // Data aggregation
  aggregate: {
    /**
     * Group visits by time bucket (hour or day)
     * @param {Array} visits - Array of visit objects with timestamps
     * @param {string} granularity - 'hour' or 'day'
     * @returns {Array} Array of {timestamp, count} objects
     */
    groupByTime(visits, granularity = 'hour') {
      if (!visits || !visits.length) return [];

      const buckets = {};

      visits.forEach((visit) => {
        const date = new Date(visit.timestamp);
        let key;

        if (granularity === 'hour') {
          // Round to hour
          date.setMinutes(0, 0, 0);
          key = date.toISOString();
        } else {
          // Round to day
          date.setHours(0, 0, 0, 0);
          key = date.toISOString().split('T')[0];
        }

        buckets[key] = (buckets[key] || 0) + 1;
      });

      // Convert to array and sort by timestamp
      return Object.entries(buckets)
        .map(([timestamp, count]) => ({ timestamp, count }))
        .sort((a, b) => new Date(a.timestamp) - new Date(b.timestamp));
    },

    /**
     * Group items and limit to top N, grouping rest as "Other"
     * @param {Array} items - Array of {label, value} objects
     * @param {number} limit - Max items before grouping
     * @returns {Array} Processed array
     */
    groupTopN(items, limit = 10) {
      if (!items || items.length <= limit) return items;

      const topItems = items.slice(0, limit);
      const otherItems = items.slice(limit);
      const otherSum = otherItems.reduce((sum, item) => sum + item.value, 0);

      if (otherSum > 0) {
        topItems.push({ label: 'Other', value: otherSum });
      }

      return topItems;
    },
  },

  // Debounce helper
  debounce(func, wait = 250) {
    let timeout;
    return function executedFunction(...args) {
      const later = () => {
        clearTimeout(timeout);
        func(...args);
      };
      clearTimeout(timeout);
      timeout = setTimeout(later, wait);
    };
  },

  // Export utilities
  export: {
    /**
     * Convert array of objects to CSV string
     */
    toCSV(data, headers) {
      if (!data || data.length === 0) return '';

      // Use provided headers or extract from first object
      const cols = headers || Object.keys(data[0]);

      // Header row
      const csv = [cols.join(',')];

      // Data rows
      data.forEach(row => {
        const values = cols.map(col => {
          const value = row[col];
          // Escape commas and quotes
          if (typeof value === 'string' && (value.includes(',') || value.includes('"'))) {
            return `"${value.replace(/"/g, '""')}"`;
          }
          return value;
        });
        csv.push(values.join(','));
      });

      return csv.join('\n');
    },

    /**
     * Trigger CSV download in browser
     */
    downloadCSV(csvContent, filename) {
      const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
      const link = document.createElement('a');
      const url = URL.createObjectURL(blob);

      link.setAttribute('href', url);
      link.setAttribute('download', filename);
      link.style.visibility = 'hidden';

      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);

      URL.revokeObjectURL(url);
    },
  },

  /**
   * Escape HTML entities to prevent XSS
   * @param {string} str - String to escape
   * @returns {string} Escaped string
   */
  escapeHtml(str) {
    if (!str) return '';
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
  },

  // Icon mappings for chart labels (Remix Icon classes)
  icons: {
    browser(name) {
      if (!name) return 'ri-global-line';
      const n = name.toLowerCase();
      if (n.includes('chrome')) return 'ri-chrome-line';
      if (n.includes('firefox')) return 'ri-firefox-line';
      if (n.includes('safari')) return 'ri-safari-line';
      if (n.includes('edge')) return 'ri-edge-line';
      if (n.includes('opera')) return 'ri-opera-line';
      if (n.includes('brave')) return 'ri-shield-line';
      if (n.includes('samsung')) return 'ri-smartphone-line';
      return 'ri-global-line';
    },
    os(name) {
      if (!name) return 'ri-terminal-line';
      const n = name.toLowerCase();
      if (n.includes('windows')) return 'ri-windows-line';
      if (n.includes('mac') || n.includes('ios')) return 'ri-apple-line';
      if (n.includes('android')) return 'ri-android-line';
      if (n.includes('linux') || n.includes('ubuntu')) return 'ri-ubuntu-line';
      if (n.includes('chrome')) return 'ri-chrome-line';
      return 'ri-terminal-line';
    },
    device(name) {
      if (!name) return 'ri-device-line';
      const n = name.toLowerCase();
      if (n.includes('desktop') || n.includes('computer')) return 'ri-computer-line';
      if (n.includes('mobile') || n.includes('phone')) return 'ri-smartphone-line';
      if (n.includes('tablet')) return 'ri-tablet-line';
      return 'ri-device-line';
    },
    referrer(name) {
      if (!name) return 'ri-link';
      const n = name.toLowerCase();
      if (n.includes('google')) return 'ri-google-line';
      if (n.includes('facebook') || n.includes('fb.')) return 'ri-facebook-line';
      if (n.includes('twitter') || n.includes('t.co') || n.includes('x.com')) return 'ri-twitter-x-line';
      if (n.includes('reddit')) return 'ri-reddit-line';
      if (n.includes('linkedin')) return 'ri-linkedin-line';
      if (n.includes('github')) return 'ri-github-line';
      if (n.includes('youtube')) return 'ri-youtube-line';
      if (n.includes('instagram')) return 'ri-instagram-line';
      if (n.includes('tiktok')) return 'ri-tiktok-line';
      if (n.includes('pinterest')) return 'ri-pinterest-line';
      if (n === 'direct' || n === 'none') return 'ri-home-4-line';
      return 'ri-external-link-line';
    },
    /**
     * Convert country name or ISO code to flag emoji
     * @param {string} country - Country name (e.g., "United States") or ISO 2-letter code (e.g., "US")
     * @returns {string} Flag emoji or empty string
     */
    countryFlag(country) {
      if (!country) return '';
      // If already a 2-letter code, use directly
      let code = country.length === 2 ? country.toUpperCase() : this._countryToCode(country);
      if (!code) return '';
      const base = 0x1F1E6;
      const char1 = String.fromCodePoint(base + code.charCodeAt(0) - 65);
      const char2 = String.fromCodePoint(base + code.charCodeAt(1) - 65);
      return char1 + char2;
    },
    _countryToCode(name) {
      const map = {
        'afghanistan':'AF','albania':'AL','algeria':'DZ','argentina':'AR','armenia':'AM',
        'australia':'AU','austria':'AT','azerbaijan':'AZ','bahrain':'BH','bangladesh':'BD',
        'belarus':'BY','belgium':'BE','bolivia':'BO','bosnia and herzegovina':'BA',
        'brazil':'BR','bulgaria':'BG','cambodia':'KH','cameroon':'CM','canada':'CA',
        'chile':'CL','china':'CN','colombia':'CO','costa rica':'CR','croatia':'HR',
        'cuba':'CU','cyprus':'CY','czech republic':'CZ','czechia':'CZ',
        'denmark':'DK','dominican republic':'DO','ecuador':'EC','egypt':'EG',
        'el salvador':'SV','estonia':'EE','ethiopia':'ET','finland':'FI','france':'FR',
        'georgia':'GE','germany':'DE','ghana':'GH','greece':'GR','guatemala':'GT',
        'honduras':'HN','hong kong':'HK','hungary':'HU','iceland':'IS','india':'IN',
        'indonesia':'ID','iran':'IR','iraq':'IQ','ireland':'IE','israel':'IL',
        'italy':'IT','jamaica':'JM','japan':'JP','jordan':'JO','kazakhstan':'KZ',
        'kenya':'KE','korea':'KR','south korea':'KR','kuwait':'KW','latvia':'LV',
        'lebanon':'LB','libya':'LY','lithuania':'LT','luxembourg':'LU','malaysia':'MY',
        'mexico':'MX','moldova':'MD','mongolia':'MN','morocco':'MA','mozambique':'MZ',
        'myanmar':'MM','nepal':'NP','netherlands':'NL','new zealand':'NZ','nicaragua':'NI',
        'nigeria':'NG','north macedonia':'MK','norway':'NO','oman':'OM','pakistan':'PK',
        'panama':'PA','paraguay':'PY','peru':'PE','philippines':'PH','poland':'PL',
        'portugal':'PT','qatar':'QA','romania':'RO','russia':'RU','saudi arabia':'SA',
        'senegal':'SN','serbia':'RS','singapore':'SG','slovakia':'SK','slovenia':'SI',
        'south africa':'ZA','spain':'ES','sri lanka':'LK','sudan':'SD','sweden':'SE',
        'switzerland':'CH','syria':'SY','taiwan':'TW','tanzania':'TZ','thailand':'TH',
        'tunisia':'TN','turkey':'TR','turkiye':'TR','ukraine':'UA',
        'united arab emirates':'AE','united kingdom':'GB','united states':'US',
        'uruguay':'UY','uzbekistan':'UZ','venezuela':'VE','vietnam':'VN','yemen':'YE',
        'zambia':'ZM','zimbabwe':'ZW'
      };
      return map[name.toLowerCase()] || '';
    },
  },
};
