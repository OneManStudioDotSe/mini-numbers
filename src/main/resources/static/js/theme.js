/**
 * Theme Management
 * Handles light/dark mode switching with localStorage persistence
 */

const ThemeManager = {
  // Storage key
  STORAGE_KEY: 'ma-theme-preference',

  // Available themes
  THEMES: {
    LIGHT: 'light',
    DARK: 'dark',
  },

  /**
   * Initialize theme system
   * Loads saved preference or detects system preference
   */
  init() {
    // Prevent transition flash on page load
    document.documentElement.classList.add('no-transition');

    // Load and apply theme
    const savedTheme = this.getSavedTheme();
    const theme = savedTheme || this.getSystemPreference();
    this.applyTheme(theme);

    // Remove no-transition class after a brief delay
    setTimeout(() => {
      document.documentElement.classList.remove('no-transition');
    }, 100);

    // Listen for system theme changes
    this.watchSystemPreference();
  },

  /**
   * Get saved theme from localStorage
   * @returns {string|null} Saved theme or null
   */
  getSavedTheme() {
    try {
      return localStorage.getItem(this.STORAGE_KEY);
    } catch (error) {
      console.warn('Unable to access localStorage for theme:', error);
      return null;
    }
  },

  /**
   * Save theme preference to localStorage
   * @param {string} theme - Theme to save
   */
  saveTheme(theme) {
    try {
      localStorage.setItem(this.STORAGE_KEY, theme);
    } catch (error) {
      console.warn('Unable to save theme to localStorage:', error);
    }
  },

  /**
   * Detect system/OS theme preference
   * @returns {string} System theme (light or dark)
   */
  getSystemPreference() {
    if (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) {
      return this.THEMES.DARK;
    }
    return this.THEMES.LIGHT;
  },

  /**
   * Watch for system theme preference changes
   */
  watchSystemPreference() {
    if (window.matchMedia) {
      const darkModeQuery = window.matchMedia('(prefers-color-scheme: dark)');

      // Modern browsers
      if (darkModeQuery.addEventListener) {
        darkModeQuery.addEventListener('change', (e) => {
          // Only apply if no saved preference
          if (!this.getSavedTheme()) {
            const newTheme = e.matches ? this.THEMES.DARK : this.THEMES.LIGHT;
            this.applyTheme(newTheme);
          }
        });
      }
      // Fallback for older browsers
      else if (darkModeQuery.addListener) {
        darkModeQuery.addListener((e) => {
          if (!this.getSavedTheme()) {
            const newTheme = e.matches ? this.THEMES.DARK : this.THEMES.LIGHT;
            this.applyTheme(newTheme);
          }
        });
      }
    }
  },

  /**
   * Apply theme to the page
   * @param {string} theme - Theme to apply
   */
  applyTheme(theme) {
    const htmlElement = document.documentElement;

    if (theme === this.THEMES.DARK) {
      htmlElement.setAttribute('data-theme', 'dark');
    } else {
      htmlElement.removeAttribute('data-theme');
    }

    // Dispatch custom event for other components (e.g., charts)
    window.dispatchEvent(
      new CustomEvent('themeChange', {
        detail: { theme },
      })
    );
  },

  /**
   * Get current active theme
   * @returns {string} Current theme
   */
  getCurrentTheme() {
    const htmlElement = document.documentElement;
    return htmlElement.getAttribute('data-theme') === 'dark'
      ? this.THEMES.DARK
      : this.THEMES.LIGHT;
  },

  /**
   * Toggle between light and dark themes
   */
  toggle() {
    const currentTheme = this.getCurrentTheme();
    const newTheme =
      currentTheme === this.THEMES.DARK ? this.THEMES.LIGHT : this.THEMES.DARK;

    this.applyTheme(newTheme);
    this.saveTheme(newTheme);

    return newTheme;
  },

  /**
   * Check if dark mode is active
   * @returns {boolean} True if dark mode
   */
  isDark() {
    return this.getCurrentTheme() === this.THEMES.DARK;
  },
};

// Initialize theme on load
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', () => ThemeManager.init());
} else {
  ThemeManager.init();
}
