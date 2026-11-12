# SPDX-License-Identifier: GPL-3.0-only
"""Focused typing integration against the pinned upstream."""
import json
import shutil
from pathlib import Path

def apply(context):
    root, here, replace = context.root, context.here, context.replace
    # Materialize punctuation spacing immediately, rather than upstream phantom spacing.
    logic = root/'app/src/main/java/helium314/keyboard/latin/inputlogic/InputLogic.java'
    text = logic.read_text()
    assert text.count('    public void startInput(final String combiningSpec, final SettingsValues settingsValues) {') == 1
    text = text.replace('    public void startInput(final String combiningSpec, final SettingsValues settingsValues) {',
        '    private int mTecladoBrAutoSpacePosition = -1;\n\n    public void startInput(final String combiningSpec, final SettingsValues settingsValues) {\n        mTecladoBrAutoSpacePosition = -1;')
    text = text.replace('        final Event processedEvent = mWordComposer.processEvent(event);',
        '        prepareTecladoBrAutoSpace(event);\n        final Event processedEvent = mWordComposer.processEvent(event);')
    old = '            mConnection.commitCodePoint(codePoint);\n\n            if (isInlineEmojiSearchAction()) {'
    assert text.count(old) == 1
    text = text.replace(old, '            mConnection.commitCodePoint(codePoint);\n            insertTecladoBrPunctuationSpace(codePoint, settingsValues);\n\n            if (isInlineEmojiSearchAction()) {')
    methods = '''    // Track only the space inserted by this feature, never arbitrary user whitespace.
        private void prepareTecladoBrAutoSpace(final Event event) {
            if (mTecladoBrAutoSpacePosition >= 0
                    && mConnection.getExpectedSelectionStart() == mTecladoBrAutoSpacePosition
                    && mConnection.getExpectedSelectionEnd() == mTecladoBrAutoSpacePosition
                    && mConnection.getCodePointBeforeCursor() == Constants.CODE_SPACE) {
                final int cp = event.getCodePoint();
                if (cp == Constants.CODE_SPACE || cp == Constants.CODE_ENTER
                        || (cp >= 0 && ".,!?;:)]}\\\"".indexOf(cp) >= 0)) {
                    mConnection.beginBatchEdit();
                    mConnection.deleteTextBeforeCursor(1);
                    mConnection.endBatchEdit();
                }
            }
            mTecladoBrAutoSpacePosition = -1;
        }

        private void insertTecladoBrPunctuationSpace(final int cp, final SettingsValues settings) {
            if (!helium314.keyboard.latin.BuildConfig.BUILD_TYPE.equals("debugNoMinify")
                    || !settings.mAutospaceAfterPunctuation
                    || !settings.mInputAttributes.mShouldInsertSpacesAutomatically
                    || settings.mInputAttributes.mIsPasswordField
                    || cp < 0 || ".,!?;:".indexOf(cp) < 0) return;
            final CharSequence before = mConnection.getTextBeforeCursor(96, 0);
            if (before == null || before.length() < 2) return;
            final char previous = before.charAt(before.length() - 2);
            if (!Character.isLetter(previous) && ".,!?;:".indexOf(previous) < 0) return;
            final String token = before.toString().substring(0, before.length() - 1);
            final int boundary = Math.max(token.lastIndexOf(' '), token.lastIndexOf('\\n'));
            final String current = token.substring(boundary + 1);
            if (current.indexOf('@') >= 0 || current.indexOf('/') >= 0 || current.contains("://")) return;
            mConnection.commitCodePoint(Constants.CODE_SPACE);
            mSpaceState = SpaceState.NONE;
            mTecladoBrAutoSpacePosition = mConnection.getExpectedSelectionStart();
        }

    '''
    text = text.replace('    private void handleSeparatorEvent(final Event event, final InputTransaction inputTransaction,',
        methods + '    private void handleSeparatorEvent(final Event event, final InputTransaction inputTransaction,')
    logic.write_text(text)


