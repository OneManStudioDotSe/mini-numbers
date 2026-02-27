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

    const stats = this.state.stats;
    if (!stats || stats.totalRevenue === 0) {
      section.style.display = 'none';
      return;
    }

    section.style.display = '';
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
      badge.className = 'revenue-comparison';
      badge.style.cssText = `font-size: 0.75rem; font-weight: 500; margin-left: 0.5rem; color: var(--color-${isUp ? 'success' : 'danger'});`;
      badge.innerHTML = `<i class="ri-arrow-${isUp ? 'up' : 'down'}-s-line"></i>${Math.abs(change).toFixed(1)}%`;
      el.parentElement.appendChild(badge);
    }
  },

  renderBreakdown(breakdown) {
    const container = document.getElementById('revenue-breakdown');
    if (!container) return;

    if (!breakdown.length) {
      container.innerHTML = '<div style="color: var(--color-text-muted); text-align: center; padding: 1rem;">No revenue events yet</div>';
      return;
    }

    const maxRev = Math.max(...breakdown.map(b => b.revenue));
    container.innerHTML = breakdown.map(b => {
      const pct = maxRev > 0 ? (b.revenue / maxRev) * 100 : 0;
      return `
        <div style="display: flex; align-items: center; gap: 0.75rem; padding: 0.5rem 0; border-bottom: 1px solid var(--color-border);">
          <div style="flex: 1; min-width: 0;">
            <div style="font-weight: 500; font-size: 0.9rem;">${b.eventName}</div>
            <div style="font-size: 0.75rem; color: var(--color-text-muted);">${b.transactions} txn &middot; avg $${b.avgValue.toFixed(2)}</div>
          </div>
          <div style="width: 40%; position: relative; height: 20px; background: var(--color-bg-secondary); border-radius: 4px; overflow: hidden;">
            <div style="height: 100%; width: ${pct}%; background: linear-gradient(90deg, var(--color-primary), #8b5cf6); border-radius: 4px; transition: width 0.6s ease;"></div>
          </div>
          <div style="min-width: 80px; text-align: right; font-weight: 600; font-size: 0.9rem;">$${b.revenue.toFixed(2)}</div>
        </div>`;
    }).join('');
  },

  renderAttribution(attribution) {
    const tbody = document.getElementById('revenue-attribution-body');
    if (!tbody) return;

    if (!attribution.length) {
      tbody.innerHTML = '<tr><td colspan="5" style="text-align: center; color: var(--color-text-muted);">No attribution data</td></tr>';
      return;
    }

    tbody.innerHTML = attribution.map(a => `
      <tr>
        <td style="font-weight: 500;">${a.source}</td>
        <td style="font-weight: 600; color: var(--color-primary);">$${a.revenue.toFixed(2)}</td>
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
