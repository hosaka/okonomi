#!/bin/sh
# Turns a batched changie notes file (.changes/vX.Y.Z.md) into what a release
# publishes. version.yml writes the Play "What's new" text with `play`,
# publish-release.sh takes the forge release body from `body`, and pr-test.yml
# runs `play` over a dry-run batch so a note too long for Play fails the PR
# rather than the release.
#
# Usage: release-notes.sh play <notes.md|-> <out.txt>
#        release-notes.sh body <notes.md>
#
# play: Play shows at most 500 characters. Only the `- ` bullets go there, so
# a cut can never leave a heading with nothing under it, and whole bullets are
# kept while they fit, warning when any are dropped. A first bullet that alone
# exceeds the limit is an error. With no bullets (an explicit bump with nothing
# pending) no file is left behind, since an empty one would publish a blank
# "What's new" rather than none. mawk (Debian's awk) counts bytes rather than
# characters, which only errs short.
#
# body: the notes without their `## vX.Y.Z - date` heading and the blank lines
# after it, printed to stdout.
set -eu

usage="usage: $0 play <notes.md|-> <out.txt> | body <notes.md>"
command="${1:?$usage}"

# Without pipefail a missing file would only make grep or sed complain while
# the pipeline still succeeds with nothing in it, so check up front.
require_readable() {
  if [ "$1" != - ] && { [ ! -r "$1" ] || [ -d "$1" ]; }; then
    echo "::error::Release notes file $1 is missing or unreadable." >&2
    exit 1
  fi
}

case "$command" in
play)
  notes="${2:?$usage}"
  out="${3:?$usage}"
  require_readable "$notes"
  cut_marker=$(mktemp)
  stdin_copy=""
  trap 'rm -f "$cut_marker" ${stdin_copy:+"$stdin_copy"}' EXIT
  label="$notes"
  if [ "$notes" = - ]; then
    # Read twice: once to cut, once to name what the cut leaves out.
    label="the pending release notes"
    stdin_copy=$(mktemp)
    cat > "$stdin_copy"
    notes="$stdin_copy"
  fi
  mkdir -p "$(dirname "$out")"
  grep '^- ' "$notes" \
    | awk -v max=500 '
        { add = length($0) + (NR > 1 ? 1 : 0) }
        total + add > max { cut = 1; exit }
        { total += add; print }
        END { if (cut) print "cut" > "/dev/stderr" }' \
    > "$out" 2> "$cut_marker"
  if [ -s "$cut_marker" ]; then
    if [ ! -s "$out" ]; then
      rm -f "$out"
      echo "::error::The first note in $label exceeds Play's 500 characters; shorten it." >&2
      exit 1
    fi
    if [ -n "$stdin_copy" ]; then
      kept=$(wc -l < "$out")
      total=$(grep -c '^- ' "$notes")
      echo "::warning::The pending release notes exceed Play's 500 characters; Play would show only the first $kept of $total bullets. The release still gets the full notes. Left out of Play:"
      grep '^- ' "$notes" | sed "1,${kept}d"
    else
      echo "::warning::Release notes exceed Play's 500 characters; $out keeps only the bullets that fit. The Forgejo release has them in full."
    fi
  fi
  [ -s "$out" ] || rm "$out"
  ;;
body)
  notes="${2:?$usage}"
  require_readable "$notes"
  sed '1{/^## /d}' "$notes" | awk 'NF { started = 1 } started'
  ;;
*)
  echo "$usage" >&2
  exit 1
  ;;
esac
