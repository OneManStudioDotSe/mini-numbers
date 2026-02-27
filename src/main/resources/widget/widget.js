/** Mini Numbers Analytics Widget — https://github.com/user/mini-numbers */
(function() {
    var s = document.currentScript;
    if (!s) return;

    var apiKey   = s.getAttribute('data-project-key');
    var widget   = s.getAttribute('data-widget');
    var theme    = s.getAttribute('data-theme') || 'light';
    var target   = s.getAttribute('data-target');
    var baseUrl  = s.getAttribute('data-api-endpoint') || s.src.replace(/\/widget\/widget\.js.*$/, '');

    if (!apiKey || !widget) return;

    // ── Inject scoped CSS once ──────────────────────────────────────────
    if (!document.getElementById('mn-widget-styles')) {
        var style = document.createElement('style');
        style.id = 'mn-widget-styles';
        style.textContent =
            '.mn-widget{font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif;' +
            'display:inline-flex;align-items:center;gap:8px;padding:8px 14px;border-radius:8px;font-size:14px;line-height:1.4;}' +
            '.mn-theme-light{background:#f8f9fa;color:#1a1a2e;border:1px solid #e2e8f0;}' +
            '.mn-theme-dark{background:#1e1e2e;color:#e2e8f0;border:1px solid #333;}' +

            /* Realtime */
            '.mn-dot{width:8px;height:8px;border-radius:50%;flex-shrink:0;}' +
            '.mn-dot--live{background:#22c55e;animation:mn-pulse 2s infinite;}' +
            '.mn-dot--offline{background:#94a3b8;}' +
            '@keyframes mn-pulse{0%,100%{opacity:1;box-shadow:0 0 0 0 rgba(34,197,94,.4);}' +
            '50%{opacity:.8;box-shadow:0 0 0 6px rgba(34,197,94,0);}}' +
            '.mn-count{font-weight:700;font-size:16px;}' +
            '.mn-label{opacity:.7;font-size:13px;}' +

            /* Pageviews */
            '.mn-views-count{font-weight:700;font-size:18px;}' +
            '.mn-views-label{opacity:.7;font-size:12px;}' +

            /* Top pages */
            '.mn-toppages{font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif;' +
            'padding:12px 16px;border-radius:8px;font-size:14px;min-width:200px;max-width:320px;}' +
            '.mn-toppages-title{font-weight:600;font-size:13px;margin-bottom:8px;opacity:.7;}' +
            '.mn-toppages ol{list-style:none;margin:0;padding:0;}' +
            '.mn-toppages li{display:flex;align-items:center;gap:8px;margin-bottom:6px;}' +
            '.mn-page-name{flex:1;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;font-size:13px;}' +
            '.mn-page-bar{height:4px;border-radius:2px;min-width:2px;transition:width .3s;}' +
            '.mn-theme-light .mn-page-bar{background:#6366f1;}' +
            '.mn-theme-dark .mn-page-bar{background:#818cf8;}' +
            '.mn-page-views{font-size:12px;opacity:.6;flex-shrink:0;min-width:30px;text-align:right;}' +

            /* Sparkline */
            '.mn-sparkline{display:inline-block;vertical-align:middle;}' +
            '.mn-sparkline svg{display:block;}';
        document.head.appendChild(style);
    }

    // ── Resolve container ───────────────────────────────────────────────
    var container;
    if (target) {
        container = document.querySelector(target);
    }
    if (!container) {
        container = document.createElement('div');
        s.parentNode.insertBefore(container, s.nextSibling);
    }

    // ── Fetch helper ────────────────────────────────────────────────────
    function fetchWidget(path, params, cb) {
        var qs = 'key=' + encodeURIComponent(apiKey);
        if (params) {
            for (var k in params) {
                if (params[k] != null) qs += '&' + k + '=' + encodeURIComponent(params[k]);
            }
        }
        var url = baseUrl + '/widget/' + path + '?' + qs;
        fetch(url, { mode: 'cors' })
            .then(function(r) { return r.ok ? r.json() : null; })
            .then(function(d) { if (d) cb(d); })
            .catch(function() {});
    }

    // ── Number formatting ───────────────────────────────────────────────
    var fmt = typeof Intl !== 'undefined' && Intl.NumberFormat
        ? new Intl.NumberFormat() : { format: function(n) { return String(n); } };

    // ── Widget renderers ────────────────────────────────────────────────

    function renderRealtime(data) {
        var active = data.activeVisitors > 0;
        container.className = 'mn-widget mn-theme-' + theme;
        container.innerHTML =
            '<span class="mn-dot ' + (active ? 'mn-dot--live' : 'mn-dot--offline') + '"></span>' +
            '<span class="mn-count">' + fmt.format(data.activeVisitors) + '</span>' +
            '<span class="mn-label">' + (data.activeVisitors === 1 ? 'visitor online' : 'visitors online') + '</span>';
    }

    function renderPageviews(data) {
        var filterLabels = { '24h': 'today', '3d': 'last 3 days', '7d': 'this week', '30d': 'this month', '365d': 'this year' };
        var label = filterLabels[data.filter] || data.filter;
        if (data.scope === 'page' && data.path) label += ' on ' + data.path;
        container.className = 'mn-widget mn-theme-' + theme;
        container.innerHTML =
            '<span class="mn-views-count">' + fmt.format(data.views) + '</span>' +
            '<span class="mn-views-label">views ' + label + '</span>';
    }

    function renderToppages(data) {
        var title = s.getAttribute('data-title') || 'Popular pages';
        var maxViews = data.pages.length ? data.pages[0].views : 1;
        var html = '<div class="mn-toppages-title">' + escHtml(title) + '</div><ol>';
        for (var i = 0; i < data.pages.length; i++) {
            var p = data.pages[i];
            var pct = Math.max(4, Math.round((p.views / maxViews) * 100));
            var name = p.path.length > 40 ? p.path.substring(0, 37) + '...' : p.path;
            html += '<li>' +
                '<span class="mn-page-name" title="' + escAttr(p.path) + '">' + escHtml(name) + '</span>' +
                '<span class="mn-page-bar" style="width:' + pct + 'px"></span>' +
                '<span class="mn-page-views">' + fmt.format(p.views) + '</span>' +
                '</li>';
        }
        html += '</ol>';
        container.className = 'mn-toppages mn-theme-' + theme;
        container.innerHTML = html;
    }

    function renderSparkline(data) {
        var w = parseInt(s.getAttribute('data-width')) || 120;
        var h = parseInt(s.getAttribute('data-height')) || 32;
        var color = s.getAttribute('data-color') || '#6366f1';
        var pts = data.points;
        var max = data.maxValue || 1;
        var pad = 2;

        // Build polyline points
        var coords = [];
        for (var i = 0; i < pts.length; i++) {
            var x = pad + (i / Math.max(pts.length - 1, 1)) * (w - pad * 2);
            var y = h - pad - ((pts[i].views / max) * (h - pad * 2));
            coords.push(x.toFixed(1) + ',' + y.toFixed(1));
        }
        var polyline = coords.join(' ');

        // Area polygon (polyline + bottom corners)
        var area = coords.join(' ') + ' ' +
            (w - pad).toFixed(1) + ',' + (h - pad) + ' ' +
            pad + ',' + (h - pad);

        container.className = 'mn-sparkline';
        container.innerHTML =
            '<svg width="' + w + '" height="' + h + '" viewBox="0 0 ' + w + ' ' + h + '" xmlns="http://www.w3.org/2000/svg">' +
            '<polygon points="' + area + '" fill="' + color + '" fill-opacity="0.15"/>' +
            '<polyline points="' + polyline + '" fill="none" stroke="' + color + '" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>' +
            '</svg>';
    }

    // ── HTML escaping ───────────────────────────────────────────────────
    function escHtml(str) {
        var d = document.createElement('div');
        d.appendChild(document.createTextNode(str));
        return d.innerHTML;
    }
    function escAttr(str) {
        return str.replace(/&/g,'&amp;').replace(/"/g,'&quot;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
    }

    // ── Dispatch ────────────────────────────────────────────────────────
    var renderers = {
        realtime:  { path: 'realtime',  params: {},                                                                render: renderRealtime },
        pageviews: { path: 'pageviews', params: { scope: s.getAttribute('data-scope'), filter: s.getAttribute('data-filter'), path: s.getAttribute('data-path') }, render: renderPageviews },
        toppages:  { path: 'toppages',  params: { filter: s.getAttribute('data-filter'), limit: s.getAttribute('data-limit') }, render: renderToppages },
        sparkline: { path: 'sparkline', params: {},                                                                render: renderSparkline }
    };

    var cfg = renderers[widget];
    if (!cfg) return;

    // Initial fetch
    fetchWidget(cfg.path, cfg.params, cfg.render);

    // Polling for realtime widget
    if (widget === 'realtime') {
        var pollInterval = parseInt(s.getAttribute('data-poll')) || 12000;
        var timer;

        function startPoll() {
            timer = setInterval(function() {
                fetchWidget(cfg.path, cfg.params, cfg.render);
            }, pollInterval);
        }

        startPoll();

        // Pause polling when tab is hidden
        document.addEventListener('visibilitychange', function() {
            if (document.hidden) {
                clearInterval(timer);
            } else {
                fetchWidget(cfg.path, cfg.params, cfg.render);
                startPoll();
            }
        });
    }
})();
