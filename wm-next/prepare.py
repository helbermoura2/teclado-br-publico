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
patch("gradle.properties", "wmkb.versionName=0.6.8rc1", "wmkb.versionName=0.1.0-hbx-next")
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
patch(f"{ime}/AiPanel.kt",
      "                rememberModelDownload()?.let { ModelDownloadProgress(it, wide = false) }\n                ActionChips(actions, onAction, enabled = state.aiHasText, focused = focusedResult)",
      "                rememberModelDownload()?.let { ModelDownloadProgress(it, wide = false) }\n                HbxWritingMenu(actions, onAction, enabled = state.aiHasText, focused = focusedResult)")
dest = root / f"{ime}/HbxWritingMenu.kt"
dest.write_text((Path(__file__).parent / "HbxWritingMenu.kt").read_text(encoding="utf-8"), encoding="utf-8")
print("HBX Next prepared; isolated package; source and upstream license retained.")
