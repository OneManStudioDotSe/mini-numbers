/**
 * Webhooks Manager
 * Handles webhook CRUD, delivery logs, setup guide, and test sends.
 */

const WebhooksManager = {
  state: {
    webhooks: [],
    selectedWebhookId: null
  },

  // ── API ─────────────────────────────────────────────────────

  async loadWebhooks(projectId) {
    try {
      const response = await fetch(`/admin/projects/${projectId}/webhooks`);
      if (!response.ok) return [];
      this.state.webhooks = await response.json();
      return this.state.webhooks;
    } catch (e) {
      console.error('Failed to load webhooks:', e);
      return [];
    }
  },

  async createWebhook(projectId, data) {
    const response = await fetch(`/admin/projects/${projectId}/webhooks`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data)
    });
    if (!response.ok) {
      const err = await response.json();
      throw new Error(err.error || 'Failed to create webhook');
    }
    return await response.json();
  },

  async deleteWebhook(projectId, webhookId) {
    const response = await fetch(`/admin/projects/${projectId}/webhooks/${webhookId}`, {
      method: 'DELETE'
    });
    if (!response.ok) throw new Error('Failed to delete webhook');
  },

  async testWebhook(projectId, webhookId) {
    const response = await fetch(`/admin/projects/${projectId}/webhooks/${webhookId}/test`, {
      method: 'POST'
    });
    if (!response.ok) throw new Error('Failed to send test webhook');
    return await response.json();
  },

  async loadDeliveries(projectId, webhookId) {
    try {
      const response = await fetch(`/admin/projects/${projectId}/webhooks/${webhookId}/deliveries`);
      if (!response.ok) return [];
      return await response.json();
    } catch (e) {
      console.error('Failed to load deliveries:', e);
      return [];
    }
  },

  // ── Rendering ───────────────────────────────────────────────

  renderWebhooks(webhooks, projectId) {
    const container = document.getElementById('webhooks-list');
    const section = document.getElementById('webhooks-section');
    if (!container || !section) return;

    section.style.display = '';

    if (!webhooks.length) {
      container.innerHTML = `
        <div style="text-align: center; padding: 2rem; color: var(--color-text-muted);">
          <i class="ri-webhook-line" style="font-size: 2rem; display: block; margin-bottom: 0.5rem;"></i>
          No webhooks configured yet. Click "Manage webhooks" to add one.
        </div>`;
      return;
    }

    container.innerHTML = webhooks.map(wh => `
      <div class="stat-entry" style="display: flex; align-items: center; justify-content: space-between; padding: 0.75rem 0; border-bottom: 1px solid var(--color-border);">
        <div style="flex: 1; min-width: 0;">
          <div style="font-weight: 500; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">
            <i class="ri-link" style="color: var(--color-primary);"></i>
            ${this.maskUrl(wh.url)}
          </div>
          <div style="font-size: 0.8rem; color: var(--color-text-muted); margin-top: 2px;">
            Events: ${wh.events.map(e => `<span class="badge badge-sm">${e}</span>`).join(' ')}
            &nbsp;&middot;&nbsp;
            ${wh.isActive ? '<span style="color: var(--color-success);">Active</span>' : '<span style="color: var(--color-text-muted);">Inactive</span>'}
          </div>
        </div>
        <div style="display: flex; gap: 0.25rem; margin-left: 0.5rem;">
          <button class="btn btn-ghost btn-sm" onclick="WebhooksManager.onTest('${projectId}', '${wh.id}')" title="Send test">
            <i class="ri-send-plane-line"></i>
          </button>
          <button class="btn btn-ghost btn-sm" onclick="WebhooksManager.onViewDeliveries('${projectId}', '${wh.id}')" title="View deliveries">
            <i class="ri-file-list-line"></i>
          </button>
        </div>
      </div>
    `).join('');
  },

  maskUrl(url) {
    try {
      const u = new URL(url);
      const masked = u.hostname.length > 20
        ? u.hostname.substring(0, 17) + '...'
        : u.hostname;
      return `${u.protocol}//${masked}${u.pathname.length > 1 ? u.pathname.substring(0, 15) + '...' : ''}`;
    } catch {
      return url.substring(0, 30) + '...';
    }
  },

  // ── Modal ───────────────────────────────────────────────────

  initModal(projectId) {
    const modal = document.getElementById('webhooks-modal');
    if (!modal) return;

    // Manage button
    const manageBtn = document.getElementById('manage-webhooks-btn');
    if (manageBtn) {
      manageBtn.onclick = () => {
        this.openModal(projectId);
      };
    }

    // Close button
    const closeBtn = document.getElementById('close-webhooks-modal');
    if (closeBtn) closeBtn.onclick = () => modal.classList.remove('show');

    // Backdrop click
    const backdrop = modal.querySelector('.modal-backdrop');
    if (backdrop) backdrop.onclick = () => modal.classList.remove('show');

    // Create button
    const createBtn = document.getElementById('create-webhook-btn');
    if (createBtn) {
      createBtn.onclick = () => this.onCreate(projectId);
    }

    // Guide toggle
    const guideToggle = document.getElementById('webhook-guide-toggle');
    const guideContent = document.getElementById('webhook-guide-content');
    if (guideToggle && guideContent) {
      guideToggle.onclick = () => {
        const expanded = guideToggle.getAttribute('aria-expanded') === 'true';
        guideToggle.setAttribute('aria-expanded', !expanded);
        guideContent.style.display = expanded ? 'none' : 'block';
        guideToggle.querySelector('.ri-arrow-down-s-line, .ri-arrow-up-s-line')
          ?.classList.toggle('ri-arrow-down-s-line', expanded);
        guideToggle.querySelector('.ri-arrow-down-s-line, .ri-arrow-up-s-line')
          ?.classList.toggle('ri-arrow-up-s-line', !expanded);
      };
    }
  },

  async openModal(projectId) {
    const modal = document.getElementById('webhooks-modal');
    if (!modal) return;
    modal.classList.add('show');

    // Refresh webhook list in table
    await this.loadWebhooks(projectId);
    this.renderModalTable(projectId);
  },

  renderModalTable(projectId) {
    const tbody = document.getElementById('webhooks-table-body');
    if (!tbody) return;

    if (!this.state.webhooks.length) {
      tbody.innerHTML = '<tr><td colspan="5" style="text-align: center; color: var(--color-text-muted);">No webhooks yet</td></tr>';
      return;
    }

    tbody.innerHTML = this.state.webhooks.map(wh => `
      <tr>
        <td style="max-width: 200px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;" title="${wh.url}">${wh.url}</td>
        <td>${wh.events.map(e => `<span class="badge badge-sm">${e}</span>`).join(' ')}</td>
        <td>${wh.isActive ? '<span style="color: var(--color-success);">Active</span>' : 'Inactive'}</td>
        <td>
          <button class="btn btn-ghost btn-sm" onclick="WebhooksManager.onTest('${projectId}', '${wh.id}')" title="Test">
            <i class="ri-send-plane-line"></i>
          </button>
          <button class="btn btn-ghost btn-sm" onclick="WebhooksManager.onViewDeliveries('${projectId}', '${wh.id}')" title="Deliveries">
            <i class="ri-file-list-line"></i>
          </button>
          <button class="btn btn-ghost btn-sm" style="color: var(--color-danger);" onclick="WebhooksManager.onDelete('${projectId}', '${wh.id}')" title="Delete">
            <i class="ri-delete-bin-line"></i>
          </button>
        </td>
      </tr>
    `).join('');
  },

  // ── Actions ─────────────────────────────────────────────────

  async onCreate(projectId) {
    const urlInput = document.getElementById('webhook-url');
    const url = urlInput?.value?.trim();

    if (!url || !url.startsWith('https://')) {
      alert('Webhook URL must use HTTPS');
      return;
    }

    const events = [];
    document.querySelectorAll('.webhook-event-cb:checked').forEach(cb => {
      events.push(cb.value);
    });
    if (!events.length) {
      alert('Select at least one event type');
      return;
    }

    try {
      const result = await this.createWebhook(projectId, { url, events });

      // Show the secret once
      this.showSecretModal(result.secret);

      // Reset form
      if (urlInput) urlInput.value = '';
      document.querySelectorAll('.webhook-event-cb').forEach(cb => { cb.checked = true; });

      // Refresh
      await this.loadWebhooks(projectId);
      this.renderModalTable(projectId);
      this.renderWebhooks(this.state.webhooks, projectId);
    } catch (e) {
      alert('Failed to create webhook: ' + e.message);
    }
  },

  showSecretModal(secret) {
    const el = document.getElementById('webhook-secret-display');
    if (el) {
      el.querySelector('.webhook-secret-value').textContent = secret;
      el.style.display = 'block';
    }
  },

  async onDelete(projectId, webhookId) {
    if (!confirm('Delete this webhook? Delivery history will also be removed.')) return;
    try {
      await this.deleteWebhook(projectId, webhookId);
      await this.loadWebhooks(projectId);
      this.renderModalTable(projectId);
      this.renderWebhooks(this.state.webhooks, projectId);
    } catch (e) {
      alert('Failed to delete webhook: ' + e.message);
    }
  },

  async onTest(projectId, webhookId) {
    try {
      await this.testWebhook(projectId, webhookId);
      alert('Test webhook queued for delivery');
    } catch (e) {
      alert('Failed to send test: ' + e.message);
    }
  },

  async onViewDeliveries(projectId, webhookId) {
    const deliveries = await this.loadDeliveries(projectId, webhookId);
    const container = document.getElementById('webhook-deliveries-log');
    if (!container) return;

    // Show deliveries section
    document.getElementById('webhook-deliveries-section').style.display = 'block';

    if (!deliveries.length) {
      container.innerHTML = '<p style="color: var(--color-text-muted); text-align: center;">No deliveries yet</p>';
      return;
    }

    container.innerHTML = `
      <div class="table-container">
        <table>
          <thead>
            <tr><th>Event</th><th>Status</th><th>Code</th><th>Attempt</th><th>Time</th></tr>
          </thead>
          <tbody>
            ${deliveries.map(d => `
              <tr>
                <td>${d.eventType}</td>
                <td>
                  <span class="badge badge-sm ${d.status === 'success' ? 'badge-success' : d.status === 'failed' ? 'badge-danger' : 'badge-warning'}">
                    ${d.status}
                  </span>
                </td>
                <td>${d.responseCode || '—'}</td>
                <td>${d.attempt}/3</td>
                <td style="font-size: 0.8rem;">${new Date(d.createdAt).toLocaleString()}</td>
              </tr>
            `).join('')}
          </tbody>
        </table>
      </div>`;
  },

  copySecret() {
    const val = document.querySelector('.webhook-secret-value')?.textContent;
    if (val) {
      navigator.clipboard.writeText(val).then(() => {
        const btn = document.querySelector('.copy-secret-btn');
        if (btn) { btn.textContent = 'Copied!'; setTimeout(() => { btn.textContent = 'Copy'; }, 2000); }
      });
    }
  }
};
