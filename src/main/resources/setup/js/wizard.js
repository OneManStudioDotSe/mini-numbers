/**
 * Mini Numbers 2.0 Setup Wizard Logic - Final Premium Version
 */

const SetupWizard = {
    currentStep: 1,
    totalSteps: 6,
    isTransitioning: false,
    particles: [],
    ctx: null,
    canvas: null,

    init() {
        this.initTheme();
        this.setupEventListeners();
        this.generateInitialSalt();
        this.checkSetupStatus();
        this.updateDatabaseFields();
        this.initBackgroundAnimation();
    },

    initTheme() {
        const saved = localStorage.getItem('mn_theme');
        if (saved === 'dark' || (!saved && window.matchMedia('(prefers-color-scheme: dark)').matches)) {
            document.body.classList.add('dark-mode');
            this.updateThemeIcons();
        }
    },

    toggleTheme() {
        document.body.classList.toggle('dark-mode');
        const isDark = document.body.classList.contains('dark-mode');
        localStorage.setItem('mn_theme', isDark ? 'dark' : 'light');
        this.updateThemeIcons();
    },

    updateThemeIcons() {
        const isDark = document.body.classList.contains('dark-mode');
        document.querySelector('.sun').style.display = isDark ? 'none' : 'block';
        document.querySelector('.moon').style.display = isDark ? 'block' : 'none';
    },

    setupEventListeners() {
        document.getElementById('theme-toggle').addEventListener('click', () => this.toggleTheme());
        document.getElementById('btn-quick-setup').addEventListener('click', () => this.quickSetup());
        document.getElementById('btn-next').addEventListener('click', () => this.nextStep());
        document.getElementById('btn-previous').addEventListener('click', () => this.previousStep());
        
        document.getElementById('btn-submit').addEventListener('click', (e) => {
            e.preventDefault();
            this.submitConfiguration();
        });

        document.getElementById('setup-form').addEventListener('submit', (e) => {
            e.preventDefault();
            this.submitConfiguration();
        });

        document.querySelectorAll('.toggle-password').forEach(btn => {
            btn.addEventListener('click', (e) => {
                const targetId = e.target.dataset.target;
                const input = document.getElementById(targetId);
                if (input.type === 'password') {
                    input.type = 'text';
                    e.target.textContent = 'Hide';
                } else {
                    input.type = 'password';
                    e.target.textContent = 'Show';
                }
            });
        });

        document.querySelector('.btn-generate-salt').addEventListener('click', () => this.generateSalt());

        document.querySelectorAll('input[name="dbType"]').forEach(radio => {
            radio.addEventListener('change', () => this.updateDatabaseFields());
        });

        // Clear errors on input
        document.querySelectorAll('input').forEach(input => {
            input.addEventListener('input', (e) => {
                input.classList.remove('invalid');
                const errorElement = document.getElementById(`error-${input.name}`);
                if (errorElement) errorElement.textContent = '';
            });
        });
    },

    initBackgroundAnimation() {
        this.canvas = document.getElementById('bg-canvas');
        if (!this.canvas) return;
        this.ctx = this.canvas.getContext('2d');
        
        const resize = () => {
            this.canvas.width = window.innerWidth;
            this.canvas.height = window.innerHeight;
        };
        
        window.addEventListener('resize', resize);
        resize();

        const self = this;
        class Particle {
            constructor() { this.reset(); }
            reset() {
                this.x = Math.random() * self.canvas.width;
                this.y = Math.random() * self.canvas.height;
                this.vx = (Math.random() - 0.5) * 0.4;
                this.vy = (Math.random() - 0.5) * 0.4;
                this.radius = Math.random() * 1.5 + 1;
            }
            update() {
                this.x += this.vx;
                this.y += this.vy;
                if (this.x < 0 || this.x > self.canvas.width) this.vx *= -1;
                if (this.y < 0 || this.y > self.canvas.height) this.vy *= -1;
            }
            draw() {
                self.ctx.beginPath();
                self.ctx.arc(this.x, this.y, this.radius, 0, Math.PI * 2);
                self.ctx.fillStyle = getComputedStyle(document.body).getPropertyValue('--canvas-dot');
                self.ctx.fill();
            }
        }

        for (let i = 0; i < 80; i++) this.particles.push(new Particle());

        const animate = () => {
            this.ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);
            this.ctx.strokeStyle = getComputedStyle(document.body).getPropertyValue('--canvas-line');
            this.ctx.lineWidth = 0.8;
            for (let i = 0; i < this.particles.length; i++) {
                for (let j = i + 1; j < this.particles.length; j++) {
                    const dx = this.particles[i].x - this.particles[j].x;
                    const dy = this.particles[i].y - this.particles[j].y;
                    const dist = Math.sqrt(dx * dx + dy * dy);
                    if (dist < 140) {
                        this.ctx.beginPath();
                        this.ctx.moveTo(this.particles[i].x, this.particles[i].y);
                        this.ctx.lineTo(this.particles[j].x, this.particles[j].y);
                        this.ctx.stroke();
                    }
                }
            }
            this.particles.forEach(p => { p.update(); p.draw(); });
            requestAnimationFrame(animate);
        };
        animate();
    },

    async checkSetupStatus() {
        try {
            const response = await fetch('/setup/api/status');
            const data = await response.json();
            if (!data.setupNeeded) window.location.href = '/admin-panel';
        } catch (e) {}
    },

    async generateInitialSalt() {
        const saltInput = document.getElementById('server-salt');
        if (!saltInput.value) await this.generateSalt();
    },

    async generateSalt() {
        try {
            const response = await fetch('/setup/api/generate-salt');
            const data = await response.json();
            document.getElementById('server-salt').value = data.salt;
        } catch (e) { this.showError('Failed to generate salt.'); }
    },

    updateDatabaseFields() {
        const dbType = document.querySelector('input[name="dbType"]:checked').value;
        document.getElementById('sqlite-fields').style.display = dbType === 'SQLITE' ? 'block' : 'none';
        document.getElementById('postgresql-fields').style.display = dbType === 'POSTGRESQL' ? 'block' : 'none';
    },

    nextStep() {
        if (this.isTransitioning || !this.validateCurrentStep()) return;
        if (this.currentStep < this.totalSteps) this.transitionTo(this.currentStep + 1, 'next');
    },

    previousStep() {
        if (this.isTransitioning || this.currentStep <= 1) return;
        this.transitionTo(this.currentStep - 1, 'prev');
    },

    transitionTo(step, direction) {
        if (this.isTransitioning) return;
        this.isTransitioning = true;
        const currentEl = document.querySelector(`.wizard-step[data-step="${this.currentStep}"]`);
        const nextEl = document.querySelector(`.wizard-step[data-step="${step}"]`);
        if (step === this.totalSteps) this.populateReview();
        currentEl.classList.add('exit-step');
        setTimeout(() => {
            currentEl.classList.remove('active', 'exit-step', 'slide-next', 'slide-prev');
            this.currentStep = step;
            nextEl.classList.add('active');
            nextEl.classList.add(direction === 'next' ? 'slide-next' : 'slide-prev');
            this.updateUIState();
            setTimeout(() => { this.isTransitioning = false; }, 400);
        }, 150);
    },

    updateUIState() {
        document.querySelectorAll('.progress-dot').forEach((dot, idx) => {
            dot.classList.toggle('active', (idx + 1) === this.currentStep);
            dot.classList.toggle('completed', (idx + 1) < this.currentStep);
        });
        document.getElementById('btn-previous').style.visibility = this.currentStep > 1 ? 'visible' : 'hidden';
        document.getElementById('btn-next').style.display = this.currentStep < this.totalSteps ? 'flex' : 'none';
        document.getElementById('btn-submit').style.display = this.currentStep === this.totalSteps ? 'flex' : 'none';
    },

    validateCurrentStep() {
        document.querySelectorAll('.error-message').forEach(el => el.textContent = '');
        if (this.currentStep === 1) {
            const user = document.querySelector('input[name="adminUsername"]');
            const pass = document.getElementById('admin-password');
            let valid = true;
            if (!user.value.trim()) { this.showFieldError('adminUsername', 'Username is required'); valid = false; }
            if (pass.value.length < 8) { this.showFieldError('adminPassword', 'Password must be 8+ chars'); valid = false; }
            return valid;
        }
        return true;
    },

    showFieldError(name, msg) {
        const el = document.getElementById(`error-${name}`);
        const input = document.querySelector(`input[name="${name}"]`) || document.getElementById(name);
        if (input) {
            input.classList.add('invalid');
            input.parentElement.classList.add('shake');
            setTimeout(() => input.parentElement.classList.remove('shake'), 500);
        }
        if (el) el.textContent = msg;
    },

    populateReview() {
        const content = document.getElementById('review-content');
        const config = this.buildConfigFromForm();
        content.innerHTML = `
            <div class="review-item"><span class="review-label">Admin User</span><span class="review-value">${config.adminUsername}</span></div>
            <div class="review-item"><span class="review-label">Database</span><span class="review-value">${config.database.type}</span></div>
            <div class="review-item"><span class="review-label">Privacy</span><span class="review-value">${config.privacyMode}</span></div>
            <div class="review-item"><span class="review-label">Server Port</span><span class="review-value">${config.server.port}</span></div>
            <div class="review-item"><span class="review-label">Allowed Origins</span><span class="review-value">${config.allowedOrigins || 'All (*)'}</span></div>
            <div class="review-item"><span class="review-label">IP Rate Limit</span><span class="review-value">${config.rateLimit.perIp}/min</span></div>
            <div class="review-item"><span class="review-label">Dev Mode</span><span class="review-value">${config.server.isDevelopment ? 'Enabled' : 'Disabled'}</span></div>
        `;
    },

    buildConfigFromForm() {
        const dbType = document.querySelector('input[name="dbType"]:checked').value;
        const privacy = document.querySelector('input[name="privacyMode"]:checked')?.value || 'STANDARD';
        return {
            adminUsername: document.querySelector('input[name="adminUsername"]').value,
            adminPassword: document.querySelector('input[name="adminPassword"]').value,
            serverSalt: document.getElementById('server-salt').value,
            privacyMode: privacy,
            allowedOrigins: document.querySelector('input[name="allowedOrigins"]').value,
            database: {
                type: dbType,
                sqlitePath: document.querySelector('input[name="dbSqlitePath"]').value,
                pgHost: document.querySelector('input[name="dbPgHost"]').value,
                pgPort: parseInt(document.querySelector('input[name="dbPgPort"]').value) || 5432,
                pgName: document.querySelector('input[name="dbPgName"]').value,
                pgUsername: document.querySelector('input[name="dbPgUsername"]').value,
                pgPassword: document.querySelector('input[name="dbPgPassword"]').value,
                pgMaxPoolSize: 3
            },
            server: {
                port: parseInt(document.querySelector('input[name="serverPort"]').value) || 8080,
                isDevelopment: document.querySelector('input[name="isDevelopment"]').checked
            },
            geoip: { databasePath: "src/main/resources/geo/geolite2-city.mmdb" },
            rateLimit: {
                perIp: parseInt(document.querySelector('input[name="rateLimitPerIp"]').value) || 1000,
                perApiKey: parseInt(document.querySelector('input[name="rateLimitPerApiKey"]').value) || 10000
            }
        };
    },

    async submitConfiguration() {
        document.getElementById('loading-overlay').style.display = 'flex';
        try {
            const response = await fetch('/setup/api/save', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(this.buildConfigFromForm())
            });
            const data = await response.json();
            if (response.ok && data.success) {
                document.getElementById('loading-overlay').style.display = 'none';
                document.getElementById('success-modal').style.display = 'flex';
                this.pollForServiceReady();
            } else {
                this.handleError(data);
            }
        } catch (e) {
            this.showError('Connection failed.');
            document.getElementById('loading-overlay').style.display = 'none';
        }
    },

    handleError(data) {
        document.getElementById('loading-overlay').style.display = 'none';
        if (data.errors) {
            Object.keys(data.errors).forEach(k => this.showFieldError(k, data.errors[k]));
            this.transitionTo(1, 'prev');
        } else {
            this.showError(data.error || 'Setup failed');
        }
    },

    async pollForServiceReady() {
        const poll = async () => {
            try {
                const response = await fetch('/setup/api/status');
                const data = await response.json();
                if (!data.setupNeeded && data.servicesReady) { this.startCountdown(); return; }
            } catch (e) {}
            setTimeout(poll, 1000);
        };
        poll();
    },

    startCountdown() {
        let seconds = 5;
        const tick = () => {
            seconds--;
            if (document.getElementById('countdown-number')) document.getElementById('countdown-number').textContent = seconds;
            if (document.getElementById('countdown-bar')) document.getElementById('countdown-bar').style.width = `${((5 - seconds) / 5) * 100}%`;
            if (seconds <= 0) window.location.href = '/login';
            else setTimeout(tick, 1000);
        };
        setTimeout(tick, 1000);
    },

    showError(msg) {
        document.getElementById('error-message').textContent = msg;
        document.getElementById('error-modal').style.display = 'flex';
    },

    async quickSetup() {
        if (this.isTransitioning) return;
        
        // 1. Fill all fields with sensible defaults
        document.querySelector('input[name="adminUsername"]').value = 'admin';
        document.querySelector('input[name="adminPassword"]').value = 'admin123456';
        
        // Ensure we have a fresh salt
        await this.generateSalt();
        
        // Database
        const sqliteRadio = document.querySelector('input[name="dbType"][value="SQLITE"]');
        if (sqliteRadio) sqliteRadio.checked = true;
        this.updateDatabaseFields();
        document.querySelector('input[name="dbSqlitePath"]').value = './stats.db';
        
        // Privacy
        const privacyRadio = document.querySelector('input[name="privacyMode"][value="STANDARD"]');
        if (privacyRadio) privacyRadio.checked = true;
        
        // Server
        document.querySelector('input[name="serverPort"]').value = '8080';
        document.querySelector('input[name="allowedOrigins"]').value = '*';
        document.querySelector('input[name="isDevelopment"]').checked = true;
        
        // Rate Limits
        document.querySelector('input[name="rateLimitPerIp"]').value = '1000';
        document.querySelector('input[name="rateLimitPerApiKey"]').value = '10000';

        // 2. Visual State Update: Jump to Review Step
        this.currentStep = this.totalSteps;
        this.populateReview();
        
        // Mark all steps as active/completed in UI
        document.querySelectorAll('.wizard-step').forEach(s => s.classList.remove('active'));
        const reviewStep = document.querySelector(`.wizard-step[data-step="${this.totalSteps}"]`);
        reviewStep.classList.add('active', 'slide-next');
        
        this.updateUIState();

        // 3. Trigger Action: Submit after a small "visual confirmation" delay
        setTimeout(() => {
            this.submitConfiguration();
        }, 800);
    }
};

document.addEventListener('DOMContentLoaded', () => SetupWizard.init());
