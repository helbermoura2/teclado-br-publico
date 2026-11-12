#!/usr/bin/env python3
"""Prepare an isolated HBX-on-CleverKeys preview. No GitHub Actions involved.

Run from the *public* teclado-br-publico checkout after cloning the EXACT
CleverKeys commit in hbx-cleverkeys/upstream.json. Only the source copy passed
as argv[1] is modified; no changes to the HeliBoard app or its signing key.
"""
from __future__ import annotations

import json
from pathlib import Path
import re
import shutil
import subprocess
import sys

here = Path(__file__).resolve().parent
repository = here.parent
upstream = json.loads((here / "upstream.json").read_text())
if len(sys.argv) != 2:
    raise SystemExit("Usage: python3 cleverkeys-port/prepare.py /path/to/CleverKeys")
target = Path(sys.argv[1]).resolve()
if not (target / "AndroidManifest.xml").exists():
    raise SystemExit("Missing CleverKeys AndroidManifest.xml")
if not (target / ".git").exists():
    raise SystemExit("Use a clone of the CleverKeys repository, not an extracted APK.")
revision = subprocess.check_output(["git", "rev-parse", "HEAD"],
                                   cwd=target, text=True).strip()
if revision != upstream["cleverkeys_commit"]:
    raise SystemExit("Wrong CleverKeys upstream revision: " + revision)

def patch(rel: str, old: str, new: str) -> None:
    path = target / rel
    original = path.read_text(encoding="utf-8")
    if old not in original:
        raise RuntimeError(f"Upstream changed or already patched: {rel}: {old[:90]!r}")
    if original.count(old) != 1:
        raise RuntimeError(f"Expected exactly one patch site in {rel}")
    path.write_text(original.replace(old, new, 1), encoding="utf-8")

# Distinct package/signing during migration: no upgrade or data loss risk to
# br.com.tecladobr.heli.debug. The final upgrade requires signature continuity.
patch("build.gradle", 'applicationId "tribixbite.cleverkeys"',
      'applicationId "br.com.hbx.clever.preview"')
patch("build.gradle", "minSdk 24", "minSdk 26")
patch("build.gradle", "versionCode rootProject.ext.versionCode", "versionCode 100")
patch("build.gradle", "versionName rootProject.ext.versionNameStr",
      'versionName "0.1.0-hbx-clever-preview"')
patch("build.gradle",
      "include 'armeabi-v7a', 'arm64-v8a', 'x86_64'",
      "include 'arm64-v8a', 'x86_64'")

# CleverKeys ships with NO internet permission. This HBX fork explicitly
# adds INTERNET for user-initiated Gemini/OpenRouter only. Not a silent change
# to the original upstream security claim.
patch("AndroidManifest.xml",
      '  <!-- PRIVACY: No INTERNET permission - CleverKeys is fully offline, no data leaves your device -->',
      '  <!-- HBX fork: network access ONLY for a user-started AI operation. -->\n'
      '  <uses-permission android:name="android.permission.INTERNET"/>\n'
      '  <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>')

activities = """    <!-- HBX assistant is opt-in. PROCESS_TEXT is the user's explicit selection. -->
    <activity android:name="helium314.keyboard.latin.ai.HbxTextActivity"
        android:exported="true" android:excludeFromRecents="true"
        android:theme="@style/settingsTheme">
      <intent-filter>
        <action android:name="android.intent.action.PROCESS_TEXT"/>
        <category android:name="android.intent.category.DEFAULT"/>
        <data android:mimeType="text/plain"/>
      </intent-filter>
    </activity>
    <activity android:name="helium314.keyboard.latin.ai.HbxSettingsActivity"
        android:exported="false" android:theme="@style/settingsTheme"/>
"""
patch("AndroidManifest.xml",
      '    <!-- CleverKeys Keyboard Service -->',
      activities + '    <!-- CleverKeys Keyboard Service -->')

# Tool row is independent from native CleverKeys suggestion/clipboard/emoji
# systems, keeping their callbacks and gesture engine unchanged.
injection = '''        HbxQuickActions.attach(
            _inputViewContainer, this,
            !isPasswordField && (info.inputType and
                android.text.InputType.TYPE_MASK_CLASS) ==
                android.text.InputType.TYPE_CLASS_TEXT,
            openAi = {
                val selection = currentInputConnection?.getSelectedText(0)?.toString().orEmpty()
                startActivity(Intent(this,
                    helium314.keyboard.latin.ai.HbxTextActivity::class.java).apply {
                    putExtra(Intent.EXTRA_PROCESS_TEXT, selection.take(4000))
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            },
            openClipboard = { triggerKeyboardEvent(KeyValue.Event.SWITCH_CLIPBOARD) },
            openEmoji = { triggerKeyboardEvent(KeyValue.Event.SWITCH_EMOJI) },
            openTranslate = {
                val selection = currentInputConnection?.getSelectedText(0)?.toString().orEmpty()
                startActivity(Intent(this,
                    helium314.keyboard.latin.ai.HbxTextActivity::class.java).apply {
                    putExtra(Intent.EXTRA_PROCESS_TEXT, selection.take(4000))
                    putExtra(helium314.keyboard.latin.ai.HbxTextActivity.EXTRA_PRESET_ACTION,
                        "Traduzir para inglês")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            },
            openSettings = {
                startActivity(Intent(this,
                    helium314.keyboard.latin.ai.HbxSettingsActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
        )
'''
patch("src/main/kotlin/tribixbite/cleverkeys/CleverKeysService.kt",
      "        val isPasswordField = SuggestionBar.isPasswordField(info)\n",
      "        val isPasswordField = SuggestionBar.isPasswordField(info)\n"+injection)

# PT-BR layout's keys (including ç) remain native CleverKeys, while the
# familiar Samsung look supplies key shapes, gap, dark mode and action colours.
patch("src/main/layouts/latn_qwerty_br.xml",
      '<keyboard name="QWERTY (Brasileiro)" script="latin">',
      '<keyboard name="QWERTY (Brasileiro)" script="latin" theme="look_samsung">')

destination = target/"src/main/java/helium314/keyboard/latin/ai"
destination.mkdir(parents=True,exist_ok=True)
portable = (
    "GeminiClient.java", "OpenRouterClient.java", "InferenceResult.java",
    "InferenceRouter.java", "SecretStore.java", "AiPreferences.java",
    "ModelStore.java", "OfflineEngine.java", "OfflineNative.java",
    "PromptBuilder.java",
)
for filename in portable:
    shutil.copy2(repository/"heliboard/ai/java"/filename,destination/filename)
for filename in ("HbxTextActivity.java","HbxSettingsActivity.java"):
    shutil.copy2(here/filename,destination/filename)
quick = target/"src/main/kotlin/tribixbite/cleverkeys/HbxQuickActions.kt"
shutil.copy2(here/"HbxQuickActions.kt",quick)

# Two native code bases coexist: ONNX for CleverKeys word prediction and
# llama.cpp for the independent HBX on-device generative models.
native=target/"hbx-native"
native.mkdir(parents=True,exist_ok=True)
for filename in ("engine.cpp","CMakeLists.txt"):
    shutil.copy2(repository/"heliboard/ai/native"/filename,native/filename)

art=repository/"heliboard/overlay/hbx_brand.webp"
if not art.is_file() or art.stat().st_size<1000:
    raise RuntimeError("The owner's HBX artwork is required.")
drawable=target/"res/drawable-nodpi"
drawable.mkdir(parents=True,exist_ok=True)
shutil.copy2(art,drawable/"hbx_brand.webp")
launcher=target/"res/mipmap-anydpi-v26/ic_launcher.xml"
launcher.write_text('''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
  <background android:drawable="@android:color/black"/>
  <foreground android:drawable="@drawable/hbx_brand"/>
</adaptive-icon>
''',encoding="utf-8")

strings=target/"res/values/strings.xml"
raw=strings.read_text(encoding="utf-8")
for name,value in (("app_name","HBX · Clever"),("manifest_ime_service_label","HBX · Clever")):
    pattern=rf'(<string\\s+name="{name}"[^>]*>).*?(</string>)'
    raw,count=re.subn(pattern,lambda m:m.group(1)+value+m.group(2),raw,count=1)
    if count!=1: raise RuntimeError("Cannot locate app label "+name)
strings.write_text(raw,encoding="utf-8")

print("HBX CleverKeys preview prepared from",revision)
print("NOTE: compile native libtecladoai.so before building the APK.")
print("NOTE: preview has a DIFFERENT package; it cannot read existing HBX keys.")
print("No APK was compiled, signed or installed by this script.")
