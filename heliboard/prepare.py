#!/usr/bin/env python3
"""Apply the small, auditable Teclado BR overlay to the pinned HeliBoard tree."""
# SPDX-License-Identifier: GPL-3.0-only
import json
from pathlib import Path
import shutil
import sys
import xml.etree.ElementTree as ET

here = Path(__file__).resolve().parent
root = Path(sys.argv[1]).resolve()
lock = json.loads((here / 'upstream.json').read_text())
from patches import PatchContext
from patches import suggestions, typing, assistant, visual, toolbar, translation
context = PatchContext(root, here)
replace = context.replace
if (root/'.git').exists():
    import subprocess
    actual = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip()
    if actual != lock['commit']:
        raise RuntimeError('Prepare requires the pinned upstream commit from upstream.json')

replace('app/build.gradle.kts', 'applicationId = "helium314.keyboard"', 'applicationId = "br.com.tecladobr.heli"')
replace('app/build.gradle.kts', 'versionCode = 3603', 'versionCode = 9019')
replace('app/build.gradle.kts', 'android {', 'android {\n    signingConfigs.getByName("debug") {\n        storeFile = File(System.getenv("HOME") ?: System.getProperty("user.home"), ".android/debug.keystore")\n        storePassword = "android"\n        keyAlias = "androiddebugkey"\n        keyPassword = "android"\n    }')
replace('app/build.gradle.kts', 'versionName = "3.6"', 'versionName = "0.9.2-beta.18-heli3.6"')
# Robolectric 4.14.1 uses its own MavenArtifactFetcher at unit-test runtime,
# independent of Gradle's repositories. repo1.maven.org can intermittently
# reject Android SDK jars, causing many unrelated JVM tests to fail together.
# Use the official Maven Central endpoint that Gradle also uses and prefetch
# the pinned SDK with checksum verification in CI.
replace('app/build.gradle.kts',
    '            isIncludeAndroidResources = true',
    '            isIncludeAndroidResources = true\n'
    '            all {\n'
    '                it.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")\n'
    '                it.systemProperty("robolectric.dependency.repo.id", "mavenCentral")\n'
    '            }')
replace('app/build.gradle.kts', 'listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")', 'listOf("arm64-v8a", "x86_64")')
replace('app/src/main/java/helium314/keyboard/latin/App.kt', '        AppUpgrade.transferOldPinnedClips(this) // todo: remove in a few months, maybe mid 2026', '        AppUpgrade.transferOldPinnedClips(this) // todo: remove in a few months, maybe mid 2026\n        configureTecladoBr(this)\n        SubtypeSettings.reloadEnabledSubtypes(this)')
replace('app/src/main/res/values/donottranslate.xml', '<item>qwerty</item>', '<item>qwerty</item>\n        <item>tecladobr</item>')
replace('app/src/main/res/values/donottranslate.xml', '<item>QWERTY</item>', '<item>QWERTY</item>\n        <item>Português BR</item>')
shutil.copyfile(here/'overlay/TecladoBrDefaults.kt', root/'app/src/main/java/helium314/keyboard/latin/TecladoBrDefaults.kt')
layout = root/'app/src/main/assets/layouts/main/tecladobr.txt'
layout.write_text('q\nw\ne\nr\nt\ny\nu\ni\no\np\n\na\ns\nd\nf\ng\nh\nj\nk\nl\n\nz\nx\nc\nv\nb\nn\nm\n')
# Put the native emoji key immediately after 123, preserving the globe.
functional = root/'app/src/main/assets/layouts/functional/functional_keys.json'
rows = json.loads(functional.read_text())
emoji = rows[1].pop(3)
assert 'emojiKeyEnabled' in emoji
rows[1].insert(1, emoji)
functional.write_text(json.dumps(rows, ensure_ascii=False, indent=2) + '\n')
# Visual resources only: the input engine and touch dispatch stay upstream.
shutil.copyfile(here/'overlay/teclado_br_more.xml', root/'app/src/main/res/drawable/teclado_br_more.xml')
replace('app/src/main/res/values/config.xml', '<fraction name="config_key_vertical_gap_holo">6.127%p</fraction>', '<fraction name="config_key_vertical_gap_holo">4.6%p</fraction>')
replace('app/src/main/res/values/config.xml', '<fraction name="config_key_horizontal_gap_holo">1.739%p</fraction>', '<fraction name="config_key_horizontal_gap_holo">1.35%p</fraction>')
replace('app/src/main/res/values/config.xml', '<fraction name="config_key_hint_letter_ratio_lxx">25%</fraction>', '<fraction name="config_key_hint_letter_ratio_lxx">32%</fraction>')
for name in ('btn_keyboard_key_normal_lxx_base_border.xml', 'btn_keyboard_key_pressed_lxx_base_border.xml'):
    file = root/'app/src/main/res/drawable'/name
    file.write_text(file.read_text().replace('5dp', '7dp'))
replace('app/src/main/java/helium314/keyboard/keyboard/KeyboardView.java',
    '        mTypeface = Settings.getInstance().getCustomTypeface();',
    '        final Typeface customTypeface = Settings.getInstance().getCustomTypeface();\n        mTypeface = customTypeface != null ? customTypeface : Typeface.create("sans-serif-condensed", Typeface.NORMAL);')
# Keep PT-BR and English data in this trial; other languages remain importable.
for dictionary in (root/'app/src/main/assets/dicts').glob('main_*.dict'):
    if dictionary.name not in ('main_pt-BR.dict', 'main_en-US.dict'):
        dictionary.unlink()
# Preserve authors/license notices. Mark the variant distinctly in every translated label.
for directory in (root/'app/src').glob('*/res/values*'):
    for file in directory.glob('*.xml'):
        text = file.read_text()
        import re
        text = re.sub(r'(<string\b[^>]*\bname="english_ime_name"[^>]*>).*?(</string>)',
                      r'\1HBX Keyboard\2', text, flags=re.S)
        # Debug resource variants can independently override the launcher/IME
        # label. Update their spelling/settings labels as well, without
        # changing package IDs, provider authorities or persisted preferences.
        text = re.sub(
            r'(<string\b[^>]*\bname="ime_settings"[^>]*>).*?(</string>)',
            r'\1HBX Keyboard - Configurações\2', text, flags=re.S)
        text = re.sub(
            r'(<string\b[^>]*\bname="spell_checker_service_name"[^>]*>).*?(</string>)',
            r'\1HBX Keyboard - Corretor ortográfico\2', text, flags=re.S)
        text = text.replace('helium314.keyboard.debug.clipprovider', 'br.com.tecladobr.heli.debug.clipprovider')
        text = text.replace('helium314.keyboard.debug.provider', 'br.com.tecladobr.heli.debug.provider')
        file.write_text(text)
# Official HBX artwork supplied by the project owner. Both adaptive icons
# use the same art, and the launcher background is black to preserve contrast.
# Android minSdk is 26: adaptive icons apply on every supported device.
# Intentionally omit upstream HeliBoard monochrome artwork so icon themes
# cannot accidentally display the old HeliBoard mark.
hbx_icon = here/'overlay/hbx_brand.webp'
assert hbx_icon.is_file() and hbx_icon.stat().st_size > 1000, 'Missing HBX brand image'
drawable_nodpi = root/'app/src/main/res/drawable-nodpi'
drawable_nodpi.mkdir(parents=True, exist_ok=True)
shutil.copyfile(hbx_icon, drawable_nodpi/'hbx_brand.webp')
for icon_name in ('ic_launcher.xml', 'ic_launcher_round.xml'):
    adaptive = root/'app/src/main/res/mipmap-anydpi-v26'/icon_name
    source = adaptive.read_text()
    assert '@drawable/ic_launcher_foreground' in source, icon_name
    assert '@drawable/ic_launcher_background' in source, icon_name
    source = source.replace('@drawable/ic_launcher_foreground', '@drawable/hbx_brand')
    source = source.replace('@drawable/ic_launcher_background', '@android:color/black')
    source = re.sub(r'\s*<monochrome\s+android:drawable="@drawable/hbx_brand"\s*/>', '', source)
    adaptive.write_text(source)

# Add a separate-process editor and framework instrumentation to the trial build only.
source = root/'app/src/debugNoMinify/java/br/com/tecladobr/heli/testing'
source.mkdir(parents=True, exist_ok=True)
for name in ('TrialEditor.java', 'HeliTrialInstrumentation.java', 'HybridTrial.java', 'ToolbarTrial.java', 'TypingTrial.java'):
    if (here/'overlay'/name).exists(): shutil.copyfile(here/'overlay'/name, source/name)
manifest = root/'app/src/debugNoMinify/AndroidManifest.xml'
manifest.write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android">
<instrumentation android:name="br.com.tecladobr.heli.testing.HeliTrialInstrumentation" android:targetPackage="br.com.tecladobr.heli.debug" android:functionalTest="true"/>
<application><activity android:name="br.com.tecladobr.heli.testing.TrialEditor" android:exported="true" android:process=":typing" android:theme="@android:style/Theme.Material.NoActionBar" android:windowSoftInputMode="adjustResize"/></application>
</manifest>''')
# The complete source archive accompanies every APK; no model is bundled or simulated.
shutil.copyfile(here/'README.md', root/'TECLADO-BR.md')
shutil.copyfile(here/'upstream.json', root/'TECLADO-BR-UPSTREAM.json')

for module in (suggestions, typing, assistant, visual, toolbar, translation):
    module.apply(context)
# Native/JVM assertions for the new writing actions ship with the prepared source.
ai_tests = root/'app/src/test/java/helium314/keyboard/latin/ai'
ai_tests.mkdir(parents=True, exist_ok=True)
shutil.copyfile(here/'tests/PromptBuilderOneUiTest.java', ai_tests/'PromptBuilderOneUiTest.java')
shutil.copyfile(here/'tests/UpdatePolicyTest.java', ai_tests/'UpdatePolicyTest.java')
shutil.copyfile(here/'tests/GeminiDiagnosticTest.java', ai_tests/'GeminiDiagnosticTest.java')
shutil.copyfile(here/'tests/GeminiCascadeTest.java', ai_tests/'GeminiCascadeTest.java')
shutil.copyfile(here/'tests/GeminiShortRequestTest.java', ai_tests/'GeminiShortRequestTest.java')
context.report(lock)
shutil.copytree(here, root/'teclado-br-overlay', dirs_exist_ok=True, ignore=shutil.ignore_patterns('__pycache__', '*.pyc'))
print('Prepared', lock['trial_version'], 'from', lock['commit'])


# Public builders never possess the private update signing key.
import os
if os.environ.get("TECLADO_UNSIGNED_BUILD") == "1":
    with (root/"app/build.gradle.kts").open("a") as build:
        build.write("\nandroid.buildTypes.configureEach { signingConfig = null }\n")

# Verify the production Instagram workaround without weakening private fields.
compat_tests = root/'app/src/test/java/helium314/keyboard/compat'
compat_tests.mkdir(parents=True, exist_ok=True)
shutil.copyfile(here/'tests/MessagingInputTypeTest.java', compat_tests/'MessagingInputTypeTest.java')
