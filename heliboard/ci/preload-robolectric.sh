#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-only
# Android 15 Robolectric 4.14.1 SDK runtime, pinned from upstream
# DefaultSdkProvider.java (15 / 12650502 / preinstrumented i7).
# The standard Robolectric fetcher downloads directly from repo1.maven.org
# during test execution, without retrying. This fetch is explicit, SHA-512
# validated, and cached in the same local Maven repository Robolectric uses.
set -euo pipefail

version="15-robolectric-12650502-i7"
name="android-all-instrumented-${version}"
folder="${HOME}/.m2/repository/org/robolectric/android-all-instrumented/${version}"
base="https://repo.maven.apache.org/maven2/org/robolectric/android-all-instrumented/${version}"
mkdir -p "$folder"

for ext in jar pom; do
  file="$folder/$name.$ext"
  hash="$file.sha512"
  if [[ -s "$file" && -s "$hash" ]] &&
     (cd "$folder" && printf '%s  %s\n' "$(tr -d '\r\n[:space:]' < "$hash")" "$name.$ext" | sha512sum -c - >/dev/null); then
    echo "Verified cached Robolectric SDK: $name.$ext"
    continue
  fi

  rm -f "$file" "$hash"
  echo "Downloading Robolectric SDK $name.$ext from the official Maven Central endpoint..."
  curl --fail --location --retry 5 --retry-all-errors --retry-delay 3 --connect-timeout 20 \
    --output "$file.tmp" "$base/$name.$ext"
  curl --fail --location --retry 5 --retry-all-errors --retry-delay 3 --connect-timeout 20 \
    --output "$hash.tmp" "$base/$name.$ext.sha512"
  (cd "$folder" && printf '%s  %s\n' "$(tr -d '\r\n[:space:]' < "$hash.tmp")" "$name.$ext.tmp" | sha512sum -c -)
  mv "$file.tmp" "$file"
  mv "$hash.tmp" "$hash"
done

echo "Robolectric 4.14.1 Android 15 runtime present and SHA-512 verified."

