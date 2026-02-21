/**
 * Mini Numbers Setup Wizard
 * WordPress-style interactive setup wizard
 */

const SetupWizard = {
    currentStep: 1,
    totalSteps: 5,

    /**
     * Initialize wizard
     */
    init() {
        this.setupEventListeners();
        this.generateInitialSalt();
        this.checkSetupStatus();
        this.updateDatabaseFields(); // Initialize database fields visibility
    },

    /**
     * Set up all event listeners
     */
    setupEventListeners() {
        // Quick setup button
        document.getElementById('btn-quick-setup').addEventListener('click', () => this.quickSetup());

        // Navigation buttons
        document.getElementById('btn-next').addEventListener('click', () => this.nextStep());
        document.getElementById('btn-previous').addEventListener('click', () => this.previousStep());
        document.getElementById('btn-submit').addEventListener('click', (e) => {
            e.preventDefault();
            this.submitConfiguration();
        });

        // Password visibility toggles
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

        // Generate salt button
        document.querySelector('.btn-generate-salt').addEventListener('click', () => this.generateSalt());

        // Database type radio buttons
        document.querySelectorAll('input[name="dbType"]').forEach(radio => {
            radio.addEventListener('change', () => this.updateDatabaseFields());
        });

        // Clear errors on input
        document.querySelectorAll('input, select, textarea').forEach(input => {
            input.addEventListener('input', (e) => {
                const errorElement = document.getElementById(`error-${e.target.name}`);
                if (errorElement) {
                    errorElement.textContent = '';
                }
            });
        });
    },

    /**
     * Check if setup is still needed
     */
    async checkSetupStatus() {
        try {
            const response = await fetch('/setup/api/status');
            const data = await response.json();

            if (!data.setupNeeded) {
                // Setup already complete, redirect to admin
                window.location.href = '/admin-panel';
            }
        } catch (error) {
            console.error('Error checking setup status:', error);
        }
    },

    /**
     * Generate initial server salt
     */
    async generateInitialSalt() {
        const saltInput = document.getElementById('server-salt');
        if (!saltInput.value) {
            await this.generateSalt();
        }
    },

    /**
     * Quick setup for testing - auto-fill with defaults and submit
     */
    async quickSetup() {
        if (!confirm('This will auto-fill all fields with default values and complete setup. Continue?')) {
            return;
        }

        // Step 1: Security
        document.getElementById('admin-username').value = 'admin';
        document.getElementById('admin-password').value = 'admin123456';
        await this.generateSalt();

        // Step 2: Database (SQLite is already selected by default)
        document.getElementById('db-sqlite-path').value = './stats.db';

        // Step 3: Server
        document.getElementById('server-port').value = '8080';
        document.getElementById('allowed-origins').value = '';
        document.getElementById('ktor-development').checked = true;

        // Step 4: Advanced (defaults are already set)
        // GeoIP, rate limits already have defaults

        // Submit directly
        await this.submitConfiguration();
    },

    /**
     * Generate secure server salt from API
     */
    async generateSalt() {
        try {
            const response = await fetch('/setup/api/generate-salt');
            const data = await response.json();
            document.getElementById('server-salt').value = data.salt;
        } catch (error) {
            console.error('Error generating salt:', error);
            this.showError('Failed to generate salt. Please try again.');
        }
    },

    /**
     * Update database fields visibility based on selected type
     */
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

    /**
     * Navigate to next step
     */
    nextStep() {
        // Validate current step before proceeding
        if (!this.validateCurrentStep()) {
            return;
        }

        if (this.currentStep < this.totalSteps) {
            this.currentStep++;
            this.updateStepDisplay();

            // If we're on the review step, populate it
            if (this.currentStep === this.totalSteps) {
                this.populateReview();
            }
        }
    },

    /**
     * Navigate to previous step
     */
    previousStep() {
        if (this.currentStep > 1) {
            this.currentStep--;
            this.updateStepDisplay();
        }
    },

    /**
     * Validate current step
     */
    validateCurrentStep() {
        // Clear all error messages
        document.querySelectorAll('.error-message').forEach(el => el.textContent = '');

        const step = this.currentStep;
        let isValid = true;

        if (step === 1) {
            // Validate security fields
            const username = document.getElementById('admin-username').value;
            const password = document.getElementById('admin-password').value;
            const salt = document.getElementById('server-salt').value;

            if (!username || username.length < 3) {
                this.showFieldError('adminUsername', 'Admin username must be at least 3 characters');
                isValid = false;
            }

            if (!password || password.length < 8) {
                this.showFieldError('adminPassword', 'Admin password must be at least 8 characters');
                isValid = false;
            }

            if (!salt || salt.length < 32) {
                this.showFieldError('serverSalt', 'Server salt must be at least 32 characters');
                isValid = false;
            }
        } else if (step === 2) {
            // Validate database fields
            const dbType = document.querySelector('input[name="dbType"]:checked').value;

            if (dbType === 'SQLITE') {
                const path = document.getElementById('db-sqlite-path').value;
                if (!path) {
                    this.showFieldError('dbSqlitePath', 'SQLite database path is required');
                    isValid = false;
                }
            } else if (dbType === 'POSTGRESQL') {
                const host = document.getElementById('db-pg-host').value;
                const port = document.getElementById('db-pg-port').value;
                const name = document.getElementById('db-pg-name').value;
                const username = document.getElementById('db-pg-username').value;
                const password = document.getElementById('db-pg-password').value;

                if (!host) {
                    this.showFieldError('dbPgHost', 'PostgreSQL host is required');
                    isValid = false;
                }
                if (!port || port < 1 || port > 65535) {
                    this.showFieldError('dbPgPort', 'Port must be between 1 and 65535');
                    isValid = false;
                }
                if (!name) {
                    this.showFieldError('dbPgName', 'Database name is required');
                    isValid = false;
                }
                if (!username) {
                    this.showFieldError('dbPgUsername', 'Username is required');
                    isValid = false;
                }
                if (!password) {
                    this.showFieldError('dbPgPassword', 'Password is required');
                    isValid = false;
                }
            }
        } else if (step === 3) {
            // Validate server fields
            const port = document.getElementById('server-port').value;
            if (!port || port < 1 || port > 65535) {
                this.showFieldError('serverPort', 'Server port must be between 1 and 65535');
                isValid = false;
            }
        }

        return isValid;
    },

    /**
     * Show field-specific error message
     */
    showFieldError(fieldName, message) {
        const errorElement = document.getElementById(`error-${fieldName}`);
        if (errorElement) {
            errorElement.textContent = message;
        }
    },

    /**
     * Update step display
     */
    updateStepDisplay() {
        // Update step visibility
        document.querySelectorAll('.wizard-step').forEach(step => {
            step.classList.remove('active');
        });
        document.querySelector(`.wizard-step[data-step="${this.currentStep}"]`).classList.add('active');

        // Update progress indicator
        document.querySelectorAll('.progress-step').forEach((step, index) => {
            if (index < this.currentStep) {
                step.classList.add('completed');
                step.classList.remove('active');
            } else if (index === this.currentStep - 1) {
                step.classList.add('active');
                step.classList.remove('completed');
            } else {
                step.classList.remove('active', 'completed');
            }
        });

        // Update navigation buttons
        const btnPrevious = document.getElementById('btn-previous');
        const btnNext = document.getElementById('btn-next');
        const btnSubmit = document.getElementById('btn-submit');

        btnPrevious.style.display = this.currentStep > 1 ? 'inline-block' : 'none';
        btnNext.style.display = this.currentStep < this.totalSteps ? 'inline-block' : 'none';
        btnSubmit.style.display = this.currentStep === this.totalSteps ? 'inline-block' : 'none';

        // Scroll to top
        window.scrollTo({ top: 0, behavior: 'smooth' });
    },

    /**
     * Populate review step with all configuration
     */
    populateReview() {
        const reviewContent = document.getElementById('review-content');
        const config = this.buildConfigFromForm();

        const html = `
            <div class="review-section">
                <h3>Security</h3>
                <div class="review-item">
                    <span class="review-label">Admin Username:</span>
                    <span class="review-value">${this.escapeHtml(config.adminUsername)}</span>
                </div>
                <div class="review-item">
                    <span class="review-label">Admin Password:</span>
                    <span class="review-value">••••••••</span>
                </div>
                <div class="review-item">
                    <span class="review-label">Server Salt:</span>
                    <span class="review-value">${this.escapeHtml(config.serverSalt.substring(0, 16))}... (${config.serverSalt.length} characters)</span>
                </div>
            </div>

            <div class="review-section">
                <h3>Database</h3>
                <div class="review-item">
                    <span class="review-label">Type:</span>
                    <span class="review-value">${config.database.type}</span>
                </div>
                ${config.database.type === 'SQLITE' ? `
                    <div class="review-item">
                        <span class="review-label">Database Path:</span>
                        <span class="review-value">${this.escapeHtml(config.database.sqlitePath)}</span>
                    </div>
                ` : `
                    <div class="review-item">
                        <span class="review-label">Host:</span>
                        <span class="review-value">${this.escapeHtml(config.database.pgHost)}:${config.database.pgPort}</span>
                    </div>
                    <div class="review-item">
                        <span class="review-label">Database Name:</span>
                        <span class="review-value">${this.escapeHtml(config.database.pgName)}</span>
                    </div>
                    <div class="review-item">
                        <span class="review-label">Username:</span>
                        <span class="review-value">${this.escapeHtml(config.database.pgUsername)}</span>
                    </div>
                    <div class="review-item">
                        <span class="review-label">Password:</span>
                        <span class="review-value">••••••••</span>
                    </div>
                    <div class="review-item">
                        <span class="review-label">Pool Size:</span>
                        <span class="review-value">${config.database.pgMaxPoolSize || 3}</span>
                    </div>
                `}
            </div>

            <div class="review-section">
                <h3>Server</h3>
                <div class="review-item">
                    <span class="review-label">Port:</span>
                    <span class="review-value">${config.server.port}</span>
                </div>
                <div class="review-item">
                    <span class="review-label">Allowed Origins:</span>
                    <span class="review-value">${config.allowedOrigins || '(none)'}</span>
                </div>
                <div class="review-item">
                    <span class="review-label">Development Mode:</span>
                    <span class="review-value">${config.server.isDevelopment ? 'Yes' : 'No'}</span>
                </div>
            </div>

            <div class="review-section">
                <h3>Advanced</h3>
                <div class="review-item">
                    <span class="review-label">GeoIP Database:</span>
                    <span class="review-value">${this.escapeHtml(config.geoip.databasePath)}</span>
                </div>
                <div class="review-item">
                    <span class="review-label">Rate Limit (per IP):</span>
                    <span class="review-value">${config.rateLimit.perIp} req/min</span>
                </div>
                <div class="review-item">
                    <span class="review-label">Rate Limit (per API Key):</span>
                    <span class="review-value">${config.rateLimit.perApiKey} req/min</span>
                </div>
            </div>
        `;

        reviewContent.innerHTML = html;
    },

    /**
     * Build configuration object from form
     */
    buildConfigFromForm() {
        const dbType = document.querySelector('input[name="dbType"]:checked').value;

        const config = {
            adminUsername: document.getElementById('admin-username').value,
            adminPassword: document.getElementById('admin-password').value,
            serverSalt: document.getElementById('server-salt').value,
            allowedOrigins: document.getElementById('allowed-origins').value,
            database: {
                type: dbType,
                sqlitePath: dbType === 'SQLITE' ? document.getElementById('db-sqlite-path').value : null,
                pgHost: dbType === 'POSTGRESQL' ? document.getElementById('db-pg-host').value : null,
                pgPort: dbType === 'POSTGRESQL' ? parseInt(document.getElementById('db-pg-port').value) : null,
                pgName: dbType === 'POSTGRESQL' ? document.getElementById('db-pg-name').value : null,
                pgUsername: dbType === 'POSTGRESQL' ? document.getElementById('db-pg-username').value : null,
                pgPassword: dbType === 'POSTGRESQL' ? document.getElementById('db-pg-password').value : null,
                pgMaxPoolSize: dbType === 'POSTGRESQL' ? parseInt(document.getElementById('db-pg-pool-size').value) : null
            },
            server: {
                port: parseInt(document.getElementById('server-port').value),
                isDevelopment: document.getElementById('ktor-development').checked
            },
            geoip: {
                databasePath: document.getElementById('geoip-path').value
            },
            rateLimit: {
                perIp: parseInt(document.getElementById('rate-limit-ip').value),
                perApiKey: parseInt(document.getElementById('rate-limit-key').value)
            }
        };

        return config;
    },

    /**
     * Submit configuration to server
     */
    async submitConfiguration() {
        // Show loading overlay
        document.getElementById('loading-overlay').style.display = 'flex';

        const config = this.buildConfigFromForm();

        try {
            const response = await fetch('/setup/api/save', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(config)
            });

            const data = await response.json();

            if (response.ok && data.success) {
                // Hide loading, show success
                document.getElementById('loading-overlay').style.display = 'none';
                document.getElementById('success-modal').style.display = 'flex';

                // Poll for service ready (no restart needed!)
                this.pollForServiceReady();
            } else {
                // Validation errors from server
                document.getElementById('loading-overlay').style.display = 'none';

                if (data.errors) {
                    // Show field-specific errors
                    Object.keys(data.errors).forEach(fieldName => {
                        this.showFieldError(fieldName, data.errors[fieldName]);
                    });

                    // Go back to first step with errors
                    this.currentStep = 1;
                    this.updateStepDisplay();

                    this.showError('Please fix the errors and try again.');
                } else {
                    this.showError(data.error || 'Configuration validation failed');
                }
            }
        } catch (error) {
            console.error('Error submitting configuration:', error);
            document.getElementById('loading-overlay').style.display = 'none';
            this.showError('Failed to save configuration. Please check your connection and try again.');
        }
    },

    /**
     * Poll server to detect when services are ready, then show countdown
     * No restart needed - services initialize in-place
     */
    async pollForServiceReady() {
        let attempts = 0;
        const maxAttempts = 30;  // 15 seconds total
        const pollInterval = 500;  // Check every 500ms

        const poll = async () => {
            attempts++;

            try {
                const response = await fetch('/setup/api/status');

                if (response.ok) {
                    const data = await response.json();

                    // Check if services are ready
                    if (!data.setupNeeded && data.servicesReady) {
                        // Services ready - start the countdown to login
                        this.startCountdown();
                        return;
                    }
                }
            } catch (error) {
                console.error('Error polling service status:', error);
            }

            if (attempts < maxAttempts) {
                setTimeout(poll, pollInterval);
            } else {
                // Timeout - show manual recovery
                document.getElementById('success-modal').innerHTML = `
                    <div class="modal-content warning">
                        <h2>Service Initialization Timeout</h2>
                        <p>Configuration was saved successfully, but services are taking longer than expected to initialize.</p>
                        <p><strong>Please try:</strong></p>
                        <ol style="text-align: left; margin: 20px auto; max-width: 400px;">
                            <li>Check the server logs for errors</li>
                            <li>Refresh this page manually</li>
                            <li>Visit <a href="/login">/login</a> directly</li>
                        </ol>
                        <button class="btn btn-primary" onclick="window.location.href='/login'">
                            Go to Login
                        </button>
                    </div>
                `;
            }
        };

        // Start polling immediately
        poll();
    },

    /**
     * Start a visible 5-second countdown before redirecting to login
     */
    startCountdown() {
        let seconds = 5;
        const numberEl = document.getElementById('countdown-number');
        const barEl = document.getElementById('countdown-bar');

        if (numberEl) numberEl.textContent = seconds;
        if (barEl) barEl.style.width = '0%';

        // Animate progress bar immediately
        requestAnimationFrame(() => {
            if (barEl) barEl.style.width = '20%';
        });

        const tick = () => {
            seconds--;
            if (numberEl) {
                numberEl.style.transform = 'scale(1.3)';
                setTimeout(() => { numberEl.style.transform = 'scale(1)'; }, 150);
                numberEl.textContent = seconds;
            }
            if (barEl) barEl.style.width = `${((5 - seconds) / 5) * 100}%`;

            if (seconds <= 0) {
                window.location.href = '/login';
            } else {
                setTimeout(tick, 1000);
            }
        };

        setTimeout(tick, 1000);
    },

    /**
     * Show error modal
     */
    showError(message) {
        document.getElementById('error-message').textContent = message;
        document.getElementById('error-modal').style.display = 'flex';
    },

    /**
     * Escape HTML to prevent XSS
     */
    escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }
};

// Initialize wizard when DOM is ready
document.addEventListener('DOMContentLoaded', () => {
    SetupWizard.init();
});
