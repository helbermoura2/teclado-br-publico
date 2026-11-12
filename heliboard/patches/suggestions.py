# SPDX-License-Identifier: GPL-3.0-only
"""Focused suggestions integration against the pinned upstream."""
import json
import shutil
from pathlib import Path

def apply(context):
    root, here, replace = context.root, context.here, context.replace
    # Instagram message composers may opt out of suggestions. Limit the workaround
    # to ordinary prose; keep password, address, filter and numeric fields untouched.
    replace('app/src/main/java/helium314/keyboard/compat/AppWorkarounds.kt',
        '    fun adjustInputType(inputType: Int, packageName: String?): Int {\n        return when (packageName) {',
        '    fun adjustInputType(inputType: Int, packageName: String?): Int {\n        if (packageName == "com.instagram.android"\n            && inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT) {\n            val variation = inputType and InputType.TYPE_MASK_VARIATION\n            if (variation == InputType.TYPE_TEXT_VARIATION_NORMAL\n                || variation == InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE\n                || variation == InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE\n                || variation == InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT) {\n                return inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS.inv()\n            }\n        }\n        return when (packageName) {')
    # Native toolbar language switch, including all three icon styles.
    replace('app/src/main/java/helium314/keyboard/latin/utils/ToolbarUtils.kt',
        '    EMOJI -> KeyCode.EMOJI', '    LANGUAGE -> KeyCode.LANGUAGE_SWITCH\n    EMOJI -> KeyCode.EMOJI')
    replace('app/src/main/java/helium314/keyboard/latin/utils/ToolbarUtils.kt',
        '    PAGE_UP, PAGE_DOWN, FULL_LEFT, FULL_RIGHT, PAGE_START, PAGE_END\n}',
        '    PAGE_UP, PAGE_DOWN, FULL_LEFT, FULL_RIGHT, PAGE_START, PAGE_END, LANGUAGE\n}')
    for style, drawable in [('holo', 'sym_keyboard_language_switch'), ('lxx', 'sym_keyboard_language_switch_lxx'), ('rounded', 'sym_keyboard_language_switch_lxx')]:
        replace('app/src/main/java/helium314/keyboard/keyboard/internal/KeyboardIconsSet.kt',
            f'                    ToolbarKey.EMOJI -> R.drawable.sym_keyboard_smiley_{style}',
            f'                    ToolbarKey.LANGUAGE -> R.drawable.{drawable}\n                    ToolbarKey.EMOJI -> R.drawable.sym_keyboard_smiley_{style}')
    strings = root/'app/src/main/res/values/strings.xml'
    strings.write_text(strings.read_text().replace('</resources>', '<string name="language">Idiomas</string>\n</resources>'))
    # Keep the typed word visible so an unknown word can be saved explicitly.
    replace('app/src/main/java/helium314/keyboard/latin/suggestions/SuggestionStripLayoutHelper.java',
        'settingsValues.mGestureFloatingPreviewTextEnabled, true);',
        'settingsValues.mGestureFloatingPreviewTextEnabled, suggestedWords.mInputStyle != SuggestedWords.INPUT_STYLE_TYPING);')
    # Explicit, user-triggered personal dictionary entry from a suggested word.
    strip = root/'app/src/main/java/helium314/keyboard/latin/suggestions/SuggestionStripView.kt'
    text = strip.read_text()
    start = text.index('    private fun onLongClickSuggestion(wordView: TextView): Boolean {')
    end = text.index('    private fun showMoreSuggestions(): Boolean {', start)
    text = text[:start] + '''    private fun onLongClickSuggestion(wordView: TextView): Boolean {
            val index = wordView.tag as? Int ?: return false
            if (index < 0 || index >= suggestedWords.size()) return false
            val info = suggestedWords.getInfo(index)
            val word = info.mWord
            if (word.isEmpty()) return false
            helium314.keyboard.latin.SuggestionActions.show(context, wordView, word,
                { listener.pickSuggestionManually(info) },
                { helium314.keyboard.latin.PersonalWords.save(context, word, Settings.getValues().mLocale) },
                if (info.mSourceDict != Dictionary.DICTIONARY_USER_TYPED) Runnable { removeSuggestionByWord(word) } else null,
                { showMoreSuggestions() })
            return true
        }

    ''' + text[end:]
    text = text.replace('    private fun removeSuggestion(wordView: TextView) {\n        val word = wordView.text.toString()', '    private fun removeSuggestionByWord(word: String) {')
    strip.write_text(text)

    # A visible editor-word action remains available even when the typed token is absent
    # from native candidates (e.g. elongated informal spellings).
    shutil.copyfile(here/'overlay/PersonalWords.java', root/'app/src/main/java/helium314/keyboard/latin/PersonalWords.java')
    replace('app/src/main/res/layout/suggestions_strip.xml',
        '        <LinearLayout\n            android:id="@+id/pinned_keys"',
        '\n        <TextView android:id="@+id/teclado_add_word" android:layout_width="40dp" android:layout_height="match_parent" android:gravity="center" android:text="＋" android:textSize="26sp" android:contentDescription="Adicionar palavra ao dicionário" android:focusable="true" android:clickable="true"/>\n        <LinearLayout\n            android:id="@+id/pinned_keys"')
    replace('app/src/main/java/helium314/keyboard/latin/suggestions/SuggestionStripView.kt',
        '        toolbarExpandKey.scaleX = (if (toolbarVisible && !locked) -1f else 1f) * direction',
        '        findViewById<TextView>(R.id.teclado_add_word).apply {\n            isVisible = !locked && !toolbarVisible && !Settings.getValues().mIncognitoModeEnabled\n            setTextColor(Settings.getValues().mColors.get(ColorType.KEY_TEXT))\n            setOnClickListener { listener.onCodeInput(-11051, 0, 0, false) }\n        }\n        toolbarExpandKey.scaleX = (if (toolbarVisible && !locked) -1f else 1f) * direction')
    replace('app/src/main/java/helium314/keyboard/latin/LatinIME.java',
        '    public void onEvent(@NonNull final Event event) {',
        '    public void onEvent(@NonNull final Event event) {\n        if (event.getKeyCode() == -11051) { PersonalWords.open(this, mInputView.findViewById(R.id.teclado_add_word)); return; }')

    # Focused typography and full-word popup are isolated from upstream touch logic.
    for helper in ('Typography.java', 'SuggestionActions.java'):
        shutil.copyfile(here/'overlay'/helper, root/'app/src/main/java/helium314/keyboard/latin'/helper)
    replace('app/src/main/java/helium314/keyboard/keyboard/KeyboardView.java',
        'paint.setTextSize(key.selectHintTextSize(params) * mFontSizeMultiplier);',
        'paint.setTextSize(key.selectHintTextSize(params) * helium314.keyboard.latin.Typography.symbolScale());')

    # Predictions must be invalidated when the local language model learns a word.
    # Otherwise an empty result cached before the phrase was learned survives the whole field session.
    replace('app/src/main/java/helium314/keyboard/latin/inputlogic/InputLogic.java',
        '        mDictionaryFacilitator.addToUserHistory(word, wasAutoCapitalized, ngramContext,\n                timeStampInSeconds, settingsValues.mBlockPotentiallyOffensive);',
        '        mDictionaryFacilitator.addToUserHistory(word, wasAutoCapitalized, ngramContext,\n                timeStampInSeconds, settingsValues.mBlockPotentiallyOffensive);\n        mSuggest.clearNextWordSuggestionsCache();')
    replace('app/src/main/java/helium314/keyboard/latin/Suggest.kt',
        'private val nextWordSuggestionsCache = HashMap<NgramContext, SuggestionResults>()',
        'private val nextWordSuggestionsCache = java.util.concurrent.ConcurrentHashMap<NgramContext, SuggestionResults>()')
    replace('app/src/main/java/helium314/keyboard/latin/Suggest.kt',
        '        nextWordSuggestionsCache[ngramContext] = newResults',
        '        if (!newResults.isEmpty()) nextWordSuggestionsCache[ngramContext] = newResults')


    # A toolbar opened by the user stays open until the next actual text input.
    replace('app/src/main/java/helium314/keyboard/latin/suggestions/SuggestionStripView.kt',
        '    private var suggestedWords = SuggestedWords.getEmptyInstance()',
        '    private var toolbarManuallyExpanded = false\n    fun onKeyboardInput() { toolbarManuallyExpanded = false; if (Settings.getValues().mAutoHideToolbar && Settings.getValues().isSuggestionsEnabledPerUserSettings()) setToolbarVisibility(false) }\n    fun hideToolbarForSuggestions() { if (!toolbarManuallyExpanded) setToolbarVisibility(false) }\n    private var suggestedWords = SuggestedWords.getEmptyInstance()')
    replace('app/src/main/java/helium314/keyboard/latin/suggestions/SuggestionStripView.kt',
        '        if (view === toolbarExpandKey) {\n            setToolbarVisibility(toolbarContainer.visibility != VISIBLE)',
        '        if (view === toolbarExpandKey) {\n            toolbarManuallyExpanded = toolbarContainer.visibility != VISIBLE\n            setToolbarVisibility(toolbarManuallyExpanded)')
    replace('app/src/main/java/helium314/keyboard/latin/LatinIME.java',
        '            if (currentSettingsValues.mAutoHideToolbar && !noSuggestionsFromDictionaries) {\n                mSuggestionStripView.setToolbarVisibility(false);',
        '            if (currentSettingsValues.mAutoHideToolbar && !noSuggestionsFromDictionaries) {\n                mSuggestionStripView.hideToolbarForSuggestions();')

