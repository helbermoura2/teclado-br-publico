// SPDX-License-Identifier: GPL-3.0-only
package br.com.tecladobr.heli.testing;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.graphics.drawable.LayerDrawable;
import helium314.keyboard.latin.common.ColorType;
import helium314.keyboard.latin.settings.Settings;
import android.view.accessibility.AccessibilityNodeInfo;
import helium314.keyboard.latin.utils.ToolbarKey;
import helium314.keyboard.keyboard.KeyboardSwitcher;
import helium314.keyboard.keyboard.Key;
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode;
import helium314.keyboard.latin.R;
import helium314.keyboard.latin.SuggestedWords;
import helium314.keyboard.latin.suggestions.SuggestionStripView;

/** Real IME touch regression for the leading handle and sentence punctuation. */
public final class ToolbarTrial {
    private static void onMain(HeliTrialInstrumentation t,Runnable action) {
        Throwable[] failure={null};
        t.runOnMainSync(()->{try{action.run();}catch(Throwable error){failure[0]=error;}});
        if(failure[0]!=null)throw new AssertionError("Toolbar UI assertion",failure[0]);
    }
    private static SuggestionStripView strip(HeliTrialInstrumentation t) {
        return (SuggestionStripView)t.findView(KeyboardSwitcher.getInstance().getStripContainer(),v->v instanceof SuggestionStripView);
    }
    private static void mode(HeliTrialInstrumentation t,boolean tools)throws Exception {
        Thread.sleep(550);
        onMain(t, ()->{
            SuggestionStripView s=strip(t);
            t.check(s.findViewById(R.id.pinned_keys).isShown()==tools,"Wrong favorites mode: "+tools);
            t.check(s.findViewById(R.id.suggestions_strip).isShown()!=tools,"Words and favorites overlap");
            t.check(!s.findViewById(R.id.toolbar_container).isShown(),"Overflow replaced main favorites");
            t.check(!s.findViewById(R.id.suggestions_strip_toolbar_key).isShown(),"Trailing ellipsis must be absent");
            t.check(t.findView(s,v->v.getTag()==ToolbarKey.LANGUAGE&&v.isShown())==null,"Language globe must be absent from strip");
            View toggle=s.findViewById(R.id.teclado_bar_toggle);
            t.check(toggle.isShown()&&toggle.getWidth()>0,"Leading handle missing");
            float density=t.getTargetContext().getResources().getDisplayMetrics().density;
            t.check(toggle.getWidth()>=Math.round(48*density),"Handle touch width must reach 48dp");
            if(tools) {
                android.view.ViewGroup keys=s.findViewById(R.id.pinned_keys);
                int count=0;
                for(int i=0;i<keys.getChildCount();i++) {
                    View item=keys.getChildAt(i);
                    if(!(item instanceof ImageButton))continue;
                    ImageButton button=(ImageButton)item;count++;
                    int available=button.getWidth()-button.getPaddingLeft()-button.getPaddingRight();
                    t.check(Math.abs(available-Math.round(22*density))<=1,"Favorite icon box size differs");
                    t.check(button.getBackground() instanceof LayerDrawable,"Favorite background is not normalized");
                    LayerDrawable layers=(LayerDrawable)button.getBackground();
                    t.check(Math.abs(layers.getLayerWidth(0)-Math.round(36*density))<=1&&Math.abs(layers.getLayerHeight(0)-Math.round(36*density))<=1,"Favorite background must be circular");
                }
                t.check(count==5,"Five favorites must remain available");
            }
            int[] left=new int[2],words=new int[2];toggle.getLocationOnScreen(left);
            View active=s.findViewById(tools?R.id.pinned_keys:R.id.suggestions_strip);active.getLocationOnScreen(words);
            t.check(left[0]<words[0],"Handle must precede words/favorites");
        });
    }
    private static void toggle(HeliTrialInstrumentation t)throws Exception {
        Rect r=new Rect();onMain(t, ()->{View v=strip(t).findViewById(R.id.teclado_bar_toggle);int[] xy=new int[2];v.getLocationOnScreen(xy);r.set(xy[0],xy[1],xy[0]+v.getWidth(),xy[1]+v.getHeight());});t.tap(r);
    }
    private static void alphabet(HeliTrialInstrumentation t,boolean wanted)throws Exception {
        boolean[] alpha={false};int[] code={0};
        onMain(t, ()->{for(Key k:t.main().getKeyboard().getSortedKeys()){
            if(k.getCode()=='a'||k.getCode()=='A')alpha[0]=true;
            if(k.getCode()==KeyCode.SYMBOL_ALPHA||k.getCode()==(wanted?KeyCode.ALPHA:KeyCode.SYMBOL))code[0]=k.getCode();
        }});
        if(alpha[0]!=wanted){t.check(code[0]!=0,"No alphabet/symbol switch");t.tap(t.keyBounds(code[0],false));Thread.sleep(350);}
    }
    public static void run(HeliTrialInstrumentation t,Bundle result)throws Exception {
        t.clear();t.type("ola");mode(t,false);t.shot("toolbar-words");
        onMain(t, ()->t.check(Settings.getValues().mColors.get(ColorType.KEY_HINT_TEXT)==android.graphics.Color.rgb(232,233,239),"Clearer hint palette was not loaded"));
        Rect handle=new Rect();onMain(t, ()->{View v=strip(t).findViewById(R.id.teclado_bar_toggle);int[] xy=new int[2];v.getLocationOnScreen(xy);handle.set(xy[0],xy[1],xy[0]+v.getWidth(),xy[1]+v.getHeight());});
        int inset=Math.round(2*t.getTargetContext().getResources().getDisplayMetrics().density);
        t.tap(new Rect(handle.left+inset,handle.top,handle.left+inset+1,handle.bottom));mode(t,true);
        t.tap(new Rect(handle.right-inset-1,handle.top,handle.right-inset,handle.bottom));mode(t,false);
        t.expect("ola");
        result.putString("visual_refinements","PASS: 48dp handle edge taps, unchanged text, 22dp icons, 36dp circles, clearer hints");
        toggle(t);mode(t,true);t.shot("toolbar-manual-favorites");
        onMain(t, ()->{strip(t).setSuggestions(SuggestedWords.getEmptyInstance(),false);strip(t).hideToolbarForSuggestions();});mode(t,true);
        toggle(t);mode(t,false);toggle(t);mode(t,true);
        t.type("a");mode(t,false);
        for(char cp:new char[]{'.','!','?'}) {
            alphabet(t,true);t.clear();t.type("ola");mode(t,false);
            if(cp!='.')alphabet(t,false);
            t.tap(t.keyBounds(cp,false));mode(t,true);
            t.check(t.text().trim().endsWith(String.valueOf(cp)),"Punctuation missing from editor");
            onMain(t, ()->{strip(t).setSuggestions(SuggestedWords.getEmptyInstance(),false);strip(t).hideToolbarForSuggestions();});mode(t,true);
            t.shot("toolbar-after-"+(int)cp);
            toggle(t);mode(t,false);toggle(t);mode(t,true);
            alphabet(t,true);t.type("a");mode(t,false);
        }
        t.shot("toolbar-next-sentence");
        String beforeLanguagePicker=t.text();
        Rect space=t.keyBounds(' ',false);
        long down=android.os.SystemClock.uptimeMillis();
        t.event(down,android.view.MotionEvent.ACTION_DOWN,new int[]{0},new Rect[]{space});
        Thread.sleep(850);
        t.event(down,android.view.MotionEvent.ACTION_UP,new int[]{0},new Rect[]{space});
        boolean english=false,portuguese=false;
        long languageUntil=android.os.SystemClock.uptimeMillis()+10000;
        do {
            english=t.find(n->n.getText()!=null&&(n.getText().toString().contains("English")||n.getText().toString().contains("Inglês")))!=null;
            portuguese=t.find(n->n.getText()!=null&&n.getText().toString().toLowerCase(java.util.Locale.ROOT).contains("portugu"))!=null;
            if(english&&portuguese)break;
            Thread.sleep(150);
        } while(android.os.SystemClock.uptimeMillis()<languageUntil);
        t.check(english&&portuguese,"Space long press must offer English and Portuguese");
        t.shot("language-space-picker");t.shell("input keyevent 4");Thread.sleep(400);
        t.expect(beforeLanguagePicker);t.language("pt");mode(t,false);
        result.putString("language_picker","PASS: English and Portuguese on space long press; no text inserted");
        toggle(t);mode(t,true);t.tagged(ToolbarKey.SETTINGS);
        AccessibilityNodeInfo updates=null;
        long until=android.os.SystemClock.uptimeMillis()+12000;
        do {
            updates=t.find(n->n.getText()!=null&&n.getText().toString().contains("Atualizações do Teclado BR"));
            if(updates!=null)break;
            Thread.sleep(150);
        } while(android.os.SystemClock.uptimeMillis()<until);
        t.check(updates!=null,"Update entry missing from main settings");
        Rect updateBounds=new Rect();updates.getBoundsInScreen(updateBounds);t.tap(updateBounds);
        t.aiNode("ai_check_update");t.shot("updater-main-settings");
        result.putString("updater_entry","PASS: main keyboard settings open the update screen");
        result.putString("toolbar","PASS: no ellipsis/globe, leading handle, . ! ?, next letter, space language picker");
    }
}
