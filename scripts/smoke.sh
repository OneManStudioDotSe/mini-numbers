#!/usr/bin/env bash
# Mini Numbers smoke test: proves a running server collects, stores and serves data, in about 30 seconds.
#
#   BASE_URL=https://stats.example.com PROJECT_KEY=<key of a "Smoke test" project> ./scripts/smoke.sh
#
# Optional: ADMIN_USER (default admin). The password is asked for and never passed as an argument; for
# unattended runs it can come from SMOKE_PASSWORD in the environment.
# Needs only bash and curl. Exits 0 when every check passes, 1 otherwise.
#
# What it leaves behind: one page view at /smoke/<time> in the project whose key you pass (use a dedicated
# project so your real numbers stay clean), and one refresh token for ADMIN_USER that expires in 7 days.
set -u

BASE_URL="${BASE_URL:?set BASE_URL, e.g. https://stats.example.com}"
BASE_URL="${BASE_URL%/}"
PROJECT_KEY="${PROJECT_KEY:?set PROJECT_KEY to the key of a dedicated smoke-test project}"
ADMIN_USER="${ADMIN_USER:-admin}"
UA='Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36 mini-numbers-smoke'

failures=0
pass() { printf '  \033[32mPASS\033[0m  %s\n' "$1"; }
fail() { printf '  \033[31mFAIL\033[0m  %s\n' "$1"; failures=$((failures + 1)); }
skip() { printf '  \033[33mSKIP\033[0m  %s\n' "$1"; }
json_escape() { local s=${1//\\/\\\\}; printf '%s' "${s//\"/\\\"}"; }

if [ -z "${SMOKE_PASSWORD:-}" ]; then
  printf 'Password for %s: ' "$ADMIN_USER" >&2
  IFS= read -rs SMOKE_PASSWORD; echo >&2
fi

echo "Mini Numbers smoke test against $BASE_URL"

# 1. The server and its database are up
health=$(curl -fsS --max-time 10 "$BASE_URL/health" 2>&1)
if printf '%s' "$health" | grep -q '"status":"healthy"'; then
  pass "health: $(printf '%s' "$health" | grep -o '"version":"[^"]*"')"
else
  fail "health is not healthy: ${health:0:200}"
fi

# 2. HTTPS: plain HTTP redirects, and the security headers are sent
case "$BASE_URL" in
  https://*)
    code=$(curl -sS -o /dev/null -w '%{http_code}' --max-time 10 "http://${BASE_URL#https://}/health")
    case "$code" in 301|302|307|308) pass "http:// redirects to https:// ($code)";; *) fail "http:// answered $code instead of redirecting";; esac
    headers=$(curl -sSI --max-time 10 "$BASE_URL/login")
    printf '%s' "$headers" | grep -qi '^strict-transport-security:' && pass "HSTS header present" || fail "no Strict-Transport-Security header (is KTOR_DEVELOPMENT=true?)"
    printf '%s' "$headers" | grep -qi '^content-security-policy:' && pass "CSP header present" || fail "no Content-Security-Policy header"
    ;;
  *) skip "HTTPS checks (BASE_URL is not https://)";;
esac

# 3. The tracker script is served
ctype=$(curl -fsS -o /dev/null -w '%{content_type}' --max-time 10 "$BASE_URL/tracker/tracker.min.js")
case "$ctype" in *javascript*) pass "tracker.min.js served ($ctype)";; *) fail "tracker.min.js not served as JavaScript (got '${ctype:-nothing}')";; esac

# 4. A page view is accepted
marker="/smoke/$(date +%s)"
code=$(curl -sS -o /dev/null -w '%{http_code}' --max-time 10 -X POST "$BASE_URL/collect" \
  -H "X-Project-Key: $PROJECT_KEY" -H "User-Agent: $UA" -H 'Content-Type: application/json' \
  -d "{\"path\":\"$marker\",\"sessionId\":\"smoke-$(date +%s)\",\"type\":\"pageview\"}")
[ "$code" = "202" ] && pass "collect accepted a page view at $marker" || fail "collect answered $code (expected 202; wrong PROJECT_KEY?)"

# 5. Wrong credentials are refused (a made-up user, so the real account never gets locked out)
code=$(curl -sS -o /dev/null -w '%{http_code}' --max-time 10 -X POST "$BASE_URL/api/token" \
  -H 'Content-Type: application/json' -d "{\"username\":\"smoke-nobody-$(date +%s)\",\"password\":\"not-the-password\"}")
[ "$code" = "401" ] && pass "wrong credentials refused (401)" || fail "wrong credentials answered $code (expected 401)"

# 6. The admin can sign in to the API
token_json=$(curl -sS --max-time 15 -X POST "$BASE_URL/api/token" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$(json_escape "$ADMIN_USER")\",\"password\":\"$(json_escape "$SMOKE_PASSWORD")\"}")
token=$(printf '%s' "$token_json" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)
if [ -n "$token" ]; then pass "API sign-in as $ADMIN_USER"; else fail "API sign-in failed: ${token_json:0:200}"; fi

if [ -n "$token" ]; then
  auth="Authorization: Bearer $token"

  # 7. The page view from step 4 is stored and readable
  project_id=$(curl -sS --max-time 10 -H "$auth" "$BASE_URL/api/projects" | tr '}' '\n' \
    | grep "\"apiKey\":\"$PROJECT_KEY\"" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
  if [ -z "$project_id" ]; then
    fail "no project has the key PROJECT_KEY"
  else
    found=""
    for _ in 1 2 3 4 5; do
      if curl -sS --max-time 10 -H "$auth" "$BASE_URL/api/projects/$project_id/events?page=0&limit=50" | grep -q "\"path\":\"$marker\""; then
        found=yes; break
      fi
      sleep 1
    done
    [ -n "$found" ] && pass "the page view is stored and readable" || fail "the page view at $marker never showed up in raw events"
    code=$(curl -sS -o /dev/null -w '%{http_code}' --max-time 15 -H "$auth" "$BASE_URL/api/projects/$project_id/report/comparison?filter=24h")
    [ "$code" = "200" ] && pass "dashboard report answers (24h)" || fail "dashboard report answered $code"
  fi

  # 8. Widgets answer only when their lab is on (they expose stats to anyone holding the public key)
  labs=$(curl -sS --max-time 10 -H "$auth" "$BASE_URL/api/labs")
  code=$(curl -sS -o /dev/null -w '%{http_code}' --max-time 10 "$BASE_URL/widget/realtime?key=$PROJECT_KEY")
  if printf '%s' "$labs" | grep -q '"widgets"'; then
    [ "$code" = "200" ] && pass "widgets lab is on and answers" || fail "widgets lab is on but /widget/realtime answered $code"
  else
    [ "$code" = "404" ] && pass "widgets are off (404)" || fail "widgets lab is off but /widget/realtime answered $code"
  fi
  printf '        labs on: %s\n' "$(printf '%s' "$labs" | grep -o '\[[^]]*\]')"
else
  skip "stored-data, report and widget checks (no API sign-in)"
fi

echo
if [ "$failures" -eq 0 ]; then echo "All checks passed."; exit 0; fi
echo "$failures check(s) failed."; exit 1
