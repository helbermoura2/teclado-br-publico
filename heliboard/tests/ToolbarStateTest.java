// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.suggestions;

/** Standalone regression checks: compile this file together with overlay/ToolbarState.java. */
public final class ToolbarStateTest {
    private static int checks;

    private static void expect(final boolean actual, final boolean expected, final String message) {
        checks++;
        if (actual != expected) throw new AssertionError(message);
    }

    private static void shortcuts(final ToolbarState state, final String message) {
        expect(state.isShortcutsVisible(), true, message);
    }

    private static void suggestions(final ToolbarState state, final String message) {
        expect(state.isShortcutsVisible(), false, message);
    }

    public static void main(final String[] args) {
        final ToolbarState state = new ToolbarState();
        shortcuts(state, "A new field exposes shortcuts");

        state.input('o');
        suggestions(state, "The first letter shows only suggestions");
        state.input('i');
        state.input(' ');
        suggestions(state, "An ordinary word-separating space keeps suggestions");
        state.input(',');
        suggestions(state, "A comma does not end the suggestion phase");
        state.input(' ');
        suggestions(state, "A space after a comma keeps suggestions");

        state.input('.');
        shortcuts(state, "A period exposes shortcuts immediately");
        state.input(' ');
        shortcuts(state, "An explicit space after a period also preserves shortcuts");
        state.syncContext("Oi. ");
        shortcuts(state, "Automatically inserted punctuation space preserves shortcuts");
        state.syncContext("Oi. \t\n");
        shortcuts(state, "Trailing whitespace does not hide the terminal period");
        // Suggestion callbacks read this state to render; there is deliberately no callback setter.
        shortcuts(state, "A late suggestion result cannot change the chosen mode");
        state.input('b');
        suggestions(state, "The next letter returns to suggestions immediately");
        state.syncContext("Oi. B");
        suggestions(state, "A period earlier in the sentence cannot reopen shortcuts");

        state.setManualExpanded(true);
        shortcuts(state, "The expand button opens shortcuts while typing");
        state.syncContext("Oi. B");
        shortcuts(state, "A context refresh preserves a manual opening");
        state.input('o');
        suggestions(state, "The next actual edit ends the manual opening");
        state.setManualExpanded(true);
        state.input(' ');
        suggestions(state, "A word-separating space also ends the manual opening");
        state.input('.');
        state.setManualExpanded(false);
        suggestions(state, "The collapse button works even after a terminal period");

        state.input('.');
        state.input(-5);
        suggestions(state, "Delete returns to suggestions until current context is examined");
        state.syncContext("Oi");
        suggestions(state, "Deleting a terminal period keeps suggestions");
        state.syncContext("Oi.");
        shortcuts(state, "Deleting only the auto-space still leaves a terminal period");
        state.syncContext("3.14");
        suggestions(state, "A completed decimal does not count as a sentence end");
        state.syncContext("https://example.com");
        suggestions(state, "A dot inside an address does not count as a sentence end");

        state.input('a');
        state.syncContext(null);
        suggestions(state, "Unavailable editor context preserves the known input state");
        state.syncContext(" ");
        shortcuts(state, "An empty field exposes shortcuts after a cursor or field refresh");
        state.syncContext("Ola");
        state.input((CharSequence)" ");
        suggestions(state, "Inserted whitespace is not an empty field");
        state.input((CharSequence)"tudo bem. ");
        shortcuts(state, "Pasted prose ending in period and space opens shortcuts");
        state.input((CharSequence)"🙂");
        suggestions(state, "Unicode input after a period restores suggestions");
        state.input((CharSequence)null);
        suggestions(state, "An absent inserted chunk leaves mode unchanged");
        state.setManualExpanded(true);
        state.reset();
        state.input('a');
        suggestions(state, "Reset clears a previous field's manual opening");
        state.reset();
        shortcuts(state, "Reset restores the new-field state");

        System.out.println("ToolbarStateTest: " + checks + " checks passed");
    }
}
