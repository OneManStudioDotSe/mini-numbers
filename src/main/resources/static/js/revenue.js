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
    
    // If no stats or zero revenue, show premium empty state instead of hiding
    if (!stats || stats.totalRevenue === 0) {
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

  renderStatCards(stats) {
    const fmt = (v) => '$' + v.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });

    const revenueEl = document.getElementById('revenue-total');
    const aovEl = document.getElementById('revenue-aov');
    const rpvEl = document.getElementById('revenue-rpv');
    const txnEl = document.getElementById('revenue-transactions');

    if (revenueEl) {
      revenueEl.textContent = fmt(stats.totalRevenue);
      this.renderComparison(revenueEl, stats.totalRevenue, stats.previousRevenue);
    }
    if (aovEl) aovEl.textContent = fmt(stats.averageOrderValue);
    if (rpvEl) rpvEl.textContent = fmt(stats.revenuePerVisitor);
    if (txnEl) {
      txnEl.textContent = stats.transactions.toLocaleString();
      this.renderComparison(txnEl, stats.transactions, stats.previousTransactions);
    }
  },

  renderComparison(el, current, previous) {
    const parent = el.closest('.stat-card') || el.parentElement;
    let badge = parent.querySelector('.revenue-comparison');
    if (badge) badge.remove();

    if (previous > 0) {
      const change = ((current - previous) / previous) * 100;
      const isUp = change >= 0;
      badge = document.createElement('span');
      badge.className = `revenue-comparison revenue-comparison--${isUp ? 'up' : 'down'}`;
      badge.innerHTML = `<i class="ri-arrow-${isUp ? 'up' : 'down'}-s-line"></i>${Math.abs(change).toFixed(1)}%`;
      el.parentElement.appendChild(badge);
    }
  },

  renderBreakdown(breakdown) {
    const container = document.getElementById('revenue-breakdown');
    if (!container) return;

    if (!breakdown.length) {
      container.innerHTML = '<div class="text-muted text-center p-md">No revenue events yet</div>';
      return;
    }

    const maxRev = Math.max(...breakdown.map(b => b.revenue));
    container.innerHTML = breakdown.map(b => {
      const pct = maxRev > 0 ? (b.revenue / maxRev) * 100 : 0;
      return `
        <div class="revenue-row">
          <div class="revenue-row__main">
            <div class="revenue-row__name">${Utils.escapeHtml(b.eventName)}</div>
            <div class="revenue-row__meta">${b.transactions} txn &middot; avg $${b.avgValue.toFixed(2)}</div>
          </div>
          <div class="revenue-row__track">
            <div class="revenue-row__fill" style="width: ${pct}%"></div>
          </div>
          <div class="revenue-row__amount">$${b.revenue.toFixed(2)}</div>
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
        <td class="font-semibold text-accent">$${a.revenue.toFixed(2)}</td>
        <td>${a.transactions}</td>
        <td>$${a.avgValue.toFixed(2)}</td>
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
