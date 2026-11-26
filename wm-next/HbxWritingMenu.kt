package com.wasimaster.wmkeyboard.ime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wasimaster.wmkeyboard.core.tools.AiActionSpec
import com.wasimaster.wmkeyboard.core.tools.BuiltInAiActions

/** Original translucent writing surface inspired by the user's reference.
 * Uses the existing explicit-action callbacks and native secure-field gates.
 * No Samsung assets, external screen reading or network calls from the UI.
 */
@Composable
internal fun HbxWritingMenu(actions: List<AiActionSpec>, onAction: (AiActionSpec) -> Unit, enabled: Boolean, focused: Int?) {
    val groups = listOf(
        listOf(Triple(BuiltInAiActions.FIX_GRAMMAR_ID, "abc✓", "Ortografia e gramática"), Triple(BuiltInAiActions.REWRITE_ID, "≋", "Estilo de escrita"), Triple(BuiltInAiActions.SUMMARIZE_ID, "☷", "Resumir")),
        listOf(Triple(BuiltInAiActions.HBX_BULLETS_ID, "☷", "Marcador"), Triple(BuiltInAiActions.HBX_TABLE_ID, "▦", "Tabela")),
        listOf(Triple(BuiltInAiActions.CUSTOM_ID, "✎", "Compositor")),
    )
    val pinned = groups.flatten().map { it.first }.toSet()
    val bg = Brush.linearGradient(listOf(Color(0xFF485F83), Color(0xFF486F72), Color(0xFF726E96)))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(bg).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        groups.forEach { group ->
            val row = group.mapNotNull { (id, icon, title) -> actions.firstOrNull { it.id == id }?.let { Triple(it, icon, title) } }
            if (row.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(0x77414D58)), verticalAlignment = Alignment.CenterVertically) {
                    row.forEachIndexed { i, (action, icon, title) ->
                        val active = enabled || action.worksWithoutText
                        if (i > 0) Spacer(Modifier.width(1.dp).height(40.dp).background(Color.White.copy(alpha = .22f)))
                        Column(Modifier.weight(1f).heightIn(min = if (group.size == 3) 76.dp else 48.dp)
                            .focusRing(actions.indexOf(action) == focused)
                            .clickable(enabled = active) { onAction(action) }.padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            val ink = Color.White.copy(alpha = if (active) 1f else .42f)
                            Text(icon, color = ink, fontSize = 19.sp, fontWeight = FontWeight.Medium)
                            Text(title, color = ink, fontSize = 12.sp, lineHeight = 15.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
        val extra = actions.filter { it.id !in pinned }
        if (extra.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                extra.forEach { action ->
                    val active = enabled || action.worksWithoutText
                    Text(aiActionLabel(action), color = Color.White.copy(alpha = if (active) 1f else .42f), fontSize = 12.sp,
                        modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(Color(0x66313A45))
                            .focusRing(actions.indexOf(action) == focused).clickable(enabled = active) { onAction(action) }.padding(10.dp))
                }
            }
        }
    }
}
