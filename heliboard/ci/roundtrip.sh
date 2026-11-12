#!/usr/bin/env bash
set -euo pipefail
# Disposable certificate only. Verify protocol with Android's independent verifier.
keytool -genkeypair -keystore "$RUNNER_TEMP/fixture.jks" -alias androiddebugkey -storepass android -keypass android -keyalg RSA -keysize 2048 -validity 2 -dname 'CN=Disposable protocol test' >/dev/null 2>&1
cert=$(keytool -exportcert -keystore "$RUNNER_TEMP/fixture.jks" -alias androiddebugkey -storepass android | sha256sum | cut -d' ' -f1)
java heliboard/ci/SignDigest.java "$RUNNER_TEMP/fixture.jks" "$(python3 -c 'import json;print(json.load(open("candidate/request.json"))["contentDigest"])')" "$cert" "$RUNNER_TEMP/fixture.block"
python3 - <<'PY'
import json,base64,os
from pathlib import Path
m=json.loads(Path('candidate/request.json').read_text());m['signingBlock']=base64.b64encode(Path(os.environ['RUNNER_TEMP']+'/fixture.block').read_bytes()).decode();Path(os.environ['RUNNER_TEMP']+'/fixture.json').write_text(json.dumps(m))
PY
python3 heliboard/ci/apk_v2.py assemble candidate/unsigned.apk "$RUNNER_TEMP/fixture.json" "$RUNNER_TEMP/fixture.apk"
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --verbose --print-certs --min-sdk-version 26 "$RUNNER_TEMP/fixture.apk"
cp "$RUNNER_TEMP/fixture.apk" candidate/ui-test.apk
rm "$RUNNER_TEMP/fixture.jks" "$RUNNER_TEMP/fixture.block" "$RUNNER_TEMP/fixture.apk" "$RUNNER_TEMP/fixture.json"
