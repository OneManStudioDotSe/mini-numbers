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
  },

  // Date/Time formatting
  time: {
    /**
     * Format ISO timestamp to readable time
     * @param {string} isoString - ISO timestamp
     * @returns {string} Formatted time (HH:MM)
     */
    formatTime(isoString) {
      if (!isoString) return '';
      try {
        const date = new Date(isoString);
        return date.toLocaleTimeString('en-US', {
          hour: '2-digit',
          minute: '2-digit',
          hour12: false,
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

  // API helpers
  api: {
    /**
     * Fetch data with caching and error handling
     * @param {string} url - API URL
     * @param {Object} options - Fetch options
     * @returns {Promise} Response data
     */
    async fetch(url, options = {}) {
      // Extract our custom cache option
      const useCache = options.useCache !== false; // Default true
      const { useCache: _, ...fetchOptions } = options; // Remove custom option

      // Check cache first
      if (useCache && Utils.cache.has(url)) {
        return Utils.cache.get(url);
      }

      try {
        const response = await fetch(url, {
          headers: {
            ...Utils.auth.getHeader(),
            ...fetchOptions.headers,
          },
          ...fetchOptions,
        });

        if (!response.ok) {
          throw new Error(`HTTP ${response.status}: ${response.statusText}`);
        }

        const data = await response.json();

        // Cache successful responses
        if (useCache) {
          Utils.cache.set(url, data);
        }

        return data;
      } catch (error) {
        console.error('API fetch error:', error);
        Utils.toast.error(`Failed to fetch data: ${error.message}`);
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
};
