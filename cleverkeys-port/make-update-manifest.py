#!/usr/bin/env python3
"""Create a signed HBX-Clever preview update manifest, never a signed APK.

Requires a previously built and signed .apk and local Android build-tools.
This script performs no downloads, no network access, and no publishing.
"""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess

PACKAGE = "br.com.hbx.clever.preview"
REPO = "helbermoura2/teclado-br-publico"
TAG = re.compile(r"^hbx-clever-v(\d+)\.(\d+)\.(\d+)$")
HEX = re.compile(r"^[0-9a-fA-F]{64}$")

def command(*args: str) -> str:
    result=subprocess.run(args,check=True,stdout=subprocess.PIPE,
                          stderr=subprocess.PIPE,text=True)
    return result.stdout

def main() -> None:
    p=argparse.ArgumentParser()
    p.add_argument("--apk",type=Path,required=True)
    p.add_argument("--tag",required=True)
    p.add_argument("--expected-cert-sha256",required=True,
                   help="PIN: SHA-256 of original signed preview APK certificate")
    p.add_argument("--aapt",default="aapt")
    p.add_argument("--apksigner",default="apksigner")
    p.add_argument("--output",type=Path,required=True)
    a=p.parse_args()
    assert TAG.fullmatch(a.tag), "Unexpected CleverKeys preview release tag"
    cert=a.expected_cert_sha256.lower()
    assert HEX.fullmatch(cert), "Invalid signer pin SHA-256"
    assert a.apk.is_file() and a.apk.name == a.tag+".apk", "APK filename mismatch"
    size=a.apk.stat().st_size
    assert 1_000_000 <= size <= 180*1024*1024, "Unexpected APK size"
    badging=command(a.aapt,"dump","badging",str(a.apk))
    match=re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'",
                    badging,re.MULTILINE)
    assert match, "aapt could not read signed APK package and version"
    pkg,code,version=match.groups()
    assert pkg == PACKAGE, "This APK would replace the wrong Android package"
    expected=".".join(TAG.fullmatch(a.tag).groups())
    assert version.startswith(expected), "Release tag disagrees with APK version"
    assert int(code)>0, "APK has invalid versionCode"
    signature=command(a.apksigner,"verify","--print-certs",str(a.apk))
    pins=re.findall(r"^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]{64})$",
                    signature,re.MULTILINE)
    assert len(pins)==1 and pins[0].lower()==cert, "APK signer changed"
    digest=hashlib.sha256(a.apk.read_bytes()).hexdigest()
    endpoint=f"https://github.com/{REPO}/releases"
    payload={
        "schema":"hbx-clever-updates-v1","enabled":True,
        "packageName":PACKAGE,
        "versionCode":int(code),"versionName":version,
        "sha256":digest,"signerSha256":cert,"sizeBytes":size,
        "apkUrl":f"{endpoint}/download/{a.tag}/{a.apk.name}",
        "releaseUrl":f"{endpoint}/tag/{a.tag}",
        "notes":"HBX CleverKeys - atualização pelo próprio teclado. Nunca substitui o HeliBoard."
    }
    if a.output.exists(): raise SystemExit("Refusing to overwrite existing update manifest")
    a.output.parent.mkdir(parents=True,exist_ok=True)
    a.output.write_text(json.dumps(payload,indent=2,ensure_ascii=False)+"\n",encoding="utf-8")
    print("Manifest ready for publication after real-device validation:",a.output)
    print("Package:",pkg,"Code:",code,"Signer:",cert)
    print("Nothing was uploaded or installed.")

if __name__ == "__main__":
    main()
