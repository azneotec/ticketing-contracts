#!/usr/bin/env bash
# Proves the compatibility checks bite: each case edits a throw-away copy of this repo and
# checks that `./gradlew check` passes or fails as expected. The repo itself is never modified.
#
# The copy compares against a temporary tag `verify-baseline` on the current HEAD (passed as
# -Pticketing.compatibilityBaselineTag), so this works before the first release and no tag is
# ever pushed.
#
# Prerequisites: a clean working tree (the baseline is HEAD); buf installed.
# Usage:         samples/verify-failure-cases.sh
set -euo pipefail

REPO="$(cd "$(dirname "$0")/.." && pwd)"
WORK="$(mktemp -d)"
COPY="$WORK/contracts"
trap 'rm -rf "$WORK"' EXIT

if [ -n "$(git -C "$REPO" status --porcelain)" ]; then
  echo "Commit or stash your changes first: the baseline is HEAD." >&2
  exit 1
fi

rsync -a --exclude build --exclude .gradle --exclude .kotlin "$REPO/" "$COPY/"
git -C "$COPY" tag verify-baseline

PROTO="$COPY/proto/ticketing/catalogue/v1/catalogue_service.proto"
AVSC="$COPY/avro/catalogue/v1/EventPublished.avsc"

reset_copy() { git -C "$COPY" checkout -q -- . && git -C "$COPY" clean -fdq -e build -e .gradle; }

run_check() { # writes the build log to $WORK/log; returns Gradle's exit code
  "$COPY/gradlew" -p "$COPY" check --console=plain \
    -Pticketing.compatibilityBaselineTag=verify-baseline >"$WORK/log" 2>&1
}

add_avro_field() { # $1 = JSON of the new field; inserted before "status"
  python3 -I - "$AVSC" "$1" <<'PY'
import json, sys
path, field = sys.argv[1], json.loads(sys.argv[2])
schema = json.load(open(path))
schema["fields"].insert(len(schema["fields"]) - 1, field)
json.dump(schema, open(path, "w"), indent=2)
PY
}

PASS=0
FAIL=0
expect_success() { # $1 = case name
  if run_check; then
    echo "OK    $1: check passed"
    PASS=$((PASS + 1))
  else
    echo "FAIL  $1: check failed, but it should pass. Last lines:"
    tail -25 "$WORK/log"
    FAIL=$((FAIL + 1))
  fi
}
expect_failure() { # $1 = case name, $2 = text that must appear in the log
  if run_check; then
    echo "FAIL  $1: check passed, but it should have failed"
    FAIL=$((FAIL + 1))
  elif grep -q -- "$2" "$WORK/log"; then
    echo "OK    $1: check failed with \"$2\""
    PASS=$((PASS + 1))
  else
    echo "FAIL  $1: check failed, but without \"$2\". Last lines:"
    tail -25 "$WORK/log"
    FAIL=$((FAIL + 1))
  fi
}

# 0. Baseline: the untouched copy passes.
expect_success "baseline"

# 1. Remove a proto field without reserving its number -> buf breaking.
reset_copy
sed -i.bak '/string city = 3;/d' "$PROTO" && rm "$PROTO.bak"
expect_failure "proto-field-removed" 'Previously present field "3" with name "city" on message "VenueSummary" was deleted'

# 2. Remove it WITH `reserved` -> still fails: FILE rules also protect generated Java code
#    (getCity() disappears). Reserving only makes the removal wire-safe (WIRE rules).
reset_copy
sed -i.bak 's/  string city = 3;/  reserved 3;\n  reserved "city";/' "$PROTO" && rm "$PROTO.bak"
expect_failure "proto-field-removed-with-reserved" 'was deleted'

# 3. Change a proto field's type -> buf breaking.
reset_copy
sed -i.bak 's/  string city = 3;/  int32 city = 3;/' "$PROTO" && rm "$PROTO.bak"
expect_failure "proto-field-type-changed" 'changed type from "string" to "int32"'

# 4. Add an Avro field without a default -> not BACKWARD compatible.
reset_copy
add_avro_field '{"name": "organiserId", "type": "string", "doc": "Id of the organiser."}'
expect_failure "avro-field-without-default" "new field EventPublished.organiserId needs a default"

# 5. Add an Avro field with a default -> compatible.
reset_copy
add_avro_field '{"name": "organiserId", "type": ["null", "string"], "default": null, "doc": "Id of the organiser, if known."}'
expect_success "avro-field-with-default"

# 6. Versioning: a release tag on a clean tree gives the release version, but uncommitted work on
#    that same tag must give a -SNAPSHOT (0.1.1), so a dirty build never overwrites a release in
#    mavenLocal. Uses a fake tag v9.9.9 on the copy's HEAD.
reset_copy
git -C "$COPY" tag v9.9.9
project_version() { "$COPY/gradlew" -p "$COPY" -q properties | sed -n 's/^version: //p'; }
expect_version() { # $1 = case name, $2 = expected version
  local actual
  actual="$(project_version)"
  if [ "$actual" = "$2" ]; then
    echo "OK    $1: version is $2"
    PASS=$((PASS + 1))
  else
    echo "FAIL  $1: expected version $2, got '$actual'"
    FAIL=$((FAIL + 1))
  fi
}
expect_version "version-clean-on-tag" "9.9.9"
echo "uncommitted" >"$COPY/dirty.txt"
expect_version "version-dirty-on-tag" "9.9.10-SNAPSHOT"

echo
echo "$PASS passed, $FAIL failed"
[ "$FAIL" -eq 0 ]
