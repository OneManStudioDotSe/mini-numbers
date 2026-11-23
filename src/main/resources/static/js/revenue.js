/**
 * Revenue Dashboard Manager
 * Handles loading and rendering revenue analytics data.
 */

const RevenueManager = {
  state: {
    stats: null,
    breakdown: [],
    attribution: []
  },

  // ── API ─────────────────────────────────────────────────────

  async loadRevenue(projectId, filter) {
    try {
      const [statsRes, breakdownRes, attributionRes] = await Promise.all([
        fetch(`/admin/projects/${projectId}/revenue?filter=${filter}`),
        fetch(`/admin/projects/${projectId}/revenue/breakdown?filter=${filter}`),
        fetch(`/admin/projects/${projectId}/revenue/attribution?filter=${filter}`)
      ]);

      if (statsRes.ok) this.state.stats = await statsRes.json();
      if (breakdownRes.ok) this.state.breakdown = await breakdownRes.json();
      if (attributionRes.ok) this.state.attribution = await attributionRes.json();

      return this.state;
    } catch (e) {
      console.error('Failed to load revenue data:', e);
      return this.state;
    }
  },

  // ── Rendering ─────────────────────────────────────────────────

  render(projectId) {
    const section = document.getElementById('revenue-section');
    if (!section) return;

    // First-time load: capture original HTML to restore it later if data arrives
    if (!section.dataset.originalHtml) {
      section.dataset.originalHtml = section.innerHTML;
    }

    const stats = this.state.stats;
    
    // If there is no revenue event in the period, show the empty state instead of hiding
    if (!stats || !stats.currencies.length) {
      section.style.display = ''; // Keep section visible
      section.innerHTML = ''; // Clear for empty state
      Utils.dom.showEmptyState(section, { 
        icon: '&#128176;', 
        message: 'No revenue data yet', 
        hint: 'Start tracking income by adding a "revenue" property to your custom events.' 
      });
      return;
    }

    section.style.display = '';
    // Restore layout if it was an empty state
    if (section.innerHTML.includes('empty-state')) {
      section.innerHTML = section.dataset.originalHtml;
      this.initGuide(); // Re-bind guide events after restoring HTML
    }
    
    this.renderStatCards(stats);
    this.renderBreakdown(this.state.breakdown);
    this.renderAttribution(this.state.attribution);
  },

  // An amount with the currency's own symbol; a code Intl does not know is shown as "<amount> <CODE>"
  formatAmount(amount, currency) {
    try {
      return new Intl.NumberFormat(undefined, { style: 'currency', currency }).format(amount);
    } catch (e) {
      return `${amount.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })} ${currency}`;
    }
  },

  comparisonBadge(current, previous) {
    if (!(previous > 0)) return '';
    const change = ((current - previous) / previous) * 100;
    const dir = change >= 0 ? 'up' : 'down';
    return `<span class="revenue-comparison revenue-comparison--${dir}"><i class="ri-arrow-${dir}-s-line"></i>${Math.abs(change).toFixed(1)}%</span>`;
  },

  // One line per currency: amounts of different currencies are never added together
  renderStatCards(stats) {
    const lines = (id, html) => {
      const el = document.getElementById(id);
      if (!el) return;
      el.classList.toggle('revenue-amounts--multi', stats.currencies.length > 1);
      el.innerHTML = stats.currencies.map(c => `<div class="revenue-amount">${html(c)}</div>`).join('');
    };
    const fmt = (v, c) => Utils.escapeHtml(this.formatAmount(v, c.currency));

    lines('revenue-total', c => `${fmt(c.totalRevenue, c)}${this.comparisonBadge(c.totalRevenue, c.previousRevenue)}`);
    lines('revenue-aov', c => fmt(c.averageOrderValue, c));
    lines('revenue-rpv', c => fmt(c.revenuePerVisitor, c));

    const refunded = stats.currencies.filter(c => c.refundCount > 0);
    const refundsEl = document.getElementById('revenue-refunds');
    if (refundsEl) {
      refundsEl.innerHTML = refunded.length
        ? 'Refunds: ' + refunded.map(c => `-${fmt(c.refunds, c)} (${c.refundCount})`).join(', ')
        : '';
    }

    const txnEl = document.getElementById('revenue-transactions');
    if (txnEl) {
      const current = stats.currencies.reduce((n, c) => n + c.transactions, 0);
      const previous = stats.currencies.reduce((n, c) => n + c.previousTransactions, 0);
      txnEl.innerHTML = `${current.toLocaleString()}${this.comparisonBadge(current, previous)}`;
    }
  },

  renderBreakdown(breakdown) {
    const container = document.getElementById('revenue-breakdown');
    if (!container) return;

    if (!breakdown.length) {
      container.innerHTML = '<div class="text-muted text-center p-md">No revenue events yet</div>';
      return;
    }

    // Bars compare amounts of the same currency only
    const maxRev = {};
    breakdown.forEach(b => { maxRev[b.currency] = Math.max(maxRev[b.currency] || 0, b.revenue); });
    container.innerHTML = breakdown.map(b => {
      const pct = maxRev[b.currency] > 0 ? (Math.max(b.revenue, 0) / maxRev[b.currency]) * 100 : 0;
      const refunds = b.refunds > 0 ? ` &middot; refunded ${Utils.escapeHtml(this.formatAmount(b.refunds, b.currency))}` : '';
      return `
        <div class="revenue-row">
          <div class="revenue-row__main">
            <div class="revenue-row__name">${Utils.escapeHtml(b.eventName)}</div>
            <div class="revenue-row__meta">${b.transactions} txn &middot; avg ${Utils.escapeHtml(this.formatAmount(b.avgValue, b.currency))}${refunds}</div>
          </div>
          <div class="revenue-row__track">
            <div class="revenue-row__fill" style="width: ${pct}%"></div>
          </div>
          <div class="revenue-row__amount">${Utils.escapeHtml(this.formatAmount(b.revenue, b.currency))}</div>
        </div>`;
    }).join('');
  },

  renderAttribution(attribution) {
    const tbody = document.getElementById('revenue-attribution-body');
    if (!tbody) return;

    if (!attribution.length) {
      tbody.innerHTML = '<tr><td colspan="5" class="cell-placeholder">No attribution data</td></tr>';
      return;
    }

    tbody.innerHTML = attribution.map(a => `
      <tr>
        <td class="font-medium">${Utils.escapeHtml(a.source)}</td>
        <td class="font-semibold text-accent">${Utils.escapeHtml(this.formatAmount(a.revenue, a.currency))}</td>
        <td>${a.transactions}</td>
        <td>${Utils.escapeHtml(this.formatAmount(a.avgValue, a.currency))}</td>
        <td>${a.conversionRate.toFixed(1)}%</td>
      </tr>
    `).join('');
  },

  // ── Guide toggle ──────────────────────────────────────────────

  initGuide() {
    const toggle = document.getElementById('revenue-guide-toggle');
    const content = document.getElementById('revenue-guide-content');
    if (toggle && content) {
      toggle.onclick = () => {
        const expanded = toggle.getAttribute('aria-expanded') === 'true';
        toggle.setAttribute('aria-expanded', !expanded);
        content.style.display = expanded ? 'none' : 'block';
      };
    }
  }
};
