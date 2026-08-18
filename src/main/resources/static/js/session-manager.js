// ============================================================
// SESSION MANAGER - Handles session expiry warnings and auto-logout
// ============================================================

class SessionManager {
    constructor(options = {}) {
        const contextPath = options.contextPath || window.CONTEXT_PATH || '/assetIQ-pro';

        this.config = {
            // Check every 10 seconds
            checkInterval: options.checkInterval || 10000,
            // Show warning at 60 seconds remaining (1 minute)
            warningThreshold: options.warningThreshold || 60,
            // Show critical at 15 seconds remaining
            criticalThreshold: options.criticalThreshold || 15,
            // REMOVED: No auto-extend - user must click the button
            logoutEndpoint: options.logoutEndpoint || `${contextPath}/logout`,
            statusEndpoint: options.statusEndpoint || `${contextPath}/api/session/status`,
            extendEndpoint: options.extendEndpoint || `${contextPath}/api/session/extend`,
            pingEndpoint: options.pingEndpoint || `${contextPath}/api/session/ping`
        };

        this.sessionData = {
            remainingSeconds: 0,
            maxInactiveInterval: 300,
            lastAccessTime: null,
            status: 'active',
            username: null
        };

        this.warningShown = false;
        this.criticalShown = false;
        this.isModalOpen = false;
        this.timer = null;
        this.countdownTimer = null;
        this.pingTimer = null;
        this.consecutiveFailures = 0;
        this.localCountdown = 0;
        this.localTimer = null;

        this.init();
    }

    init() {
        console.log('🕐 Session Manager initialized (5-minute timeout)');
        console.log(`📍 API endpoint: ${this.config.statusEndpoint}`);
        console.log(`⚠️ Warning at ${this.config.warningThreshold}s remaining`);
        console.log(`🔴 Critical at ${this.config.criticalThreshold}s remaining`);
        console.log(`ℹ️ Auto-extend DISABLED - User must click "Extend Session"`);
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

    startPing() {
        this.pingTimer = setInterval(() => {
            this.pingSession();
        }, 120000); // 2 minutes
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

            // Detect session reset
            if (previousRemaining > 0 && remaining > previousRemaining + 10) {
                console.log(`🔄 Session reset detected! Previous: ${previousRemaining}s, New: ${remaining}s`);
                // Reset warning flags on session reset
                this.warningShown = false;
                this.criticalShown = false;
                // Close modal if open
                if (this.isModalOpen) {
                    const modal = bootstrap.Modal.getInstance(document.getElementById('sessionWarningModal'));
                    if (modal) {
                        modal.hide();
                    }
                    this.isModalOpen = false;
                }
            }

            // Log remaining time
            const mins = Math.floor(remaining / 60);
            const secs = remaining % 60;
            console.log(`⏱️ Session: ${mins}m ${secs}s remaining (${remaining}s)`);

            this.updateSessionTimerDisplay();

            if (remaining <= 0) {
                console.log('🔴 Session expired (0 seconds remaining)');
                this.handleSessionExpired();
                return;
            }

            // Check warning threshold (1 minute = 60 seconds)
            if (remaining <= this.config.warningThreshold) {
                if (!this.warningShown && !this.isModalOpen) {
                    console.log(`⚠️⚠️⚠️ WARNING: ${remaining}s remaining - SHOWING MODAL`);
                    this.showWarning();
                }
                // REMOVED: Auto-extend logic - user must click the button
            } else {
                if (remaining > this.config.warningThreshold + 10) {
                    this.warningShown = false;
                    this.criticalShown = false;
                }
            }

            if (this.isModalOpen) {
                this.updateModalCountdown();
            }

            // Start local countdown timer for smooth display
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

    startLocalCountdown() {
        if (this.localTimer) {
            clearInterval(this.localTimer);
            this.localTimer = null;
        }

        if (this.sessionData.remainingSeconds > 0) {
            this.localTimer = setInterval(() => {
                if (this.localCountdown > 0) {
                    this.localCountdown--;
                    this.updateSessionTimerDisplay();
                    if (this.isModalOpen) {
                        this.updateModalCountdown();
                    }
                }
            }, 1000);
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
                                Your session will expire in <strong id="countdownDisplay" style="font-size: 32px; color: #ffc107;">1:00</strong>
                            </p>
                            <p class="text-center text-muted small">
                                Click <strong>"Extend Session"</strong> to continue working.<br>
                                Otherwise, you will be automatically logged out when the timer reaches 0.
                            </p>
                            <div id="sessionWarningCritical" class="alert alert-danger mt-3 text-center" style="display: none;">
                                <i class="fas fa-exclamation-triangle me-2"></i>
                                <strong>Critical!</strong> Your session is about to expire in <span id="criticalCountdown">15</span> seconds!
                                <br>
                                <span class="small">Click "Extend Session" now!</span>
                            </div>
                        </div>
                    </div>
                    <div class="modal-footer" style="border-top: 1px solid var(--border-color);">
                        <button type="button" class="btn btn-primary btn-lg" onclick="window.sessionManager.extendAndDismiss()" style="padding: 12px 40px;">
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

        console.log(`⚠️⚠️⚠️ SHOWING WARNING MODAL at ${this.sessionData.remainingSeconds}s remaining`);

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

        this.sessionData.remainingSeconds = remaining;

        // Check if session expired while modal is open
        if (remaining <= 0) {
            this.handleSessionExpired();
        }
    }

    updateSessionTimerDisplay() {
        const display = document.getElementById('sessionTimeDisplay');
        if (!display) return;

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

        // Close modal if open
        if (this.isModalOpen) {
            const modal = bootstrap.Modal.getInstance(document.getElementById('sessionWarningModal'));
            if (modal) {
                modal.hide();
            }
            this.isModalOpen = false;
        }

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
                    // Only check if warning isn't already shown
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
            checkInterval: 10000,      // Check every 10 seconds
            warningThreshold: 60,       // Show warning at 60 seconds remaining
            criticalThreshold: 15,      // Show critical at 15 seconds remaining
            // No extendBuffer - user must click the button
        });
        window.sessionManager = sessionManager;
    } else {
        console.log('📄 On login/logout page, session manager not initialized');
    }
});

window.sessionManager = sessionManager;