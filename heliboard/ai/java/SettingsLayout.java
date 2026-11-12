// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;
/** Keep private settings usable under Android 15 edge-to-edge system bars. */
final class SettingsLayout {
    static void insets(View root,int padding) {
        root.setPadding(padding,padding,padding,padding);
        root.setOnApplyWindowInsetsListener((view,insets)-> {
            int left,top,right,bottom;
            if(Build.VERSION.SDK_INT>=30) {
                android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
                left=bars.left;top=bars.top;right=bars.right;bottom=bars.bottom;
            } else {
                left=insets.getSystemWindowInsetLeft();top=insets.getSystemWindowInsetTop();
                right=insets.getSystemWindowInsetRight();bottom=insets.getSystemWindowInsetBottom();
            }
            view.setPadding(padding+left,padding+top,padding+right,padding+bottom);
            return insets;
        });
        root.requestApplyInsets();
    }
}

