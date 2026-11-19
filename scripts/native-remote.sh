#!/usr/bin/env bash
#
# Runs migratory's native-image tests on a Linux build host over ssh.
#
# The committed HEAD is sent as a git bundle (nothing is pushed), checked out on the host, and
# each native app is compiled with GraalVM and tested there, one after another.
#
# Usage: scripts/native-remote.sh [app...]     e.g. scripts/native-remote.sh native-app
#        With no apps, every migratory-native-tests/native-* app runs.
#
# Environment:
#   NATIVE_HOST       required: the ssh host to build on
#   NATIVE_JAVA_HOME  the GraalVM (or Liberica NIK) home on the host; default: the host's JAVA_HOME
#   NATIVE_DIR        the working copy on the host; default: ~/migratory-native
#   NATIVE_MAVEN_ARGS extra Maven arguments, such as -Pspring-boot-latest; default: none
#
# The host needs git, Docker (the tests start PostgreSQL), gcc and zlib headers. Logs stay on the
# host as $NATIVE_DIR/<app>.log. Exits non-zero when any app fails.

set -euo pipefail

: "${NATIVE_HOST:?set NATIVE_HOST to the ssh host to build on}"
NATIVE_DIR="${NATIVE_DIR:-~/migratory-native}"
NATIVE_JAVA_HOME="${NATIVE_JAVA_HOME:-}"
NATIVE_MAVEN_ARGS="${NATIVE_MAVEN_ARGS:-}"

cd "$(git rev-parse --show-toplevel)"

if [[ $# -gt 0 ]]; then
  apps=("$@")
else
  apps=()
  for dir in migratory-native-tests/native-*/; do
    app="$(basename "$dir")"
    apps+=("$app")
  done
fi
for app in "${apps[@]}"; do
  [[ -d "migratory-native-tests/$app" ]] || { echo "no such app: $app" >&2; exit 2; }
done

if [[ -n "$(git status --porcelain --untracked-files=no)" ]]; then
  echo "warning: uncommitted changes are not sent; the host builds $(git rev-parse --short HEAD)" >&2
fi

bundle="$(mktemp -t migratory-native.XXXXXX)"
trap 'rm -f "$bundle"' EXIT
git bundle create --quiet "$bundle" HEAD
scp -q "$bundle" "$NATIVE_HOST:migratory-native.bundle"

echo "building $(git rev-parse --short HEAD) on the build host: ${apps[*]}"

# The remote script takes its settings as arguments, so nothing local is expanded inside it.
# ssh joins its arguments into one command line, which would drop an empty one: quote each.
ssh -o ServerAliveInterval=60 "$NATIVE_HOST" bash -s -- \
  "$(printf '%q ' "$NATIVE_DIR" "$NATIVE_JAVA_HOME" "$NATIVE_MAVEN_ARGS" "${apps[@]}")" <<'REMOTE'
set -uo pipefail
dir="${1/#\~/$HOME}"; java_home="${2/#\~/$HOME}"; read -r -a maven_args <<<"$3"; shift 3
if [[ -n "$java_home" ]]; then
  export JAVA_HOME="$java_home"
  export PATH="$JAVA_HOME/bin:$PATH"
fi
[[ -n "${JAVA_HOME:-}" ]] || { echo "JAVA_HOME is not set on the host; set NATIVE_JAVA_HOME" >&2; exit 2; }
if [[ ! -d "$dir/.git" ]]; then
  git clone --quiet ~/migratory-native.bundle "$dir"
fi
cd "$dir"
git fetch --quiet ~/migratory-native.bundle HEAD
git checkout --quiet --force --detach FETCH_HEAD
# a fresh tree each run: build output left from another commit (a deleted aot.factories, say)
# would otherwise end up in the image
git clean -fdxq -e '/*.log'
failed=0
# Maven gets /dev/null as its input: this script arrives on stdin, and a command reading stdin
# would swallow the rest of it, the final exit status among it.
for app in "$@"; do
  start=$SECONDS
  if ./mvnw -B "${maven_args[@]}" -Pnative-tests -Dnative verify -pl "migratory-native-tests/$app" -am \
      -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false < /dev/null > "$app.log" 2>&1; then
    result=passed
  else
    result=FAILED
    failed=1
  fi
  tests="$(grep -E 'Tests run:.*Failures' "$app.log" | tail -1 | sed 's/^\[[A-Z]*\] *//')"
  echo "$app: $result in $((SECONDS - start))s ${tests:+($tests)}"
done
exit $failed
REMOTE
