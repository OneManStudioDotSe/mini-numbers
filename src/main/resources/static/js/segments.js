/**
 * Segments Manager
 * Handles user segment CRUD and visual filter builder with AND/OR logic
 */
const SegmentsManager = {
  filterCount: 0,

  /**
   * Load segments for a project
   */
  async loadSegments(projectId) {
    try {
      const segments = await Utils.api.fetch(`/admin/projects/${projectId}/segments`);
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

    if (!segments || segments.length === 0) {
      section.style.display = 'none';
      return;
    }

    section.style.display = '';
    list.innerHTML = segments.map(segment => `
      <div class="segment-card" role="listitem">
        <div class="segment-card__header">
          <div>
            <strong>${Utils.escapeHtml(segment.name)}</strong>
            ${segment.description ? `<div class="text-muted text-sm">${Utils.escapeHtml(segment.description)}</div>` : ''}
          </div>
          <button class="btn btn-ghost btn-sm" onclick="SegmentsManager.analyzeSegment('${projectId}', '${segment.id}', '${filter}')" aria-label="Analyze segment ${Utils.escapeHtml(segment.name)}">
            <i class="ri-bar-chart-line"></i> Analyze
          </button>
        </div>
        <div class="segment-card__filters">
          ${segment.filters.map((f, i) => `
            <span class="badge badge-secondary">${f.field} ${f.operator} "${f.value}"</span>
            ${i < segment.filters.length - 1 ? `<span class="segment-logic">${f.logic}</span>` : ''}
          `).join('')}
        </div>
      </div>
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
   * Initialize the segments management modal
   */
  initModal(projectId) {
    const modal = document.getElementById('segments-modal');
    const manageBtn = document.getElementById('manage-segments-btn');
    const closeBtn = document.getElementById('close-segments-modal');
    const addFilterBtn = document.getElementById('add-segment-filter-btn');
    const createBtn = document.getElementById('create-segment-btn');

    if (!modal || !manageBtn) return;

    manageBtn.addEventListener('click', () => {
      modal.classList.add('show');
      this.filterCount = 0;
      document.getElementById('segment-filters-container').innerHTML = '';
      this.addFilterRow();
      this.loadSegmentsTable(projectId);
    });

    closeBtn?.addEventListener('click', () => modal.classList.remove('show'));

    addFilterBtn?.addEventListener('click', () => this.addFilterRow());

    createBtn?.addEventListener('click', async () => {
      await this.createSegment(projectId);
      modal.classList.remove('show');
    });
  },

  /**
   * Add a filter row to the segment builder
   */
  addFilterRow() {
    const container = document.getElementById('segment-filters-container');
    if (!container) return;

    this.filterCount++;
    const isFirst = this.filterCount === 1;

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
      <button class="btn btn-ghost btn-icon" onclick="this.parentElement.remove()" aria-label="Remove filter">
        <i class="ri-close-line"></i>
      </button>
    `;
    container.appendChild(row);
  },

  /**
   * Create a new segment from the form
   */
  async createSegment(projectId) {
    const name = document.getElementById('segment-name')?.value.trim();
    const description = document.getElementById('segment-description')?.value.trim();

    if (!name) {
      Utils.toast.error('Segment name is required');
      return;
    }

    const container = document.getElementById('segment-filters-container');
    const rows = container.querySelectorAll('.segment-filter-row');
    const filters = [];

    rows.forEach((row, index) => {
      const field = row.querySelector('[data-filter="field"]')?.value;
      const operator = row.querySelector('[data-filter="operator"]')?.value;
      const value = row.querySelector('[data-filter="value"]')?.value?.trim();
      const logicSelect = row.querySelector('.segment-logic-select');
      const logic = logicSelect ? logicSelect.value : 'AND';

      if (field && operator && value) {
        filters.push({ field, operator, value, logic });
      }
    });

    if (filters.length === 0) {
      Utils.toast.error('At least one filter is required');
      return;
    }

    try {
      await Utils.api.post(`/admin/projects/${projectId}/segments`, {
        name, description: description || null, filters
      });
      Utils.toast.success('Segment created!');

      // Clear form
      document.getElementById('segment-name').value = '';
      document.getElementById('segment-description').value = '';

      // Reload
      this.loadSegmentsTable(projectId);
      const segments = await this.loadSegments(projectId);
      this.renderSegments(segments, projectId, Dashboard.state.currentFilter);
    } catch (error) {
      console.error('Failed to create segment:', error);
    }
  },

  /**
   * Load segments into the management table
   */
  async loadSegmentsTable(projectId) {
    const tbody = document.getElementById('segments-table-body');
    if (!tbody) return;

    try {
      const segments = await Utils.api.fetch(`/admin/projects/${projectId}/segments`, { useCache: false });

      if (!segments || segments.length === 0) {
        tbody.innerHTML = '<tr><td colspan="4" style="text-align: center; color: var(--color-text-muted);">No segments yet</td></tr>';
        return;
      }

      tbody.innerHTML = segments.map(s => `
        <tr>
          <td>${Utils.escapeHtml(s.name)}</td>
          <td>${s.description ? Utils.escapeHtml(s.description) : '<span class="text-muted">-</span>'}</td>
          <td>${s.filters.map(f => `<span class="badge badge-secondary">${f.field} ${f.operator} "${f.value}"</span>`).join(' ')}</td>
          <td>
            <button class="btn btn-ghost btn-icon" onclick="SegmentsManager.deleteSegment('${projectId}', '${s.id}')" aria-label="Delete segment ${Utils.escapeHtml(s.name)}">
              <i class="ri-delete-bin-line"></i>
            </button>
          </td>
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
      await fetch(`/admin/projects/${projectId}/segments/${segmentId}`, { method: 'DELETE' });
      Utils.toast.success('Segment deleted');
      this.loadSegmentsTable(projectId);
      const segments = await this.loadSegments(projectId);
      this.renderSegments(segments, projectId, Dashboard.state.currentFilter);
    } catch (error) {
      console.error('Failed to delete segment:', error);
      Utils.toast.error('Failed to delete segment');
    }
  }
};
