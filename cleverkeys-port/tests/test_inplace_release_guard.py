#!/usr/bin/env python3
"""No-Android-SDK unit tests for the no-Actions in-place release guard."""
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

SCRIPT=Path(__file__).resolve().parents[1]/"make-inplace-update-manifest.py"
spec=importlib.util.spec_from_file_location("make_inplace_update_manifest",SCRIPT)
module=importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class InplaceGuardTests(unittest.TestCase):
    SIGNER="a"*64

    def fixtures(self,root):
        root=Path(root)
        apk=root/"teclado-br-heli-0.9.2-beta.20.apk"
        apk.write_bytes(b"HBX"+b"x"*1_000_000)  # fake: test simulates aapt/apksigner only
        baseline=root/"update.json"
        baseline.write_text(json.dumps({
            "schema":"teclado-br-updates-v1","enabled":True,
            "packageName":"br.com.tecladobr.heli.debug",
            "signerSha256":self.SIGNER,"versionCode":9018,
            "apkUrl":"https://github.com/helbermoura2/teclado-br-publico/releases/download/heli-0.9.2-beta.17/teclado-br-heli-0.9.2-beta.17.apk"
        }))
        return apk,baseline,root/"candidate.json"

    def execute(self,apk,baseline,out,package="br.com.tecladobr.heli.debug",
                code=9022,signer=None,tested=False):
        signer=signer or self.SIGNER
        aapt=f"package: name='{package}' versionCode='{code}' versionName='0.9.2-beta.20-hbx-clever'\n"
        apksigner=f"Signer #1 certificate SHA-256 digest: {signer}\n"
        argv=["guard","--apk",str(apk),"--tag","heli-0.9.2-beta.20",
              "--baseline",str(baseline),"--output",str(out)]
        if tested:argv.append("--device-tested")
        with patch.object(sys,"argv",argv),patch.object(module,"run",
            side_effect=[aapt,apksigner]):
            module.main()

    def test_candidate_is_disabled_without_phone_test(self):
        with tempfile.TemporaryDirectory() as root:
            apk,baseline,out=self.fixtures(root)
            self.execute(apk,baseline,out)
            result=json.loads(out.read_text())
            self.assertFalse(result["enabled"])
            self.assertEqual(result["packageName"],"br.com.tecladobr.heli.debug")
            self.assertEqual(result["versionCode"],9022)
            self.assertEqual(result["signerSha256"],self.SIGNER)
            self.assertEqual(result["sha256"],module.digest(apk))

    def test_device_test_approval_is_explicit(self):
        with tempfile.TemporaryDirectory() as root:
            apk,baseline,out=self.fixtures(root)
            self.execute(apk,baseline,out,tested=True)
            self.assertTrue(json.loads(out.read_text())["enabled"])

    def test_rejects_different_package(self):
        with tempfile.TemporaryDirectory() as root:
            apk,baseline,out=self.fixtures(root)
            with self.assertRaisesRegex(AssertionError,"Package differs"):
                self.execute(apk,baseline,out,package="br.com.hbx.clever.preview")
            self.assertFalse(out.exists())

    def test_rejects_old_version(self):
        with tempfile.TemporaryDirectory() as root:
            apk,baseline,out=self.fixtures(root)
            with self.assertRaisesRegex(AssertionError,"not newer"):
                self.execute(apk,baseline,out,code=9018)
            self.assertFalse(out.exists())

    def test_rejects_different_certificate(self):
        with tempfile.TemporaryDirectory() as root:
            apk,baseline,out=self.fixtures(root)
            with self.assertRaisesRegex(AssertionError,"signing certificate"):
                self.execute(apk,baseline,out,signer="b"*64)
            self.assertFalse(out.exists())

    def test_does_not_overwrite_existing_manifest(self):
        with tempfile.TemporaryDirectory() as root:
            apk,baseline,out=self.fixtures(root)
            out.write_text("existing")
            with self.assertRaisesRegex(SystemExit,"Refusing to overwrite"):
                self.execute(apk,baseline,out)
            self.assertEqual(out.read_text(),"existing")

if __name__=="__main__":
    unittest.main()
