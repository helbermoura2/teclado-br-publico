package com.wasimaster.wmkeyboard.ime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
internal fun HbxWritingMenu(actions: List<AiActionSpec>, onAction: (AiActionSpec) -> Unit, enabled: Boolean, focused: Int?, providerReady: Boolean = true) {
    val groups = listOf(
        listOf(Triple(BuiltInAiActions.FIX_GRAMMAR_ID, "abc✓", "Ortografia e gramática"), Triple(BuiltInAiActions.REWRITE_ID, "≋", "Estilo de escrita"), Triple(BuiltInAiActions.SUMMARIZE_ID, "☷", "Resumir")),
        listOf(Triple(BuiltInAiActions.HBX_BULLETS_ID, "☷", "Marcadores"), Triple(BuiltInAiActions.HBX_TABLE_ID, "▦", "Tabela"), Triple(BuiltInAiActions.CUSTOM_ID, "✎", "Compositor")),
    )
    val pinned = groups.flatten().map { it.first }.toSet()
    val bg = Color(0xFF101010)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(bg).border(1.dp, Color(0xFF353535), RoundedCornerShape(20.dp)).padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("✦  Ferramentas de escrita", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
        groups.forEach { group ->
            val row = group.mapNotNull { (id, icon, title) -> actions.firstOrNull { it.id == id }?.let { Triple(it, icon, title) } }
            if (row.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFF202020)), verticalAlignment = Alignment.CenterVertically) {
                    row.forEachIndexed { i, (action, icon, title) ->
                        val active = providerReady && (enabled || action.worksWithoutText)
                        if (i > 0) Spacer(Modifier.width(1.dp).height(32.dp).background(Color(0xFF424242)))
                        Column(Modifier.weight(1f).heightIn(min = 52.dp)
                            .focusRing(actions.indexOf(action) == focused)
                            .clickable(enabled = active) { onAction(action) }.padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            val ink = Color.White.copy(alpha = if (active) 1f else .55f)
                            Text(icon, color = ink, fontSize = 21.sp, fontWeight = FontWeight.Medium)
                            Text(title, color = ink, fontSize = 12.sp, lineHeight = 14.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
        val extra = actions.filter { it.id !in pinned }
        if (extra.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                extra.forEach { action ->
                    val active = providerReady && (enabled || action.worksWithoutText)
                    Text(aiActionLabel(action), color = Color.White.copy(alpha = if (active) 1f else .55f), fontSize = 12.sp,
                        modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(Color(0xFF222222))
                            .focusRing(actions.indexOf(action) == focused).clickable(enabled = active) { onAction(action) }.padding(horizontal = 12.dp, vertical = 8.dp))
                }
            }
        }
    }
}
