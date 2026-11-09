#!/usr/bin/env bash
set -euo pipefail

root_directory="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
artifact="${DUNGEONS_ARTIFACT:?Missing Dungeons artifact}"
acceptance_classpath="$(cat "${DUNGEONS_ACCEPTANCE_CLASSPATH:?Missing classpath file}")"
paper_version="${DUNGEONS_PAPER_VERSION:?Missing Paper runtime version}"
paper_build="${DUNGEONS_PAPER_BUILD:?Missing Paper runtime build}"
paper_sha256="${DUNGEONS_PAPER_SHA256:?Missing Paper runtime checksum}"
work_directory="${DUNGEONS_ACCEPTANCE_WORK_DIRECTORY:-$(mktemp -d)}"
keep_work_directory="${DUNGEONS_ACCEPTANCE_KEEP_WORK_DIRECTORY:-false}"
paper_pid=""
paper_input_fd=""
paper_log="$work_directory/paper/paper.log"

fail() { echo "Dungeons acceptance failure: $*" >&2; exit 1; }
wait_for_log() {
  local expected=$1 deadline=$((SECONDS + 300))
  while ((SECONDS < deadline)); do
    if grep -Eq 'DUNGEONS_ACCEPTANCE_FAIL|Exception in thread|Error occurred while enabling|Could not load plugin' "$paper_log" 2>/dev/null; then
      fail "Paper reported a plugin failure while waiting for $expected"
    fi
    grep -Eq -- "$expected" "$paper_log" 2>/dev/null && return
    kill -0 "$paper_pid" 2>/dev/null || fail "Paper exited while waiting for $expected"
    sleep 1
  done
  fail "Timed out waiting for $expected"
}
start_server() {
  paper_log="$work_directory/paper/$1"
  (cd "$work_directory/paper" && exec "${JAVA_HOME:?Set JAVA_HOME to JDK 25}/bin/java" -Xms512M -Xmx1G -jar paper.jar --nogui <console.in >"$paper_log" 2>&1) &
  paper_pid=$!
  exec {paper_input_fd}>"$work_directory/paper/console.in"
  wait_for_log 'Done \('
}
stop_server() {
  if [[ -n "$paper_input_fd" ]]; then
    printf 'stop\n' >&"$paper_input_fd" || true
    exec {paper_input_fd}>&-
    paper_input_fd=""
  fi
  if [[ -n "$paper_pid" ]]; then
    local deadline=$((SECONDS + 30)) result=0
    while kill -0 "$paper_pid" 2>/dev/null && ((SECONDS < deadline)); do sleep 1; done
    if kill -0 "$paper_pid" 2>/dev/null; then
      kill "$paper_pid" 2>/dev/null || true
      result=1
    fi
    wait "$paper_pid" || result=1
    paper_pid=""
    return "$result"
  fi
}
check_report() {
  python3 - "$work_directory/paper/plugins/DungeonsAcceptance/results-$1.xml" <<'PYTHON'
import sys
import xml.etree.ElementTree as ET
report = ET.parse(sys.argv[1]).getroot()
assert int(report.attrib['tests']) > 0, 'No acceptance cases ran'
assert report.attrib['failures'] == '0', 'Acceptance failures'
assert report.attrib['skipped'] == '0', 'Acceptance cases were skipped'
PYTHON
}
# ShellCheck cannot see that EXIT invokes this function.
# shellcheck disable=SC2317
cleanup() {
  local result=$?
  stop_server || true
  if [[ "$result" -ne 0 ]]; then
    for log in "$work_directory"/paper/*.log; do
      [[ ! -f "$log" ]] || tail -n 180 "$log" >&2
    done
  fi
  if [[ "$keep_work_directory" == true || -n "${DUNGEONS_ACCEPTANCE_WORK_DIRECTORY:-}" ]]; then
    echo "Dungeons acceptance logs retained in $work_directory" >&2
  else
    rm -rf "$work_directory"
  fi
  exit "$result"
}
trap cleanup EXIT

for command in curl sha256sum java javac jar python3; do command -v "$command" >/dev/null || fail "Missing $command"; done
[[ -f "$artifact" ]] || fail "Missing jar: $artifact"
[[ -n "$acceptance_classpath" ]] || fail 'Empty acceptance compilation classpath'
[[ ! -e "$work_directory/paper/server.properties" ]] || fail 'Use a fresh acceptance work directory'
mkdir -p "$work_directory/paper/plugins" "$work_directory/consumer/classes"
"${JAVA_HOME:?Set JAVA_HOME to JDK 25}/bin/javac" --release 25 \
  -cp "$artifact:$acceptance_classpath" -d "$work_directory/consumer/classes" \
  "$root_directory"/src/acceptance/java/nl/hauntedmc/dungeons/acceptance/*.java
"$JAVA_HOME/bin/jar" --create --file "$work_directory/paper/plugins/DungeonsAcceptance.jar" \
  -C "$work_directory/consumer/classes" . -C "$root_directory/src/acceptance/resources" .
paper_url="https://fill-data.papermc.io/v1/objects/$paper_sha256/paper-$paper_version-$paper_build.jar"
if [[ -n "${DUNGEONS_PAPER_JAR:-}" ]]; then
  cp "$DUNGEONS_PAPER_JAR" "$work_directory/paper/paper.jar"
else
  curl --fail --silent --show-error --location --connect-timeout 15 --max-time 600 \
    --retry 2 --output "$work_directory/paper/paper.jar" "$paper_url"
fi
[[ "$(sha256sum "$work_directory/paper/paper.jar" | cut -d ' ' -f1)" == "$paper_sha256" ]] || fail 'Paper runtime checksum mismatch'
cp "$artifact" "$work_directory/paper/plugins/Dungeons.jar"
# Exercise a current-format dungeon on a fresh installation.
dungeon_data="$work_directory/paper/plugins/Dungeons/dungeons"
current_map="$dungeon_data/current-config"
mkdir -p "$current_map"
cat >"$current_map/config.yml" <<'YAML'
dungeon:
  type: branching
generator:
  layout: branching
  branching:
    trunk:
      straightness: 0.8
YAML
printf 'eula=true\n' >"$work_directory/paper/eula.txt"
printf 'server-port=0\nlevel-type=minecraft:flat\n' >"$work_directory/paper/server.properties"
mkfifo "$work_directory/paper/console.in"
start_server paper.log
wait_for_log 'Enabling Dungeons'
[[ -d "$work_directory/paper/plugins/Dungeons" ]] || fail 'Plugin did not initialize its data directory'
wait_for_log "Loaded dungeon 'current-config' successfully"
grep -Fq 'straightness: 0.8' "$current_map/config.yml" || fail 'Current generator configuration was not loaded'
printf 'dungeon map maintenance current-config on Acceptance configuration mutations\n' >&"$paper_input_fd"
wait_for_log 'Maintenance on for current-config'
printf 'dungeon map settings current-config set branch.add command_side\n' >&"$paper_input_fd"
wait_for_log 'branch.add.*command_side'
grep -Fq 'command_side:' "$current_map/config.yml" || fail 'Branch command did not save to config.yml'
printf 'dungeon map settings current-config set branch.command_side.straightness 0.75\n' >&"$paper_input_fd"
wait_for_log 'branch.command_side.straightness.*0.75'
grep -Fq 'straightness: 0.75' "$current_map/config.yml" || fail 'Named branch edit was not saved'
printf 'dungeon map settings current-config set branch.remove command_side\n' >&"$paper_input_fd"
wait_for_log 'branch.remove.*command_side'
if grep -Fq 'command_side:' "$current_map/config.yml"; then fail 'Branch removal was not saved'; fi
printf 'dungeon map settings current-config set layout minecrafty\n' >&"$paper_input_fd"
wait_for_log 'layout.*minecrafty'
grep -Fq 'layout: minecrafty' "$current_map/config.yml" || fail 'Layout command did not save to config.yml'
printf 'dungeon map settings current-config set layout branching\n' >&"$paper_input_fd"
wait_for_log 'layout.*branching'
grep -Fq 'layout: branching' "$current_map/config.yml" || fail 'Layout switch back was not saved'
before_lines=$(wc -l <"$paper_log")
printf 'dungeon map maintenance current-config off Acceptance configuration complete\n' >&"$paper_input_fd"
wait_for_log 'Maintenance off for current-config'
printf 'dungeon help\n' >&"$paper_input_fd"
local_deadline=$((SECONDS + 45))
while ((SECONDS < local_deadline)); do
  if tail -n "+$((before_lines + 1))" "$paper_log" | grep -Eqi '/dungeon|dungeon help|dungeons'; then
    printf 'dungeonsacceptance run\n' >&"$paper_input_fd"
    wait_for_log 'DUNGEONS_ACCEPTANCE_COMPLETE phase=run '
    check_report run
    stop_server || fail 'First Paper process did not shut down cleanly'
    start_server paper-restart.log
    printf 'dungeonsacceptance restart\n' >&"$paper_input_fd"
    wait_for_log 'DUNGEONS_ACCEPTANCE_COMPLETE phase=restart '
    check_report restart
    stop_server || fail 'Restarted Paper process did not shut down cleanly'
    # NativeDisposal deliberately cancels exactly one unload for this isolated test dungeon.
    # Keep rejecting every other runtime error, including disposal failures for other worlds.
    if grep -Eh 'DUNGEONS_ACCEPTANCE_FAIL|Error occurred while (enabling|disabling)|Task .* for Dungeons .* generated an exception|\[Dungeons\].*(Failed|failed)' "$work_directory"/paper/*.log \
      | grep -Ev "\[Dungeons\] Instance disposal failed for 'acceptance_unload_retry_[0-9]+' in dungeon 'acceptance_unload_retry'; retaining registration and files\. Call disposeAsync\(\) to retry\.$"; then
      fail 'Paper logged an unexpected dungeon runtime or lifecycle error'
    fi
    if grep -Eh '\[NBTAPI\].*(not supported|Unable to find)' "$work_directory"/paper/*.log; then
      fail 'Bundled NBT-API reported unsupported native methods or a server-version mismatch'
    fi
    echo 'Dungeons Paper integration and acceptance passed.'
    exit 0
  fi
  sleep 1
done
fail 'No response to dungeon help command'
