# Mini Numbers - Performance benchmarking

## Backend benchmarking

### Tools
- **wrk** - HTTP benchmarking tool
- **Apache Benchmark (ab)** - Simple load testing

### Data collection endpoint (`POST /collect`)

```bash
# Basic throughput test (4 threads, 100 connections, 30 seconds)
wrk -t4 -c100 -d30s --script=post.lua http://localhost:8080/collect

# post.lua script:
wrk.method = "POST"
wrk.headers["Content-Type"] = "application/json"
wrk.headers["X-Project-Key"] = "your-api-key"
wrk.body = '{"path":"/test","sessionId":"abc123","eventType":"pageview"}'
```

### Analytics report endpoint (`GET /admin/projects/{id}/report`)

```bash
# With session cookie authentication
ab -n 1000 -c 10 -C "mini_numbers_session=your-session-cookie" \
  http://localhost:8080/admin/projects/{id}/report?filter=7d
```

### Expected metrics

| Endpoint | Target | Notes |
|----------|--------|-------|
| `POST /collect` | > 5000 req/s | Simple insert with caching |
| `GET /report` (cached) | > 2000 req/s | 30s Caffeine cache |
| `GET /report` (uncached) | > 100 req/s | Database aggregation |
| `GET /health` | > 10000 req/s | No database access |

---

## Frontend performance

### Lighthouse CI

Run Lighthouse audits via Chrome DevTools or CI:

```bash
npx lighthouse http://localhost:8080/admin-panel \
  --output=json --output-path=./lighthouse-report.json
```

### Key metrics

| Metric | Target | Description |
|--------|--------|-------------|
| FCP (First Contentful Paint) | < 1.5s | Time to first visible content |
| LCP (Largest Contentful Paint) | < 2.5s | Time to largest content element |
| TBT (Total Blocking Time) | < 300ms | Time main thread is blocked |
| CLS (Cumulative Layout Shift) | < 0.1 | Visual stability score |

### Current architecture

**External Libraries (loaded synchronously):**
- Chart.js 4.4.0 (~200KB)
- chartjs-chart-geo (~50KB)
- chartjs-chart-matrix (~20KB)
- Feather Icons (~60KB)
- Leaflet 1.9.4 (~150KB)

**Optimization Opportunities:**
1. Add `defer` attribute to non-critical scripts (Leaflet, chartjs-geo)
2. Lazy-load map library only when map view is activated
3. Use CDN with proper cache headers for external libraries

### Current optimizations

- `Promise.allSettled()` for parallel secondary data loading
- Debounced time filter changes (300ms)
- Frontend cache (`Utils.cache` with 5-minute TTL)
- Loading skeleton screens for perceived performance

---

## Database performance

### SQLite

- **Best for**: < 100K events, single instance
- **Limitations**: Single writer, file-based locking
- **Optimization**: WAL mode enabled automatically

### PostgreSQL

- **Best for**: > 100K events, concurrent access, production
- **Connection pooling**: HikariCP with configurable pool size

### Indexes (8 composite indexes)

```
idx_events_timestamp           — timestamp
idx_events_project_timestamp   — projectId + timestamp
idx_events_project_session     — projectId + sessionId
idx_events_project_eventname   — projectId + eventName
idx_events_project_visitor     — projectId + visitorHash
idx_events_project_path        — projectId + path
idx_events_project_type_ts     — projectId + eventType + timestamp
idx_events_project_country     — projectId + country
idx_events_project_browser     — projectId + browser
```

### Query performance tips

- Reports use `filter` parameter to limit time range (reduces scan size)
- Contribution calendar queries 365 days with GROUP BY date
- Live feed queries last 5 minutes only

---

## Caching architecture

### Backend (Caffeine)

| Cache | Size | TTL | Invalidation |
|-------|------|-----|-------------|
| Query results | 500 entries | 30 seconds | On data write |
| GeoIP lookups | 10,000 entries | 1 hour | TTL-based |
| Rate limit counters | Unbounded | 1 minute | TTL-based |

### Frontend (Utils.cache)

| Cache | TTL | Scope |
|-------|-----|-------|
| API responses | 5 minutes | Per URL |
| Chart preferences | Permanent | localStorage |
| Theme preference | Permanent | localStorage |

---

## Optimization recommendations

### High impact
1. **PostgreSQL for production** — SQLite single-writer lock limits write throughput
2. **CDN for static assets** — External libraries total ~480KB, benefit from edge caching
3. **HTTP/2** — Multiplexing reduces connection overhead for parallel API calls

### Medium impact
4. **Lazy-load Leaflet** — Only load ~150KB map library when user toggles to map view
5. **Database partitioning** — For > 1M events, partition by month
6. **Response compression** — Enable gzip/brotli for API responses

### Low impact (already implemented)
7. Query caching with auto-invalidation
8. GeoIP result caching
9. Database indexes on all query patterns
10. Parallel API calls with `Promise.allSettled()`
11. Debounced filter changes
12. Loading skeleton screens
