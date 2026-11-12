// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin;
import android.content.Context;
import helium314.keyboard.latin.utils.DeviceProtectedUtils;
public final class Typography {
    public static final String SYMBOL_SCALE="teclado_br_symbol_scale";
    private static volatile float symbols=1f;
    public static void load(Context context) {
        symbols=Math.max(.7f,Math.min(1.3f,DeviceProtectedUtils.getSharedPreferences(context).getFloat(SYMBOL_SCALE,1f)));
    }
    public static float symbolScale() {
        return symbols;
    }
}

