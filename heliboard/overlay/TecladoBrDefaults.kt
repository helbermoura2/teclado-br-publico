// SPDX-License-Identifier: GPL-3.0-only
// Teclado BR private trial customization, 2026-10-31. AI-assisted development.
package helium314.keyboard.latin

import android.content.Context
import android.graphics.Color
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.DefaultColors
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.SettingsSubtype.Companion.toSettingsSubtype
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.settings.SettingsSubtype
import helium314.keyboard.latin.utils.LayoutType
import helium314.keyboard.latin.utils.ToolbarKey
import helium314.keyboard.latin.utils.prefs
import java.util.Locale

fun configureTecladoBr(context: Context) {
    if (BuildConfig.BUILD_TYPE != "debugNoMinify") return
    Typography.load(context)
    val prefs = context.prefs()
    if (!prefs.getBoolean("teclado_br_defaults_v2", false)) {
    val subtype = SettingsSubtype(Locale.forLanguageTag("pt-BR"), "")
        .withLayout(LayoutType.MAIN, "tecladobr").toPref()
    val keys = listOf(ToolbarKey.SETTINGS, ToolbarKey.CLIPBOARD, ToolbarKey.EMOJI,
        ToolbarKey.SELECT_ALL, ToolbarKey.COPY, ToolbarKey.CUT, ToolbarKey.PASTE,
        ToolbarKey.LEFT, ToolbarKey.RIGHT, ToolbarKey.UNDO, ToolbarKey.REDO)
    prefs.edit()
        .putString(Settings.PREF_ADDITIONAL_SUBTYPES, subtype)
        .putString(Settings.PREF_ENABLED_SUBTYPES, subtype)
        .putString(Settings.PREF_SELECTED_SUBTYPE, subtype)
        .putString(Settings.PREF_THEME_COLORS, "darker")
        .putString(Settings.PREF_THEME_COLORS_NIGHT, "darker")
        .putBoolean(Settings.PREF_THEME_DAY_NIGHT, false)
        .putBoolean(Settings.PREF_THEME_KEY_BORDERS, true)
        .putBoolean(Settings.PREF_SHOW_NUMBER_ROW, true)
        .putBoolean(Settings.PREF_AUTO_CAP, true)
        .putBoolean(Settings.PREF_AUTO_CORRECTION, true)
        .putBoolean(Settings.PREF_SHOW_SUGGESTIONS, true)
        .putBoolean(Settings.PREF_BIGRAM_PREDICTIONS, true)
        .putBoolean(Settings.PREF_USE_CONTACTS, false)
        .putBoolean(Settings.PREF_USE_APPS, false)
        .putString(Settings.PREF_TOOLBAR_MODE, "EXPANDABLE")
        .putBoolean(Settings.PREF_AUTO_SHOW_TOOLBAR, true)
        .putBoolean(Settings.PREF_AUTO_HIDE_TOOLBAR, true)
        .putString(Settings.PREF_TOOLBAR_KEYS, keys.joinToString("|") { "${it.name}:true" })
        .putBoolean("teclado_br_defaults_v2", true)
        .commit()
    }
    if (!prefs.getBoolean("teclado_br_languages_pins_v1", false)) {
        val enabled = SubtypeSettings.createSettingsSubtypes(prefs.getString(Settings.PREF_ENABLED_SUBTYPES, Defaults.PREF_ENABLED_SUBTYPES)!!).toMutableList()
        if (enabled.none { it.locale == Locale.US }) {
            enabled.add(SubtypeSettings.getResourceSubtypesForLocale(Locale.US).first().toSettingsSubtype())
        }
        val pinned = prefs.getString(Settings.PREF_PINNED_TOOLBAR_KEYS, Defaults.PREF_PINNED_TOOLBAR_KEYS)!!
            .split("|").filterNot { it.startsWith("CLIPBOARD:") }.toMutableList()
        pinned.add(0, "CLIPBOARD:true")
        prefs.edit()
            .putString(Settings.PREF_ENABLED_SUBTYPES, SubtypeSettings.createPrefSubtypes(enabled))
            .putBoolean(Settings.PREF_SHOW_LANGUAGE_SWITCH_KEY, true)
            .putString(Settings.PREF_LANGUAGE_SWITCH_KEY, "internal")
            .putBoolean(Settings.PREF_ENABLE_CLIPBOARD_HISTORY, true)
            .putBoolean(Settings.PREF_CLIPBOARD_HISTORY_PINNED_FIRST, true)
            .putString(Settings.PREF_PINNED_TOOLBAR_KEYS, pinned.joinToString("|"))
            .putBoolean("teclado_br_languages_pins_v1", true)
            .commit()
    }
    if (!prefs.getBoolean("teclado_br_layout_v2", false)) {
        prefs.edit()
            .putBoolean(Settings.PREF_SHOW_EMOJI_KEY, true)
            .putBoolean("teclado_br_layout_v2", true)
            .commit()
    }
    if (!prefs.getBoolean("teclado_br_typing_v1", false)) {
        val keys = prefs.getString(Settings.PREF_TOOLBAR_KEYS, Defaults.PREF_TOOLBAR_KEYS)!!
            .split("|").filterNot { it.startsWith("LANGUAGE:") || it.startsWith("EMOJI:") }
        prefs.edit()
            .putBoolean(Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, true)
            .putBoolean(Settings.PREF_SHOW_LANGUAGE_SWITCH_KEY, false)
            .putBoolean(Settings.PREF_SHOW_EMOJI_KEY, false)
            .putBoolean(Settings.PREF_SPACE_TO_CHANGE_LANG, true)
            .putBoolean(Settings.PREF_KEY_USE_PERSONALIZED_DICTS, true)
            .putBoolean(Settings.PREF_ADD_TO_PERSONAL_DICTIONARY, true)
            .putString(Settings.PREF_TOOLBAR_KEYS, (listOf("LANGUAGE:true", "EMOJI:true") + keys).joinToString("|"))
            .putBoolean("teclado_br_typing_v1", true)
            .commit()
    }
    if (!prefs.getBoolean("teclado_br_learning_v2", false)) {
        prefs.edit()
            .putBoolean(Settings.PREF_SHOW_SUGGESTIONS, true)
            .putBoolean(Settings.PREF_BIGRAM_PREDICTIONS, true)
            .putBoolean(Settings.PREF_KEY_USE_PERSONALIZED_DICTS, true)
            .putBoolean(Settings.PREF_ADD_TO_PERSONAL_DICTIONARY, true)
            .putBoolean("teclado_br_learning_v2", true)
            .commit()
    }
    if (!prefs.getBoolean("teclado_br_offline_ai_v1", false)) {
        val keys = prefs.getString(Settings.PREF_TOOLBAR_KEYS, Defaults.PREF_TOOLBAR_KEYS)!!
            .split("|").filterNot { it.startsWith("AI:") }
        prefs.edit()
            .putString(Settings.PREF_TOOLBAR_KEYS, (listOf("AI:true") + keys).joinToString("|"))
            .putBoolean("teclado_br_offline_ai_v1", true)
            .commit()
    }
    if (!prefs.getBoolean("teclado_br_visual_v1", false)) {
        val theme = "Teclado BR · escuro"
        val blue = Color.rgb(109, 159, 231)
        val base = DefaultColors("Material", true,
            Color.rgb(23, 23, 25), Color.BLACK, Color.rgb(49, 49, 51),
            Color.rgb(20, 20, 22), Color.rgb(53, 53, 55), Color.rgb(242, 242, 244),
            Color.rgb(140, 140, 145), Color.rgb(235, 235, 238), Color.rgb(220, 220, 223), blue)
        val colors = ColorType.entries.associateWith { base.get(it) }.toMutableMap()
        colors[ColorType.ACTION_KEY_BACKGROUND] = Color.rgb(20, 20, 22)
        colors[ColorType.ACTION_KEY_ICON] = Color.WHITE
        colors[ColorType.SHIFT_KEY_ICON] = blue
        colors[ColorType.CLIPBOARD_PIN] = blue
        colors[ColorType.STRIP_BACKGROUND] = Color.rgb(23, 23, 25)
        colors[ColorType.TOOL_BAR_EXPAND_KEY_BACKGROUND] = Color.rgb(42, 42, 45)
        prefs.edit()
            .putString(Settings.PREF_THEME_STYLE, "Material")
            .putBoolean(Settings.PREF_THEME_KEY_BORDERS, true)
            .putBoolean(Settings.PREF_THEME_DAY_NIGHT, false)
            .putString(Settings.PREF_THEME_COLORS, theme)
            .putString(Settings.PREF_THEME_COLORS_NIGHT, theme)
            .putInt(Settings.PREF_USER_MORE_COLORS_PREFIX + theme, 2)
            .putString(Settings.PREF_USER_ALL_COLORS_PREFIX + theme, colors.entries.joinToString(";") { "${it.key},${it.value}" })
            .putFloat(Settings.PREF_KEYBOARD_HEIGHT_SCALE_PREFIX + "_false", 1.10f)
            .putBoolean(Settings.PREF_NARROW_KEY_GAPS, false)
            .putBoolean("teclado_br_visual_v1", true)
            .commit()
    }

    if (!prefs.getBoolean("teclado_br_hint_legibility_v1", false)) {
        val colorKey = Settings.PREF_USER_ALL_COLORS_PREFIX + "Teclado BR · escuro"
        val colors = prefs.getString(colorKey, "")!!.split(";")
            .filter { it.isNotBlank() && !it.startsWith("${ColorType.KEY_HINT_TEXT},") }
        prefs.edit()
            .putString(colorKey, (colors + "${ColorType.KEY_HINT_TEXT},${Color.rgb(210, 210, 214)}").joinToString(";"))
            .putBoolean("teclado_br_hint_legibility_v1", true)
            .commit()
    }

    // Apply the requested sentence-aware compact bar once. The existing
    // function saves the former toolbar configuration for the settings toggle.
    if (!prefs.getBoolean("teclado_br_sentence_toolbar_v1", false)) {
        if (applyTecladoBrCompactToolbar(context, true)) {
            prefs.edit().putBoolean("teclado_br_sentence_toolbar_v1", true).commit()
        }
    }

    // One-time lossless migration for users who enabled the compact toolbar
    // in 0.9.1. They should not need to open Settings just to recover editing.
    if (prefs.getBoolean("teclado_br_compact_toolbar_active", false)
            && !prefs.getBoolean("teclado_br_compact_toolbar_v4", false)) {
        applyTecladoBrCompactToolbar(context, true)
    }

}


/**
 * Optional original palette inspired by One UI 8.5's dark keyboard.
 * No Samsung assets, names, fonts or proprietary code are distributed.
 * This toggles colors only: dictionary, typing, key layout, user font,
 * height, symbols, models, shortcuts and pinned clipboard stay untouched.
 */
fun applyTecladoBrOneUi(context: Context, enabled: Boolean): Boolean {
    val prefs = context.prefs()
    val key = "teclado_br_one_ui_active"
    if (prefs.getBoolean(key, false) == enabled) return true
    if (enabled) {
        val theme = "Teclado BR · Estilo One UI"
        val base = DefaultColors("Material", true,
            Color.rgb(20, 21, 26), Color.BLACK, Color.rgb(55, 56, 61),
            Color.rgb(32, 33, 38), Color.rgb(43, 44, 49), Color.rgb(248, 248, 251),
            Color.rgb(214, 215, 222), Color.rgb(246, 246, 249), Color.rgb(230, 231, 236),
            Color.rgb(133, 171, 243))
        val palette = ColorType.entries.associateWith { base.get(it) }.toMutableMap()
        palette[ColorType.ACTION_KEY_BACKGROUND] = Color.rgb(30, 31, 36)
        palette[ColorType.ACTION_KEY_ICON] = Color.WHITE
        palette[ColorType.SHIFT_KEY_ICON] = Color.rgb(133, 171, 243)
        palette[ColorType.CLIPBOARD_PIN] = Color.rgb(133, 171, 243)
        palette[ColorType.KEY_HINT_TEXT] = Color.rgb(214, 215, 222)
        palette[ColorType.STRIP_BACKGROUND] = Color.rgb(20, 21, 26)
        palette[ColorType.TOOL_BAR_EXPAND_KEY_BACKGROUND] = Color.rgb(43, 44, 49)
        return prefs.edit()
            .putString("teclado_br_one_ui_prev_style", prefs.getString(Settings.PREF_THEME_STYLE, "Material"))
            .putString("teclado_br_one_ui_prev_day", prefs.getString(Settings.PREF_THEME_COLORS, "darker"))
            .putString("teclado_br_one_ui_prev_night", prefs.getString(Settings.PREF_THEME_COLORS_NIGHT, "darker"))
            .putBoolean("teclado_br_one_ui_prev_auto", prefs.getBoolean(Settings.PREF_THEME_DAY_NIGHT, false))
            .putBoolean("teclado_br_one_ui_prev_borders", prefs.getBoolean(Settings.PREF_THEME_KEY_BORDERS, true))
            .putString(Settings.PREF_USER_ALL_COLORS_PREFIX + theme, palette.entries.joinToString(";") { "${it.key},${it.value}" })
            .putInt(Settings.PREF_USER_MORE_COLORS_PREFIX + theme, 2)
            .putString(Settings.PREF_THEME_STYLE, "Material")
            .putString(Settings.PREF_THEME_COLORS, theme)
            .putString(Settings.PREF_THEME_COLORS_NIGHT, theme)
            .putBoolean(Settings.PREF_THEME_DAY_NIGHT, false)
            .putBoolean(Settings.PREF_THEME_KEY_BORDERS, true)
            .putBoolean(key, true)
            .commit()
    } else {
        return prefs.edit()
            .putString(Settings.PREF_THEME_STYLE, prefs.getString("teclado_br_one_ui_prev_style", "Material"))
            .putString(Settings.PREF_THEME_COLORS, prefs.getString("teclado_br_one_ui_prev_day", "darker"))
            .putString(Settings.PREF_THEME_COLORS_NIGHT, prefs.getString("teclado_br_one_ui_prev_night", "darker"))
            .putBoolean(Settings.PREF_THEME_DAY_NIGHT, prefs.getBoolean("teclado_br_one_ui_prev_auto", false))
            .putBoolean(Settings.PREF_THEME_KEY_BORDERS, prefs.getBoolean("teclado_br_one_ui_prev_borders", true))
            .putBoolean(key, false)
            .remove("teclado_br_one_ui_prev_day")
            .remove("teclado_br_one_ui_prev_style")
            .remove("teclado_br_one_ui_prev_night")
            .remove("teclado_br_one_ui_prev_auto")
            .remove("teclado_br_one_ui_prev_borders")
            .commit()
    }
}


/**
 * Teclado BR compact: five favorites, plus a separate clipboard editor.
 * The ⋯ overflow contains miscellaneous non-editing tools ONLY.
 * All text editing and navigation actions are reached by long-pressing
 * Clipboard (its regular tap still opens the native clipboard history).
 * There are no duplicates of the five favorites in the editing panel.
 *
 * Snapshots preserve the exact original order, visibility, toolbar mode
 * and auto-show preferences. Existing compact betas migrate losslessly.
 */
fun applyTecladoBrCompactToolbar(context: Context, enabled: Boolean): Boolean {
    val prefs = context.prefs()
    val flag = "teclado_br_compact_toolbar_active"
    val v2 = "teclado_br_compact_toolbar_v2"
    val v3 = "teclado_br_compact_toolbar_v3"
    val v4 = "teclado_br_compact_toolbar_v4"
    val alreadyActive = prefs.getBoolean(flag, false)
    if (alreadyActive == enabled && (!enabled || prefs.getBoolean(v4, false))) return true
    val previousKey = "teclado_br_compact_toolbar_previous"
    val pinnedKey = "teclado_br_compact_toolbar_previous_pinned"
    val modeKey = "teclado_br_compact_toolbar_previous_mode"
    val showKey = "teclado_br_compact_toolbar_previous_auto_show"
    val hideKey = "teclado_br_compact_toolbar_previous_auto_hide"

    if (enabled) {
        // If 0.9.1 already enabled compact mode, use its PRE-compact snapshot
        // rather than treating hidden shortcuts as the user's original set.
        val original = (if (alreadyActive) prefs.getString(previousKey, null)
            else prefs.getString(Settings.PREF_TOOLBAR_KEYS, Defaults.PREF_TOOLBAR_KEYS)) ?: return false
        val originalPinned = prefs.getString(Settings.PREF_PINNED_TOOLBAR_KEYS, Defaults.PREF_PINNED_TOOLBAR_KEYS)
            ?: return false
        val originalItems = original.split("|").filter { it.isNotBlank() }
        val favorites = listOf("AI", "CLIPBOARD", "EMOJI", "TRANSLATE", "SETTINGS")
        // Every editing/selection/navigation action belongs ONLY to the
        // clipboard's long-press panel. Neither the ⋯ overflow nor the five
        // circular favorites repeats those actions.
        val editActions = listOf("SELECT_ALL", "SELECT_WORD", "COPY", "CUT",
            "PASTE", "UNDO", "REDO", "LEFT", "RIGHT", "UP", "DOWN",
            "WORD_LEFT", "WORD_RIGHT", "FULL_LEFT", "FULL_RIGHT",
            "PAGE_UP", "PAGE_DOWN", "PAGE_START", "PAGE_END",
            "CLEAR_CLIPBOARD")
        val hiddenFromOverflow = (favorites + editActions).toSet()
        // Retain the user's original non-editor tools and their enabled
        // states/order. Never read from the previous beta's expanded list.
        val overflow = originalItems
            .filter { it.substringBefore(":") !in hiddenFromOverflow }
            .joinToString("|")
        val pinnedItems = originalPinned.split("|").filter { it.isNotBlank() }
        val compactPinned = (favorites.map { it + ":true" } +
            pinnedItems.filter { it.substringBefore(":") !in favorites }
                .map { it.substringBefore(":") + ":false" }).joinToString("|")

        val editor = prefs.edit()
        if (!alreadyActive) editor.putString(previousKey, original)
        // In 0.9.1 pinned shortcuts and auto-mode were never overwritten.
        if (!prefs.contains(pinnedKey)) editor.putString(pinnedKey, originalPinned)
        if (!prefs.contains(modeKey)) editor.putString(modeKey,
            prefs.getString(Settings.PREF_TOOLBAR_MODE, Defaults.PREF_TOOLBAR_MODE))
        if (!prefs.contains(showKey)) editor.putBoolean(showKey,
            prefs.getBoolean(Settings.PREF_AUTO_SHOW_TOOLBAR, Defaults.PREF_AUTO_SHOW_TOOLBAR))
        if (!prefs.contains(hideKey)) editor.putBoolean(hideKey,
            prefs.getBoolean(Settings.PREF_AUTO_HIDE_TOOLBAR, Defaults.PREF_AUTO_HIDE_TOOLBAR))
        return editor
            .putString(Settings.PREF_TOOLBAR_KEYS, overflow)
            .putString(Settings.PREF_PINNED_TOOLBAR_KEYS, compactPinned)
            .putString(Settings.PREF_TOOLBAR_MODE, "EXPANDABLE")
            .putBoolean(Settings.PREF_AUTO_SHOW_TOOLBAR, false)
            .putBoolean(Settings.PREF_AUTO_HIDE_TOOLBAR, true)
            .putBoolean(flag, true)
            .putBoolean(v2, true)
            .putBoolean(v3, true)
            .putBoolean(v4, true)
            .commit()
    }

    val original = prefs.getString(previousKey, null) ?: return false
    val editor = prefs.edit()
        .putString(Settings.PREF_TOOLBAR_KEYS, original)
        .putBoolean(flag, false)
        .remove(previousKey)
        .remove(v2)
        .remove(v3)
        .remove(v4)
    // Upgrading straight from v0.9.1 does not have these extra snapshots.
    if (prefs.contains(pinnedKey)) editor.putString(Settings.PREF_PINNED_TOOLBAR_KEYS,
        prefs.getString(pinnedKey, Defaults.PREF_PINNED_TOOLBAR_KEYS)).remove(pinnedKey)
    if (prefs.contains(modeKey)) editor.putString(Settings.PREF_TOOLBAR_MODE,
        prefs.getString(modeKey, Defaults.PREF_TOOLBAR_MODE)).remove(modeKey)
    if (prefs.contains(showKey)) editor.putBoolean(Settings.PREF_AUTO_SHOW_TOOLBAR,
        prefs.getBoolean(showKey, Defaults.PREF_AUTO_SHOW_TOOLBAR)).remove(showKey)
    if (prefs.contains(hideKey)) editor.putBoolean(Settings.PREF_AUTO_HIDE_TOOLBAR,
        prefs.getBoolean(hideKey, Defaults.PREF_AUTO_HIDE_TOOLBAR)).remove(hideKey)
    return editor.commit()
}

