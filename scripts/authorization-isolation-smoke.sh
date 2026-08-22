#!/usr/bin/env bash
set -Eeuo pipefail

API_URL="${1:-${STAGING_API_URL:-}}"
if [[ -z "$API_URL" ]]; then
  echo "Usage: $0 <api-base-url>" >&2
  exit 2
fi
API_URL="${API_URL%/}"

TMP_DIR=$(mktemp -d)
RUN_ID="$(date +%s)-${RANDOM}-${RANDOM}"
PASSWORD="Authorization-smoke-2026"
EMAIL_A="authorization-a-${RUN_ID}@stoxsim.test"
EMAIL_B="authorization-b-${RUN_ID}@stoxsim.test"
TOKEN_A=""
TOKEN_B=""

api_call() {
  local token="$1"
  local method="$2"
  local path="$3"
  local output="$4"
  local json_body="${5:-}"
  local header_output="${output}.headers"
  shift 5 || true
  if [[ "$output" == "/dev/null" ]]; then
    header_output="$TMP_DIR/discarded-response.headers"
  fi

  local args=(
    --silent
    --show-error
    --output "$output"
    --dump-header "$header_output"
    --write-out '%{http_code}'
    --request "$method"
    --header "User-Agent: StoxSim-Authorization-Isolation/1.0"
  )
  if [[ -n "$token" ]]; then
    args+=(--header "Authorization: Bearer ${token}")
  fi
  if [[ -n "$json_body" ]]; then
    args+=(--header "Content-Type: application/json" --data "$json_body")
  fi
  while [[ "$#" -gt 0 ]]; do
    args+=(--header "$1")
    shift
  done

  curl "${args[@]}" "${API_URL}${path}"
}

expect_success() {
  local description="$1"
  local token="$2"
  local method="$3"
  local path="$4"
  local output="$5"
  local json_body="${6:-}"
  if [[ "$#" -ge 6 ]]; then
    shift 6
  else
    set --
  fi
  local status
  status=$(api_call "$token" "$method" "$path" "$output" "$json_body" "$@")
  if [[ ! "$status" =~ ^20[0-4]$ ]]; then
    echo "::error::${description} failed with HTTP ${status}" >&2
    sed -n '/^[Xx]-[Rr]equest-[Ii][Dd]:/p' "${output}.headers" >&2 || true
    jq -c . "$output" 2>/dev/null || head -c 500 "$output" >&2
    echo >&2
    exit 1
  fi
}

expect_success_with_retry() {
  local description="$1"
  local token="$2"
  local method="$3"
  local path="$4"
  local output="$5"
  local json_body="$6"
  shift 6
  local status

  for attempt in 1 2 3 4; do
    status=$(api_call "$token" "$method" "$path" "$output" "$json_body" "$@")
    if [[ "$status" =~ ^20[0-4]$ ]]; then
      return
    fi
    if [[ ! "$status" =~ ^(429|500|502|503|504)$ ]] || [[ "$attempt" -eq 4 ]]; then
      echo "::error::${description} failed with HTTP ${status} after ${attempt} attempt(s)" >&2
      sed -n '/^[Xx]-[Rr]equest-[Ii][Dd]:/p' "${output}.headers" >&2 || true
      jq -c . "$output" 2>/dev/null || head -c 500 "$output" >&2
      echo >&2
      exit 1
    fi
    echo "::warning::${description} returned retryable HTTP ${status} on attempt ${attempt}/4"
    sleep $((attempt * 5))
  done
}

expect_denied() {
  local description="$1"
  local token="$2"
  local method="$3"
  local path="$4"
  local json_body="${5:-}"
  local output="$TMP_DIR/denied-response.json"
  local status
  status=$(api_call "$token" "$method" "$path" "$output" "$json_body")
  if [[ ! "$status" =~ ^(401|403|404)$ ]]; then
    echo "::error::${description} was not denied; received HTTP ${status}" >&2
    jq -c . "$output" 2>/dev/null || head -c 500 "$output" >&2
    echo >&2
    exit 1
  fi
}

delete_account() {
  local label="$1"
  local token="$2"
  if [[ -z "$token" ]]; then
    return
  fi
  local output="$TMP_DIR/delete-${label}.json"
  local body
  local status
  body=$(jq -nc --arg password "$PASSWORD" '{password: $password}')

  for attempt in 1 2 3 4; do
    status=$(api_call "$token" DELETE "/api/v1/auth/me" "$output" "$body") || status="000"
    if [[ "$status" == "204" ]]; then
      echo "Deleted temporary learner ${label}"
      return 0
    fi
    if [[ ! "$status" =~ ^(000|429|500|502|503|504)$ ]] || [[ "$attempt" -eq 4 ]]; then
      echo "::error::Failed to delete temporary learner ${label}; HTTP ${status}" >&2
      sed -n '/^[Xx]-[Rr]equest-[Ii][Dd]:/p' "${output}.headers" >&2 || true
      jq -c . "$output" 2>/dev/null || head -c 500 "$output" >&2 || true
      echo >&2
      return 1
    fi
    echo "::warning::Deleting temporary learner ${label} returned HTTP ${status} on attempt ${attempt}/4"
    sleep $((attempt * 5))
  done
}

cleanup() {
  local original_status=$?
  local cleanup_failed=0
  trap - EXIT
  set +e
  delete_account B "$TOKEN_B" || cleanup_failed=1
  delete_account A "$TOKEN_A" || cleanup_failed=1
  rm -rf "$TMP_DIR"
  if [[ "$original_status" -ne 0 ]]; then
    exit "$original_status"
  fi
  exit "$cleanup_failed"
}
trap cleanup EXIT

register_user() {
  local label="$1"
  local email="$2"
  local output="$3"
  local body
  body=$(jq -nc \
    --arg displayName "Authorization ${label}" \
    --arg email "$email" \
    --arg password "$PASSWORD" \
    '{displayName: $displayName, email: $email, password: $password, termsAccepted: true}')
  expect_success "Register learner ${label}" "" POST "/api/v1/auth/register" "$output" "$body"
  jq -e \
    --arg email "$email" \
    '.accessToken | type == "string" and length > 20' \
    "$output" >/dev/null
  jq -e \
    --arg email "$email" \
    '.user.email == $email and (.user.accounts | length) == 2' \
    "$output" >/dev/null
}

echo "Registering two isolated staging learners"
register_user A "$EMAIL_A" "$TMP_DIR/register-a.json"
TOKEN_A=$(jq -er '.accessToken' "$TMP_DIR/register-a.json")

expect_success "Read US market session" "$TOKEN_A" GET "/api/v1/market/status?exchange=NASDAQ" "$TMP_DIR/market-status.json"
if ! jq -e '.phase == "REGULAR"' "$TMP_DIR/market-status.json" >/dev/null; then
  phase=$(jq -r '.phase // "UNKNOWN"' "$TMP_DIR/market-status.json")
  next_transition=$(jq -r '.nextTransition // "unknown"' "$TMP_DIR/market-status.json")
  echo "::error::Authorization holdings and ledger isolation must run during the regular US session; current phase is ${phase}, next transition is ${next_transition}" >&2
  exit 1
fi

register_user B "$EMAIL_B" "$TMP_DIR/register-b.json"
TOKEN_B=$(jq -er '.accessToken' "$TMP_DIR/register-b.json")

echo "Selecting a shared tradable instrument"
INSTRUMENT=""
MARKET_REGION=""
EXCHANGE=""
SYMBOL=""
for candidate in QQQ DIA IWM GLD TLT XLF XLK XLE XLV EEM SPY; do
  search_file="$TMP_DIR/instruments-${candidate}.json"
  search_status=$(api_call \
    "$TOKEN_A" \
    GET \
    "/api/v1/instruments/search?marketRegion=UNITED_STATES&q=${candidate}" \
    "$search_file" \
    "")
  if [[ "$search_status" != 200 ]]; then
    continue
  fi
  instrument=$(jq -cer \
    --arg candidate "$candidate" \
    'map(select(.tradingSymbol == $candidate and (.instrumentType == "EQUITY" or .instrumentType == "ETF"))) | first' \
    "$search_file" 2>/dev/null) || continue
  region=$(jq -er '.marketRegion' <<<"$instrument")
  exchange=$(jq -er '.exchange' <<<"$instrument")
  symbol=$(jq -er '.tradingSymbol' <<<"$instrument")
  quote_file="$TMP_DIR/quote-${candidate}.json"
  quote_status=$(api_call \
    "$TOKEN_A" \
    GET \
    "/api/v1/instruments/${region}/${exchange}/${symbol}/quote" \
    "$quote_file" \
    "")
  if [[ "$quote_status" != 200 ]] || ! jq -e '.lastPrice | (type == "number" and . > 0)' "$quote_file" >/dev/null; then
    continue
  fi
  received_at=$(jq -er '.receivedAt' "$quote_file")
  received_epoch=$(date -d "$received_at" +%s 2>/dev/null) || continue
  quote_age=$(( $(date +%s) - received_epoch ))
  if (( quote_age < 0 || quote_age > 10 )); then
    continue
  fi
  INSTRUMENT="$instrument"
  MARKET_REGION="$region"
  EXCHANGE="$exchange"
  SYMBOL="$symbol"
  break
done
if [[ -z "$INSTRUMENT" ]]; then
  echo "::error::No fresh US quote was available for the authorization order exercise" >&2
  exit 1
fi
echo "Using ${MARKET_REGION}/${EXCHANGE}/${SYMBOL} with a fresh quote"

create_user_resources() {
  local label="$1"
  local token="$2"
  local watchlist_output="$3"
  local order_output="$4"
  local watchlist_body
  local order_body
  watchlist_body=$(jq -nc \
    --arg marketRegion "$MARKET_REGION" \
    --arg exchange "$EXCHANGE" \
    --arg symbol "$SYMBOL" \
    '{marketRegion: $marketRegion, exchange: $exchange, symbol: $symbol}')
  order_body=$(jq -nc \
    --arg marketRegion "$MARKET_REGION" \
    --arg exchange "$EXCHANGE" \
    --arg symbol "$SYMBOL" \
    '{marketRegion: $marketRegion, exchange: $exchange, symbol: $symbol, side: "BUY", orderType: "MARKET", quantity: 1, limitPrice: null}')

  expect_success_with_retry \
    "Create learner ${label} order" \
    "$token" \
    POST \
    "/api/v1/orders" \
    "$order_output" \
    "$order_body" \
    "Idempotency-Key: authorization-${label}-${RUN_ID}"
  jq -e --arg symbol "$SYMBOL" '.id and .symbol == $symbol and .status == "EXECUTED" and .executedAt' "$order_output" >/dev/null

  expect_success \
    "Create learner ${label} watchlist item" \
    "$token" \
    POST \
    "/api/v1/watchlists/default/items" \
    "$watchlist_output" \
    "$watchlist_body"
  jq -e --arg symbol "$SYMBOL" 'any(.items[]; .symbol == $symbol)' "$watchlist_output" >/dev/null
}

echo "Creating separate resources for both learners"
create_user_resources A "$TOKEN_A" "$TMP_DIR/watchlist-a.json" "$TMP_DIR/order-a.json"
create_user_resources B "$TOKEN_B" "$TMP_DIR/watchlist-b.json" "$TMP_DIR/order-b.json"

ORDER_A=$(jq -er '.id' "$TMP_DIR/order-a.json")
ORDER_B=$(jq -er '.id' "$TMP_DIR/order-b.json")
WATCHLIST_ITEM_A=$(jq -er --arg symbol "$SYMBOL" '.items[] | select(.symbol == $symbol) | .itemId' "$TMP_DIR/watchlist-a.json")
WATCHLIST_ITEM_B=$(jq -er --arg symbol "$SYMBOL" '.items[] | select(.symbol == $symbol) | .itemId' "$TMP_DIR/watchlist-b.json")

expect_success "List learner A sessions" "$TOKEN_A" GET "/api/v1/auth/sessions" "$TMP_DIR/sessions-a.json"
expect_success "List learner B sessions" "$TOKEN_B" GET "/api/v1/auth/sessions" "$TMP_DIR/sessions-b.json"
SESSION_A=$(jq -er '.[0].id' "$TMP_DIR/sessions-a.json")
SESSION_B=$(jq -er '.[0].id' "$TMP_DIR/sessions-b.json")

MODIFY_BODY='{"quantity":2,"limitPrice":null}'
echo "Proving cross-user resource access is denied"
expect_denied "Learner B read learner A order" "$TOKEN_B" GET "/api/v1/orders/${ORDER_A}"
expect_denied "Learner B modify learner A order" "$TOKEN_B" PUT "/api/v1/orders/${ORDER_A}" "$MODIFY_BODY"
expect_denied "Learner B cancel learner A order" "$TOKEN_B" DELETE "/api/v1/orders/${ORDER_A}"
expect_denied "Learner B delete learner A watchlist item" "$TOKEN_B" DELETE "/api/v1/watchlists/default/items/${WATCHLIST_ITEM_A}"
expect_denied "Learner B revoke learner A session" "$TOKEN_B" DELETE "/api/v1/auth/sessions/${SESSION_A}"

expect_denied "Learner A read learner B order" "$TOKEN_A" GET "/api/v1/orders/${ORDER_B}"
expect_denied "Learner A modify learner B order" "$TOKEN_A" PUT "/api/v1/orders/${ORDER_B}" "$MODIFY_BODY"
expect_denied "Learner A cancel learner B order" "$TOKEN_A" DELETE "/api/v1/orders/${ORDER_B}"
expect_denied "Learner A delete learner B watchlist item" "$TOKEN_A" DELETE "/api/v1/watchlists/default/items/${WATCHLIST_ITEM_B}"
expect_denied "Learner A revoke learner B session" "$TOKEN_A" DELETE "/api/v1/auth/sessions/${SESSION_B}"

collect_user_view() {
  local label="$1"
  local token="$2"
  expect_success "Read learner ${label} profile" "$token" GET "/api/v1/auth/me" "$TMP_DIR/me-${label}.json"
  expect_success "Read learner ${label} orders" "$token" GET "/api/v1/orders?marketRegion=${MARKET_REGION}" "$TMP_DIR/orders-${label}.json"
  expect_success "Read learner ${label} holdings" "$token" GET "/api/v1/holdings?marketRegion=${MARKET_REGION}" "$TMP_DIR/holdings-${label}.json"
  expect_success "Read learner ${label} portfolio" "$token" GET "/api/v1/portfolio?marketRegion=${MARKET_REGION}" "$TMP_DIR/portfolio-${label}.json"
  expect_success "Read learner ${label} ledger" "$token" GET "/api/v1/account/ledger?marketRegion=${MARKET_REGION}" "$TMP_DIR/ledger-${label}.json"
  expect_success "Read learner ${label} events" "$token" GET "/api/v1/auth/events" "$TMP_DIR/events-${label}.json"
  expect_success "Export learner ${label} account" "$token" POST "/api/v1/auth/me/export" "$TMP_DIR/export-${label}.json"
}

echo "Comparing user-scoped portfolio, ledger, event and export views"
collect_user_view a "$TOKEN_A"
collect_user_view b "$TOKEN_B"

jq -e --arg email "$EMAIL_A" '.email == $email' "$TMP_DIR/me-a.json" >/dev/null
jq -e --arg email "$EMAIL_B" '.email == $email' "$TMP_DIR/me-b.json" >/dev/null
jq -e --arg id "$ORDER_A" 'any(.[]; .id == $id)' "$TMP_DIR/orders-a.json" >/dev/null
jq -e --arg id "$ORDER_B" 'any(.[]; .id == $id)' "$TMP_DIR/orders-b.json" >/dev/null
jq -e 'length > 0 and all(.[]; .id and .quantity > 0)' "$TMP_DIR/holdings-a.json" >/dev/null
jq -e 'length > 0 and all(.[]; .id and .quantity > 0)' "$TMP_DIR/holdings-b.json" >/dev/null
jq -e '.holdings | length > 0 and all(.[]; .holdingId)' "$TMP_DIR/portfolio-a.json" >/dev/null
jq -e '.holdings | length > 0 and all(.[]; .holdingId)' "$TMP_DIR/portfolio-b.json" >/dev/null
jq -e 'length > 0 and all(.[]; .id)' "$TMP_DIR/ledger-a.json" >/dev/null
jq -e 'length > 0 and all(.[]; .id)' "$TMP_DIR/ledger-b.json" >/dev/null

USER_A_IDS="$TMP_DIR/user-a-ids.txt"
{
  jq -r '.user.accounts[].id' "$TMP_DIR/register-a.json"
  printf '%s\n' "$ORDER_A" "$WATCHLIST_ITEM_A" "$SESSION_A"
  jq -r '.[].id' "$TMP_DIR/holdings-a.json" "$TMP_DIR/ledger-a.json" "$TMP_DIR/events-a.json"
  jq -r '.holdings[].holdingId' "$TMP_DIR/portfolio-a.json"
} | sed '/^null$/d; /^$/d' | sort -u > "$USER_A_IDS"

USER_B_IDS="$TMP_DIR/user-b-ids.txt"
{
  jq -r '.user.accounts[].id' "$TMP_DIR/register-b.json"
  printf '%s\n' "$ORDER_B" "$WATCHLIST_ITEM_B" "$SESSION_B"
  jq -r '.[].id' "$TMP_DIR/holdings-b.json" "$TMP_DIR/ledger-b.json" "$TMP_DIR/events-b.json"
  jq -r '.holdings[].holdingId' "$TMP_DIR/portfolio-b.json"
} | sed '/^null$/d; /^$/d' | sort -u > "$USER_B_IDS"

if comm -12 "$USER_A_IDS" "$USER_B_IDS" | grep -q .; then
  echo "::error::Learner A and learner B share a user-owned resource identifier" >&2
  exit 1
fi

cat \
  "$TMP_DIR/me-b.json" \
  "$TMP_DIR/orders-b.json" \
  "$TMP_DIR/holdings-b.json" \
  "$TMP_DIR/portfolio-b.json" \
  "$TMP_DIR/ledger-b.json" \
  "$TMP_DIR/events-b.json" \
  "$TMP_DIR/export-b.json" \
  > "$TMP_DIR/all-b-data.json"
while IFS= read -r identifier; do
  if grep -Fq "$identifier" "$TMP_DIR/all-b-data.json"; then
    echo "::error::Learner B response exposed learner A resource ${identifier}" >&2
    exit 1
  fi
done < "$USER_A_IDS"
if grep -Fq "$EMAIL_A" "$TMP_DIR/all-b-data.json"; then
  echo "::error::Learner B response exposed learner A email" >&2
  exit 1
fi

cat \
  "$TMP_DIR/me-a.json" \
  "$TMP_DIR/orders-a.json" \
  "$TMP_DIR/holdings-a.json" \
  "$TMP_DIR/portfolio-a.json" \
  "$TMP_DIR/ledger-a.json" \
  "$TMP_DIR/events-a.json" \
  "$TMP_DIR/export-a.json" \
  > "$TMP_DIR/all-a-data.json"
while IFS= read -r identifier; do
  if grep -Fq "$identifier" "$TMP_DIR/all-a-data.json"; then
    echo "::error::Learner A response exposed learner B resource ${identifier}" >&2
    exit 1
  fi
done < "$USER_B_IDS"
if grep -Fq "$EMAIL_B" "$TMP_DIR/all-a-data.json"; then
  echo "::error::Learner A response exposed learner B email" >&2
  exit 1
fi

echo "Two-user authorization isolation passed for orders, sessions, watchlists, holdings, portfolios, ledgers, events and exports"
