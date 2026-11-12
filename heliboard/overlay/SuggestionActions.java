// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
/** Long words remain readable in a multiline popup; actions always use the original candidate. */
public final class SuggestionActions {
    public static void show(Context context,View anchor,String word,Runnable use,Runnable add,Runnable remove,Runnable more) {
        float density=context.getResources().getDisplayMetrics().density;
        int pad=(int)(16*density);
        LinearLayout root=new LinearLayout(context);
        root.setOrientation(1);
        root.setPadding(pad,pad,pad,pad);
        GradientDrawable bg=new GradientDrawable();
        bg.setColor(0xff25272d);
        bg.setCornerRadius(16*density);
        root.setBackground(bg);
        PopupWindow popup=new PopupWindow(root,Math.min((int)(360*density),context.getResources().getDisplayMetrics().widthPixels-pad*2),-2,true);
        popup.setBackgroundDrawable(bg);
        popup.setElevation(12*density);
        popup.setOutsideTouchable(true);
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        TextView full=new TextView(context);
        full.setText(word);
        full.setTextSize(22);
        full.setTextColor(Color.WHITE);
        full.setContentDescription("suggestion_full_word");
        full.setPadding(0,0,0,pad);
        root.addView(full,new LinearLayout.LayoutParams(-1,-2));
        String[] labels= {
            "Usar palavra","Adicionar ao dicionário","Remover sugestão","Mais sugestões"
        };
        Runnable[] actions= {
            use,add,remove,more
        };
        for(int i=0;i<actions.length;i++) {
            if(actions[i]==null)continue;
            final Runnable action=actions[i];
            Button b=new Button(context);
            b.setAllCaps(false);
            b.setText(labels[i]);
            b.setContentDescription(labels[i]);
            b.setOnClickListener(v-> {
                popup.dismiss();
                action.run();
            });
            root.addView(b,new LinearLayout.LayoutParams(-1,(int)(48*density)));
        }
        popup.showAsDropDown(anchor,0,0,Gravity.END);
    }
}

