#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
import os, pathlib, re, subprocess
apk=next(pathlib.Path('heli-source/app/build/outputs/apk/debugNoMinify').glob('*.apk'))
tool=pathlib.Path(os.environ['ANDROID_HOME'])/'build-tools/35.0.0/apksigner'
output=subprocess.check_output([str(tool),'verify','--print-certs',str(apk)],text=True)
match=re.search(r'Signer #1 certificate SHA-256 digest: ([0-9a-fA-F]+)',output)
expected=pathlib.Path('heliboard/signing-cert.sha256').read_text().strip()
if not match or match.group(1).lower()!=expected:
    raise SystemExit('O APK não usa a assinatura de teste registrada; não distribuir.')
print('Assinatura criptográfica do APK verificada:',expected)

