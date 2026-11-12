// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;

import org.junit.Test;
import static org.junit.Assert.*;

/** Fast JVM contract tests. No network, no key, no private user data. */
public final class HbxUpdatePolicyTest {
    @Test public void packageAndChannelAreSeparateFromLegacyKeyboard() {
        assertEquals("br.com.hbx.clever.preview",HbxUpdatePolicy.PACKAGE);
        assertNotEquals("br.com.tecladobr.heli.debug",HbxUpdatePolicy.PACKAGE);
        assertTrue(HbxUpdatePolicy.MANIFEST.endsWith("/cleverkeys/update.json"));
    }
    @Test public void onlySamePackagePreviewGithubApksAllowed() {
        assertTrue(HbxUpdatePolicy.apkUrl(
            "https://github.com/helbermoura2/teclado-br-publico/releases/download/"+
            "hbx-clever-v0.1.1/hbx-clever-v0.1.1.apk"));
        assertFalse(HbxUpdatePolicy.apkUrl("http://example.org/stolen.apk"));
        assertFalse(HbxUpdatePolicy.apkUrl(
            "https://github.com/other/repo/releases/download/hbx-clever-v0.1.1/hbx-clever-v0.1.1.apk"));
        assertFalse(HbxUpdatePolicy.apkUrl(
            "https://github.com/helbermoura2/teclado-br-publico/releases/download/"+
            "heli-0.9.2-beta.17/teclado-br-heli-0.9.2-beta.17.apk"));
    }
    @Test public void rejectsTamperedOrQueryBearingReleaseUrls() {
        assertTrue(HbxUpdatePolicy.releaseUrl(
            "https://github.com/helbermoura2/teclado-br-publico/releases/tag/hbx-clever-v0.1.1"));
        assertFalse(HbxUpdatePolicy.releaseUrl(
            "https://github.com/helbermoura2/teclado-br-publico/releases/tag/"+
            "hbx-clever-v0.1.1?redirect=evil"));
        assertFalse(HbxUpdatePolicy.apkUrl(
            "https://github.com/helbermoura2/teclado-br-publico/releases/download/"+
            "hbx-clever-v0.1.1/hbx-clever-v0.1.1.apk#bad"));
    }
    @Test public void signedGithubRedirectsOnly() {
        assertTrue(HbxUpdatePolicy.redirect(
            "https://release-assets.githubusercontent.com/asset-id?token=placeholder"));
        assertFalse(HbxUpdatePolicy.redirect("https://attacker.invalid/payload?token=foo"));
        assertFalse(HbxUpdatePolicy.redirect("http://release-assets.githubusercontent.com/payload"));
    }
    @Test public void versionAndDigestRequirements() {
        assertTrue(HbxUpdatePolicy.newer(1002,1012));
        assertFalse(HbxUpdatePolicy.newer(1002,1002));
        assertFalse(HbxUpdatePolicy.newer(1002,1001));
        assertFalse(HbxUpdatePolicy.newer(0,1012));
        assertFalse(HbxUpdatePolicy.hexSha("not-a-sha"));
        assertTrue(HbxUpdatePolicy.hexSha(new String(new char[64]).replace('\0','a')));
    }
}
