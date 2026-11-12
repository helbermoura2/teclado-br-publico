#!/usr/bin/env python3
"""Strict, offline gate for a HeliBoard -> CleverKeys *in-place* signed update.

Does not compile, sign, upload, or alter the published update.json.
It only prepares JSON from a verified APK + the current signed-channel metadata.
"""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess

EXPECTED_PACKAGE="br.com.tecladobr.heli.debug"
EXPECTED_SCHEMA="teclado-br-updates-v1"
EXPECTED_HOST="https://github.com/helbermoura2/teclado-br-publico"
TAG=re.compile(r"^heli-0[.]9[.]2-beta[.](\d+)$")
HEX=re.compile(r"^[a-fA-F0-9]{64}$")

def run(*cmd: str)->str:
    p=subprocess.run(cmd,capture_output=True,text=True,check=True)
    return p.stdout

def digest(path: Path)->str:
    h=hashlib.sha256()
    with path.open("rb") as src:
        for block in iter(lambda:src.read(1024*1024),b""):
            h.update(block)
    return h.hexdigest()

def main()->None:
    ap=argparse.ArgumentParser()
    ap.add_argument("--apk",required=True,type=Path)
    ap.add_argument("--tag",required=True)
    ap.add_argument("--baseline",required=True,type=Path,
                    help="Current main/update.json from public repository")
    ap.add_argument("--output",required=True,type=Path,
                    help="New candidate JSON path; existing files are never overwritten")
    ap.add_argument("--device-tested",action="store_true",
                    help="Required only to mark manifest enabled. Never assume tests passed.")
    ap.add_argument("--aapt",default="aapt")
    ap.add_argument("--apksigner",default="apksigner")
    args=ap.parse_args()
    match=TAG.fullmatch(args.tag)
    assert match and int(match.group(1))>=18,"Release tag must be newer than beta17"
    assert args.apk.is_file(),"A real signed APK is required"
    assert args.apk.name=="teclado-br-"+args.tag+".apk","APK filename mismatch"
    assert 1_000_000<=args.apk.stat().st_size<=180*1024*1024,"Unexpected APK size"
    baseline=json.loads(args.baseline.read_text(encoding="utf-8"))
    assert baseline.get("schema")==EXPECTED_SCHEMA,"Unexpected channel schema"
    assert baseline.get("enabled") is True,"Existing update channel disabled"
    assert baseline.get("packageName")==EXPECTED_PACKAGE,"Wrong installed package"
    signer=str(baseline.get("signerSha256","")).lower()
    assert HEX.fullmatch(signer),"Invalid baseline signer fingerprint"
    assert baseline.get("apkUrl","").startswith(EXPECTED_HOST+"/releases/download/"),"Channel mismatch"
    minCode=int(baseline.get("versionCode",0))
    assert minCode>0,"No previous version to upgrade"
    badging=run(args.aapt,"dump","badging",str(args.apk))
    app=re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'",
                  badging,re.MULTILINE)
    assert app,"Could not read package name/code/version"
    package,code,version=app.groups()
    assert package==EXPECTED_PACKAGE,"Package differs: would install another app"
    assert minCode<int(code)<1_000_000,"APK is not newer than installed version"
    expectedPrefix="0.9.2-beta."+match.group(1)
    assert version==expectedPrefix or version.startswith(expectedPrefix+"-"),"Version/tag mismatch"
    sig=run(args.apksigner,"verify","--print-certs",str(args.apk))
    found=re.findall(r"^Signer #\d+ certificate SHA-256 digest: ([a-fA-F0-9]{64})$",
                     sig,re.MULTILINE)
    assert len(found)==1 and found[0].lower()==signer,"Old HBX signing certificate missing"
    candidate={
        "schema":EXPECTED_SCHEMA,
        "enabled":bool(args.device_tested),
        "packageName":EXPECTED_PACKAGE,
        "versionCode":int(code),
        "versionName":version,
        "sha256":digest(args.apk),
        "signerSha256":signer,
        "sizeBytes":args.apk.stat().st_size,
        "apkUrl":(f"{EXPECTED_HOST}/releases/download/{args.tag}/"
                  f"{args.apk.name}"),
        "releaseUrl":f"{EXPECTED_HOST}/releases/tag/{args.tag}",
        "notes":("Migração CleverKeys mantendo pacote e certificado HBX. "
                 "Modelos GGUF não estão embutidos no APK.")
    }
    if args.output.exists():raise SystemExit("Refusing to overwrite a manifest")
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(candidate,indent=2,ensure_ascii=False)+"\n",
                           encoding="utf-8")
    print("Verified package, increasing versionCode, original signer and APK SHA-256")
    print("Candidate file:",args.output)
    print("Enabled:",bool(args.device_tested))
    print("No network, upload, installation, signing or APK build was performed.")
    if not args.device_tested:
        print("Device validation still required to enable this update!")

if __name__=="__main__":
    main()
