# SPDX-License-Identifier: GPL-3.0-only
"""Teclado BR compact toolbar and selection-context overlay.

Applied only to the pinned HeliBoard v3.6 tree after AI shortcuts exist.
When disabled, the original HeliBoard suggestion strip is unchanged.
"""
import shutil

def apply(context):
    shutil.copyfile(context.here/"overlay/ToolbarState.java", context.root/"app/src/main/java/helium314/keyboard/latin/suggestions/ToolbarState.java")
    strip = context.root / 'app/src/main/java/helium314/keyboard/latin/suggestions/SuggestionStripView.kt'
    ime = context.root / 'app/src/main/java/helium314/keyboard/latin/LatinIME.java'

    def replace_once(text, old, new, where):
        count = text.count(old)
        if count != 1:
            raise RuntimeError('Teclado BR toolbar anchor changed: %s (%d matches)' % (where, count))
        return text.replace(old, new, 1)

    content = strip.read_text()
    content = replace_once(content, '    private val toolbar: ViewGroup = findViewById(R.id.toolbar)', '    private val toolbarState = ToolbarState()\n    private val toolbar: ViewGroup = findViewById(R.id.toolbar)', 'Initialize sentence state before rendering')
    content = replace_once(content,
        'import android.widget.ImageButton',
        'import android.widget.HorizontalScrollView\nimport android.widget.ImageButton',
        'Use native Android horizontal scroll for clipboard editing actions')
    # The special '+' key was added by suggestions.py for the PERSONAL
    # DICTIONARY. It is a real action; keep it, but place it in the overflow
    # toolbox in compact mode instead of stealing width from word candidates.
    # Move the existing View, not a copy, so its listener and accessibility
    # name remain intact. The classic theme keeps its original layout.
    content = replace_once(content,
        '''        updateKeys()
    }

    private lateinit var listener: Listener''',
        '''        updateKeys()
        if (context.prefs().getBoolean("teclado_br_compact_toolbar_active", false)) {
            val stripRow = toolbarExpandKey.parent as? LinearLayout
            val dictionary = findViewById<TextView>(R.id.teclado_add_word)
            if (stripRow != null && dictionary != null) {
                // Upstream suggestions_strip.xml uses animateLayoutChanges=true.
                // With rapid typing it can crossfade pinned favorites AND words
                // in the same row. Compact mode must switch instantly so icons
                // never draw over word candidates. Classic theme is untouched.
                stripRow.layoutTransition = null
                (dictionary.parent as? ViewGroup)?.removeView(dictionary)
                toolbar.addView(dictionary, 0, LinearLayout.LayoutParams(
                    42.dpToPx(resources), LinearLayout.LayoutParams.MATCH_PARENT))
                dictionary.isVisible = false

                // The toolbar scroller cannot keep MATCH_PARENT width when
                // the ⋯ toggle is placed AFTER it (Samsung-like placement).
                (toolbarContainer.layoutParams as? LinearLayout.LayoutParams)?.let {
                    it.width = 0
                    it.weight = 1f
                    toolbarContainer.layoutParams = it
                }
                stripRow.removeView(toolbarExpandKey)
                stripRow.addView(toolbarExpandKey)
            }
            val toggle = findViewById<ImageButton>(R.id.teclado_bar_toggle)
            toggle.setImageResource(R.drawable.teclado_br_bar_toggle)
            setupKey(toggle, Settings.getValues().mColors)
            val padding = 10.dpToPx(resources)
            toggle.setPadding(padding, padding, padding, padding)
            toggle.setOnClickListener { toggleTecladoBrFavorites() }
            toggle.setOnLongClickListener { true }
            tecladoBrRebuildPinnedKeys()
        }
    }

    private lateinit var listener: Listener''',
        'Rearrange compact toolbar right toggle and relocate dictionary action')
    content = replace_once(content,
        '    private lateinit var listener: Listener',
        '''    // Compact shortcuts and suggestions are exclusive, just like on Samsung.
    // No text is read by this view state machine.
    private var tecladoBrHasSelection = false
    private var tecladoBrHasPredictions = false
    private var tecladoBrEditingMode = false

    private fun tecladoBrCompact(): Boolean =
        context.prefs().getBoolean("teclado_br_compact_toolbar_active", false)

    private fun tecladoBrStyleFavorite(button: ImageButton) {
        val background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.rgb(53, 54, 60))
        }
        button.background = android.graphics.drawable.InsetDrawable(
            background, 7.dpToPx(resources), 3.dpToPx(resources),
            7.dpToPx(resources), 3.dpToPx(resources))
        val pad = 11.dpToPx(resources)
        button.setPadding(pad, pad, pad, pad)
    }

    // Editing toolbox is INSIDE the clipboard's long-press view, not ⋯.
    // Its native action buttons are horizontally scrollable: arrows, word
    // navigation and page movements must not be crushed into 40px of width.
    private fun tecladoBrRebuildPinnedKeys() {
        if (!tecladoBrCompact() || Settings.getValues().mSuggestionStripHiddenPerUserSettings)
            return

        // Move the SAME dictionary '+' view between its hidden holder and
        // the editing scroller. Never clone it or lose its real click listener.
        val dictionary = findViewById<TextView>(R.id.teclado_add_word)
        (dictionary?.parent as? ViewGroup)?.removeView(dictionary)
        pinnedKeys.removeAllViews()
        val colors = Settings.getValues().mColors
        (pinnedKeys.layoutParams as? LinearLayout.LayoutParams)?.let {
            it.width = 0
            it.weight = 1f
            pinnedKeys.layoutParams = it
        }

        if (tecladoBrEditingMode && !tecladoBrHasSelection) {
            val back = ImageButton(context, null, R.attr.suggestionWordStyle)
            back.contentDescription = "Voltar aos atalhos principais"
            back.setImageDrawable(context.getDrawable(android.R.drawable.ic_media_previous))
            back.layoutParams = LinearLayout.LayoutParams(
                42.dpToPx(resources), LinearLayout.LayoutParams.MATCH_PARENT)
            setupKey(back, colors)
            back.setOnClickListener {
                tecladoBrEditingMode = false
                tecladoBrRebuildPinnedKeys()
            }
            back.setOnLongClickListener { true }
            pinnedKeys.addView(back)

            val scroller = HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
            }
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            val editingKeys = listOf(
                ToolbarKey.SELECT_ALL, ToolbarKey.SELECT_WORD,
                ToolbarKey.COPY, ToolbarKey.CUT, ToolbarKey.PASTE,
                ToolbarKey.UNDO, ToolbarKey.REDO,
                ToolbarKey.LEFT, ToolbarKey.RIGHT, ToolbarKey.UP, ToolbarKey.DOWN,
                ToolbarKey.WORD_LEFT, ToolbarKey.WORD_RIGHT,
                ToolbarKey.FULL_LEFT, ToolbarKey.FULL_RIGHT,
                ToolbarKey.PAGE_UP, ToolbarKey.PAGE_DOWN,
                ToolbarKey.PAGE_START, ToolbarKey.PAGE_END,
                ToolbarKey.CLEAR_CLIPBOARD)
            for (key in editingKeys) {
                val button = createToolbarKey(context, key)
                button.layoutParams = LinearLayout.LayoutParams(
                    44.dpToPx(resources), LinearLayout.LayoutParams.MATCH_PARENT)
                setupKey(button, colors)
                // Don't enter upstream quick-pin logic from a scroll row.
                // Preserve the native longer-press action for navigation.
                button.setOnLongClickListener {
                    val code = getCodeForToolbarKeyLongClick(key)
                    if (code != KeyCode.UNSPECIFIED) {
                        listener.onCodeInput(code,
                            Constants.SUGGESTION_STRIP_COORDINATE,
                            Constants.SUGGESTION_STRIP_COORDINATE, false)
                    }
                    true
                }
                row.addView(button)
            }
            if (dictionary != null) {
                dictionary.layoutParams = LinearLayout.LayoutParams(
                    44.dpToPx(resources), LinearLayout.LayoutParams.MATCH_PARENT)
                dictionary.isVisible = !Settings.getValues().mIncognitoModeEnabled
                row.addView(dictionary)
            }
            scroller.addView(row, android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT))
            pinnedKeys.addView(scroller, LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        } else {
            // The ⋯ overflow is now miscellaneous tools ONLY. '+' belongs to
            // the clipboard editing panel and must be invisible in ⋯.
            if (dictionary != null) {
                dictionary.isVisible = false
                toolbar.addView(dictionary, 0, LinearLayout.LayoutParams(
                    42.dpToPx(resources), LinearLayout.LayoutParams.MATCH_PARENT))
            }
            val keys = if (tecladoBrHasSelection)
                listOf(ToolbarKey.SELECT_ALL, ToolbarKey.COPY,
                    ToolbarKey.CUT, ToolbarKey.PASTE)
                else getPinnedToolbarKeys(context.prefs()).filter { it != ToolbarKey.LANGUAGE }
            val favorites = !tecladoBrHasSelection
            for (key in keys) {
                val button = createToolbarKey(context, key)
                button.layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                setupKey(button, colors)
                if (favorites) tecladoBrStyleFavorite(button)
                pinnedKeys.addView(button)
            }
        }
        tecladoBrApplyStripVisibility()
    }

    private fun tecladoBrApplyStripVisibility() {
        if (!tecladoBrCompact()) return
        val locked = isDeviceLocked(context)
        // Language switching already lives on the spacebar long press.
        // The compact bar uses only its leading handle and five favorites.
        toolbarContainer.isVisible = false
        findViewById<TextView>(R.id.teclado_add_word)?.isVisible =
            !locked && tecladoBrEditingMode && !tecladoBrHasSelection
                && !Settings.getValues().mIncognitoModeEnabled
        // Do not reserve strip width for the redundant trailing menu.
        val showTools = tecladoBrHasSelection || tecladoBrEditingMode || toolbarState.isShortcutsVisible()
        findViewById<ImageButton>(R.id.teclado_bar_toggle)?.apply {
            isVisible = !locked
            contentDescription = if (showTools) "Mostrar sugestões" else "Mostrar atalhos"
            val showingTools = showTools
            // Mirror only the icon, keeping the View's touch/accessibility
            // coordinates stable at the leading edge of the strip.
            if (tag != showingTools) {
                setImageResource(if (showingTools) R.drawable.teclado_br_bar_toggle_close
                    else R.drawable.teclado_br_bar_toggle)
                Settings.getValues().mColors.setColor(this, ColorType.TOOL_BAR_KEY)
                tag = showingTools
            }
        }
        toolbarExpandKey.isVisible = false
        if (locked) {
            // No word or tool candidates are exposed on the lock screen.
            pinnedKeys.visibility = View.GONE
            suggestionsStrip.visibility = View.GONE
            return
        }
        // Hide the INACTIVE row first, then reveal the active row. This
        // prevents a transient overlap on the UI thread.
        if (showTools) {
            suggestionsStrip.visibility = View.GONE
            pinnedKeys.visibility = View.VISIBLE
        } else {
            pinnedKeys.visibility = View.GONE
            suggestionsStrip.visibility = View.VISIBLE
        }
    }

    fun updateTecladoBrSelection(hasSelection: Boolean) {
        if (!tecladoBrCompact() || tecladoBrHasSelection == hasSelection) return
        tecladoBrHasSelection = hasSelection
        if (hasSelection) tecladoBrEditingMode = false
        tecladoBrRebuildPinnedKeys()
    }

    private lateinit var listener: Listener''',
        'SuggestionStripView selection method')
    content = replace_once(content,
        '            toolbarExpandKey.setImageDrawable(toolbarArrowIcon)',
        '''            val compact = context.prefs().getBoolean("teclado_br_compact_toolbar_active", false)
            toolbarExpandKey.setImageDrawable(if (compact)
                context.getDrawable(R.drawable.teclado_br_more) else toolbarArrowIcon)
            if (compact) toolbarExpandKey.contentDescription = "Mais ferramentas de edição"''',
        'SuggestionStripView expand icon')
    # Keep the usual tap on Clipboard untouched: it still opens history.
    # Long press only in the compact favorite row enters a dedicated
    # edit-only strip with a Back button. Classic mode keeps paste-on-hold.
    content = replace_once(content,
        '''        val tag = view.tag as? ToolbarKey ?: return
        if (!Settings.getValues().mQuickPinToolbarKeys || view.parent === pinnedKeys) {''',
        '''        val tag = view.tag as? ToolbarKey ?: return
        if (tag == ToolbarKey.CLIPBOARD && view.parent === pinnedKeys
                && tecladoBrCompact()) {
            tecladoBrEditingMode = true
            tecladoBrRebuildPinnedKeys()
            return
        }
        if (!Settings.getValues().mQuickPinToolbarKeys || view.parent === pinnedKeys) {''',
        'Clipboard long press opens selection/copy/cut/paste/undo/redo')
    # Predictions must be given all the horizontal space, before the
    # native layout helper truncates word candidates. The state can change
    # without toggling the native expanded editing menu.
    content = replace_once(content,
        '''        toolbarExpandKey.scaleX = (if (toolbarVisible && !locked) -1f else 1f) * direction
    }''',
        '''        toolbarExpandKey.scaleX = (if (toolbarVisible && !locked) -1f else 1f) * direction
        tecladoBrApplyStripVisibility()
    }''',
        'SuggestionStripView setToolbarVisibility')
    content = replace_once(content,
        '''        suggestedWords = suggestions
        startIndexOfMoreSuggestions = layoutHelper.layoutAndReturnStartIndexOfMoreSuggestions(''',
        '''        suggestedWords = suggestions
        // Punctuation rows ("! ? , : ;") and next-word predictions
        // are not active composition. On an empty editor we must show
        // the compact favorites instead of treating punctuation as words.
        // Keep the FULL suggestion strip for actual typing/correction.
        val composing = when (suggestions.mInputStyle) {
            SuggestedWords.INPUT_STYLE_TYPING,
            SuggestedWords.INPUT_STYLE_RECORRECTION,
            SuggestedWords.INPUT_STYLE_UPDATE_BATCH,
            SuggestedWords.INPUT_STYLE_TAIL_BATCH,
            SuggestedWords.INPUT_STYLE_APPLICATION_SPECIFIED -> true
            else -> false
        }
        tecladoBrHasPredictions = !suggestions.isPunctuationSuggestions()
            && composing && suggestions.getWordCountToShow() > 0
        tecladoBrApplyStripVisibility()
        startIndexOfMoreSuggestions = layoutHelper.layoutAndReturnStartIndexOfMoreSuggestions(''',
        'SuggestionStripView prepare full-width predictions')
    content = replace_once(content,
        '''        isExternalSuggestionVisible = false
        updateKeys()
    }

    fun setExternalSuggestionView''',
        '''        isExternalSuggestionVisible = false
        updateKeys()
        // updateKeys resets visibility, so reapply the compact row priority.
        tecladoBrApplyStripVisibility()
    }

    fun setExternalSuggestionView''',
        'SuggestionStripView retain prediction priority')
    # The native onVisibilityChanged() resets the suggestions View to
    # VISIBLE when the whole keyboard returns onscreen, even if compact
    # favorites should be shown. Keep the exclusive state invariant.
    content = replace_once(content,
        '''        if (view === this)
            suggestionsStrip.visibility = visibility''',
        '''        if (view === this) {
            suggestionsStrip.visibility = visibility
            if (tecladoBrCompact()) tecladoBrApplyStripVisibility()
        }''',
        'SuggestionStripView preserve exclusive favorites on keyboard visibility')

    # Keep native long-press and popup access, but remove the visual
    # three-dot underline from the center prediction in compact mode.
    # More suggestions are still accessible through the existing gesture
    # and the long-press word menu.
    helper = context.root / 'app/src/main/java/helium314/keyboard/latin/suggestions/SuggestionStripLayoutHelper.java'
    helper_text = helper.read_text()
    helper_text = replace_once(helper_text,
        'if (positionInStrip == mCenterPositionInStrip && mMoreSuggestionsAvailable) {',
        '''if (positionInStrip == mCenterPositionInStrip && mMoreSuggestionsAvailable
                && !helium314.keyboard.latin.utils.DeviceProtectedUtils
                    .getSharedPreferences(context)
                    .getBoolean("teclado_br_compact_toolbar_active", false)) {''',
        'Hide only the compact theme decorative suggestion dots')
    helper.write_text(helper_text)

    strip.write_text(content)

    content = ime.read_text()
    content = replace_once(content,
        '        final SettingsValues settingsValues = mSettings.getCurrent();\n        if (isInputViewShown()\n                && mInputLogic.onUpdateSelection',
        '''        // Selection-state callback is only a view update: no text is
        // accessed or sent anywhere and the editor remains authoritative.
        if (isInputViewShown() && hasSuggestionStripView()) {
            mSuggestionStripView.updateTecladoBrSelection(
                    newSelStart >= 0 && newSelEnd >= 0 && newSelStart != newSelEnd);
        }
        final SettingsValues settingsValues = mSettings.getCurrent();
        if (isInputViewShown()
                && mInputLogic.onUpdateSelection''',
        'LatinIME selection callback')
    ime.write_text(content)


    previous = ('    private var toolbarManuallyExpanded = false\n'
                '    fun onKeyboardInput() { toolbarManuallyExpanded = false; if (Settings.getValues().mAutoHideToolbar && Settings.getValues().isSuggestionsEnabledPerUserSettings()) setToolbarVisibility(false) }\n'
                '    fun hideToolbarForSuggestions() { if (!toolbarManuallyExpanded) setToolbarVisibility(false) }')
    methods = '    private var toolbarManuallyExpanded = false\n    private fun toggleTecladoBrFavorites() {\n        val wasShowingTools = pinnedKeys.isVisible || toolbarContainer.isVisible\n        toolbarState.setManualExpanded(!wasShowingTools)\n        toolbarManuallyExpanded = false\n        tecladoBrEditingMode = false\n        setToolbarVisibility(false)\n        tecladoBrRebuildPinnedKeys()\n    }\n    private fun refreshSentenceToolbar() {\n        toolbarManuallyExpanded = false\n        val wasEditing = tecladoBrEditingMode\n        tecladoBrEditingMode = false\n        if (toolbarContainer.isVisible) setToolbarVisibility(false)\n        if (wasEditing) tecladoBrRebuildPinnedKeys()\n        else tecladoBrApplyStripVisibility()\n    }\n    fun onKeyboardInput(codePoint: Int) {\n        toolbarState.input(codePoint)\n        refreshSentenceToolbar()\n    }\n    fun onKeyboardText(text: CharSequence?) {\n        toolbarState.input(text)\n        refreshSentenceToolbar()\n    }\n    fun syncToolbarContext(beforeCursor: CharSequence?) {\n        toolbarState.syncContext(beforeCursor)\n        tecladoBrApplyStripVisibility()\n    }\n    fun beginToolbarContext(beforeCursor: CharSequence?) {\n        toolbarState.reset()\n        toolbarManuallyExpanded = false\n        tecladoBrEditingMode = false\n        tecladoBrHasSelection = false\n        setToolbarVisibility(false)\n        tecladoBrRebuildPinnedKeys()\n        syncToolbarContext(beforeCursor)\n    }\n    fun hideToolbarForSuggestions() { if (!toolbarManuallyExpanded) setToolbarVisibility(false) }'
    context.replace('app/src/main/java/helium314/keyboard/latin/suggestions/SuggestionStripView.kt', previous, methods)
    context.replace('app/src/main/java/helium314/keyboard/latin/suggestions/SuggestionStripView.kt', '            listener.pickSuggestionManually(wordInfo)',
            '            onKeyboardText(wordInfo.mWord)\n            listener.pickSuggestionManually(wordInfo)')
    context.replace('app/src/main/java/helium314/keyboard/latin/suggestions/SuggestionStripView.kt', '{ listener.pickSuggestionManually(info) },',
            '{ onKeyboardText(word); listener.pickSuggestionManually(info) },')
    context.replace('app/src/main/java/helium314/keyboard/latin/LatinIME.java', '        if (mSuggestionStripView != null && (event.getCodePoint() >= 0 || event.getKeyCode() == KeyCode.DELETE)) mSuggestionStripView.onKeyboardInput();\n        if (helium314.keyboard.latin.ai.WritingPanel.key(event)) { mKeyboardSwitcher.onEvent(event, 0, 0); return; }',
            '        if (helium314.keyboard.latin.ai.WritingPanel.key(event)) { mKeyboardSwitcher.onEvent(event, 0, 0); return; }\n        if (mSuggestionStripView != null && (event.getCodePoint() >= 0 || event.getKeyCode() == KeyCode.DELETE || event.getKeyCode() == KeyCode.UNDO || event.getKeyCode() == KeyCode.REDO)) mSuggestionStripView.onKeyboardInput(event.getCodePoint());')
    context.replace('app/src/main/java/helium314/keyboard/latin/LatinIME.java', '        if (mSuggestionStripView != null) mSuggestionStripView.onKeyboardInput();\n        if (helium314.keyboard.latin.ai.WritingPanel.text(rawText)) return;',
            '        if (helium314.keyboard.latin.ai.WritingPanel.text(rawText)) return;\n        if (mSuggestionStripView != null) mSuggestionStripView.onKeyboardText(rawText);')
    context.replace('app/src/main/java/helium314/keyboard/latin/LatinIME.java', '        // Set neutral suggestions and show the toolbar if the "Auto show toolbar" setting is enabled.',
            '        if (mSuggestionStripView != null) {\n            final CharSequence before = mInputLogic.mConnection.getTextBeforeCursor(128, 0);\n            if (!restarting) mSuggestionStripView.beginToolbarContext(before);\n            else mSuggestionStripView.syncToolbarContext(before);\n        }\n        // Set neutral suggestions and show the toolbar if the "Auto show toolbar" setting is enabled.')
    context.replace('app/src/main/java/helium314/keyboard/latin/LatinIME.java', '            // we don\'t want to update a manually set shift state if selection changed towards one side',
            '            if (mSuggestionStripView != null && !helium314.keyboard.latin.ai.WritingPanel.isActive()) mSuggestionStripView.syncToolbarContext(mInputLogic.mConnection.getTextBeforeCursor(128, 0));\n            // we don\'t want to update a manually set shift state if selection changed towards one side')
    context.replace('app/src/main/java/helium314/keyboard/latin/LatinIME.java', '                        mKeyboardSwitcher.getKeyboardShiftMode(),\n                        mKeyboardSwitcher.getCurrentKeyboardScript(), mHandler);\n        updateStateAfterInputTransaction(completeInputTransaction);',
            '                        mKeyboardSwitcher.getKeyboardShiftMode(),\n                        mKeyboardSwitcher.getCurrentKeyboardScript(), mHandler);\n        if (event.getCodePoint() < 0 && completeInputTransaction.didAffectContents() && mSuggestionStripView != null) { mSuggestionStripView.onKeyboardInput(-1); mSuggestionStripView.syncToolbarContext(mInputLogic.mConnection.getTextBeforeCursor(128, 0)); }\n        updateStateAfterInputTransaction(completeInputTransaction);')
    context.replace('app/src/main/java/helium314/keyboard/latin/LatinIME.java', '    public void onStartBatchInput() {\n        if (helium314.keyboard.latin.ai.WritingPanel.isEditing()) return;',
            '    public void onStartBatchInput() {\n        if (helium314.keyboard.latin.ai.WritingPanel.isEditing()) return;\n        if (mSuggestionStripView != null) mSuggestionStripView.onKeyboardInput(0);')

    # Independent leading handle toggles FAVORITES, while the trailing ellipsis
    # continues to open miscellaneous tools. Neither consumes candidate slots.
    context.replace('app/src/main/res/layout/suggestions_strip.xml',
        '        <ImageButton\n            android:id="@+id/suggestions_strip_toolbar_key"',
        '<ImageButton android:id="@+id/teclado_bar_toggle" android:layout_width="40dp" android:layout_height="match_parent" android:background="@drawable/toolbar_expand_key_background" android:contentDescription="Mostrar atalhos" android:visibility="gone" style="?attr/suggestionWordStyle"/>\n        <ImageButton\n            android:id="@+id/suggestions_strip_toolbar_key"')
    (context.root/'app/src/main/res/drawable/teclado_br_bar_toggle.xml').write_text('<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24"><path android:fillColor="#FFFFFFFF" android:pathData="M8.6,5.6 L15,12 L8.6,18.4 L10,19.8 L17.8,12 L10,4.2 Z"/></vector>')

    (context.root/'app/src/main/res/drawable/teclado_br_bar_toggle_close.xml').write_text('<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24"><path android:fillColor="#FFFFFFFF" android:pathData="M15.4,5.6 L9,12 L15.4,18.4 L14,19.8 L6.2,12 L14,4.2 Z"/></vector>')
