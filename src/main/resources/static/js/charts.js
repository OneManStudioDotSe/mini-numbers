/**
 * Chart Management
 * Centralized chart creation, updates, and theme management
 */

const ChartManager = {
  // Store chart instances
  instances: {},

  // Default chart options
  defaults: {
    responsive: true,
    maintainAspectRatio: false,
    interaction: {
      intersect: false,
      mode: 'index',
    },
    plugins: {
      legend: {
        display: true,
        position: 'bottom',
        labels: {
          usePointStyle: true,
          padding: 15,
          font: {
            family: 'var(--font-family-base)',
            size: 12,
          },
        },
      },
      tooltip: {
        backgroundColor: 'rgba(0, 0, 0, 0.8)',
        padding: 12,
        titleFont: {
          size: 13,
          weight: '600',
        },
        bodyFont: {
          size: 12,
        },
        borderColor: 'rgba(255, 255, 255, 0.1)',
        borderWidth: 1,
        displayColors: true,
        callbacks: {
          label: function (context) {
            let label = context.dataset.label || '';
            if (label) {
              label += ': ';
            }
            label += Utils.format.number(context.parsed.y || context.parsed);
            return label;
          },
        },
      },
    },
  },

  /**
   * Get theme-aware colors
   * @returns {Object} Color configuration
   */
  getColors() {
    const root = getComputedStyle(document.documentElement);

    return {
      primary: root.getPropertyValue('--color-primary').trim(),
      text: root.getPropertyValue('--color-text-primary').trim(),
      textSecondary: root.getPropertyValue('--color-text-secondary').trim(),
      textMuted: root.getPropertyValue('--color-text-muted').trim(),
      border: root.getPropertyValue('--color-border').trim(),
      bgSecondary: root.getPropertyValue('--color-bg-secondary').trim(),
      chart: [
        root.getPropertyValue('--chart-color-1').trim(),
        root.getPropertyValue('--chart-color-2').trim(),
        root.getPropertyValue('--chart-color-3').trim(),
        root.getPropertyValue('--chart-color-4').trim(),
        root.getPropertyValue('--chart-color-5').trim(),
        root.getPropertyValue('--chart-color-6').trim(),
        root.getPropertyValue('--chart-color-7').trim(),
        root.getPropertyValue('--chart-color-8').trim(),
        root.getPropertyValue('--chart-color-9').trim(),
        root.getPropertyValue('--chart-color-10').trim(),
      ],
      chartBg: [
        root.getPropertyValue('--chart-bg-1').trim(),
        root.getPropertyValue('--chart-bg-2').trim(),
        root.getPropertyValue('--chart-bg-3').trim(),
        root.getPropertyValue('--chart-bg-4').trim(),
        root.getPropertyValue('--chart-bg-5').trim(),
      ],
    };
  },

  /**
   * Create or update a chart
   * @param {string} id - Canvas element ID
   * @param {string} type - Chart type
   * @param {Object} data - Chart data
   * @param {Object} options - Chart options
   * @returns {Chart} Chart instance
   */
  create(id, type, data, options = {}) {
    // Destroy existing chart if it exists
    if (this.instances[id]) {
      this.instances[id].destroy();
      delete this.instances[id];
    }

    const canvas = document.getElementById(id);
    if (!canvas) {
      console.error(`Canvas element #${id} not found`);
      return null;
    }

    // Reset canvas dimensions to prevent accumulation
    const parent = canvas.parentElement;
    if (parent) {
      const computedStyle = getComputedStyle(parent);
      canvas.width = parseInt(computedStyle.width);
      canvas.height = parseInt(computedStyle.height);
    }

    const ctx = canvas.getContext('2d');
    const colors = this.getColors();

    // Merge options with defaults
    const mergedOptions = this.mergeDeep(this.defaults, options);

    // Apply theme-aware colors to scales if they exist
    if (mergedOptions.scales) {
      Object.keys(mergedOptions.scales).forEach((axis) => {
        if (mergedOptions.scales[axis].grid) {
          mergedOptions.scales[axis].grid.color = colors.border;
        }
        if (mergedOptions.scales[axis].ticks) {
          mergedOptions.scales[axis].ticks.color = colors.textSecondary;
        }
      });
    }

    // Update legend colors
    if (mergedOptions.plugins?.legend?.labels) {
      mergedOptions.plugins.legend.labels.color = colors.text;
    }

    // Destroy existing chart instance if it exists
    if (this.instances[id]) {
      this.instances[id].destroy();
      delete this.instances[id];
    }

    const chart = new Chart(ctx, {
      type,
      data,
      options: mergedOptions,
    });

    this.instances[id] = chart;
    return chart;
  },

  /**
   * Create sparkline chart
   * @param {string} id - Canvas element ID
   * @param {Array} data - Data points
   * @returns {Chart} Chart instance
   */
  createSparkline(id, data) {
    const colors = this.getColors();

    return this.create(
      id,
      'line',
      {
        labels: data.map((_, i) => i),
        datasets: [
          {
            data: data,
            borderColor: colors.primary,
            backgroundColor: colors.chartBg[0],
            borderWidth: 2,
            fill: true,
            tension: 0.4,
            pointRadius: 0,
            pointHoverRadius: 0,
          },
        ],
      },
      {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { display: false },
          tooltip: { enabled: false },
        },
        scales: {
          x: { display: false },
          y: { display: false },
        },
      }
    );
  },

  /**
   * Create activity heatmap chart with optional peak highlighting
   * @param {string} id - Canvas element ID
   * @param {Array} heatmapData - Array of {dayOfWeek, hourOfDay, count} objects
   * @param {number} peakHour - Peak hour (0-23) for highlighting (optional)
   * @param {number} peakDay - Peak day (0-6) for highlighting (optional)
   * @returns {Chart} Chart instance
   */
  createHeatmap(id, heatmapData, peakHour = null, peakDay = null, dateLabels = null) {
    const colors = this.getColors();
    const isDark = ThemeManager.isDark();

    // Transform to matrix format
    const dataPoints = [];
    const maxValue = Math.max(...heatmapData.map(cell => cell.count), 1);

    heatmapData.forEach(cell => {
      dataPoints.push({
        x: cell.hourOfDay,
        y: cell.dayOfWeek,
        v: cell.count
      });
    });

    // Get heatmap color from settings
    const heatmapColor = typeof SettingsManager !== 'undefined'
      ? SettingsManager.get('heatmapColors') || 'blue'
      : 'blue';

    const colorMap = {
      blue:   { light: [37, 99, 235],   dark: [96, 165, 250] },
      green:  { light: [34, 197, 94],   dark: [74, 222, 128] },
      purple: { light: [139, 92, 246],  dark: [167, 139, 250] },
      orange: { light: [249, 115, 22],  dark: [251, 146, 60] },
    };

    const scheme = colorMap[heatmapColor] || colorMap.blue;
    const baseColor = isDark ? scheme.dark : scheme.light;
    const peakColor = [239, 68, 68]; // Red for peak cells

    // Update legend gradient to match selected color
    const legendEl = document.querySelector('.legend-gradient');
    if (legendEl) {
      legendEl.style.background = `linear-gradient(to right, rgba(${baseColor.join(',')}, 0.1), rgba(${baseColor.join(',')}, 1))`;
    }

    // Day labels with optional dates
    const defaultLabels = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
    const yLabels = dateLabels || defaultLabels;

    return this.create(
      id,
      'matrix',
      {
        datasets: [{
          label: 'Activity',
          data: dataPoints,
          backgroundColor: (context) => {
            const value = context.dataset.data[context.dataIndex]?.v || 0;
            const x = context.dataset.data[context.dataIndex]?.x;
            const y = context.dataset.data[context.dataIndex]?.y;

            const isPeak = (peakHour !== null && peakDay !== null && x === peakHour && y === peakDay);

            const alpha = value / maxValue;
            const color = isPeak ? peakColor : baseColor;

            const minAlpha = isPeak ? 0.6 : 0.1;
            return `rgba(${color[0]}, ${color[1]}, ${color[2]}, ${Math.max(alpha, minAlpha)})`;
          },
          borderColor: colors.border,
          borderWidth: 1,
          width: (ctx) => {
            const area = ctx.chart.chartArea;
            return area ? (area.width / 24) - 1 : 10;
          },
          height: (ctx) => {
            const area = ctx.chart.chartArea;
            return area ? (area.height / 7) - 1 : 10;
          }
        }]
      },
      {
        responsive: true,
        maintainAspectRatio: false,
        layout: {
          padding: {
            bottom: 10
          }
        },
        plugins: {
          legend: { display: false },
          tooltip: {
            callbacks: {
              title: (items) => {
                const dayIndex = items[0].raw.y;
                const days = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
                const day = days[dayIndex] || 'Unknown';
                const hour = items[0].raw.x;
                const isPeak = (peakHour !== null && peakDay !== null &&
                               items[0].raw.x === peakHour && dayIndex === peakDay);
                return `${day} at ${hour}:00${isPeak ? ' \uD83D\uDD25 PEAK' : ''}`;
              },
              label: (context) => `${context.raw.v} visits`
            }
          }
        },
        scales: {
          x: {
            type: 'linear',
            min: -0.5,
            max: 23.5,
            offset: true,
            ticks: {
              stepSize: 1,
              color: colors.textSecondary,
              font: { size: 11, weight: '500' },
              callback: (val) => val >= 0 && val <= 23 ? `${val}:00` : ''
            },
            title: {
              display: true,
              text: 'Hour of Day',
              color: colors.text,
              font: { size: 12, weight: '600' },
              padding: { top: 8 }
            },
            grid: { display: false }
          },
          y: {
            type: 'linear',
            min: -0.5,
            max: 6.5,
            offset: true,
            reverse: true,
            ticks: {
              stepSize: 1,
              color: colors.textSecondary,
              font: { size: 11, weight: '500' },
              callback: (val) => val >= 0 && val <= 6 ? (yLabels[val] || '') : ''
            },
            title: {
              display: true,
              text: 'Day of Week',
              color: colors.text,
              font: { size: 12, weight: '600' },
              padding: { bottom: 8 }
            },
            grid: { display: false }
          }
        }
      }
    );
  },

  /**
   * Create radar chart for multi-dimensional data
   * @param {string} id - Canvas element ID
   * @param {Array} data - Array of {label, value} objects
   * @returns {Chart} Chart instance
   */
  createRadarChart(id, data) {
    const colors = this.getColors();

    // Transform data for radar chart
    const labels = data.map(item => item.label);
    const values = data.map(item => item.value);

    return this.create(
      id,
      'radar',
      {
        labels: labels,
        datasets: [{
          label: 'Activity',
          data: values,
          backgroundColor: `${colors.primary}33`,  // 20% opacity
          borderColor: colors.primary,
          borderWidth: 2,
          pointBackgroundColor: colors.primary,
          pointBorderColor: colors.background,
          pointBorderWidth: 2,
          pointRadius: 4,
          pointHoverRadius: 6
        }]
      },
      {
        responsive: true,
        maintainAspectRatio: false,
        scales: {
          r: {
            beginAtZero: true,
            ticks: {
              color: colors.text,
              backdropColor: 'transparent'
            },
            grid: {
              color: colors.grid
            },
            pointLabels: {
              color: colors.text,
              font: {
                size: 12
              }
            }
          }
        },
        plugins: {
          legend: {
            display: false
          },
          tooltip: {
            enabled: true,
            callbacks: {
              label: (context) => `${context.parsed.r} visits`
            }
          }
        }
      }
    );
  },

  /**
   * Create horizontal bar chart
   * @param {string} id - Canvas element ID
   * @param {Array} items - Array of {label, value} objects
   * @param {Object} options - Additional options
   * @returns {Chart} Chart instance
   */
  createBarChart(id, items, options = {}) {
    const colors = this.getColors();
    const labels = items.map((item) => item.label);
    const values = items.map((item) => item.value);

    return this.create(
      id,
      'bar',
      {
        labels: labels,
        datasets: [
          {
            data: values,
            backgroundColor: colors.chart[0],
            borderRadius: 6,
            barThickness: 20,
          },
        ],
      },
      {
        indexAxis: 'y',
        plugins: {
          legend: { display: false },
        },
        scales: {
          x: {
            beginAtZero: true,
            grace: '5%',
            grid: {
              display: true,
            },
            ticks: {
              callback: function (value) {
                return Utils.format.compact(value);
              },
            },
          },
          y: {
            grid: {
              display: false,
            },
            ticks: {
              padding: 8,
            },
          },
        },
        elements: {
          bar: {
            borderRadius: 6,
            categoryPercentage: 0.7,
            barPercentage: 0.75,
          },
        },
        onClick: (event, activeElements, chart) => {
          if (activeElements.length > 0) {
            const index = activeElements[0].index;
            const label = chart.data.labels[index];
            const value = chart.data.datasets[0].data[index];

            window.dispatchEvent(new CustomEvent('chartBarClick', {
              detail: { label, value, chartId: chart.canvas.id }
            }));
          }
        },
        onHover: (event, activeElements, chart) => {
          chart.canvas.style.cursor = activeElements.length > 0 ? 'pointer' : 'default';
        },
        ...options,
      }
    );
  },

  /**
   * Create HTML-based horizontal bar chart with icons
   * Replaces Canvas chart with styled HTML bars for icon support
   * @param {string} id - Canvas element ID (canvas will be hidden, HTML placed alongside)
   * @param {Array} items - Array of {label, value} objects
   * @param {string} iconType - Icon category: 'browser', 'os', 'device', 'referrer', 'country'
   */
  createIconBarChart(id, items, iconType) {
    const canvas = document.getElementById(id);
    if (!canvas) return;

    const container = canvas.parentElement;

    // Destroy any existing Chart.js instance on this canvas
    if (this.instances[id]) {
      this.instances[id].destroy();
      delete this.instances[id];
    }

    // Hide canvas
    canvas.style.display = 'none';

    // Remove any existing icon bar chart
    const existingChart = container.querySelector('.icon-bar-chart');
    if (existingChart) existingChart.remove();

    if (!items || items.length === 0) return;

    const maxValue = Math.max(...items.map(d => d.value));
    const total = items.reduce((sum, d) => sum + d.value, 0);

    const chartEl = document.createElement('div');
    chartEl.className = 'icon-bar-chart';

    chartEl.innerHTML = items.map(item => {
      const pct = maxValue > 0 ? (item.value / maxValue) * 100 : 0;
      const pctOfTotal = total > 0 ? ((item.value / total) * 100).toFixed(1) : '0.0';
      let iconHtml = '';

      if (iconType === 'country') {
        const flag = Utils.icons.countryFlag(item.label);
        iconHtml = flag ? `<span class="icon-bar-chart__flag">${flag}</span>` : '';
      } else if (iconType && Utils.icons[iconType]) {
        const iconClass = Utils.icons[iconType](item.label);
        iconHtml = `<i class="${iconClass} icon-bar-chart__icon"></i>`;
      }

      return `
        <div class="icon-bar-chart__item" data-label="${Utils.escapeHtml(item.label)}" data-value="${item.value}">
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

    // Make items clickable (dispatch chartBarClick for country drill-down etc.)
    chartEl.addEventListener('click', (e) => {
      const item = e.target.closest('.icon-bar-chart__item');
      if (item) {
        window.dispatchEvent(new CustomEvent('chartBarClick', {
          detail: { label: item.dataset.label, value: parseInt(item.dataset.value), chartId: id }
        }));
      }
    });

    container.appendChild(chartEl);
  },

  /**
   * Create doughnut chart with custom HTML icon legend
   * @param {string} id - Canvas element ID
   * @param {Array} items - Array of {label, value} objects
   * @param {string} iconType - Icon category for legend: 'browser', 'os', 'device'
   * @param {Object} options - Additional options
   * @returns {Chart} Chart instance
   */
  createIconDoughnutChart(id, items, iconType, options = {}) {
    const colors = this.getColors();
    const labels = items.map(item => item.label);
    const values = items.map(item => item.value);
    const total = values.reduce((a, b) => a + b, 0);

    const backgroundColors = labels.map(
      (_, index) => colors.chart[index % colors.chart.length]
    );

    const chart = this.create(
      id,
      'doughnut',
      {
        labels,
        datasets: [{
          data: values,
          backgroundColor: backgroundColors,
          borderWidth: 2,
          borderColor: colors.bgSecondary,
        }],
      },
      {
        plugins: {
          legend: { display: false }, // We'll use custom HTML legend
          tooltip: {
            callbacks: {
              label: (context) => {
                const label = context.label || '';
                const value = context.parsed || 0;
                const percentage = ((value / total) * 100).toFixed(1);
                return `${label}: ${Utils.format.number(value)} (${percentage}%)`;
              },
            },
          },
        },
        ...options,
      }
    );

    // Build custom HTML legend with icons
    const canvas = document.getElementById(id);
    if (canvas) {
      const container = canvas.parentElement;
      let legendEl = container.querySelector('.icon-legend');
      if (legendEl) legendEl.remove();

      legendEl = document.createElement('div');
      legendEl.className = 'icon-legend';
      legendEl.innerHTML = items.map((item, i) => {
        const color = backgroundColors[i % backgroundColors.length];
        const iconClass = Utils.icons[iconType] ? Utils.icons[iconType](item.label) : '';
        const pct = total > 0 ? ((item.value / total) * 100).toFixed(1) : '0.0';
        return `
          <div class="icon-legend__item">
            <span class="icon-legend__color" style="background: ${color}"></span>
            ${iconClass ? `<i class="${iconClass} icon-legend__icon"></i>` : ''}
            <span class="icon-legend__label">${Utils.escapeHtml(item.label)}</span>
            <span class="icon-legend__value">${pct}%</span>
          </div>`;
      }).join('');

      container.appendChild(legendEl);
    }

    return chart;
  },

  /**
   * Create doughnut chart
   * @param {string} id - Canvas element ID
   * @param {Array} items - Array of {label, value} objects
   * @param {Object} options - Additional options
   * @returns {Chart} Chart instance
   */
  createDoughnutChart(id, items, options = {}) {
    const colors = this.getColors();
    const labels = items.map((item) => item.label);
    const values = items.map((item) => item.value);

    // Assign colors from palette
    const backgroundColors = labels.map(
      (_, index) => colors.chart[index % colors.chart.length]
    );

    return this.create(
      id,
      'doughnut',
      {
        labels: labels,
        datasets: [
          {
            data: values,
            backgroundColor: backgroundColors,
            borderWidth: 2,
            borderColor: colors.bgSecondary,
          },
        ],
      },
      {
        plugins: {
          legend: {
            display: true,
            position: 'bottom',
          },
          tooltip: {
            callbacks: {
              label: function (context) {
                const label = context.label || '';
                const value = context.parsed || 0;
                const total = context.dataset.data.reduce((a, b) => a + b, 0);
                const percentage = ((value / total) * 100).toFixed(1);
                return `${label}: ${Utils.format.number(value)} (${percentage}%)`;
              },
            },
          },
        },
        ...options,
      }
    );
  },

  /**
   * Create line chart with area fill
   * @param {string} id - Canvas element ID
   * @param {Array} data - Array of {timestamp, count} objects
   * @param {Object} options - Additional options
   * @returns {Chart} Chart instance
   */
  createLineChart(id, data, options = {}) {
    const colors = this.getColors();
    const labels = data.map((item) => {
      const date = new Date(item.timestamp);
      return date.toLocaleDateString('en-US', {
        month: 'short',
        day: 'numeric',
        hour: data.length <= 24 ? '2-digit' : undefined,
      });
    });
    const values = data.map((item) => item.count);

    return this.create(
      id,
      'line',
      {
        labels: labels,
        datasets: [
          {
            label: 'Page views',
            data: values,
            borderColor: colors.chart[0],
            backgroundColor: colors.chartBg[0],
            borderWidth: 2,
            fill: true,
            tension: 0.4,
            pointRadius: 4,
            pointHoverRadius: 6,
            pointBackgroundColor: colors.chart[0],
            pointBorderColor: colors.bgSecondary,
            pointBorderWidth: 2,
          },
        ],
      },
      {
        plugins: {
          legend: { display: false },
        },
        scales: {
          x: {
            grid: {
              display: false,
            },
          },
          y: {
            beginAtZero: true,
            grace: '10%',
            grid: {
              display: true,
            },
            ticks: {
              precision: 0,
              callback: function (value) {
                return Utils.format.compact(value);
              },
            },
          },
        },
        ...options,
      }
    );
  },

  /**
   * Update existing chart with new data
   * @param {string} id - Chart ID
   * @param {Object} newData - New chart data
   */
  update(id, newData) {
    const chart = this.instances[id];
    if (!chart) {
      console.warn(`Chart #${id} not found`);
      return;
    }

    chart.data = newData;
    chart.update();
  },

  /**
   * Destroy chart instance
   * @param {string} id - Chart ID
   */
  destroy(id) {
    const chart = this.instances[id];
    if (chart) {
      chart.destroy();
      delete this.instances[id];
    }
  },

  /**
   * Update all charts for theme change
   */
  updateTheme() {
    const colors = this.getColors();

    Object.entries(this.instances).forEach(([id, chart]) => {
      // Update scales colors
      if (chart.options.scales) {
        Object.keys(chart.options.scales).forEach((axis) => {
          if (chart.options.scales[axis].grid) {
            chart.options.scales[axis].grid.color = colors.border;
          }
          if (chart.options.scales[axis].ticks) {
            chart.options.scales[axis].ticks.color = colors.textSecondary;
          }
        });
      }

      // Update legend colors
      if (chart.options.plugins?.legend?.labels) {
        chart.options.plugins.legend.labels.color = colors.text;
      }

      // Update dataset colors for specific chart types
      if (chart.config.type === 'doughnut' || chart.config.type === 'pie') {
        chart.data.datasets.forEach((dataset) => {
          dataset.borderColor = colors.bgSecondary;
        });
      }

      chart.update();
    });
  },

  /**
   * Deep merge objects (for options)
   * @param {Object} target - Target object
   * @param {Object} source - Source object
   * @returns {Object} Merged object
   */
  mergeDeep(target, source) {
    const output = Object.assign({}, target);
    if (this.isObject(target) && this.isObject(source)) {
      Object.keys(source).forEach((key) => {
        if (this.isObject(source[key])) {
          if (!(key in target)) {
            Object.assign(output, { [key]: source[key] });
          } else {
            output[key] = this.mergeDeep(target[key], source[key]);
          }
        } else {
          Object.assign(output, { [key]: source[key] });
        }
      });
    }
    return output;
  },

  /**
   * Check if value is object
   * @param {*} item - Value to check
   * @returns {boolean} True if object
   */
  isObject(item) {
    return item && typeof item === 'object' && !Array.isArray(item);
  },
};

// Listen for theme changes and update all charts
window.addEventListener('themeChange', () => {
  ChartManager.updateTheme();
});
