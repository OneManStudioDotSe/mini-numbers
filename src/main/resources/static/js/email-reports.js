/**
 * Email Reports Manager
 * Handles email report CRUD, SMTP settings, template customization, and scheduling.
 */

const EmailReportsManager = {
  state: {
    reports: [],
    smtpStatus: null
  },

  // ── API ─────────────────────────────────────────────────────

  async loadReports(projectId) {
    try {
      const response = await fetch(`/admin/projects/${projectId}/email-reports`);
      if (!response.ok) return [];
      this.state.reports = await response.json();
      return this.state.reports;
    } catch (e) {
      console.error('Failed to load email reports:', e);
      return [];
    }
  },

  async loadSmtpStatus() {
    try {
      const response = await fetch('/admin/smtp/status');
      if (!response.ok) return null;
      this.state.smtpStatus = await response.json();
      return this.state.smtpStatus;
    } catch (e) {
      console.error('Failed to load SMTP status:', e);
      return null;
    }
  },

  async createReport(projectId, data) {
    const response = await fetch(`/admin/projects/${projectId}/email-reports`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data)
    });
    if (!response.ok) {
      const err = await response.json();
      throw new Error(err.error || 'Failed to create email report');
    }
    return await response.json();
  },

  async updateReport(projectId, reportId, data) {
    const response = await fetch(`/admin/projects/${projectId}/email-reports/${reportId}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data)
    });
    if (!response.ok) throw new Error('Failed to update email report');
    return await response.json();
  },

  async deleteReport(projectId, reportId) {
    const response = await fetch(`/admin/projects/${projectId}/email-reports/${reportId}`, {
      method: 'DELETE'
    });
    if (!response.ok) throw new Error('Failed to delete email report');
  },

  async testReport(projectId, reportId) {
    const response = await fetch(`/admin/projects/${projectId}/email-reports/${reportId}/test`, {
      method: 'POST'
    });
    if (!response.ok) {
      const err = await response.json();
      throw new Error(err.error || 'Failed to send test report');
    }
    return await response.json();
  },

  // ── Rendering ───────────────────────────────────────────────

  renderReports(reports, projectId) {
    const container = document.getElementById('email-reports-list');
    const section = document.getElementById('email-reports-section');
    if (!container || !section) return;

    section.style.display = '';

    if (!reports.length) {
      container.innerHTML = `
        <div style="text-align: center; padding: 2rem; color: var(--color-text-muted);">
          <i class="ri-mail-line" style="font-size: 2rem; display: block; margin-bottom: 0.5rem;"></i>
          No email reports configured. Click "Manage reports" to set one up.
        </div>`;
      return;
    }

    container.innerHTML = reports.map(r => `
      <div class="stat-entry" style="display: flex; align-items: center; justify-content: space-between; padding: 0.75rem 0; border-bottom: 1px solid var(--color-border);">
        <div style="flex: 1; min-width: 0;">
          <div style="font-weight: 500;">
            <i class="ri-mail-send-line" style="color: var(--color-primary);"></i>
            ${r.recipientEmail}
          </div>
          <div style="font-size: 0.8rem; color: var(--color-text-muted); margin-top: 2px;">
            <span class="badge badge-sm">${r.schedule}</span>
            &middot; ${r.isActive ? '<span style="color: var(--color-success);">Active</span>' : '<span>Inactive</span>'}
            ${r.lastSentAt ? '&middot; Last sent: ' + new Date(r.lastSentAt).toLocaleDateString() : ''}
          </div>
        </div>
        <div style="display: flex; gap: 0.25rem; margin-left: 0.5rem;">
          <button class="btn btn-ghost btn-sm" onclick="EmailReportsManager.onToggle('${projectId}', '${r.id}', ${!r.isActive})" title="${r.isActive ? 'Pause' : 'Activate'}">
            <i class="${r.isActive ? 'ri-pause-line' : 'ri-play-line'}"></i>
          </button>
          <button class="btn btn-ghost btn-sm" onclick="EmailReportsManager.onTest('${projectId}', '${r.id}')" title="Send test email">
            <i class="ri-send-plane-line"></i>
          </button>
        </div>
      </div>
    `).join('');
  },

  // ── Modal ───────────────────────────────────────────────────

  initModal(projectId) {
    const modal = document.getElementById('email-reports-modal');
    if (!modal) return;

    const manageBtn = document.getElementById('manage-email-reports-btn');
    if (manageBtn) manageBtn.onclick = () => this.openModal(projectId);

    const closeBtn = document.getElementById('close-email-reports-modal');
    if (closeBtn) closeBtn.onclick = () => modal.classList.remove('show');

    const backdrop = modal.querySelector('.modal-backdrop');
    if (backdrop) backdrop.onclick = () => modal.classList.remove('show');

    const createBtn = document.getElementById('create-email-report-btn');
    if (createBtn) createBtn.onclick = () => this.onCreate(projectId);

    // Guide toggle
    const guideToggle = document.getElementById('email-guide-toggle');
    const guideContent = document.getElementById('email-guide-content');
    if (guideToggle && guideContent) {
      guideToggle.onclick = () => {
        const expanded = guideToggle.getAttribute('aria-expanded') === 'true';
        guideToggle.setAttribute('aria-expanded', !expanded);
        guideContent.style.display = expanded ? 'none' : 'block';
      };
    }
  },

  async openModal(projectId) {
    const modal = document.getElementById('email-reports-modal');
    if (!modal) return;
    modal.classList.add('show');

    // Load SMTP status and reports in parallel
    const [smtpStatus] = await Promise.all([
      this.loadSmtpStatus(),
      this.loadReports(projectId)
    ]);

    // Update SMTP status indicator
    const smtpIndicator = document.getElementById('smtp-status-indicator');
    if (smtpIndicator && smtpStatus) {
      if (smtpStatus.configured) {
        smtpIndicator.innerHTML = `<span style="color: var(--color-success);"><i class="ri-check-line"></i> Connected (${smtpStatus.host})</span>`;
      } else {
        smtpIndicator.innerHTML = `<span style="color: var(--color-warning);"><i class="ri-alert-line"></i> Not configured</span>`;
      }
    }

    this.renderModalTable(projectId);
  },

  renderModalTable(projectId) {
    const tbody = document.getElementById('email-reports-table-body');
    if (!tbody) return;

    if (!this.state.reports.length) {
      tbody.innerHTML = '<tr><td colspan="5" style="text-align: center; color: var(--color-text-muted);">No email reports yet</td></tr>';
      return;
    }

    tbody.innerHTML = this.state.reports.map(r => `
      <tr>
        <td>${r.recipientEmail}</td>
        <td><span class="badge badge-sm">${r.schedule}</span></td>
        <td>${r.isActive ? '<span style="color: var(--color-success);">Active</span>' : 'Inactive'}</td>
        <td style="font-size: 0.85em;">${r.lastSentAt ? new Date(r.lastSentAt).toLocaleString() : 'Never'}</td>
        <td>
          <button class="btn btn-ghost btn-sm" onclick="EmailReportsManager.onToggle('${projectId}', '${r.id}', ${!r.isActive})" title="${r.isActive ? 'Pause' : 'Activate'}">
            <i class="${r.isActive ? 'ri-pause-line' : 'ri-play-line'}"></i>
          </button>
          <button class="btn btn-ghost btn-sm" onclick="EmailReportsManager.onTest('${projectId}', '${r.id}')" title="Test">
            <i class="ri-send-plane-line"></i>
          </button>
          <button class="btn btn-ghost btn-sm" style="color: var(--color-danger);" onclick="EmailReportsManager.onDelete('${projectId}', '${r.id}')" title="Delete">
            <i class="ri-delete-bin-line"></i>
          </button>
        </td>
      </tr>
    `).join('');
  },

  // ── Actions ─────────────────────────────────────────────────

  async onCreate(projectId) {
    const emailInput = document.getElementById('email-report-recipient');
    const scheduleSelect = document.getElementById('email-report-schedule');
    const hourInput = document.getElementById('email-report-hour');

    const email = emailInput?.value?.trim();
    if (!email || !email.includes('@')) {
      alert('Please enter a valid email address');
      return;
    }

    const data = {
      recipientEmail: email,
      schedule: scheduleSelect?.value || 'WEEKLY',
      sendHour: parseInt(hourInput?.value || '8', 10)
    };

    // Collect include sections
    const sections = [];
    document.querySelectorAll('.email-section-cb:checked').forEach(cb => {
      sections.push(cb.value);
    });
    if (sections.length) data.includeSections = sections;

    // Custom template fields
    const subjectInput = document.getElementById('email-report-subject');
    const headerInput = document.getElementById('email-report-header');
    const footerInput = document.getElementById('email-report-footer');
    if (subjectInput?.value?.trim()) data.subjectTemplate = subjectInput.value.trim();
    if (headerInput?.value?.trim()) data.headerText = headerInput.value.trim();
    if (footerInput?.value?.trim()) data.footerText = footerInput.value.trim();

    try {
      await this.createReport(projectId, data);
      if (emailInput) emailInput.value = '';
      await this.loadReports(projectId);
      this.renderModalTable(projectId);
      this.renderReports(this.state.reports, projectId);
    } catch (e) {
      alert('Failed to create report: ' + e.message);
    }
  },

  async onDelete(projectId, reportId) {
    if (!confirm('Delete this email report schedule?')) return;
    try {
      await this.deleteReport(projectId, reportId);
      await this.loadReports(projectId);
      this.renderModalTable(projectId);
      this.renderReports(this.state.reports, projectId);
    } catch (e) {
      alert('Failed to delete: ' + e.message);
    }
  },

  async onToggle(projectId, reportId, isActive) {
    try {
      await this.updateReport(projectId, reportId, { isActive });
      await this.loadReports(projectId);
      this.renderModalTable(projectId);
      this.renderReports(this.state.reports, projectId);
    } catch (e) {
      alert('Failed to update: ' + e.message);
    }
  },

  async onTest(projectId, reportId) {
    try {
      await this.testReport(projectId, reportId);
      alert('Test email queued for delivery');
    } catch (e) {
      alert('Failed to send test: ' + e.message);
    }
  }
};
