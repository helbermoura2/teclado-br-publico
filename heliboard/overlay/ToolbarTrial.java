// SPDX-License-Identifier: GPL-3.0-only
package br.com.tecladobr.heli.testing;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.View;
import helium314.keyboard.keyboard.KeyboardSwitcher;
import helium314.keyboard.keyboard.Key;
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode;
import helium314.keyboard.latin.R;
import helium314.keyboard.latin.SuggestedWords;
import helium314.keyboard.latin.suggestions.SuggestionStripView;

/** Real IME touch regression for the leading handle and sentence punctuation. */
public final class ToolbarTrial {
    private static SuggestionStripView strip(HeliTrialInstrumentation t) {
        return (SuggestionStripView)t.findView(KeyboardSwitcher.getInstance().getStripContainer(),v->v instanceof SuggestionStripView);
    }
    private static void mode(HeliTrialInstrumentation t,boolean tools)throws Exception {
        Thread.sleep(550);
        t.runOnMainSync(()->{
            SuggestionStripView s=strip(t);
            t.check(s.findViewById(R.id.pinned_keys).isShown()==tools,"Wrong favorites mode: "+tools);
            t.check(s.findViewById(R.id.suggestions_strip).isShown()!=tools,"Words and favorites overlap");
            t.check(!s.findViewById(R.id.toolbar_container).isShown(),"Overflow replaced main favorites");
            View toggle=s.findViewById(R.id.teclado_bar_toggle);
            t.check(toggle.isShown()&&toggle.getWidth()>0,"Leading handle missing");
            int[] left=new int[2],words=new int[2];toggle.getLocationOnScreen(left);
            View active=s.findViewById(tools?R.id.pinned_keys:R.id.suggestions_strip);active.getLocationOnScreen(words);
            t.check(left[0]<words[0],"Handle must precede words/favorites");
        });
    }
    private static void toggle(HeliTrialInstrumentation t)throws Exception {
        Rect r=new Rect();t.runOnMainSync(()->{View v=strip(t).findViewById(R.id.teclado_bar_toggle);int[] xy=new int[2];v.getLocationOnScreen(xy);r.set(xy[0],xy[1],xy[0]+v.getWidth(),xy[1]+v.getHeight());});t.tap(r);
    }
    private static void alphabet(HeliTrialInstrumentation t,boolean wanted)throws Exception {
        boolean[] alpha={false};int[] code={0};
        t.runOnMainSync(()->{for(Key k:t.main().getKeyboard().getSortedKeys()){
            if(k.getCode()=='a'||k.getCode()=='A')alpha[0]=true;
            if(k.getCode()==KeyCode.SYMBOL_ALPHA||k.getCode()==(wanted?KeyCode.ALPHA:KeyCode.SYMBOL))code[0]=k.getCode();
        }});
        if(alpha[0]!=wanted){t.check(code[0]!=0,"No alphabet/symbol switch");t.tap(t.keyBounds(code[0],false));Thread.sleep(350);}
    }
    public static void run(HeliTrialInstrumentation t,Bundle result)throws Exception {
        t.clear();t.type("ola");mode(t,false);t.shot("toolbar-words");
        toggle(t);mode(t,true);t.shot("toolbar-manual-favorites");
        t.runOnMainSync(()->{strip(t).setSuggestions(SuggestedWords.getEmptyInstance(),false);strip(t).hideToolbarForSuggestions();});mode(t,true);
        toggle(t);mode(t,false);toggle(t);mode(t,true);
        t.type("a");mode(t,false);
        for(char cp:new char[]{'.','!','?'}) {
            alphabet(t,true);t.clear();t.type("ola");mode(t,false);
            if(cp!='.')alphabet(t,false);
            t.tap(t.keyBounds(cp,false));mode(t,true);
            t.check(t.text().trim().endsWith(String.valueOf(cp)),"Punctuation missing from editor");
            t.runOnMainSync(()->{strip(t).setSuggestions(SuggestedWords.getEmptyInstance(),false);strip(t).hideToolbarForSuggestions();});mode(t,true);
            t.shot("toolbar-after-"+(int)cp);
            toggle(t);mode(t,false);toggle(t);mode(t,true);
            alphabet(t,true);t.type("a");mode(t,false);
        }
        t.shot("toolbar-next-sentence");
        result.putString("toolbar","PASS: leading handle, manual open/collapse, . ! ?, delayed results, next letter");
    }
}
