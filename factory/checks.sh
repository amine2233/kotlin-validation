#!/usr/bin/env bash
# The repo's checks. Exit 0 only when the branch is fit to review.
# Mirrors CI (amine2233/kotlin-ci-shared): `mise run lint` and `mise run test`.
set -euo pipefail

mise trust --yes --quiet . >/dev/null 2>&1 || true
mise exec -- ktlint '**/*.kt' '**/*.kts' '!**/build/**' --reporter=plain
mise exec -- ./gradlew test --no-daemon --console=plain
