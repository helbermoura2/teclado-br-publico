// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.suggestions;

/** Toolbar mode follows editor input; arriving suggestion results do not change it. */
public final class ToolbarState {
    private boolean shortcuts = true;
    private Boolean manualOverride;

    /** Start a new field with shortcuts available before the user types. */
    public void reset() {
        shortcuts = true;
        manualOverride = null;
    }

    /** Call for an actual editor edit, including delete (a negative key code). */
    public void input(final int codePoint) {
        final boolean wasManual = manualOverride != null;
        manualOverride = null;
        shortcuts = isSentenceEnd(codePoint) || (!wasManual && shortcuts && Character.isWhitespace(codePoint));
    }

    /** Process inserted text as edits, not as the full contents of an editor. */
    public void input(final CharSequence text) {
        if (text == null) return;
        for (int index = 0; index < text.length();) {
            final int cp = Character.codePointAt(text, index);
            input(cp);
            index += Character.charCount(cp);
        }
    }

    /** A user override lasts until the next edit; collapsing also leaves suggestions visible. */
    public void setManualExpanded(final boolean expanded) {
        manualOverride = expanded;
        if (!expanded) shortcuts = false;
    }

    /**
     * Refresh after cursor movement, deletion or opening an existing field.
     * Pass current editor text, never a snapshot attached to a suggestion result.
     * Whitespace includes the space inserted automatically after punctuation.
     * A dot inside a decimal or address stops being terminal as soon as typing resumes.
     */
    public void syncContext(final CharSequence beforeCursor) {
        if (beforeCursor == null) return;
        int end = beforeCursor.length();
        while (end > 0 && Character.isWhitespace(Character.codePointBefore(beforeCursor, end))) {
            end -= Character.charCount(Character.codePointBefore(beforeCursor, end));
        }
        shortcuts = end == 0 || isSentenceEnd(Character.codePointBefore(beforeCursor, end));
    }

    private static boolean isSentenceEnd(int cp) {
        return cp == '.' || cp == '!' || cp == '?';
    }

    public boolean isShortcutsVisible() {
        return manualOverride != null ? manualOverride : shortcuts;
    }
}
