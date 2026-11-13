#!/usr/bin/env python3
"""Static regression test for the HBX Keyboard branding overlay."""
from pathlib import Path
import re

here=Path(__file__).resolve().parents[1]
prepare=(here/"prepare.py").read_text(encoding="utf-8")
assert r"r'\1HBX Keyboard\2'" in prepare, "IME name not HBX Keyboard"
assert "HBX Keyboard - Configurações" in prepare
assert "HBX Keyboard - Corretor ortográfico" in prepare
assert "for icon_name in ('ic_launcher.xml', 'ic_launcher_round.xml')" in prepare
assert "shutil.copyfile(hbx_icon, drawable_nodpi/'hbx_brand.webp')" in prepare
assert "source.replace('@drawable/ic_launcher_foreground', '@drawable/hbx_brand')" in prepare
assert "source.replace('@drawable/ic_launcher_background', '@android:color/black')" in prepare
assert "source = re.sub(" in prepare and "monochrome" in prepare
assert "applicationId = \"br.com.tecladobr.heli\"" in prepare
assert "versionCode = 9019" in prepare
assert "0.9.2-beta.18-heli3.6" in prepare
icon=(here/"overlay/hbx_brand.webp")
assert icon.is_file() and icon.stat().st_size>1000
assert icon.read_bytes().startswith(b"RIFF") and icon.read_bytes()[8:12]==b"WEBP"
print("PASS: HBX Keyboard name, adaptive icons, package identity and WebP artwork")
