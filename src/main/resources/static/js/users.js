/**
 * Account and users (Settings → Account): every user can change their own password; admins can add users,
 * change their role, deactivate and remove them. The server refuses changes to your own role or account.
 */
const UsersManager = {
  me: { username: '', role: '' },

  init(me) {
    this.me = me;
    document.getElementById('account-username').textContent = me.username || '…';
    document.getElementById('account-role').textContent = me.role || '';

    document.getElementById('change-password-btn').addEventListener('click', () => this.open('change-password-modal'));
    document.getElementById('manage-users-btn').addEventListener('click', () => {
      this.open('users-modal');
      this.loadUsers();
    });

    for (const [modalId, closeIds] of [
      ['change-password-modal', ['close-change-password', 'cancel-change-password']],
      ['users-modal', ['close-users-modal']],
    ]) {
      const modal = document.getElementById(modalId);
      closeIds.forEach(id => document.getElementById(id).addEventListener('click', () => this.close(modal)));
      modal.querySelector('.modal-backdrop').addEventListener('click', () => this.close(modal));
    }

    document.getElementById('change-password-form').addEventListener('submit', e => {
      e.preventDefault();
      this.changePassword();
    });
    document.getElementById('add-user-form').addEventListener('submit', e => {
      e.preventDefault();
      this.addUser();
    });
    document.getElementById('copy-new-user-password').addEventListener('click', async () => {
      await navigator.clipboard.writeText(document.getElementById('new-user-password-value').textContent);
      Utils.toast.success('Password copied');
    });
    document.getElementById('users-list').addEventListener('change', e => {
      if (e.target.matches('select[data-user-id]')) this.setRole(e.target);
    });
    document.getElementById('users-list').addEventListener('click', e => {
      const button = e.target.closest('button[data-action]');
      if (button) this[button.dataset.action](button.dataset.userId, button.dataset.username, button);
    });
  },

  /** Opened from Settings, which closes first so only one dialog traps focus. */
  open(modalId) {
    const modal = document.getElementById(modalId);
    document.getElementById('settings-modal').classList.remove('show');
    Utils.focusTrap.closeModal();
    if (modalId === 'change-password-modal') {
      document.getElementById('change-password-form').reset();
      this.showError('change-password-error', '');
    } else {
      document.getElementById('add-user-form').reset();
      this.showError('add-user-error', '');
      document.getElementById('new-user-password').hidden = true;
    }
    modal.classList.add('show');
    Utils.focusTrap.openModal(modal, () => this.close(modal));
  },

  close(modal) {
    modal.classList.remove('show');
    Utils.focusTrap.closeModal();
  },

  showError(id, message) {
    const el = document.getElementById(id);
    el.textContent = message;
    el.hidden = !message;
  },

  async errorMessage(response, fallback) {
    if (response.status === 429) return 'Too many attempts. Wait a minute and try again.';
    try {
      return (await response.json()).error || fallback;
    } catch {
      return fallback;
    }
  },

  // ── Change password ─────────────────────────────────────────

  async changePassword() {
    const current = document.getElementById('current-password').value;
    const next = document.getElementById('new-password').value;
    const repeat = document.getElementById('repeat-password').value;
    if (!current) return this.showError('change-password-error', 'Enter your current password.');
    if (next.length < 8) return this.showError('change-password-error', 'The new password needs at least 8 characters.');
    if (next !== repeat) return this.showError('change-password-error', "The new passwords don't match.");

    const button = document.getElementById('submit-change-password');
    button.disabled = true;
    try {
      const response = await fetch('/admin/me/password', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ currentPassword: current, newPassword: next }),
      });
      if (!response.ok) {
        this.showError('change-password-error', await this.errorMessage(response, 'The password could not be changed.'));
        return;
      }
      Utils.toast.success('Password changed. Sign in with the new password.');
      setTimeout(() => { window.location.href = '/login'; }, 1200);
    } catch {
      this.showError('change-password-error', 'The server could not be reached.');
    } finally {
      button.disabled = false;
    }
  },

  // ── Users ───────────────────────────────────────────────────

  async loadUsers() {
    const list = document.getElementById('users-list');
    try {
      const response = await fetch('/admin/users');
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      this.renderUsers(await response.json());
    } catch (error) {
      console.error('Failed to load users:', error);
      list.innerHTML = '<tr><td colspan="4">The users could not be loaded.</td></tr>';
    }
  },

  renderUsers(users) {
    const esc = Utils.escapeHtml;
    document.getElementById('users-list').innerHTML = users.map(user => {
      const self = user.username === this.me.username;
      const name = `${esc(user.username)}${self ? ' <span class="badge badge-secondary">you</span>' : ''}`;
      const role = self
        ? esc(user.role)
        : `<select class="select" data-user-id="${esc(user.id)}" data-previous="${esc(user.role)}" aria-label="Role of ${esc(user.username)}">
             <option value="viewer"${user.role === 'viewer' ? ' selected' : ''}>viewer</option>
             <option value="admin"${user.role === 'admin' ? ' selected' : ''}>admin</option>
           </select>`;
      const actions = self ? '' : `
          <button class="btn btn-ghost btn-sm" data-action="toggleActive" data-user-id="${esc(user.id)}"
                  data-username="${esc(user.username)}" data-active="${user.isActive}">${user.isActive ? 'Deactivate' : 'Reactivate'}</button>
          <button class="btn btn-ghost btn-sm" data-action="removeUser" data-user-id="${esc(user.id)}"
                  data-username="${esc(user.username)}" aria-label="Remove ${esc(user.username)}"><i class="ri-delete-bin-line"></i></button>`;
      return `<tr class="${user.isActive ? '' : 'users-table__inactive'}">
          <td>${name}</td>
          <td>${role}</td>
          <td>${user.isActive ? 'Active' : 'Deactivated'}</td>
          <td><div class="users-table__actions">${actions}</div></td>
        </tr>`;
    }).join('');
  },

  async setRole(select) {
    const response = await fetch(`/admin/users/${select.dataset.userId}/role`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ role: select.value }),
    });
    if (response.ok) {
      select.dataset.previous = select.value;
      Utils.toast.success('Role changed');
    } else {
      select.value = select.dataset.previous;
      Utils.toast.error(await this.errorMessage(response, 'The role could not be changed.'));
    }
  },

  async toggleActive(userId, username, button) {
    const active = button.dataset.active !== 'true';
    if (!active && !confirm(`Deactivate ${username}? They are signed out at once and can't sign in until reactivated.`)) return;
    const response = await fetch(`/admin/users/${userId}/active`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ isActive: active }),
    });
    if (!response.ok) return Utils.toast.error(await this.errorMessage(response, 'The user could not be changed.'));
    this.loadUsers();
  },

  async removeUser(userId, username) {
    if (!confirm(`Remove ${username}? They are signed out at once. This cannot be undone.`)) return;
    const response = await fetch(`/admin/users/${userId}`, { method: 'DELETE' });
    if (!response.ok) return Utils.toast.error(await this.errorMessage(response, 'The user could not be removed.'));
    Utils.toast.success(`${username} removed`);
    this.loadUsers();
  },

  async addUser() {
    const username = document.getElementById('new-user-name').value.trim();
    const role = document.getElementById('new-user-role').value;
    if (!username) return this.showError('add-user-error', 'Enter a username.');

    const password = this.generatePassword();
    const response = await fetch('/admin/users', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password, role }),
    });
    if (!response.ok) {
      this.showError('add-user-error', response.status === 409
        ? `There is already a user named ${username}.`
        : await this.errorMessage(response, 'The user could not be added.'));
      return;
    }
    this.showError('add-user-error', '');
    document.getElementById('add-user-form').reset();
    document.getElementById('new-user-password-name').textContent = username;
    document.getElementById('new-user-password-value').textContent = password;
    document.getElementById('new-user-password').hidden = false;
    this.loadUsers();
  },

  /** Same as the setup wizard's: 20 characters without look-alikes (no 0/O, 1/l/I), drawn without modulo bias. */
  generatePassword() {
    const alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789';
    const limit = 256 - (256 % alphabet.length);
    let password = '';
    while (password.length < 20) {
      for (const byte of crypto.getRandomValues(new Uint8Array(32))) {
        if (byte < limit && password.length < 20) password += alphabet[byte % alphabet.length];
      }
    }
    return password;
  },
};
