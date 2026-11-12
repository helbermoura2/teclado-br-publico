#!/usr/bin/env python3
"""Prepare a SEPARATE HBX AnySoftKeyboard PT-BR punctuation preview.

This does not change the currently installed HBX HeliBoard, its updater,
signing key, models, or data. It does not contain online AI in this first test.
"""
from __future__ import annotations
import argparse
import json
from pathlib import Path
import shutil
import subprocess

HERE=Path(__file__).resolve().parent
CONF=json.loads((HERE/"upstream.json").read_text(encoding="utf-8"))

def edit(root:Path, path:str, old:str, new:str)->None:
    destination=root/path
    content=destination.read_text(encoding="utf-8")
    if content.count(old)!=1:
        raise RuntimeError(f"Expected one matching site in {path}: {old[:60]!r}, got {content.count(old)}")
    destination.write_text(content.replace(old,new,1),encoding="utf-8")

def main()->None:
    p=argparse.ArgumentParser()
    p.add_argument("source",type=Path,help="Pinned upstream AnySoftKeyboard checkout")
    a=p.parse_args()
    root=a.source.resolve()
    if not (root/".git").exists():raise RuntimeError("Expected a git checkout, not a ZIP/APK")
    head=subprocess.check_output(["git","rev-parse","HEAD"],cwd=root,text=True).strip()
    if head!=CONF["anysoft_commit"]:
        raise RuntimeError(f"Unexpected AnySoftKeyboard revision {head}; expected {CONF['anysoft_commit']}")
    app="ime/app/build.gradle"
    edit(root,app,"apply plugin: 'androidx.navigation.safeargs'",
         """apply plugin: 'androidx.navigation.safeargs'

    // HBX AnySoft preview: package is separate from HeliBoard and CleverKeys.
    // Keep upstream namespace 'com.menny.android.anysoftkeyboard' unchanged.
    android {
        defaultConfig { applicationId 'br.com.hbx.anysoft.preview' }
    }
    """)
    edit(root,app,"    implementation project(':addons:languages:english:pack')",
         """    implementation project(':addons:languages:english:pack')
    // Bundle the official Brazilian PT-BR layout and AOSP dictionary.
    implementation project(':addons:languages:brazilian:pack')""")
    defaults="ime/app/src/main/res/values/settings_defaults_dont_translate.xml"
    # Native Brazilian "QWERTY com Ç" layout: dedicated ç key in 2nd row.
    edit(root,defaults,
         '<string name="settings_default_keyboard_id">@string/main_english_keyboard_id</string>',
         '<string name="settings_default_keyboard_id">4c99178a-9a80-4218-8b13-f05f474c7f5e</string>')
    # No extra number/nav shortcut row above the letters. The normal
    # candidate/suggestion strip remains native AnySoft.
    edit(root,defaults,
         '<string name="settings_default_top_row_key">5d945f40-ded5-11e0-9572-0800200c9a66</string>',
         '<string name="settings_default_top_row_key">82910c70-ded1-11e0-9572-0800200c9a66</string>')
    # Normal row has ? , . ' alongside Space; long-press offers ! ; : -
    edit(root,defaults,
         '<string name="settings_default_ext_kbd_bottom_row_key">09f8f280-dee2-11e0-9572-0800200c9a66</string>',
         '<string name="settings_default_ext_kbd_bottom_row_key">09f8f280-dee2-11e0-9572-0800200c9a55</string>')
    edit(root,"ime/app/src/main/res/values/strings_dont_translate.xml",
         '<string name="ime_name">AnySoftKeyboard</string>',
         '<string name="ime_name">HBX AnySoft (Teste)</string>')
    edit(root,"ime/app/src/debug/res/values/strings.xml",
         'tools:ignore="MissingTranslation">AnySoftKeyboard Dev</string>',
         'tools:ignore="MissingTranslation">HBX AnySoft (Teste)</string>')
    # Never replace other launchers' icons or borrow proprietary Samsung assets.
    image=HERE/"hbx_brand.webp"
    if image.stat().st_size<500:raise RuntimeError("HBX icon artwork missing")
    drawable=root/"ime/app/src/main/res/drawable-nodpi"
    drawable.mkdir(parents=True,exist_ok=True)
    shutil.copy2(image,drawable/"hbx_brand.webp")
    for icon in ("ic_launcher.xml","ic_launcher_round.xml"):
        target=root/"ime/app/src/main/res/mipmap-anydpi-v26"/icon
        source=target.read_text(encoding="utf-8")
        if '@drawable/ic_launcher_foreground' not in source:
            raise RuntimeError(f"Unexpected adaptive launcher in {icon}")
        target.write_text(source.replace('@drawable/ic_launcher_foreground',
                                         '@drawable/hbx_brand'),encoding="utf-8")
    print("HBX AnySoft PT-BR preview prepared")
    print("Upstream commit:",head)
    print("Package: br.com.hbx.anysoft.preview")
    print("PT-BR Ç layout with punctuation keys and NO extra number/nav row")
    print("Offline-first keyboard; online AI integration is NOT in this first preview")
    print("No APK was compiled, signed or published by prepare.py")

if __name__=="__main__":
    main()
