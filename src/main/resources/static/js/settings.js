/**
 * Settings Manager - Centralized user preferences management
 * Persists settings to localStorage and dispatches change events
 */
const SettingsManager = {
  defaults: {
    timeFormat: '24h',        // '24h' or '12h'
    dateFormat: 'MM/DD/YYYY', // 'MM/DD/YYYY', 'DD/MM/YYYY', 'YYYY-MM-DD'
    heatmapColors: 'blue',    // 'blue', 'green', 'purple', 'orange'
    sectionState: {           // Dashboard section expand/collapse state
      overview: true,
      content: false,
      traffic: false,
      audience: false,
      'time-patterns': false,
      events: false,
      realtime: false,
      conversions: false,
    },
  },

  /**
   * Load settings from localStorage, fallback to defaults
   * @returns {Object} Settings object
   */
  load() {
    const stored = localStorage.getItem('mini-numbers-settings');
    return stored ? { ...this.defaults, ...JSON.parse(stored) } : this.defaults;
  },

  /**
   * Save settings to localStorage and dispatch change event
   * @param {Object} settings - Settings object to save
   */
  save(settings) {
    localStorage.setItem('mini-numbers-settings', JSON.stringify(settings));
    window.dispatchEvent(new CustomEvent('settingsChanged', { detail: settings }));
  },

  /**
   * Get a single setting value
   * @param {string} key - Setting key
   * @returns {*} Setting value
   */
  get(key) {
    return this.load()[key];
  },

  /**
   * Set a single setting value
   * @param {string} key - Setting key
   * @param {*} value - Setting value
   */
  set(key, value) {
    const settings = this.load();
    settings[key] = value;
    this.save(settings);
  }
};
