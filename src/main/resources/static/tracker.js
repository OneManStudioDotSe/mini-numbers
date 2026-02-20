/**
 * Mini Numbers Analytics Tracker
 *
 * Usage:
 * <script
 *    async
 *    src="https://analytics.example.com/admin-panel/tracker.js"
 *    data-project-key="your-api-key-here"
 *    data-api-endpoint="https://analytics.example.com/collect">
 * </script>
 *
 * Options:
 * - data-project-key (required): Your project's API key
 * - data-api-endpoint (optional): Custom API endpoint URL
 *   If not specified, defaults to same origin + '/collect'
 **/

(function() {
    // 1. Configuration: Get settings from script tag's data attributes
    const script = document.currentScript;
    const apiKey = script.getAttribute('data-project-key');

    // Configurable endpoint: use data-api-endpoint or default to same origin
    const endpoint = script.getAttribute('data-api-endpoint')
        || window.location.origin + '/collect';

    if (!apiKey) {
        console.error("Mini Numbers Analytics: Missing data-project-key attribute");
        return;
    }

    // 2. Session management (No Cookies)
    // sessionStorage persists for the duration of the page session (tab)
    let sessionId = sessionStorage.getItem('ma_session_id');
    if (!sessionId) {
        // Generate cryptographically secure random session ID
        sessionId = generateSecureSessionId();
        sessionStorage.setItem('ma_session_id', sessionId);
    }

    /**
     * Generate a cryptographically secure random session ID
     * Uses crypto.getRandomValues() for security, falls back to Math.random()
     * for older browsers that don't support the Crypto API
     */
    function generateSecureSessionId() {
        if (window.crypto && window.crypto.getRandomValues) {
            // Modern browsers: use cryptographically secure random
            const array = new Uint8Array(16); // 128 bits of entropy
            window.crypto.getRandomValues(array);
            // Convert to hex string
            return Array.from(array, byte => byte.toString(16).padStart(2, '0')).join('');
        } else {
            // Fallback for older browsers (not cryptographically secure, but better than nothing)
            console.warn('Mini Numbers Analytics: crypto.getRandomValues() not available, using Math.random() fallback');
            return Math.random().toString(36).substring(2, 15) + Math.random().toString(36).substring(2, 15);
        }
    }

    // 3. The Tracking Function
    const track = (type = 'pageview') => {
        const payload = JSON.stringify({
            path: window.location.pathname,
            referrer: document.referrer || null,
            sessionId: sessionId,
            type: type
        });

        // useBeacon is standard for analytics; it's non-blocking and reliable on page unload
        if (navigator.sendBeacon) {
            navigator.sendBeacon(endpoint + "?key=" + apiKey, payload);
        } else {
            // Fallback for very old browsers
            fetch(endpoint, {
                method: 'POST',
                body: payload,
                headers: { 'Content-Type': 'application/json', 'X-Project-Key': apiKey },
                keepalive: true
            });
        }
    };

    // 4. Initial Load
    track('pageview');

    // 5. Heartbeat (Track duration)
    // Sends a ping every 30 seconds
    setInterval(() => {
        track('heartbeat');
    }, 30000);

    // 6. SPA Support (Single Page Applications)
    // Detects URL changes without a full page reload
    let lastPath = window.location.pathname;
    const observer = new MutationObserver(() => {
        if (lastPath !== window.location.pathname) {
            lastPath = window.location.pathname;
            track('pageview');
        }
    });
    observer.observe(document, { subtree: true, childList: true });

})();
