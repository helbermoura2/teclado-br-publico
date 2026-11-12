// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;

import java.net.URI;
import java.util.Locale;

/**
 * Strict allowlist for the separate HBX CleverKeys preview updater.
 *
 * This updater NEVER offers a different Android package and NEVER upgrades
 * the user's currently installed HeliBoard-based keyboard. Promotion to that
 * package requires separately built, signer-matching and tested APKs.
 */
public final class HbxUpdatePolicy {
    private HbxUpdatePolicy() {}
    public static final String SCHEMA="hbx-clever-updates-v1";
    public static final String PACKAGE="br.com.hbx.clever.preview";
    public static final String MANIFEST=
        "https://raw.githubusercontent.com/helbermoura2/teclado-br-publico/main/cleverkeys/update.json";

    public static boolean newer(long installed,long candidate) {
        return installed>0&&candidate>installed&&candidate<1_000_000;
    }
    public static boolean hexSha(String value) {
        return value!=null&&value.matches("[0-9a-fA-F]{64}");
    }
    private static URI checked(String raw) throws Exception {
        URI uri=new URI(raw);
        if(!"https".equals(uri.getScheme())||uri.getHost()==null||
            uri.getRawUserInfo()!=null||uri.getPort()!=-1||
            uri.getRawQuery()!=null||uri.getRawFragment()!=null)
            throw new IllegalArgumentException("Endereço de atualização inválido.");
        return uri;
    }
    public static boolean apkUrl(String raw) {
        try {
            URI uri=checked(raw);
            return "github.com".equals(uri.getHost().toLowerCase(Locale.ROOT))&&
                uri.getRawPath().matches(
                    "/helbermoura2/teclado-br-publico/releases/download/"+
                    "hbx-clever-v[0-9]+[.][0-9]+[.][0-9]+/"+
                    "hbx-clever-v[0-9]+[.][0-9]+[.][0-9]+[.]apk");
        }catch(Exception error){return false;}
    }
    public static boolean releaseUrl(String raw) {
        try {
            URI uri=checked(raw);
            return "github.com".equals(uri.getHost().toLowerCase(Locale.ROOT))&&
                uri.getRawPath().matches(
                    "/helbermoura2/teclado-br-publico/releases/tag/"+
                    "hbx-clever-v[0-9]+[.][0-9]+[.][0-9]+");
        }catch(Exception error){return false;}
    }
    /** GitHub asset redirects use signed query strings, never require a login
     * credential or allow a redirect to an unrelated host. */
    public static boolean redirect(String raw) {
        try {
            URI uri=new URI(raw);
            if(!"https".equals(uri.getScheme())||uri.getHost()==null||
                uri.getRawUserInfo()!=null||uri.getPort()!=-1||
                uri.getRawFragment()!=null)return false;
            String host=uri.getHost().toLowerCase(Locale.ROOT);
            return "release-assets.githubusercontent.com".equals(host)||
                "objects.githubusercontent.com".equals(host)||
                "github-releases.githubusercontent.com".equals(host);
        }catch(Exception error){return false;}
    }
}
