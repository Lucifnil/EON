#!/usr/bin/env bash
set -euo pipefail

# Run after: ./gradlew testDebugUnitTest lintRelease assembleRelease
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_dir"
build_tools="${ANDROID_HOME:?Set ANDROID_HOME}/build-tools/36.0.0"
java_bin="${JAVA_HOME:?Set JAVA_HOME}/bin"
export PATH="$java_bin:$PATH"

unsigned_apk=app/build/outputs/apk/release/app-release-unsigned.apk
credentials=signing/RELEASE_KEY.txt
test -f "$unsigned_apk"
test -f "$credentials"
stage_dir="$(mktemp -d "${TMPDIR:-/tmp}/eon-v1.XXXXXX")"
cleanup() {
    find "$stage_dir" -type f -delete
    find "$stage_dir" -depth -type d -empty -delete
}
trap cleanup EXIT

export EON_STORE_PASS EON_KEY_PASS
EON_STORE_PASS="$(sed -n 's/^Store password: //p' "$credentials")"
EON_KEY_PASS="$(sed -n 's/^Key password: //p' "$credentials")"
key_alias="$(sed -n 's/^Alias: //p' "$credentials")"
test -n "$EON_STORE_PASS" && test -n "$EON_KEY_PASS" && test -n "$key_alias"

"$build_tools/zipalign" -f -p 4 "$unsigned_apk" "$stage_dir/aligned.apk"
"$build_tools/apksigner" sign \
    --ks signing/eon-release.jks --ks-key-alias "$key_alias" \
    --ks-pass env:EON_STORE_PASS --key-pass env:EON_KEY_PASS \
    --v4-signing-enabled true --out "$stage_dir/EON-1.0.apk" "$stage_dir/aligned.apk"
unset EON_STORE_PASS EON_KEY_PASS
"$build_tools/apksigner" verify "$stage_dir/EON-1.0.apk"
"$build_tools/zipalign" -c -p 4 "$stage_dir/EON-1.0.apk"
"$build_tools/aapt" dump badging "$stage_dir/EON-1.0.apk" > "$stage_dir/apk-info.txt"
rg -q "package: name='app.eon.launcher' versionCode='1' versionName='1.0'" "$stage_dir/apk-info.txt"

# Explicit source allowlist: no signing data, local SDK paths, caches or old binaries.
"$java_bin/jar" --create --no-manifest --file "$stage_dir/EON-1.0-source.zip" \
    .gitignore README.md REVIEW-v1.md THIRD_PARTY_NOTICES.md LICENSES app/src app/build.gradle.kts build.gradle.kts \
    settings.gradle.kts gradle.properties gradle/wrapper gradlew gradlew.bat scripts \
    design/EON_APP_GENERATION_PROMPT.md design/eon-ui-reference.png design/eon-icon-reference.png
"$java_bin/jar" tf "$stage_dir/EON-1.0-source.zip" > "$stage_dir/source-entries.txt"
if rg -q '(^|/)(signing|\.git|build|dist)(/|$)|RELEASE_KEY|\.jks$|\.keystore$' "$stage_dir/source-entries.txt"; then
    echo 'Unexpected private or generated content in source ZIP' >&2
    exit 1
fi
mkdir -p dist
install -m 0644 "$stage_dir/EON-1.0.apk" dist/EON-1.0.apk
install -m 0644 "$stage_dir/EON-1.0.apk.idsig" dist/EON-1.0.apk.idsig
install -m 0644 "$stage_dir/EON-1.0-source.zip" dist/EON-1.0-source.zip
sha256sum dist/EON-1.0.apk dist/EON-1.0-source.zip
