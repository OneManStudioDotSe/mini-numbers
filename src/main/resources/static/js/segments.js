/**
 * Segments Manager
 * Handles user segment CRUD and visual filter builder with AND/OR logic
 */
const SegmentsManager = {
  /** Segments shown in the management table, by id (source for the edit form) */
  segments: {},
  /** Id of the segment being edited, or null while the form creates a new one */
  editingId: null,

  /**
   * Load segments for a project
   */
  async loadSegments(projectId) {
    try {
      const segments = await Utils.api.fetch(`/admin/projects/${projectId}/segments`, { useCache: false });
      return segments;
    } catch (error) {
      console.error('Failed to load segments:', error);
      return [];
    }
  },

  /**
   * Render segments list in dashboard
   */
  renderSegments(segments, projectId, filter) {
    const section = document.getElementById('segments-section');
    const list = document.getElementById('segments-list');
    if (!section || !list) return;

    // Always shown: the "Manage segments" button that creates the first segment lives in this card
    section.style.display = '';
    if (!segments || segments.length === 0) {
      list.innerHTML = '<p class="text-muted">No segments yet. A segment is a saved set of filters, such as "mobile visitors from Sweden".</p>';
      return;
    }

    list.innerHTML = segments.map(segment => `
      <div class="segment-card" role="listitem">
        <div class="segment-card__header">
          <div>
            <strong>${Utils.escapeHtml(segment.name)}</strong>
            ${segment.description ? `<div class="text-muted text-sm">${Utils.escapeHtml(segment.description)}</div>` : ''}
          </div>
          <button class="btn btn-ghost btn-sm" data-action="analyze-segment" data-id="${segment.id}" aria-label="Analyze segment ${Utils.escapeHtml(segment.name)}">
            <i class="ri-bar-chart-line"></i> Analyze
          </button>
        </div>
        <div class="segment-card__filters">
          ${this.filterBadges(segment.filters)}
        </div>
      </div>
    `).join('');
  },

  /** The filters of a segment as badges, with the AND/OR that joins each to the next */
  filterBadges(filters) {
    return filters.map((f, i) => `
      <span class="badge badge-secondary">${Utils.escapeHtml(f.field)} ${Utils.escapeHtml(f.operator)} "${Utils.escapeHtml(f.value)}"</span>
      ${i < filters.length - 1 ? `<span class="segment-logic">${Utils.escapeHtml(f.logic)}</span>` : ''}
    `).join('');
  },

  /**
   * Analyze a segment and show results
   */
  async analyzeSegment(projectId, segmentId, filter) {
    try {
      Utils.toast.info('Analyzing segment...');
      const analysis = await Utils.api.fetch(
        `/admin/projects/${projectId}/segments/${segmentId}/analysis?filter=${filter}`,
        { useCache: false }
      );

      Utils.toast.success(
        `${analysis.segmentName}: ${Utils.format.number(analysis.totalViews)} views, ` +
        `${Utils.format.number(analysis.uniqueVisitors)} visitors, ` +
        `${analysis.bounceRate.toFixed(1)}% bounce rate`,
        6000
      );
    } catch (error) {
      console.error('Failed to analyze segment:', error);
      Utils.toast.error('Failed to analyze segment');
    }
  },

  /**
   * Wire the management dialog. Called once; every action reads the current project when it runs.
   */
  bind() {
    const modal = document.getElementById('segments-modal');
    const manageBtn = document.getElementById('manage-segments-btn');
    if (!modal || !manageBtn) return;
    const projectId = () => Dashboard.state.currentProjectId;

    manageBtn.addEventListener('click', () => {
      modal.classList.add('show');
      Utils.focusTrap.openModal(modal, () => modal.classList.remove('show'));
      this.resetForm();
      this.loadSegmentsTable(projectId());
    });

    document.getElementById('close-segments-modal')?.addEventListener('click', () => modal.classList.remove('show'));
    document.getElementById('add-segment-filter-btn')?.addEventListener('click', () => this.addFilterRow());
    document.getElementById('create-segment-btn')?.addEventListener('click', () => this.saveSegment(projectId()));
    document.getElementById('cancel-segment-edit-btn')?.addEventListener('click', () => this.resetForm());

    document.getElementById('segment-filters-container')?.addEventListener('click', (e) => {
      const row = e.target.closest('[data-action="remove-segment-filter"]')?.closest('.segment-filter-row');
      if (!row) return;
      row.remove();
      // The first row has nothing before it to be joined to
      document.querySelector('#segment-filters-container .segment-filter-row')
        ?.querySelector('.segment-logic-select')?.remove();
    });

    document.getElementById('segments-list')?.addEventListener('click', (e) => {
      const btn = e.target.closest('[data-action="analyze-segment"]');
      if (btn) this.analyzeSegment(projectId(), btn.dataset.id, Dashboard.state.currentFilter);
    });
    document.getElementById('segments-table-body')?.addEventListener('click', (e) => {
      const edit = e.target.closest('[data-action="edit-segment"]');
      if (edit) this.editSegment(edit.dataset.id);
      const del = e.target.closest('[data-action="delete-segment"]');
      if (del) this.deleteSegment(projectId(), del.dataset.id);
    });
  },

  /** Empty the form and put it back in "create" mode with one blank filter row */
  resetForm() {
    this.editingId = null;
    document.getElementById('segment-name').value = '';
    document.getElementById('segment-description').value = '';
    document.getElementById('segment-filters-container').innerHTML = '';
    this.addFilterRow();
    document.getElementById('segment-form-title').textContent = 'Create new segment';
    document.getElementById('create-segment-btn').textContent = 'Create segment';
    document.getElementById('cancel-segment-edit-btn').style.display = 'none';
  },

  /** Load a segment from the table into the form for editing */
  editSegment(segmentId) {
    const segment = this.segments[segmentId];
    if (!segment) return;
    this.editingId = segmentId;
    document.getElementById('segment-name').value = segment.name;
    document.getElementById('segment-description').value = segment.description || '';
    document.getElementById('segment-filters-container').innerHTML = '';
    // A filter's logic joins it to the next one, so each row shows the logic of the filter before it
    segment.filters.forEach((f, i) => this.addFilterRow(f, i > 0 ? segment.filters[i - 1].logic : null));
    document.getElementById('segment-form-title').textContent = 'Edit segment';
    document.getElementById('create-segment-btn').textContent = 'Save changes';
    document.getElementById('cancel-segment-edit-btn').style.display = '';
    document.getElementById('segment-name').focus();
  },

  /**
   * Add a filter row to the segment builder
   * @param {Object} [filter] - Existing filter to show (field, operator, value)
   * @param {string} [logicBefore] - AND/OR joining this row to the one before it
   */
  addFilterRow(filter, logicBefore) {
    const container = document.getElementById('segment-filters-container');
    if (!container) return;

    const isFirst = container.querySelectorAll('.segment-filter-row').length === 0;

    const row = document.createElement('div');
    row.className = 'segment-filter-row';
    row.setAttribute('role', 'listitem');
    row.innerHTML = `
      ${!isFirst ? `
        <select class="select segment-logic-select" aria-label="Logic operator">
          <option value="AND">AND</option>
          <option value="OR">OR</option>
        </select>
      ` : ''}
      <select class="select" data-filter="field" aria-label="Filter field">
        <option value="browser">Browser</option>
        <option value="os">OS</option>
        <option value="device">Device</option>
        <option value="country">Country</option>
        <option value="city">City</option>
        <option value="path">Path</option>
        <option value="referrer">Referrer</option>
        <option value="eventType">Event type</option>
      </select>
      <select class="select" data-filter="operator" aria-label="Filter operator">
        <option value="equals">equals</option>
        <option value="not_equals">not equals</option>
        <option value="contains">contains</option>
        <option value="starts_with">starts with</option>
      </select>
      <input type="text" class="input" data-filter="value" placeholder="Value..." style="flex: 1;" aria-label="Filter value">
      <button type="button" class="btn btn-ghost btn-icon" data-action="remove-segment-filter" aria-label="Remove filter">
        <i class="ri-close-line"></i>
      </button>
    `;
    if (filter) {
      row.querySelector('[data-filter="field"]').value = filter.field;
      row.querySelector('[data-filter="operator"]').value = filter.operator;
      row.querySelector('[data-filter="value"]').value = filter.value;
    }
    if (!isFirst && logicBefore) row.querySelector('.segment-logic-select').value = logicBefore.toUpperCase();
    container.appendChild(row);
  },

  /**
   * Create a segment from the form, or save the one being edited
   */
  async saveSegment(projectId) {
    const name = document.getElementById('segment-name')?.value.trim();
    const description = document.getElementById('segment-description')?.value.trim();

    if (!name) {
      Utils.toast.error('Segment name is required');
      return;
    }

    const rows = document.querySelectorAll('#segment-filters-container .segment-filter-row');
    const filters = [];

    rows.forEach((row) => {
      const field = row.querySelector('[data-filter="field"]')?.value;
      const operator = row.querySelector('[data-filter="operator"]')?.value;
      const value = row.querySelector('[data-filter="value"]')?.value?.trim();
      if (!field || !operator || !value) return;
      // The AND/OR shown before a row joins it to the previous filter, which is where the server reads it
      const logicSelect = row.querySelector('.segment-logic-select');
      if (logicSelect && filters.length) filters[filters.length - 1].logic = logicSelect.value;
      filters.push({ field, operator, value, logic: 'AND' });
    });

    if (filters.length === 0) {
      Utils.toast.error('At least one filter is required');
      return;
    }

    const editing = this.editingId;
    const url = `/admin/projects/${projectId}/segments` + (editing ? `/${editing}` : '');
    try {
      const response = await fetch(url, {
        method: editing ? 'PUT' : 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name, description: description || null, filters })
      });
      if (!response.ok) {
        const problem = await response.json().catch(() => ({}));
        Utils.toast.error(problem.error || 'Failed to save segment');
        return;
      }
      Utils.toast.success(editing ? 'Segment saved' : 'Segment created!');
      this.resetForm();
      await this.refresh(projectId);
    } catch (error) {
      console.error('Failed to save segment:', error);
      Utils.toast.error('Failed to save segment');
    }
  },

  /** Reload the management table and the dashboard card */
  async refresh(projectId) {
    await this.loadSegmentsTable(projectId);
    this.renderSegments(Object.values(this.segments), projectId, Dashboard.state.currentFilter);
  },

  /**
   * Load segments into the management table
   */
  async loadSegmentsTable(projectId) {
    const tbody = document.getElementById('segments-table-body');
    if (!tbody) return;

    try {
      const segments = await Utils.api.fetch(`/admin/projects/${projectId}/segments`, { useCache: false });
      this.segments = Object.fromEntries((segments || []).map(s => [s.id, s]));

      if (!segments || segments.length === 0) {
        tbody.innerHTML = '<tr><td colspan="4" style="text-align: center; color: var(--color-text-muted);">No segments yet</td></tr>';
        return;
      }

      tbody.innerHTML = segments.map(s => `
        <tr>
          <td>${Utils.escapeHtml(s.name)}</td>
          <td>${s.description ? Utils.escapeHtml(s.description) : '<span class="text-muted">-</span>'}</td>
          <td>${this.filterBadges(s.filters)}</td>
          <td><div style="display: flex; justify-content: flex-end;">
            <button class="btn btn-ghost btn-icon" data-action="edit-segment" data-id="${s.id}" aria-label="Edit segment ${Utils.escapeHtml(s.name)}">
              <i class="ri-pencil-line"></i>
            </button>
            <button class="btn btn-ghost btn-icon" data-action="delete-segment" data-id="${s.id}" aria-label="Delete segment ${Utils.escapeHtml(s.name)}">
              <i class="ri-delete-bin-line"></i>
            </button>
          </div></td>
        </tr>
      `).join('');

    } catch (error) {
      console.error('Failed to load segments table:', error);
    }
  },

  /**
   * Delete a segment
   */
  async deleteSegment(projectId, segmentId) {
    if (!confirm('Delete this segment?')) return;

    try {
      const response = await fetch(`/admin/projects/${projectId}/segments/${segmentId}`, { method: 'DELETE' });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      Utils.toast.success('Segment deleted');
      if (this.editingId === segmentId) this.resetForm();
      await this.refresh(projectId);
    } catch (error) {
      console.error('Failed to delete segment:', error);
      Utils.toast.error('Failed to delete segment');
    }
  }
};

document.addEventListener('DOMContentLoaded', () => SegmentsManager.bind());
