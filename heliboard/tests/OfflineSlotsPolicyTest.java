// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;

import org.junit.Test;
import static org.junit.Assert.*;
/** Static allowlist checks: existing Gemma slot must never be discarded. */
public final class OfflineSlotsPolicyTest {
    @Test public void threeStableSlots() {
        assertEquals(3,ModelStore.SLOTS.length);
        assertEquals("existing",ModelStore.SLOTS[0]);
        assertEquals("qwen35-2b",ModelStore.SLOTS[1]);
        assertEquals("gemma4-e4b",ModelStore.SLOTS[2]);
    }
    @Test public void invalidSlotRejected() {
        try {ModelStore.label("../unsafe");fail("Unexpected slot");}
        catch(IllegalArgumentException expected){}
    }
}
