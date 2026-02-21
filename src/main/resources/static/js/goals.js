/**
 * Goals & Funnels Manager
 * Handles conversion goals and funnel tracking UI
 */

const GoalsManager = {
  state: {
    goals: [],
    funnels: [],
    funnelAnalyses: {}
  },

  // ── Goals ──────────────────────────────────────────────────

  async loadGoalStats(projectId, filter) {
    try {
      const response = await fetch(`/admin/projects/${projectId}/goals/stats?filter=${filter}`);
      if (!response.ok) return [];
      return await response.json();
    } catch (e) {
      console.error('Failed to load goal stats:', e);
      return [];
    }
  },

  async loadGoals(projectId) {
    try {
      const response = await fetch(`/admin/projects/${projectId}/goals`);
      if (!response.ok) return [];
      this.state.goals = await response.json();
      return this.state.goals;
    } catch (e) {
      console.error('Failed to load goals:', e);
      return [];
    }
  },

  async createGoal(projectId, data) {
    const response = await fetch(`/admin/projects/${projectId}/goals`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data)
    });
    if (!response.ok) {
      const err = await response.json();
      throw new Error(err.error || 'Failed to create goal');
    }
    return await response.json();
  },

  async updateGoal(projectId, goalId, data) {
    const response = await fetch(`/admin/projects/${projectId}/goals/${goalId}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data)
    });
    if (!response.ok) throw new Error('Failed to update goal');
    return await response.json();
  },

  async deleteGoal(projectId, goalId) {
    const response = await fetch(`/admin/projects/${projectId}/goals/${goalId}`, {
      method: 'DELETE'
    });
    if (!response.ok) throw new Error('Failed to delete goal');
  },

  // ── Funnels ────────────────────────────────────────────────

  async loadFunnels(projectId) {
    try {
      const response = await fetch(`/admin/projects/${projectId}/funnels`);
      if (!response.ok) return [];
      this.state.funnels = await response.json();
      return this.state.funnels;
    } catch (e) {
      console.error('Failed to load funnels:', e);
      return [];
    }
  },

  async createFunnel(projectId, data) {
    const response = await fetch(`/admin/projects/${projectId}/funnels`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data)
    });
    if (!response.ok) {
      const err = await response.json();
      throw new Error(err.error || 'Failed to create funnel');
    }
    return await response.json();
  },

  async deleteFunnel(projectId, funnelId) {
    const response = await fetch(`/admin/projects/${projectId}/funnels/${funnelId}`, {
      method: 'DELETE'
    });
    if (!response.ok) throw new Error('Failed to delete funnel');
  },

  async loadFunnelAnalysis(projectId, funnelId, filter) {
    try {
      const response = await fetch(`/admin/projects/${projectId}/funnels/${funnelId}/analysis?filter=${filter}`);
      if (!response.ok) return null;
      const analysis = await response.json();
      this.state.funnelAnalyses[funnelId] = analysis;
      return analysis;
    } catch (e) {
      console.error('Failed to load funnel analysis:', e);
      return null;
    }
  },

  // ── Rendering ──────────────────────────────────────────────

  renderGoals(goalStats) {
    const container = document.getElementById('goals-list');
    const section = document.getElementById('goals-section');
    if (!container || !section) return;

    // Always show the section so users can create goals
    section.style.display = '';

    if (!goalStats || goalStats.length === 0) {
      container.innerHTML = `
        <div style="text-align: center; padding: var(--spacing-lg); color: var(--color-text-muted);">
          <p>No conversion goals configured yet.</p>
          <p style="font-size: var(--font-size-sm);">Click "Manage goals" to create your first goal.</p>
        </div>`;
      return;
    }

    container.innerHTML = `
      <div class="goals-grid">
        ${goalStats.map(stat => {
          const change = stat.conversionRate - stat.previousConversionRate;
          const changeClass = change > 0 ? 'positive' : change < 0 ? 'negative' : '';
          const changeIcon = change > 0 ? '&uarr;' : change < 0 ? '&darr;' : '';
          const typeIcon = stat.goal.goalType === 'url' ? 'ph-link' : 'ph-lightning';

          return `
            <div class="goal-card">
              <div class="goal-card__header">
                <div class="goal-card__icon">
                  <i class="ph-duotone ${typeIcon}"></i>
                </div>
                <div>
                  <div class="goal-card__name">${Utils.escapeHtml(stat.goal.name)}</div>
                  <div class="goal-card__match">${Utils.escapeHtml(stat.goal.matchValue)}</div>
                </div>
              </div>
              <div class="goal-card__rate">${stat.conversionRate.toFixed(1)}%</div>
              <div class="goal-card__meta">
                <span>${stat.conversions} conversion${stat.conversions !== 1 ? 's' : ''}</span>
                ${change !== 0 ? `
                  <span class="stat-card__comparison ${changeClass}">
                    ${changeIcon} ${Math.abs(change).toFixed(1)}%
                  </span>
                ` : ''}
              </div>
            </div>`;
        }).join('')}
      </div>`;

  },

  renderFunnels(funnels, projectId, filter) {
    const container = document.getElementById('funnels-list');
    const section = document.getElementById('funnels-section');
    if (!container || !section) return;

    // Always show the section so users can create funnels
    section.style.display = '';

    if (!funnels || funnels.length === 0) {
      container.innerHTML = `
        <div style="text-align: center; padding: var(--spacing-lg); color: var(--color-text-muted);">
          <p>No funnels configured yet.</p>
          <p style="font-size: var(--font-size-sm);">Click "Manage funnels" to create your first funnel.</p>
        </div>`;
      return;
    }

    container.innerHTML = funnels.map(funnel => `
      <div class="funnel-card" id="funnel-${funnel.id}">
        <div class="funnel-card__header">
          <h4>${Utils.escapeHtml(funnel.name)}</h4>
          <span class="text-muted text-xs">${funnel.steps.length} steps</span>
        </div>
        <div class="funnel-visualization" id="funnel-viz-${funnel.id}">
          <div style="text-align: center; padding: var(--spacing-md); color: var(--color-text-muted);">Loading analysis...</div>
        </div>
      </div>
    `).join('');

    // Load analysis for each funnel
    funnels.forEach(funnel => {
      this.loadFunnelAnalysis(projectId, funnel.id, filter).then(analysis => {
        if (analysis) this.renderFunnelAnalysis(analysis);
      });
    });
  },

  renderFunnelAnalysis(analysis) {
    const container = document.getElementById(`funnel-viz-${analysis.funnel.id}`);
    if (!container) return;

    if (!analysis.steps || analysis.steps.length === 0) {
      container.innerHTML = '<div style="text-align: center; color: var(--color-text-muted);">No data available</div>';
      return;
    }

    const maxSessions = analysis.totalSessions || analysis.steps[0]?.sessions || 1;

    container.innerHTML = analysis.steps.map((step, i) => {
      const widthPercent = maxSessions > 0 ? (step.sessions / maxSessions) * 100 : 0;
      const isLast = i === analysis.steps.length - 1;

      return `
        <div class="funnel-step">
          <div class="funnel-step__info">
            <span class="funnel-step__number">${step.stepNumber}</span>
            <span class="funnel-step__name">${Utils.escapeHtml(step.name)}</span>
            <span class="funnel-step__sessions">${step.sessions} sessions</span>
          </div>
          <div class="funnel-step__bar-container">
            <div class="funnel-step__bar" style="width: ${Math.max(widthPercent, 2)}%">
              <span class="funnel-step__percent">${step.conversionRate.toFixed(1)}%</span>
            </div>
          </div>
          ${!isLast ? `
            <div class="funnel-step__connector">
              <span class="funnel-step__dropoff">&darr; ${step.dropOffRate.toFixed(1)}% drop-off</span>
              ${step.avgTimeFromPrevious != null ? `
                <span class="funnel-step__time">${this.formatDuration(step.avgTimeFromPrevious)}</span>
              ` : ''}
            </div>
          ` : ''}
        </div>`;
    }).join('');
  },

  formatDuration(seconds) {
    if (seconds < 60) return `${Math.round(seconds)}s avg`;
    if (seconds < 3600) return `${Math.round(seconds / 60)}m avg`;
    return `${(seconds / 3600).toFixed(1)}h avg`;
  },

  // ── Modals ─────────────────────────────────────────────────

  showGoalModal() {
    const projectId = Dashboard.state.currentProjectId;
    if (!projectId) return;

    const modal = document.getElementById('goals-modal');
    if (!modal) return;

    modal.classList.add('show');
    this.refreshGoalList(projectId);
  },

  showFunnelModal() {
    const projectId = Dashboard.state.currentProjectId;
    if (!projectId) return;

    const modal = document.getElementById('funnels-modal');
    if (!modal) return;

    modal.classList.add('show');
    this.refreshFunnelList(projectId);
    this.resetFunnelForm();
  },

  async refreshGoalList(projectId) {
    const goals = await this.loadGoals(projectId);
    const tbody = document.getElementById('goals-table-body');
    if (!tbody) return;

    if (goals.length === 0) {
      tbody.innerHTML = '<tr><td colspan="5" style="text-align: center; color: var(--color-text-muted);">No goals yet</td></tr>';
      return;
    }

    tbody.innerHTML = goals.map(goal => `
      <tr>
        <td>${Utils.escapeHtml(goal.name)}</td>
        <td><span class="badge badge--${goal.goalType}">${goal.goalType.toUpperCase()}</span></td>
        <td><code>${Utils.escapeHtml(goal.matchValue)}</code></td>
        <td>
          <label class="toggle-switch">
            <input type="checkbox" ${goal.isActive ? 'checked' : ''} onchange="GoalsManager.toggleGoal('${projectId}', '${goal.id}', this.checked)">
            <span class="toggle-slider"></span>
          </label>
        </td>
        <td>
          <button class="btn btn-ghost btn-icon btn-sm" onclick="GoalsManager.confirmDeleteGoal('${projectId}', '${goal.id}', '${Utils.escapeHtml(goal.name)}')" title="Delete">
            <i class="ph-duotone ph-trash"></i>
          </button>
        </td>
      </tr>
    `).join('');

  },

  async refreshFunnelList(projectId) {
    const funnels = await this.loadFunnels(projectId);
    const container = document.getElementById('funnels-table-body');
    if (!container) return;

    if (funnels.length === 0) {
      container.innerHTML = '<tr><td colspan="4" style="text-align: center; color: var(--color-text-muted);">No funnels yet</td></tr>';
      return;
    }

    container.innerHTML = funnels.map(funnel => `
      <tr>
        <td>${Utils.escapeHtml(funnel.name)}</td>
        <td>${funnel.steps.length}</td>
        <td>${funnel.steps.map(s => Utils.escapeHtml(s.name)).join(' &rarr; ')}</td>
        <td>
          <button class="btn btn-ghost btn-icon btn-sm" onclick="GoalsManager.confirmDeleteFunnel('${projectId}', '${funnel.id}', '${Utils.escapeHtml(funnel.name)}')" title="Delete">
            <i class="ph-duotone ph-trash"></i>
          </button>
        </td>
      </tr>
    `).join('');

  },

  async handleCreateGoal(projectId) {
    const name = document.getElementById('goal-name').value.trim();
    const goalType = document.getElementById('goal-type').value;
    const matchValue = document.getElementById('goal-match-value').value.trim();

    if (!name || !matchValue) {
      alert('Please fill in all fields');
      return;
    }

    try {
      await this.createGoal(projectId, { name, goalType, matchValue });
      document.getElementById('goal-name').value = '';
      document.getElementById('goal-match-value').value = '';
      await this.refreshGoalList(projectId);
      // Refresh dashboard goals display
      const filter = Dashboard.state.currentFilter;
      const stats = await this.loadGoalStats(projectId, filter);
      this.renderGoals(stats);
    } catch (e) {
      alert(e.message);
    }
  },

  async toggleGoal(projectId, goalId, isActive) {
    try {
      await this.updateGoal(projectId, goalId, { isActive: String(isActive) });
    } catch (e) {
      alert('Failed to update goal');
    }
  },

  confirmDeleteGoal(projectId, goalId, name) {
    if (confirm(`Delete goal "${name}"? This cannot be undone.`)) {
      this.deleteGoal(projectId, goalId).then(() => {
        this.refreshGoalList(projectId);
        const filter = Dashboard.state.currentFilter;
        this.loadGoalStats(projectId, filter).then(stats => this.renderGoals(stats));
      });
    }
  },

  // ── Funnel Form ────────────────────────────────────────────

  funnelStepCount: 2,

  resetFunnelForm() {
    this.funnelStepCount = 2;
    const container = document.getElementById('funnel-steps-container');
    if (!container) return;

    container.innerHTML = this.renderStepInput(1) + this.renderStepInput(2);
    document.getElementById('funnel-name').value = '';
  },

  renderStepInput(num) {
    return `
      <div class="funnel-step-input" id="funnel-step-${num}">
        <div class="funnel-step-input__header">
          <span class="funnel-step-input__number">Step ${num}</span>
          ${num > 2 ? `<button class="btn btn-ghost btn-icon btn-sm" onclick="GoalsManager.removeFunnelStep(${num})" title="Remove step"><i class="ph-duotone ph-x"></i></button>` : ''}
        </div>
        <div class="funnel-step-input__fields">
          <input type="text" class="input" placeholder="Step name" id="funnel-step-name-${num}">
          <select class="select" id="funnel-step-type-${num}">
            <option value="url">URL path</option>
            <option value="event">Custom event</option>
          </select>
          <input type="text" class="input" placeholder="Match value (e.g. /pricing)" id="funnel-step-match-${num}">
        </div>
      </div>`;
  },

  addFunnelStep() {
    this.funnelStepCount++;
    const container = document.getElementById('funnel-steps-container');
    if (!container) return;
    container.insertAdjacentHTML('beforeend', this.renderStepInput(this.funnelStepCount));
  },

  removeFunnelStep(num) {
    const step = document.getElementById(`funnel-step-${num}`);
    if (step) step.remove();
  },

  async handleCreateFunnel(projectId) {
    const name = document.getElementById('funnel-name').value.trim();
    if (!name) {
      alert('Please enter a funnel name');
      return;
    }

    const steps = [];
    const stepElements = document.querySelectorAll('.funnel-step-input');
    for (const el of stepElements) {
      const id = el.id.replace('funnel-step-', '');
      const stepName = document.getElementById(`funnel-step-name-${id}`)?.value.trim();
      const stepType = document.getElementById(`funnel-step-type-${id}`)?.value;
      const matchValue = document.getElementById(`funnel-step-match-${id}`)?.value.trim();

      if (!stepName || !matchValue) {
        alert('Please fill in all step fields');
        return;
      }

      steps.push({ name: stepName, stepType, matchValue });
    }

    if (steps.length < 2) {
      alert('Funnel must have at least 2 steps');
      return;
    }

    try {
      await this.createFunnel(projectId, { name, steps });
      this.resetFunnelForm();
      await this.refreshFunnelList(projectId);
      // Refresh dashboard funnels display
      const funnels = await this.loadFunnels(projectId);
      this.renderFunnels(funnels, projectId, Dashboard.state.currentFilter);
    } catch (e) {
      alert(e.message);
    }
  },

  confirmDeleteFunnel(projectId, funnelId, name) {
    if (confirm(`Delete funnel "${name}"? This cannot be undone.`)) {
      this.deleteFunnel(projectId, funnelId).then(() => {
        this.refreshFunnelList(projectId);
        this.loadFunnels(projectId).then(funnels => {
          this.renderFunnels(funnels, projectId, Dashboard.state.currentFilter);
        });
      });
    }
  },

  // ── Setup ──────────────────────────────────────────────────

  setupModals() {
    // Goals modal
    const goalsModal = document.getElementById('goals-modal');
    if (goalsModal) {
      document.getElementById('close-goals-modal')?.addEventListener('click', () => {
        goalsModal.classList.remove('show');
      });
      goalsModal.querySelector('.modal-backdrop')?.addEventListener('click', () => {
        goalsModal.classList.remove('show');
      });
    }

    // Funnels modal
    const funnelsModal = document.getElementById('funnels-modal');
    if (funnelsModal) {
      document.getElementById('close-funnels-modal')?.addEventListener('click', () => {
        funnelsModal.classList.remove('show');
      });
      funnelsModal.querySelector('.modal-backdrop')?.addEventListener('click', () => {
        funnelsModal.classList.remove('show');
      });
    }

    // Manage buttons
    document.getElementById('manage-goals-btn')?.addEventListener('click', () => this.showGoalModal());
    document.getElementById('manage-funnels-btn')?.addEventListener('click', () => this.showFunnelModal());

    // Create goal button
    document.getElementById('create-goal-btn')?.addEventListener('click', () => {
      const projectId = Dashboard.state.currentProjectId;
      if (projectId) this.handleCreateGoal(projectId);
    });

    // Create funnel button
    document.getElementById('create-funnel-btn')?.addEventListener('click', () => {
      const projectId = Dashboard.state.currentProjectId;
      if (projectId) this.handleCreateFunnel(projectId);
    });

    // Add funnel step button
    document.getElementById('add-funnel-step-btn')?.addEventListener('click', () => this.addFunnelStep());
  }
};

// Initialize modals when DOM is ready
document.addEventListener('DOMContentLoaded', () => {
  GoalsManager.setupModals();
});
