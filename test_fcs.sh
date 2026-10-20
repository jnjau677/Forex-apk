#!/bin/sh
# Smoke-test the FCS API latest-prices endpoint.
#
# The API key is read from the environment — NEVER hard-code keys in scripts
# (a real key was previously committed here and had to be rotated; see REVIEW.md F-01).
#
# Usage: FCS_API_KEY=your_key ./test_fcs.sh [SYMBOL]
set -eu
: "${FCS_API_KEY:?Set FCS_API_KEY in your environment first (see .env.example)}"
SYMBOL="${1:-EUR/USD}"
curl -s "https://fcsapi.com/api-v3/forex/latest?symbol=${SYMBOL}&access_key=${FCS_API_KEY}" | jq .
