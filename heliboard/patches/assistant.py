# SPDX-License-Identifier: GPL-3.0-only
"""Focused assistant integration against the pinned upstream."""
import json
import shutil
from pathlib import Path

def apply(context):
    root, here, replace = context.root, context.here, context.replace
    # Explicit writing tasks can use the opt-in Groq transport or the native offline runtime.
    replace('app/build.gradle.kts', 'minSdk = 21', 'minSdk = 26')
    ai_java = root/'app/src/main/java/helium314/keyboard/latin/ai'
    shutil.copytree(here/'ai/java', ai_java, dirs_exist_ok=True)
    shutil.copytree(here/'ai/native', root/'ai-native', dirs_exist_ok=True)
    shutil.copyfile(root/'ai-native/llama.cpp/LICENSE', root/'app/src/main/assets/TECLADO-BR-LLAMA-LICENSE.txt')
    replace('app/src/main/java/helium314/keyboard/latin/utils/ToolbarUtils.kt',
        '    LANGUAGE -> KeyCode.LANGUAGE_SWITCH', '    AI -> -11050\n    LANGUAGE -> KeyCode.LANGUAGE_SWITCH')
    replace('app/src/main/java/helium314/keyboard/latin/utils/ToolbarUtils.kt',
        'PAGE_START, PAGE_END, LANGUAGE\n}', 'PAGE_START, PAGE_END, LANGUAGE, AI\n}')
    for style in ('holo', 'lxx', 'rounded'):
        replace('app/src/main/java/helium314/keyboard/keyboard/internal/KeyboardIconsSet.kt',
            f'                    ToolbarKey.EMOJI -> R.drawable.sym_keyboard_smiley_{style}',
            f'                    ToolbarKey.AI -> R.drawable.teclado_br_ai\n                    ToolbarKey.EMOJI -> R.drawable.sym_keyboard_smiley_{style}')
    (root/'app/src/main/res/drawable/teclado_br_ai.xml').write_text('''<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24"><path android:fillColor="#FFFFFFFF" android:pathData="M12,2 L14.8,9.2 L22,12 L14.8,14.8 L12,22 L9.2,14.8 L2,12 L9.2,9.2 Z M21,1 L21.8,3.2 L24,4 L21.8,4.8 L21,7 L20.2,4.8 L18,4 L20.2,3.2 Z"/></vector>''')
    strings = root/'app/src/main/res/values/strings.xml'
    strings.write_text(strings.read_text().replace('</resources>', '<string name="ai">Assistente de escrita</string>\n</resources>'))
    replace('app/src/main/java/helium314/keyboard/latin/inputlogic/InputLogic.java',
        '            case KeyCode.LANGUAGE_SWITCH:',
        '            case -11050:\n                helium314.keyboard.latin.ai.WritingLauncher.open(mLatinIME);\n                break;\n            case KeyCode.LANGUAGE_SWITCH:')
    manifest = root/'app/src/main/AndroidManifest.xml'
    manifest.write_text(manifest.read_text().replace('<uses-permission android:name="android.permission.VIBRATE" />', '<uses-permission android:name="android.permission.VIBRATE" />\n    <uses-permission android:name="android.permission.INTERNET" />\n    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />\n    <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />').replace('android:supportsRtl="true"', 'android:supportsRtl="true" android:networkSecurityConfig="@xml/teclado_network_security"').replace('        <!-- Services -->', '''        <activity android:name=".ai.ModelSettingsActivity" android:exported="false"
                android:directBootAware="false" android:excludeFromRecents="true"
                android:theme="@android:style/Theme.Material.NoActionBar"/>
            <activity android:name=".ai.DictionaryActivity" android:exported="false" android:directBootAware="false" android:theme="@android:style/Theme.Material.NoActionBar"/>
            <activity android:name=".ai.AppearanceActivity" android:exported="false" android:directBootAware="false" android:theme="@android:style/Theme.Material.NoActionBar"/>
            <activity android:name=".ai.UpdateActivity" android:exported="false"
                android:directBootAware="false" android:excludeFromRecents="true"
                android:theme="@android:style/Theme.Material.NoActionBar"/>
            <provider android:name="androidx.core.content.FileProvider"
                android:authorities="${applicationId}.teclado_updates"
                android:exported="false" android:grantUriPermissions="true">
                <meta-data android:name="android.support.FILE_PROVIDER_PATHS"
                    android:resource="@xml/teclado_update_paths"/>
            </provider>
            <service android:name=".ai.WritingEngineService" android:exported="false"
                android:process=":writing" android:directBootAware="false"/>
            <!-- Services -->'''))
    # Normal transport accepts HTTPS only. The debug harness uses a fake-key loopback server.
    (root/'app/src/main/res/xml/teclado_network_security.xml').write_text('''<network-security-config><base-config cleartextTrafficPermitted="false"/><domain-config cleartextTrafficPermitted="true"><domain>127.0.0.1</domain></domain-config></network-security-config>''')
    # Only the app-private, verified update APK can be exposed to Android's package installer.
    (root/'app/src/main/res/xml/teclado_update_paths.xml').write_text(
        '<paths xmlns:android="http://schemas.android.com/apk/res/android">'
        '<cache-path name="verified_apk" path="teclado-updates/"/>'
        '</paths>')
    # Overlay stays inside the existing IME window, preserving the current app/input connection.
    replace('app/src/main/java/helium314/keyboard/latin/LatinIME.java',
        '    public void onEvent(@NonNull final Event event) {',
        '    public void onEvent(@NonNull final Event event) {\n        if (mSuggestionStripView != null && (event.getCodePoint() >= 0 || event.getKeyCode() == KeyCode.DELETE)) mSuggestionStripView.onKeyboardInput();\n        if (helium314.keyboard.latin.ai.WritingPanel.key(event)) { mKeyboardSwitcher.onEvent(event, 0, 0); return; }')
    replace('app/src/main/java/helium314/keyboard/latin/LatinIME.java',
        '    public void onTextInput(final String rawText) {',
        '    public void onTextInput(final String rawText) {\n        if (mSuggestionStripView != null) mSuggestionStripView.onKeyboardInput();\n        if (helium314.keyboard.latin.ai.WritingPanel.text(rawText)) return;')
    replace('app/src/main/java/helium314/keyboard/latin/LatinIME.java',
        '    public int getCurrentAutoCapsState() {',
        '    public int getCurrentAutoCapsState() {\n        if (helium314.keyboard.latin.ai.WritingPanel.isEditing()) return 0;')
    replace('app/src/main/java/helium314/keyboard/latin/LatinIME.java',
        '    public int getCurrentRecapitalizeState() {',
        '    public int getCurrentRecapitalizeState() {\n        if (helium314.keyboard.latin.ai.WritingPanel.isEditing()) return 0;')
    replace('app/src/main/java/helium314/keyboard/latin/LatinIME.java',
        '        super.onComputeInsets(outInsets);',
        '        super.onComputeInsets(outInsets);\n        if (mInputView != null && helium314.keyboard.latin.ai.WritingPanel.insets(outInsets, mInputView)) { if (mInsetsUpdater != null) mInsetsUpdater.setInsets(outInsets); return; }')
    replace('app/src/main/java/helium314/keyboard/latin/InputView.java',
        '    public boolean onInterceptTouchEvent(final MotionEvent me) {',
        '    public boolean onInterceptTouchEvent(final MotionEvent me) {\n        if (helium314.keyboard.latin.ai.WritingPanel.isActive()) { mActiveForwarder = null; return false; }')
    replace('app/src/main/java/helium314/keyboard/latin/LatinIME.java',
        '\n    public void onStartInput(final EditorInfo editorInfo, final boolean restarting) {',
        '\n    public void onStartInput(final EditorInfo editorInfo, final boolean restarting) {\n        helium314.keyboard.latin.ai.WritingPanel.onStart(editorInfo, restarting);')
    for method in ('onFinishInputView(final boolean finishingInput)', 'onFinishInput()', 'onDestroy()'):
        replace('app/src/main/java/helium314/keyboard/latin/LatinIME.java',
            '\n    public void ' + method + ' {',
            '\n    public void ' + method + ' {\n        helium314.keyboard.latin.ai.WritingPanel.dismiss();')
    for method in ('onStartBatchInput()', 'onUpdateBatchInput(final InputPointers batchPointers)',
                   'onEndBatchInput(final InputPointers batchPointers)', 'onCancelBatchInput()'):
        replace('app/src/main/java/helium314/keyboard/latin/LatinIME.java',
            '\n    public void ' + method + ' {',
            '\n    public void ' + method + ' {\n        if (helium314.keyboard.latin.ai.WritingPanel.isEditing()) return;')
    replace('app/src/main/java/helium314/keyboard/latin/LatinIME.java',
        '    private View mInputView;',
        '    private View mInputView;\n    public View getTecladoInputView() { return mInputView; }\n    public void prepareTecladoWriting() { mInputLogic.finishInput(); mHandler.cancelUpdateSuggestionStrip(); }')

