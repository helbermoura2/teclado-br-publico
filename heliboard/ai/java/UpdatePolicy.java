// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;
import java.net.URI;
import java.util.Locale;
/** Strict, side-effect-free Android update policy. Never accept an arbitrary URL from the manifest. */
public final class UpdatePolicy {
    private UpdatePolicy() {}
    public static final String MANIFEST="https://raw.githubusercontent.com/helbermoura2/teclado-br-publico/main/update.json";
    public static final String PACKAGE="br.com.tecladobr.heli.debug";
    public static boolean newer(long installed,long offered) {return installed>0&&offered>installed&&offered<1000000;}
    public static boolean hexSha(String value) {return value!=null&&value.matches("[0-9a-fA-F]{64}");}
    public static boolean apkUrl(String raw) {
        try {
            URI uri=new URI(raw);
            if(!"https".equals(uri.getScheme())||uri.getRawUserInfo()!=null||
               uri.getPort()!=-1||uri.getRawQuery()!=null||uri.getRawFragment()!=null)return false;
            String host=uri.getHost()==null?"":uri.getHost().toLowerCase(Locale.ROOT);
            String path=uri.getRawPath();
            return ("github.com".equals(host)&&path!=null&&
                path.matches("/helbermoura2/teclado-br-publico/releases/download/heli-0[.]9[.]2-beta[.][0-9]+/teclado-br-heli-0[.]9[.]2-beta[.][0-9]+[.]apk"))||
                ("monitorglobal.top".equals(host)&&path!=null&&path.startsWith("/downloads/teclado-br-heli-")&&path.endsWith(".apk"));
        }catch(Exception error){return false;}
    }
    public static boolean releaseUrl(String raw) {
        try {
            URI uri=new URI(raw);
            return "https".equals(uri.getScheme())&&"github.com".equals(uri.getHost())&&
                uri.getPort()==-1&&uri.getRawQuery()==null&&uri.getRawFragment()==null&&
                uri.getRawUserInfo()==null&&uri.getRawPath()!=null&&
                uri.getRawPath().matches("/helbermoura2/teclado-br-publico/releases/tag/heli-0[.]9[.]2-beta[.][0-9]+");
        }catch(Exception e){return false;}
    }
    public static boolean redirect(String raw) {
        try {
            URI uri=new URI(raw);
            if(!"https".equals(uri.getScheme())||uri.getPort()!=-1||
                uri.getRawUserInfo()!=null||uri.getRawFragment()!=null)return false;
            String host=uri.getHost()==null?"":uri.getHost().toLowerCase(Locale.ROOT);
            return "release-assets.githubusercontent.com".equals(host)||
                "objects.githubusercontent.com".equals(host)||
                "github-releases.githubusercontent.com".equals(host);
        }catch(Exception e){return false;}
    }
}
