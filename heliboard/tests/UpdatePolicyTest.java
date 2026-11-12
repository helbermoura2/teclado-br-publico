// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;
import org.junit.Test;
import static org.junit.Assert.*;
/** Policy-only tests: no network access, no real update requests, no user data. */
public final class UpdatePolicyTest {
    @Test public void versionCannotDowngradeOrReinstall(){
        assertTrue(UpdatePolicy.newer(9008,9009));
        assertFalse(UpdatePolicy.newer(9009,9009));
        assertFalse(UpdatePolicy.newer(9009,9008));
        assertFalse(UpdatePolicy.newer(0,9009));
        assertFalse(UpdatePolicy.newer(9008,1000000));
    }
    @Test public void pinnedShaOnly(){
        assertTrue(UpdatePolicy.hexSha("f".repeat(64)));
        assertFalse(UpdatePolicy.hexSha("abcdef"));
        assertFalse(UpdatePolicy.hexSha("g".repeat(64)));
        assertFalse(UpdatePolicy.hexSha(null));
    }
    @Test public void onlyReleasePathsAndExactHosts(){
        assertTrue(UpdatePolicy.apkUrl("https://github.com/helbermoura2/teclado-br-publico/releases/download/heli-0.9.2-beta.8/teclado-br-heli-0.9.2-beta.8.apk"));
        assertTrue(UpdatePolicy.apkUrl("https://monitorglobal.top/downloads/teclado-br-heli-0.9.2-beta.8.apk"));
        assertFalse(UpdatePolicy.apkUrl("https://evil.example/teclado-br-heli-0.9.2-beta.8.apk"));
        assertFalse(UpdatePolicy.apkUrl("https://github.com.evil.example/helbermoura2/teclado-br-publico/releases/download/heli-0.9.2-beta.8/teclado-br-heli-0.9.2-beta.8.apk"));
        assertFalse(UpdatePolicy.apkUrl("http://github.com/helbermoura2/teclado-br-publico/releases/download/heli-0.9.2-beta.8/teclado-br-heli-0.9.2-beta.8.apk"));
        assertFalse(UpdatePolicy.apkUrl("https://github.com:443/helbermoura2/teclado-br-publico/releases/download/heli-0.9.2-beta.8/teclado-br-heli-0.9.2-beta.8.apk"));
        assertFalse(UpdatePolicy.apkUrl("https://github.com/helbermoura2/teclado-br-publico/releases/download/heli-0.9.2-beta.8/teclado-br-heli-0.9.2-beta.8.apk?token=123"));
        assertTrue(UpdatePolicy.releaseUrl("https://github.com/helbermoura2/teclado-br-publico/releases/tag/heli-0.9.2-beta.8"));
        assertFalse(UpdatePolicy.releaseUrl("https://github.com/other/repo/releases/tag/heli-0.9.2-beta.8"));
        assertTrue(UpdatePolicy.redirect("https://release-assets.githubusercontent.com/signed?token=example"));
        assertFalse(UpdatePolicy.redirect("https://fake.release-assets.githubusercontent.com/signed?token=example"));
        assertFalse(UpdatePolicy.redirect("http://release-assets.githubusercontent.com/signed"));
    }
}
