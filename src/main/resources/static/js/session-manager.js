// ============================================================
// SESSION MANAGER - Handles session expiry warnings and auto-logout
// ============================================================

class SessionManager {
    constructor(options = {}) {
        // Get the context path from the page
        const contextPath = options.contextPath || window.CONTEXT_PATH || '/assetIQ-pro';

        this.config = {
            // Check every 5 seconds for more accurate countdown
            checkInterval: options.checkInterval || 5000,
            // Show warning at 60 seconds remaining (1 minute)
            warningThreshold: options.warningThreshold || 60,
            // Show critical at 15 seconds remaining
            criticalThreshold: options.criticalThreshold || 15,
            // Auto-extend at 20 seconds remaining
            extendBuffer: options.extendBuffer || 20,
            // Use full paths with context path
            logoutEndpoint: options.logoutEndpoint || `${contextPath}/logout`,
            statusEndpoint: options.statusEndpoint || `${contextPath}/api/session/status`,
            extendEndpoint: options.extendEndpoint || `${contextPath}/api/session/extend`,
            pingEndpoint: options.pingEndpoint || `${contextPath}/api/session/ping`
        };

        this.sessionData = {
            remainingSeconds: 0,
            maxInactiveInterval: 300, // 5 minutes
            lastAccessTime: null,
            status: 'active',
            username: null
        };

        // Track session resets
        this.lastRemainingSeconds = 0;
        this.sessionResets = 0;

        this.warningShown = false;
        this.criticalShown = false;
        this.isModalOpen = false;
        this.timer = null;
        this.countdownTimer = null;
        this.pingTimer = null;
        this.consecutiveFailures = 0;
        // Track the countdown locally for smooth display
        this.localCountdown = 0;
        this.lastServerTime = 0;

        this.init();
    }

    init() {
        console.log('🕐 Session Manager initialized (5-minute timeout)');
        console.log(`📍 API endpoint: ${this.config.statusEndpoint}`);
        console.log(`📍 Logout endpoint: ${this.config.logoutEndpoint}`);
        console.log(`⚠️ Warning at ${this.config.warningThreshold}s remaining`);
        console.log(`🔴 Critical at ${this.config.criticalThreshold}s remaining`);
        console.log(`🔄 Auto-extend at ${this.config.extendBuffer}s remaining`);
        this.createModal();
        this.startMonitoring();
        this.addActivityListeners();
        this.startPing();

        document.addEventListener('visibilitychange', () => {
            if (!document.hidden) {
                console.log('👁️ Tab visible, checking session...');
                this.checkSession();
            }
        });

        setTimeout(() => {
            this.checkSession();
        }, 1000);
    }

    // Reduced ping frequency - only every 2 minutes
    startPing() {
        this.pingTimer = setInterval(() => {
            this.pingSession();
        }, 120000); // 2 minutes - very infrequent to avoid session resets
    }

    async pingSession() {
        try {
            await fetch(this.config.pingEndpoint);
            this.consecutiveFailures = 0;
        } catch (error) {
            // Silently fail
        }
    }

    startMonitoring() {
        this.checkSession();
        this.timer = setInterval(() => {
            this.checkSession();
        }, this.config.checkInterval);
    }

    async checkSession() {
        try {
            console.log(`🔍 Checking session status...`);
            const response = await fetch(this.config.statusEndpoint);

            if (response.status === 401 || response.status === 403) {
                console.log('🔴 Session expired (HTTP 401/403)');
                this.handleSessionExpired();
                return;
            }

            const data = await response.json();

            if (!data.authenticated) {
                console.log('🔴 Session not authenticated');
                this.handleSessionExpired();
                return;
            }

            this.consecutiveFailures = 0;

            const previousRemaining = this.sessionData.remainingSeconds;

            // Store the server time
            this.lastServerTime = Date.now();

            this.sessionData = {
                remainingSeconds: data.remainingSeconds || 0,
                maxInactiveInterval: data.maxInactiveInterval || 300,
                lastAccessTime: data.lastAccessTime,
                status: data.status || 'active',
                username: data.username || null
            };

            // Set local countdown to match server
            this.localCountdown = this.sessionData.remainingSeconds;

            const remaining = this.sessionData.remainingSeconds;

            // Detect if session was reset (remaining time jumped up)
            if (previousRemaining > 0 && remaining > previousRemaining + 10) {
                this.sessionResets++;
                console.log(`🔄 Session reset detected! Previous: ${previousRemaining}s, New: ${remaining}s (Reset #${this.sessionResets})`);
            }

            console.log(`⏱️ Session remaining: ${remaining}s (${Math.floor(remaining / 60)}m ${remaining % 60}s)`);

            // Update the timer display with local countdown
            this.updateSessionTimerDisplay();

            if (remaining <= 0) {
                console.log('🔴 Session expired (0 seconds remaining)');
                this.handleSessionExpired();
                return;
            }

            // Check if we need to show warning
            if (remaining <= this.config.warningThreshold) {
                console.log(`⚠️ Warning threshold reached: ${remaining}s remaining`);
                if (!this.warningShown && !this.isModalOpen) {
                    this.showWarning();
                }

                if (remaining <= this.config.extendBuffer) {
                    console.log(`🔄 Auto-extending session (${remaining}s remaining)`);
                    await this.extendSession();
                }
            } else {
                this.warningShown = false;
                this.criticalShown = false;
            }

            if (this.isModalOpen) {
                this.updateModalCountdown();
            }

            // Start the local countdown timer if not already running
            this.startLocalCountdown();

        } catch (error) {
            this.consecutiveFailures++;
            console.error('❌ Session check failed:', error);

            if (this.consecutiveFailures >= 3) {
                console.log('🔴 Multiple consecutive failures, session likely expired');
                this.handleSessionExpired();
            }
        }
    }

    // Start a local countdown timer for smooth display
    startLocalCountdown() {
        // Clear existing local timer
        if (this.localTimer) {
            clearInterval(this.localTimer);
            this.localTimer = null;
        }

        // Only start if we have a valid remaining time
        if (this.sessionData.remainingSeconds > 0) {
            this.localTimer = setInterval(() => {
                // Decrement local countdown
                if (this.localCountdown > 0) {
                    this.localCountdown--;
                    // Update display with local countdown
                    this.updateSessionTimerDisplay();
                    // Update modal countdown if open
                    if (this.isModalOpen) {
                        this.updateModalCountdown();
                    }
                }
            }, 1000);
            console.log('✅ Local countdown timer started');
        }
    }

    createModal() {
        const existingModal = document.getElementById('sessionWarningModal');
        if (existingModal) {
            existingModal.remove();
        }

        const modalHTML = `
        <div class="modal fade" id="sessionWarningModal" tabindex="-1" data-bs-backdrop="static" data-bs-keyboard="false">
            <div class="modal-dialog modal-dialog-centered">
                <div class="modal-content">
                    <div class="modal-header" style="border-bottom: 1px solid var(--border-color);">
                        <h5 class="modal-title" style="color: var(--ink);">
                            <i class="fas fa-clock text-warning me-2"></i>
                            Session Expiring Soon
                        </h5>
                    </div>
                    <div class="modal-body" style="color: var(--ink);">
                        <div id="sessionWarningContent">
                            <div class="text-center mb-3">
                                <i class="fas fa-hourglass-half" style="font-size: 48px; color: #ffc107;"></i>
                            </div>
                            <p class="text-center mb-3" style="font-size: 16px;">
                                Your session will expire in <strong id="countdownDisplay" style="font-size: 24px; color: #ffc107;">1:00</strong>
                            </p>
                            <p class="text-center text-muted small">
                                Click "Extend Session" to continue working.<br>
                                Otherwise, you will be automatically logged out.
                            </p>
                            <div id="sessionWarningCritical" class="alert alert-danger mt-3 text-center" style="display: none;">
                                <i class="fas fa-exclamation-triangle me-2"></i>
                                <strong>Critical!</strong> Your session is about to expire in <span id="criticalCountdown">15</span> seconds!
                            </div>
                        </div>
                    </div>
                    <div class="modal-footer" style="border-top: 1px solid var(--border-color);">
                        <button type="button" class="btn btn-primary" onclick="window.sessionManager.extendAndDismiss()" style="padding: 10px 30px;">
                            <i class="fas fa-sync-alt me-2"></i>
                            Extend Session
                        </button>
                        <button type="button" class="btn btn-outline-danger" onclick="window.sessionManager.logout()">
                            <i class="fas fa-sign-out-alt me-2"></i>
                            Logout Now
                        </button>
                    </div>
                </div>
            </div>
        </div>
        `;

        document.body.insertAdjacentHTML('beforeend', modalHTML);
        console.log('✅ Session warning modal created');
    }

    showWarning() {
        if (this.isModalOpen) return;

        if (typeof bootstrap === 'undefined') {
            console.warn('Bootstrap not loaded, showing simple alert');
            this.showSimpleWarning();
            return;
        }

        const modalElement = document.getElementById('sessionWarningModal');
        if (!modalElement) {
            this.createModal();
        }

        const modal = new bootstrap.Modal(modalElement, {
            backdrop: 'static',
            keyboard: false
        });

        this.isModalOpen = true;
        this.warningShown = true;

        console.log(`⚠️ Showing session warning modal (${this.sessionData.remainingSeconds}s remaining)`);

        this.updateModalCountdown();

        const criticalEl = document.getElementById('sessionWarningCritical');
        if (this.sessionData.remainingSeconds <= this.config.criticalThreshold) {
            criticalEl.style.display = 'block';
            this.criticalShown = true;
        } else {
            criticalEl.style.display = 'none';
            this.criticalShown = false;
        }

        modal.show();

        if (this.countdownTimer) {
            clearInterval(this.countdownTimer);
        }
        this.countdownTimer = setInterval(() => {
            this.updateModalCountdown();
        }, 1000);
    }

    showSimpleWarning() {
        const remaining = this.sessionData.remainingSeconds;
        const minutes = Math.floor(remaining / 60);
        const seconds = remaining % 60;
        const message = `Your session will expire in ${minutes}m ${seconds}s. Click OK to extend.`;
        if (confirm(message)) {
            this.extendAndDismiss();
        }
    }

    updateModalCountdown() {
        const countdownEl = document.getElementById('countdownDisplay');
        if (!countdownEl) return;

        // Use local countdown for smooth display
        const remaining = Math.max(0, this.localCountdown || this.sessionData.remainingSeconds);
        const minutes = Math.floor(remaining / 60);
        const seconds = Math.floor(remaining % 60);
        countdownEl.textContent = `${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;

        const criticalCountdown = document.getElementById('criticalCountdown');
        if (criticalCountdown) {
            criticalCountdown.textContent = remaining;
        }

        const criticalEl = document.getElementById('sessionWarningCritical');
        if (remaining <= this.config.criticalThreshold) {
            criticalEl.style.display = 'block';
            this.criticalShown = true;
            countdownEl.style.color = '#dc3545';
        } else {
            criticalEl.style.display = 'none';
            this.criticalShown = false;
            countdownEl.style.color = '#ffc107';
        }

        // Update session data remaining seconds to match display
        this.sessionData.remainingSeconds = remaining;

        if (remaining <= 0) {
            this.handleSessionExpired();
        }
    }

    updateSessionTimerDisplay() {
        const display = document.getElementById('sessionTimeDisplay');
        if (!display) return;

        // Use local countdown for smooth display
        const remaining = Math.max(0, this.localCountdown || this.sessionData.remainingSeconds);
        const minutes = Math.floor(remaining / 60);
        const seconds = Math.floor(remaining % 60);
        display.textContent = `${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;

        if (remaining < 15) {
            display.style.color = '#dc3545';
            display.style.fontWeight = 'bold';
        } else if (remaining < 60) {
            display.style.color = '#ffc107';
            display.style.fontWeight = 'bold';
        } else {
            display.style.color = '';
            display.style.fontWeight = '';
        }
    }

    async extendSession() {
        try {
            console.log('🔄 Extending session...');
            const response = await fetch(this.config.extendEndpoint, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                }
            });

            const data = await response.json();

            if (data.success) {
                this.sessionData.remainingSeconds = data.remainingSeconds || 300;
                this.localCountdown = this.sessionData.remainingSeconds;
                this.warningShown = false;
                this.criticalShown = false;
                this.updateSessionTimerDisplay();
                // Restart local timer
                this.startLocalCountdown();
                console.log(`✅ Session extended successfully (${this.sessionData.remainingSeconds}s remaining)`);
                return true;
            } else {
                console.error('❌ Failed to extend session:', data.message);
                return false;
            }
        } catch (error) {
            console.error('❌ Error extending session:', error);
            return false;
        }
    }

    async extendAndDismiss() {
        const success = await this.extendSession();
        if (success) {
            const modal = bootstrap.Modal.getInstance(document.getElementById('sessionWarningModal'));
            if (modal) {
                modal.hide();
            }
            this.isModalOpen = false;
            this.warningShown = false;

            if (this.countdownTimer) {
                clearInterval(this.countdownTimer);
                this.countdownTimer = null;
            }

            this.showToast('✅ Session extended successfully!', 'success');
        } else {
            this.showToast('❌ Failed to extend session. Please log in again.', 'danger');
        }
    }

    logout() {
        console.log('🚪 User initiated logout');
        const modal = bootstrap.Modal.getInstance(document.getElementById('sessionWarningModal'));
        if (modal) {
            modal.hide();
        }
        this.isModalOpen = false;

        if (this.timer) {
            clearInterval(this.timer);
        }
        if (this.countdownTimer) {
            clearInterval(this.countdownTimer);
        }
        if (this.pingTimer) {
            clearInterval(this.pingTimer);
        }
        if (this.localTimer) {
            clearInterval(this.localTimer);
            this.localTimer = null;
        }

        fetch(this.config.logoutEndpoint, { method: 'POST' })
            .finally(() => {
                window.location.href = this.config.logoutEndpoint;
            });
    }

    handleSessionExpired() {
        console.log('🔴 Session expired - logging out');
        this.showToast('⏰ Your session has expired. Please log in again.', 'danger');

        if (this.timer) {
            clearInterval(this.timer);
        }
        if (this.countdownTimer) {
            clearInterval(this.countdownTimer);
        }
        if (this.pingTimer) {
            clearInterval(this.pingTimer);
        }
        if (this.localTimer) {
            clearInterval(this.localTimer);
            this.localTimer = null;
        }

        this.isModalOpen = false;

        setTimeout(() => {
            window.location.href = `${window.CONTEXT_PATH || '/assetIQ-pro'}/login?expired=true`;
        }, 2000);
    }

    addActivityListeners() {
        const activityEvents = ['click', 'keypress', 'mousemove', 'scroll', 'touchstart'];
        let activityTimer;

        activityEvents.forEach(event => {
            document.addEventListener(event, () => {
                clearTimeout(activityTimer);
                activityTimer = setTimeout(() => {
                    this.pingSession();
                    if (!this.warningShown) {
                        this.checkSession();
                    }
                }, 1000);
            });
        });
        console.log('✅ Activity listeners added');
    }

    showToast(message, type = 'info') {
        const toastContainer = document.getElementById('toastContainer') || this.createToastContainer();

        const toast = document.createElement('div');
        toast.className = `toast align-items-center text-white bg-${type} border-0`;
        toast.role = 'alert';
        toast.ariaLive = 'assertive';
        toast.ariaAtomic = 'true';

        toast.innerHTML = `
            <div class="d-flex">
                <div class="toast-body">
                    <i class="fas ${type === 'success' ? 'fa-check-circle' : 'fa-exclamation-circle'} me-2"></i>
                    ${message}
                </div>
                <button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast"></button>
            </div>
        `;

        toastContainer.appendChild(toast);
        const bsToast = new bootstrap.Toast(toast, { delay: 5000 });
        bsToast.show();

        toast.addEventListener('hidden.bs.toast', () => {
            toast.remove();
        });
    }

    createToastContainer() {
        const container = document.createElement('div');
        container.id = 'toastContainer';
        container.style.cssText = `
            position: fixed;
            bottom: 20px;
            right: 20px;
            z-index: 9999;
            min-width: 300px;
        `;
        document.body.appendChild(container);
        return container;
    }
}

// Initialize session manager when page loads
let sessionManager;

// Set the context path globally
window.CONTEXT_PATH = '/assetIQ-pro';

document.addEventListener('DOMContentLoaded', function() {
    const isLoginPage = window.location.pathname.includes('/login');
    const isLogoutPage = window.location.pathname.includes('/logout');

    if (!isLoginPage && !isLogoutPage) {
        console.log('🕐 Initializing Session Manager...');
        console.log(`📍 Current page: ${window.location.pathname}`);
        console.log(`📍 Context path: ${window.CONTEXT_PATH}`);
        sessionManager = new SessionManager({
            contextPath: window.CONTEXT_PATH,
            checkInterval: 5000,      // Check every 5 seconds
            warningThreshold: 60,      // Show warning at 60 seconds remaining
            criticalThreshold: 15,     // Show critical at 15 seconds remaining
            extendBuffer: 20           // Auto-extend at 20 seconds remaining
        });
        window.sessionManager = sessionManager;
    } else {
        console.log('📄 On login/logout page, session manager not initialized');
    }
});

window.sessionManager = sessionManager;