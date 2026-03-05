/**
 * Mini Numbers 2.0 Setup Wizard Logic - Enhanced
 */

const SetupWizard = {
    currentStep: 1,
    totalSteps: 6,
    isTransitioning: false,

    init() {
        this.setupEventListeners();
        this.generateInitialSalt();
        this.checkSetupStatus();
        this.updateDatabaseFields();
        this.initBackgroundAnimation();
    },

    setupEventListeners() {
        document.getElementById('btn-quick-setup').addEventListener('click', () => this.quickSetup());
        document.getElementById('btn-next').addEventListener('click', () => this.nextStep());
        document.getElementById('btn-previous').addEventListener('click', () => this.previousStep());
        
        // Fix: Explicitly handle submit button click if form submit doesn't catch it
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
                const errorElement = document.getElementById(`error-${e.target.name}`);
                if (errorElement) errorElement.textContent = '';
            });
        });
    },

    initBackgroundAnimation() {
        const canvas = document.getElementById('bg-canvas');
        if (!canvas) return;
        const ctx = canvas.getContext('2d');
        let particles = [];
        
        const resize = () => {
            canvas.width = window.innerWidth;
            canvas.height = window.innerHeight;
        };
        
        window.addEventListener('resize', resize);
        resize();

        class Particle {
            constructor() {
                this.reset();
            }
            reset() {
                this.x = Math.random() * canvas.width;
                this.y = Math.random() * canvas.height;
                this.vx = (Math.random() - 0.5) * 0.5;
                this.vy = (Math.random() - 0.5) * 0.5;
                this.radius = Math.random() * 2 + 1;
            }
            update() {
                this.x += this.vx;
                this.y += this.vy;
                if (this.x < 0 || this.x > canvas.width) this.vx *= -1;
                if (this.y < 0 || this.y > canvas.height) this.vy *= -1;
            }
            draw() {
                ctx.beginPath();
                ctx.arc(this.x, this.y, this.radius, 0, Math.PI * 2);
                ctx.fillStyle = 'rgba(99, 102, 241, 0.15)';
                ctx.fill();
            }
        }

        for (let i = 0; i < 60; i++) particles.push(new Particle());

        const animate = () => {
            ctx.clearRect(0, 0, canvas.width, canvas.height);
            
            // Draw connections
            ctx.strokeStyle = 'rgba(99, 102, 241, 0.05)';
            ctx.lineWidth = 1;
            for (let i = 0; i < particles.length; i++) {
                for (let j = i + 1; j < particles.length; j++) {
                    const dx = particles[i].x - particles[j].x;
                    const dy = particles[i].y - particles[j].y;
                    const dist = Math.sqrt(dx * dx + dy * dy);
                    if (dist < 150) {
                        ctx.beginPath();
                        ctx.moveTo(particles[i].x, particles[i].y);
                        ctx.lineTo(particles[j].x, particles[j].y);
                        ctx.stroke();
                    }
                }
            }

            particles.forEach(p => {
                p.update();
                p.draw();
            });
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
        const sqliteFields = document.getElementById('sqlite-fields');
        const postgresFields = document.getElementById('postgresql-fields');

        if (dbType === 'SQLITE') {
            sqliteFields.style.display = 'block';
            postgresFields.style.display = 'none';
        } else {
            sqliteFields.style.display = 'none';
            postgresFields.style.display = 'block';
        }
    },

    nextStep() {
        if (this.isTransitioning || !this.validateCurrentStep()) return;
        if (this.currentStep < this.totalSteps) {
            this.transitionTo(this.currentStep + 1, 'next');
        }
    },

    previousStep() {
        if (this.isTransitioning || this.currentStep <= 1) return;
        this.transitionTo(this.currentStep - 1, 'prev');
    },

    transitionTo(step, direction) {
        this.isTransitioning = true;
        const currentEl = document.querySelector(`.wizard-step[data-step="${this.currentStep}"]`);
        const nextEl = document.querySelector(`.wizard-step[data-step="${step}"]`);
        
        if (step === this.totalSteps) this.populateReview();

        currentEl.classList.remove('active', 'slide-next', 'slide-prev');
        
        setTimeout(() => {
            this.currentStep = step;
            nextEl.classList.add('active');
            nextEl.classList.add(direction === 'next' ? 'slide-next' : 'slide-prev');
            this.updateUIState();
            this.isTransitioning = false;
        }, 50);
    },

    updateUIState() {
        document.querySelectorAll('.progress-dot').forEach((dot, idx) => {
            const stepNum = idx + 1;
            dot.classList.toggle('active', stepNum === this.currentStep);
            dot.classList.toggle('completed', stepNum < this.currentStep);
        });

        const btnPrev = document.getElementById('btn-previous');
        const btnNext = document.getElementById('btn-next');
        const btnSubmit = document.getElementById('btn-submit');

        btnPrev.style.visibility = this.currentStep > 1 ? 'visible' : 'hidden';
        btnNext.style.display = this.currentStep < this.totalSteps ? 'flex' : 'none';
        btnSubmit.style.display = this.currentStep === this.totalSteps ? 'flex' : 'none';
    },

    validateCurrentStep() {
        document.querySelectorAll('.error-message').forEach(el => el.textContent = '');
        const step = this.currentStep;
        
        if (step === 1) {
            const pass = document.getElementById('admin-password').value;
            if (pass.length < 8) {
                this.showFieldError('adminPassword', 'Password must be 8+ chars');
                return false;
            }
        }
        return true;
    },

    showFieldError(name, msg) {
        const el = document.getElementById(`error-${name}`);
        if (el) el.textContent = msg;
    },

    populateReview() {
        const content = document.getElementById('review-content');
        const config = this.buildConfigFromForm();
        
        content.innerHTML = `
            <div class="review-item"><span class="review-label">Admin Account</span><span class="review-value">${config.adminUsername}</span></div>
            <div class="review-item"><span class="review-label">Storage Type</span><span class="review-value">${config.database.type}</span></div>
            <div class="review-item"><span class="review-label">Privacy Mode</span><span class="review-value">${config.privacyMode}</span></div>
            <div class="review-item"><span class="review-label">Server Port</span><span class="review-value">${config.server.port}</span></div>
            <div class="review-item"><span class="review-label">Environment</span><span class="review-value">${config.server.isDevelopment ? 'Development' : 'Production'}</span></div>
            <div class="review-item"><span class="review-label">Rate Limiting</span><span class="review-value">${config.rateLimit.perIp} req/min</span></div>
        `;
    },

    buildConfigFromForm() {
        const dbType = document.querySelector('input[name="dbType"]:checked').value;
        return {
            adminUsername: document.querySelector('input[name="adminUsername"]').value,
            adminPassword: document.querySelector('input[name="adminPassword"]').value,
            serverSalt: document.getElementById('server-salt').value,
            privacyMode: document.querySelector('input[name="privacyMode"]:checked').value,
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
            geoip: { databasePath: document.querySelector('input[name="geoipPath"]').value },
            rateLimit: {
                perIp: parseInt(document.querySelector('input[name="rateLimitPerIp"]').value) || 1000,
                perApiKey: parseInt(document.querySelector('input[name="rateLimitPerApiKey"]').value) || 10000
            }
        };
    },

    async submitConfiguration() {
        const loading = document.getElementById('loading-overlay');
        loading.style.display = 'flex';
        
        const config = this.buildConfigFromForm();

        try {
            const response = await fetch('/setup/api/save', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(config)
            });

            const data = await response.json();
            if (response.ok && data.success) {
                loading.style.display = 'none';
                document.getElementById('success-modal').style.display = 'flex';
                this.pollForServiceReady();
            } else {
                this.handleError(data);
            }
        } catch (e) {
            this.showError('Connection to server failed.');
            loading.style.display = 'none';
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
                if (!data.setupNeeded && data.servicesReady) {
                    this.startCountdown();
                    return;
                }
            } catch (e) {}
            setTimeout(poll, 1000);
        };
        poll();
    },

    startCountdown() {
        let seconds = 5;
        const num = document.getElementById('countdown-number');
        const bar = document.getElementById('countdown-bar');
        
        const tick = () => {
            seconds--;
            if (num) num.textContent = seconds;
            if (bar) bar.style.width = `${((5 - seconds) / 5) * 100}%`;
            if (seconds <= 0) window.location.href = '/login';
            else setTimeout(tick, 1000);
        };
        setTimeout(tick, 1000);
    },

    showError(msg) {
        document.getElementById('error-message').textContent = msg;
        document.getElementById('error-modal').style.display = 'flex';
    },

    quickSetup() {
        document.querySelector('input[name="adminPassword"]').value = 'admin123456';
        this.submitConfiguration();
    }
};

document.addEventListener('DOMContentLoaded', () => SetupWizard.init());
