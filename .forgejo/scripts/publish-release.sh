#!/bin/sh
# Publishes a release for <tag> on one forge: that version's notes as the body
# and <apk> attached. release.yml calls it once per forge with the APK it built,
# so every forge ships identical, identically signed files and Obtainium can
# switch between them. It also runs locally to backfill a forge for an old tag.
#
# Usage: TOKEN=... publish-release.sh forgejo|codeberg|github <tag> <apk>
#
# Without a TOKEN the mirrors warn and skip (as on a fork). Forgejo fails.
set -eu

forge="${1:?usage: $0 forgejo|codeberg|github <tag> <apk>}"
tag="${2:?missing tag}"
apk="${3:?missing apk}"
TOKEN="${TOKEN:-}"
repo=hosaka/okonomi

case "$forge" in
forgejo)
  name=Forgejo
  repo="${FORGEJO_REPOSITORY:-$repo}"
  api="${FORGEJO_URL:-https://code.hosaka.cc}/api/v1/repos/$repo"
  # The tag lives here already: it is what triggered the release.
  git_url=""
  ;;
codeberg)
  name=Codeberg
  api="https://codeberg.org/api/v1/repos/$repo"
  git_url="https://x-access-token:$TOKEN@codeberg.org/$repo.git"
  ;;
github)
  name=GitHub
  api="https://api.github.com/repos/$repo"
  git_url="https://x-access-token:$TOKEN@github.com/$repo.git"
  ;;
*)
  echo "Unknown forge: $forge" >&2
  exit 1
  ;;
esac

if [ -z "$TOKEN" ]; then
  if [ "$forge" = forgejo ]; then
    echo "No token for $name" >&2
    exit 1
  fi
  echo "::warning::No token for $name; not publishing $tag there."
  exit 0
fi

call() {
  if [ "$forge" = github ]; then
    curl -sS --fail-with-body \
      --header "Authorization: Bearer $TOKEN" \
      --header "Accept: application/vnd.github+json" \
      --header "X-GitHub-Api-Version: 2022-11-28" \
      "$@"
  else
    curl -sS --fail-with-body --header "Authorization: token $TOKEN" "$@"
  fi
}

# Tags cut before changie have no notes file and publish without a body.
notes=".changes/$tag.md"
body=""
if [ -f "$notes" ]; then
  body=$(sh "$(dirname "$0")/release-notes.sh" body "$notes")
else
  echo "::warning::No notes file $notes for $tag; publishing without a body."
fi
payload=$(jq -n --arg tag "$tag" --arg name "okonomi $tag" --arg body "$body" \
  '{tag_name: $tag, name: $name, body: $body}')

# A push mirror may not have synced the tag yet, and a release cannot point
# at a tag the forge does not have.
if [ -n "$git_url" ]; then
  local_commit=$(git rev-parse "$tag^{commit}")
  remote_commit=$(git ls-remote "$git_url" "refs/tags/$tag^{}" | cut -f1)
  if [ -z "$remote_commit" ]; then
    # A shallow checkout cannot push history the mirror has not seen yet.
    git fetch --quiet --unshallow origin 2>/dev/null || true
    git push --quiet "$git_url" "refs/tags/$tag"
    echo "Pushed $tag to $name"
  elif [ "$remote_commit" != "$local_commit" ]; then
    echo "$tag on $name is $remote_commit, here it is $local_commit" >&2
    exit 1
  fi
fi

# Not piped straight into jq: the pipeline's status would be jq's, so a failed
# call would carry on with id "null". The forge's error body is printed instead.
release=$(call --request POST \
  --header "Content-Type: application/json" \
  --data "$payload" \
  "$api/releases") || {
  echo "$release" >&2
  exit 1
}
id=$(printf '%s' "$release" | jq -r .id)
echo "Created $name release $id"

asset=$(basename "$apk")
if [ "$forge" = github ]; then
  upload=$(call --request POST \
    --header "Content-Type: application/vnd.android.package-archive" \
    --data-binary "@$apk" \
    "https://uploads.github.com/repos/$repo/releases/$id/assets?name=$asset") ||
    {
      echo "$upload" >&2
      exit 1
    }
else
  upload=$(call --request POST \
    --form "attachment=@$apk" \
    "$api/releases/$id/assets?name=$asset") ||
    {
      echo "$upload" >&2
      exit 1
    }
fi
echo "Attached $asset on $name"
