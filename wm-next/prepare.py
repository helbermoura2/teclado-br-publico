#!/usr/bin/env python3
"""Prepare isolated HBX Next prototype against a pinned WM Keyboard checkout."""
from pathlib import Path
import re
import subprocess
import sys

root = Path(sys.argv[1]).resolve()
upstream = "fd4f78b491b610dec6ea7be9e97ddb6173d88141"
assert subprocess.check_output(["git", "-C", str(root), "rev-parse", "HEAD"], text=True).strip() == upstream, "Unexpected upstream revision"

def patch(path, old, new):
    p = root / path
    s = p.read_text(encoding="utf-8")
    if s.count(old) != 1:
        raise RuntimeError(f"{path}: expected exactly one patch anchor: {old[:100]}")
    p.write_text(s.replace(old, new, 1), encoding="utf-8")

base = "com/wasimaster/wmkeyboard"
common = f"core/common/src/main/java/{base}/core/settings"
ime = f"feature/ime/src/main/java/{base}/ime/ui"
tools = f"core/tools/src/main/java/{base}/core/tools"
clip = f"core/content/src/main/java/{base}/core/clipboard"
patch("app/build.gradle.kts", 'applicationId = "com.wasimaster.wmkeyboard"', 'applicationId = "br.com.tecladobr.wm.next"')
patch("gradle.properties", "wmkb.versionName=0.6.8rc1", "wmkb.versionName=0.1.5-hbx-next")
patch("gradle.properties", "wmkb.versionCode=46", "wmkb.versionCode=51")
# Disable automatic sticker suggestions while typing; manual emoji/stickers remain.
patch(f"core/settings/src/main/java/{base}/core/settings/DynamicStickerSettings.kt",
      "val inTray: Boolean = true,", "val inTray: Boolean = false,")
patch(f"{ime}/StickerOfferUi.kt", """    return when {
        offer.inEmojiPanel -> state.panel == PanelMode.EMOJI
        state.panel != PanelMode.NONE -> false
        offerStyle(state, offer) == StickerSuggestStyle.TRAY -> true
        else -> offer.expanded
    }""", """    return offer.inEmojiPanel && state.panel == PanelMode.EMOJI""")
patch(f"{ime}/StickerOfferUi.kt", """    val offer = state.stickerOffer ?: return false
    return !offer.inEmojiPanel && state.panel == PanelMode.NONE &&
        offerStyle(state, offer) != StickerSuggestStyle.TRAY""", """    // HBX typing always keeps the ordinary word/toolbar strip.
    return false""")
patch(f"{common}/ToolbarTool.kt",
      "listOf(ToolbarTool.EMOJI, ToolbarTool.CLIPBOARD, ToolbarTool.SETTINGS)",
      "listOf(ToolbarTool.AI, ToolbarTool.CLIPBOARD, ToolbarTool.TEXT_EDIT, ToolbarTool.EMOJI, ToolbarTool.TRANSLATE, ToolbarTool.SETTINGS, ToolbarTool.UNDO, ToolbarTool.REDO)")
# Editing: preserve native arrow/selection actions, put Cut in a fifth row
# together with explicit document start/end. Undo/redo are pinned tools.
panel = f"core/language/src/main/java/{base}/core/layout/BuiltInPanelLayouts.kt"
anchor = """                    edit(TextEditAction.BACKSPACE, width = 1.46f),
                ),
"""
patch(panel, anchor, anchor + """                listOf(
                    edit(TextEditAction.CUT, width = 1.47f),
                    edit(TextEditAction.DOC_START, width = 1.47f),
                    edit(TextEditAction.DOC_END, width = 1.46f),
                ),
""")
# Keep the legacy migration fixture aligned with the new shipped arrangement.
legacy = root / f"{common}/TextEditLayout.kt"
s = legacy.read_text(encoding="utf-8")
anchor = re.search(r"(\s+TextEditKey\(TextEditAction\.BACKSPACE, width = 1\.46f\),\s*\),)", s)
if anchor:
    legacy.write_text(s[:anchor.end()] + """
        listOf(
            TextEditKey(TextEditAction.CUT, width = 1.47f),
            TextEditKey(TextEditAction.DOC_START, width = 1.47f),
            TextEditKey(TextEditAction.DOC_END, width = 1.46f),
        ),""" + s[anchor.end():], encoding="utf-8")
# Normal clipboard history is independent of Android's primary clip. Keep a day
# rather than sixty minutes from copy so the normal Android wipe doesn't consume
# the retention window. Never retain flagged or recognizably sensitive text.
p = root / f"{clip}/ClipboardStore.kt"
s = p.read_text(encoding="utf-8")
if s.count("        val whole = text.trim()") != 1:
    raise RuntimeError("Unexpected clipboard input")
s = s.replace("        val whole = text.trim()", "        if (sensitive || ClipSensitivity.looksSensitive(text)) return null\n        val whole = text.trim()", 1)
p.write_text(s, encoding="utf-8")
settings = root / f"core/settings/src/main/java/{base}/core/settings/SettingsRepository.kt"
s = settings.read_text(encoding="utf-8")
# The pinned upstream already defaults to NEVER_SAVE and 24 hours.
# Verify both in ClipboardSettings without touching the separate OTP expiry.
clipboard = re.search(r"(?m)^data class ClipboardSettings\s*\(", s)
if not clipboard:
    raise RuntimeError("Review clipboard settings declaration before building")
next_decl = re.search(r"(?m)^(?:data class|enum class|class|interface)\s+", s[clipboard.end():])
block = s[clipboard.start():clipboard.end() + next_decl.start()] if next_decl else s[clipboard.start():]
if len(re.findall(r"\bval\s+sensitiveHandling\s*:\s*SensitiveClipHandling\s*=\s*SensitiveClipHandling\.NEVER_SAVE\b", block)) != 1:
    raise RuntimeError("Review clipboard sensitive default before building")
if len(re.findall(r"\bval\s+expiryMinutes\s*:\s*Int\s*=\s*(?:24\s*\*\s*60|1440)\s*,", block)) != 1:
    raise RuntimeError("Review clipboard 24-hour expiry before building")
# The first build is a separate app. Do not use a production signing key or
# update.json. Remove screen access/overlay/listener declarations from manifests.
for p in (root / "app/src").rglob("AndroidManifest.xml"):
    s = p.read_text(encoding="utf-8")
    s = re.sub(r"<service\b[^>]*android:permission=\"android\.permission\.BIND_(?:ACCESSIBILITY_SERVICE|NOTIFICATION_LISTENER_SERVICE)\"[^>]*(?:/>|>[\s\S]*?</service>)", "", s)
    s = re.sub(r"<uses-permission\b[^>]*android:name=\"android\.permission\.SYSTEM_ALERT_WINDOW\"[^>]*/>", "", s)
    p.write_text(s, encoding="utf-8")
# Upstream NPU settings still import two icons from the removed extended
# Material Icons library. Use the existing local Material Symbols instead.
npu = f"app/src/main/java/{base}/app/NpuBuildSettings.kt"
kotlin_package = base.replace("/", ".")
for icon in ("Memory", "Speed"):
    patch(npu, f"import androidx.compose.material.icons.outlined.{icon}", f"import {kotlin_package}.core.icons.symbols.outlined.{icon}")
# Check the actual generated imports, not only the source patch anchors.
for line in (root / npu).read_text(encoding="utf-8").splitlines():
    if line.startswith("import ") and "/" in line:
        raise RuntimeError(f"Invalid Kotlin import: {line}")
# Actual provider actions: no mock completion. Keep all existing actions available.
patch(f"{tools}/AiActions.kt", '    const val CUSTOM_ID = "builtin_custom"', '''    const val CUSTOM_ID = "builtin_custom"
    const val HBX_BULLETS_ID = "hbx_bullets"
    const val HBX_TABLE_ID = "hbx_table"''')
patch(f"{tools}/AiActions.kt", """        AiActionSpec(
            id = CUSTOM_ID,""", """        AiActionSpec(
            id = HBX_BULLETS_ID,
            name = "Marcador",
            task = "organize the supplied text into concise bullet points in its original language; preserve facts and never invent details.",
        ),
        AiActionSpec(
            id = HBX_TABLE_ID,
            name = "Tabela",
            task = "organize only the supplied information in a readable Markdown table; use the original language and mark unknown information as unavailable.",
        ),
        AiActionSpec(
            id = CUSTOM_ID,""")
# Built-in local proofreading uses only the selected field and a compact prompt.
# Keep the original engine and the editor's existing replacement guards.
service = f"feature/ime/src/main/java/{base}/ime/WMKeyboardService.kt"
patch(service, "import com.wasimaster.wmkeyboard.core.tools.AiPrompts",
      "import com.wasimaster.wmkeyboard.core.tools.AiPrompts\nimport com.wasimaster.wmkeyboard.core.tools.HbxProofreading")
patch(service, """        val modelId = effectiveLocalModelId(settings)
        val modelFile = effectiveLocalModelFile(settings)
            ?: throw IOException(getString(R.string.ime_ai_error_model_missing))
        val implicitThink = isReasoningModel(modelId)
        val startedAt = SystemClock.uptimeMillis()
        var lastPartialAt = 0L
        val text = AiToolLoop.run(""",
      """        if (!generated && !action.rawPrompt && action.id == BuiltInAiActions.FIX_GRAMMAR_ID &&
            action.task == BuiltInAiActions.byId(BuiltInAiActions.FIX_GRAMMAR_ID)?.task
        ) {
            HbxProofreading.greeting(source)?.let { return AiClient.Completion(it) }
            val file = effectiveLocalModelFile(settings)
                ?: throw IOException(getString(R.string.ime_ai_error_model_missing))
            val raw = LocalLlmEngine.generate(
                context = applicationContext, modelFile = file,
                backend = settings.ai.localBackend, contextTokens = settings.ai.localContextTokens,
                system = HbxProofreading.SYSTEM, user = source,
            )
            return AiClient.Completion(HbxProofreading.finalText(raw, source)
                ?: throw IOException("O modelo não entregou uma revisão pronta para substituir. Tente outro modelo."))
        }
        val modelId = effectiveLocalModelId(settings)
        val modelFile = effectiveLocalModelFile(settings)
            ?: throw IOException(getString(R.string.ime_ai_error_model_missing))
        val implicitThink = isReasoningModel(modelId)
        val startedAt = SystemClock.uptimeMillis()
        var lastPartialAt = 0L
        val text = AiToolLoop.run(""")
dest = root / f"{tools}/HbxProofreading.kt"
dest.write_text((Path(__file__).parent / dest.name).read_text(encoding="utf-8"), encoding="utf-8")
patch(f"{ime}/AiPanel.kt",
      "                rememberModelDownload()?.let { ModelDownloadProgress(it, wide = false) }\n                ActionChips(actions, onAction, enabled = state.aiHasText, focused = focusedResult)",
      "                rememberModelDownload()?.let { ModelDownloadProgress(it, wide = false) }\n                HbxWritingMenu(actions, onAction, enabled = state.aiHasText, focused = focusedResult)")
# The visual menu remains visible before provider setup, but existing
# setup gates still prevent all provider actions until ready.
patch(f"{ime}/AiPanel.kt",
      """                val setupText = if (BuildConfig.ENABLE_LOCAL_LLM) {""",
      """                HbxWritingMenu(actions, onAction, enabled = false, focused = null, providerReady = false)
                Spacer(Modifier.height(6.dp))
                val setupText = if (BuildConfig.ENABLE_LOCAL_LLM) {""")
patch(f"{ime}/AiPanel.kt",
      """                val download = rememberModelDownload()
                if (download != null) {""",
      """                HbxWritingMenu(actions, onAction, enabled = false, focused = null, providerReady = false)
                Spacer(Modifier.height(6.dp))
                val download = rememberModelDownload()
                if (download != null) {""")
dest = root / f"{ime}/HbxWritingMenu.kt"
dest.write_text((Path(__file__).parent / "HbxWritingMenu.kt").read_text(encoding="utf-8"), encoding="utf-8")
test = root / f"app/src/test/java/{base}/core/tools/HbxProofreadingTest.kt"
test.write_text((Path(__file__).parent / test.name).read_text(encoding="utf-8"), encoding="utf-8")
print("HBX Next prepared; isolated package; source and upstream license retained.")

# Isolate the native updater from upstream and official beta20; retain native signature checks.
patch("app/src/main/java/com/wasimaster/wmkeyboard/app/updates/GithubRelease.kt", "const val REPO = \"wasi-master/WMKeyboard\"", "const val REPO = \"helbermoura2/teclado-br-publico\"")
patch("app/src/main/java/com/wasimaster/wmkeyboard/app/updates/GithubRelease.kt", "releases?per_page=5\"", "releases?per_page=30\"")
patch("app/src/main/java/com/wasimaster/wmkeyboard/app/updates/UpdatePrefs.kt", "const val DEFAULT_PRERELEASES = false", "const val DEFAULT_PRERELEASES = true")
patch("app/src/main/java/com/wasimaster/wmkeyboard/app/updates/UpdatePrefs.kt", "private const val FILE_NAME = \"app_updates\"", "private const val FILE_NAME = \"hbx_next_updates\"")
patch("app/src/github/java/com/wasimaster/wmkeyboard/app/updates/ApkDownload.kt", "private const val DIR = \"updates\"", "private const val DIR = \"hbx_next_updates\"")
patch("app/src/main/java/com/wasimaster/wmkeyboard/app/updates/GithubUpdateChecker.kt", "    private val cache: ReleaseCache,\n", "    private val cache: ReleaseCache,\n    private val releaseFilter: (List<GithubRelease>) -> List<GithubRelease> = { it },\n")
patch("app/src/main/java/com/wasimaster/wmkeyboard/app/updates/GithubUpdateChecker.kt", "            releases = releases,", "            releases = releaseFilter(releases),")
patch("app/src/github/java/com/wasimaster/wmkeyboard/app/updates/GithubUpdateManager.kt", "        cache = ReleaseFileCache(ApkStaging.listCache(context)),", "        cache = ReleaseFileCache(ApkStaging.listCache(context)),\n        releaseFilter = HbxNextUpdateChannel::eligible,")
patch("app/src/main/java/com/wasimaster/wmkeyboard/app/updates/UpdateUi.kt", "    val title = R.string.update_row_check_title\n    WmRow(\n        title = stringResource(title),", "    val title = R.string.update_row_check_title\n    WmRow(\n        title = \"ATUALIZAR\",")
(root / "app/src/main/java/com/wasimaster/wmkeyboard/app/updates/HbxNextUpdateChannel.kt").write_text((Path(__file__).parent / "HbxNextUpdateChannel.kt").read_text(encoding="utf-8"), encoding="utf-8")
(root / "app/src/test/java/com/wasimaster/wmkeyboard/app/updates/HbxNextUpdateChannelTest.kt").write_text((Path(__file__).parent / "HbxNextUpdateChannelTest.kt").read_text(encoding="utf-8"), encoding="utf-8")

# The pinned upstream has an ordinary Robolectric test, but declares its
# dependency only inside the docShots flag (which filters out ordinary tests).
# Add the existing catalog dependency to the normal test classpath only.
patch("app/build.gradle.kts", "    testImplementation(libs.junit)\n", "    testImplementation(libs.junit)\n    testImplementation(libs.robolectric)\n")

# Always expose the existing native update state machine on settings home.
patch(f"app/src/main/java/{base}/app/updates/UpdateUi.kt",
      "            UpdateCardKind.NONE -> Unit",
      """            UpdateCardKind.NONE -> UpdateCardFrame(
                title = "ATUALIZAR APLICATIVO",
                body = "Versão instalada: " + BuildConfig.VERSION_NAME + " · " + stringResource(shown.checkSubtitle()),
                modifier = modifier,
                busy = shown == UpdateState.Checking,
            ) {
                Button(
                    onClick = { updater.check(userAsked = true) },
                    enabled = shown != UpdateState.Checking && shown != UpdateState.Unsupported,
                ) { Text("Verificar atualizações") }
            }""")
# Manual checks should give immediate feedback even if a background check is running.
patch(f"app/src/github/java/{base}/app/updates/GithubUpdateManager.kt",
      "        if (running?.isActive == true) return\n        val app = context.applicationContext\n        val prefs = UpdatePrefs(app)\n        if (_switching.value)",
      """        if (running?.isActive == true) {
            if (userAsked && (_state.value == UpdateState.Idle || _state.value is UpdateState.UpToDate || _state.value is UpdateState.Failed)) {
                val previous = running
                _state.value = UpdateState.Checking
                running = scope.launch {
                    previous?.join()
                    runCheck(context.applicationContext, UpdatePrefs(context.applicationContext), true, allowPrompt)
                }
            }
            return
        }
        val app = context.applicationContext
        val prefs = UpdatePrefs(app)
        if (_switching.value)""")
