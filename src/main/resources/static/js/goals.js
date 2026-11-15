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

  /** Create a funnel, or replace the name and steps of an existing one when funnelId is given */
  async saveFunnel(projectId, data, funnelId) {
    const response = await fetch(`/admin/projects/${projectId}/funnels` + (funnelId ? `/${funnelId}` : ''), {
      method: funnelId ? 'PUT' : 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data)
    });
    if (!response.ok) {
      const err = await response.json().catch(() => ({}));
      throw new Error(err.error || 'Failed to save funnel');
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
          const typeIcon = stat.goal.goalType === 'url' ? 'ri-link' : 'ri-flashlight-line';

          return `
            <div class="goal-card">
              <div class="goal-card__header">
                <div class="goal-card__icon">
                  <i class="${typeIcon}"></i>
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
      // Drop-off and time are stored on the step they lead to, so the connector below a step shows the next one's
      const next = analysis.steps[i + 1];

      return `
        <div class="funnel-step">
          <div class="funnel-step__info">
            <span class="funnel-step__number">${step.stepNumber}</span>
            <span class="funnel-step__name">${Utils.escapeHtml(step.name)}</span>
            <span class="funnel-step__sessions">${step.sessions} session${step.sessions !== 1 ? 's' : ''}</span>
          </div>
          <div class="funnel-step__bar-container">
            <div class="funnel-step__bar" style="width: ${Math.max(widthPercent, 2)}%">
              <span class="funnel-step__percent">${step.conversionRate.toFixed(1)}%</span>
            </div>
          </div>
          ${next ? `
            <div class="funnel-step__connector">
              <span class="funnel-step__dropoff">&darr; ${next.dropOffRate.toFixed(1)}% drop-off</span>
              ${next.avgTimeFromPrevious != null ? `
                <span class="funnel-step__time">${this.formatDuration(next.avgTimeFromPrevious)}</span>
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
    Utils.focusTrap.openModal(modal, () => modal.classList.remove('show'));
    this.refreshGoalList(projectId);
  },

  showFunnelModal() {
    const projectId = Dashboard.state.currentProjectId;
    if (!projectId) return;

    const modal = document.getElementById('funnels-modal');
    if (!modal) return;

    modal.classList.add('show');
    Utils.focusTrap.openModal(modal, () => modal.classList.remove('show'));
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
            <input type="checkbox" ${goal.isActive ? 'checked' : ''} data-action="toggle-goal" data-id="${goal.id}">
            <span class="toggle-slider"></span>
          </label>
        </td>
        <td>
          <button class="btn btn-ghost btn-icon btn-sm" data-action="delete-goal" data-id="${goal.id}" data-name="${Utils.escapeHtml(goal.name)}" title="Delete">
            <i class="ri-delete-bin-line"></i>
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
        <td><div style="display: flex; justify-content: flex-end;">
          <button class="btn btn-ghost btn-icon btn-sm" data-action="edit-funnel" data-id="${funnel.id}" aria-label="Edit funnel ${Utils.escapeHtml(funnel.name)}">
            <i class="ri-pencil-line"></i>
          </button>
          <button class="btn btn-ghost btn-icon btn-sm" data-action="delete-funnel" data-id="${funnel.id}" data-name="${Utils.escapeHtml(funnel.name)}" aria-label="Delete funnel ${Utils.escapeHtml(funnel.name)}">
            <i class="ri-delete-bin-line"></i>
          </button>
        </div></td>
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

  /** Id of the funnel being edited, or null while the form creates a new one */
  editingFunnelId: null,

  /** Empty the form and put it back in "create" mode with two blank steps */
  resetFunnelForm() {
    this.editingFunnelId = null;
    const container = document.getElementById('funnel-steps-container');
    if (!container) return;

    container.innerHTML = '';
    this.addFunnelStep();
    this.addFunnelStep();
    document.getElementById('funnel-name').value = '';
    document.getElementById('funnel-form-title').textContent = 'Create new funnel';
    document.getElementById('create-funnel-btn').textContent = 'Create funnel';
    document.getElementById('cancel-funnel-edit-btn').style.display = 'none';
  },

  /** Load a funnel from the table into the form for editing */
  editFunnel(funnelId) {
    const funnel = this.state.funnels.find(f => f.id === funnelId);
    if (!funnel) return;
    this.editingFunnelId = funnelId;
    document.getElementById('funnel-steps-container').innerHTML = '';
    funnel.steps.forEach(step => this.addFunnelStep(step));
    document.getElementById('funnel-name').value = funnel.name;
    document.getElementById('funnel-form-title').textContent = 'Edit funnel';
    document.getElementById('create-funnel-btn').textContent = 'Save changes';
    document.getElementById('cancel-funnel-edit-btn').style.display = '';
    document.getElementById('funnel-name').focus();
  },

  /**
   * Add a step to the funnel form
   * @param {Object} [step] - Existing step to show (name, stepType, matchValue)
   */
  addFunnelStep(step) {
    const container = document.getElementById('funnel-steps-container');
    if (!container) return;

    const row = document.createElement('div');
    row.className = 'funnel-step-input';
    row.innerHTML = `
      <div class="funnel-step-input__header">
        <span class="funnel-step-input__number"></span>
        <button type="button" class="btn btn-ghost btn-icon btn-sm" data-action="remove-funnel-step" aria-label="Remove step"><i class="ri-close-line"></i></button>
      </div>
      <div class="funnel-step-input__fields">
        <input type="text" class="input" placeholder="Step name" data-step="name" aria-label="Step name">
        <select class="select" data-step="type" aria-label="Step type">
          <option value="url">URL path</option>
          <option value="event">Custom event</option>
        </select>
        <input type="text" class="input" placeholder="Match value (e.g. /pricing)" data-step="match" aria-label="Match value">
      </div>`;
    if (step) {
      row.querySelector('[data-step="name"]').value = step.name;
      row.querySelector('[data-step="type"]').value = step.stepType;
      row.querySelector('[data-step="match"]').value = step.matchValue;
    }
    container.appendChild(row);
    this.numberFunnelSteps();
  },

  /** Number the steps in the form 1..n; a funnel keeps at least two, so those can't be removed */
  numberFunnelSteps() {
    const rows = document.querySelectorAll('#funnel-steps-container .funnel-step-input');
    rows.forEach((row, i) => {
      row.querySelector('.funnel-step-input__number').textContent = `Step ${i + 1}`;
      row.querySelector('[data-action="remove-funnel-step"]').style.display = rows.length > 2 ? '' : 'none';
    });
  },

  /** Create a funnel from the form, or save the one being edited */
  async handleSaveFunnel(projectId) {
    const name = document.getElementById('funnel-name').value.trim();
    if (!name) {
      Utils.toast.error('Funnel name is required');
      return;
    }

    const steps = [];
    for (const row of document.querySelectorAll('#funnel-steps-container .funnel-step-input')) {
      const stepName = row.querySelector('[data-step="name"]').value.trim();
      const stepType = row.querySelector('[data-step="type"]').value;
      const matchValue = row.querySelector('[data-step="match"]').value.trim();

      if (!stepName || !matchValue) {
        Utils.toast.error('Every step needs a name and a match value');
        return;
      }

      steps.push({ name: stepName, stepType, matchValue });
    }

    const editing = this.editingFunnelId;
    try {
      await this.saveFunnel(projectId, { name, steps }, editing);
      Utils.toast.success(editing ? 'Funnel saved' : 'Funnel created!');
      this.resetFunnelForm();
      await this.refreshFunnels(projectId);
    } catch (e) {
      Utils.toast.error(e.message);
    }
  },

  /** Reload the management table and the dashboard card */
  async refreshFunnels(projectId) {
    await this.refreshFunnelList(projectId);
    this.renderFunnels(this.state.funnels, projectId, Dashboard.state.currentFilter);
  },

  async confirmDeleteFunnel(projectId, funnelId, name) {
    if (!confirm(`Delete funnel "${name}"? This cannot be undone.`)) return;
    try {
      await this.deleteFunnel(projectId, funnelId);
      Utils.toast.success('Funnel deleted');
      if (this.editingFunnelId === funnelId) this.resetFunnelForm();
      await this.refreshFunnels(projectId);
    } catch (e) {
      Utils.toast.error('Failed to delete funnel');
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
      if (projectId) this.handleSaveFunnel(projectId);
    });
    document.getElementById('cancel-funnel-edit-btn')?.addEventListener('click', () => this.resetFunnelForm());

    // Add / remove funnel step
    document.getElementById('add-funnel-step-btn')?.addEventListener('click', () => this.addFunnelStep());
    document.getElementById('funnel-steps-container')?.addEventListener('click', (e) => {
      const row = e.target.closest('[data-action="remove-funnel-step"]')?.closest('.funnel-step-input');
      if (!row) return;
      row.remove();
      this.numberFunnelSteps();
    });

    // Row actions (rows are re-rendered on every refresh, so delegate once)
    const goalsBody = document.getElementById('goals-table-body');
    goalsBody?.addEventListener('click', (e) => {
      const btn = e.target.closest('[data-action="delete-goal"]');
      if (btn) this.confirmDeleteGoal(Dashboard.state.currentProjectId, btn.dataset.id, btn.dataset.name);
    });
    goalsBody?.addEventListener('change', (e) => {
      const cb = e.target.closest('[data-action="toggle-goal"]');
      if (cb) this.toggleGoal(Dashboard.state.currentProjectId, cb.dataset.id, cb.checked);
    });
    document.getElementById('funnels-table-body')?.addEventListener('click', (e) => {
      const edit = e.target.closest('[data-action="edit-funnel"]');
      if (edit) this.editFunnel(edit.dataset.id);
      const btn = e.target.closest('[data-action="delete-funnel"]');
      if (btn) this.confirmDeleteFunnel(Dashboard.state.currentProjectId, btn.dataset.id, btn.dataset.name);
    });
  }
};

// Initialize modals when DOM is ready
document.addEventListener('DOMContentLoaded', () => {
  GoalsManager.setupModals();
});
