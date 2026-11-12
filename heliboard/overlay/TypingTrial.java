// SPDX-License-Identifier: GPL-3.0-only
package br.com.tecladobr.heli.testing;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.InputType;
import android.view.MotionEvent;
import android.view.View;
import helium314.keyboard.compat.AppWorkarounds;
import helium314.keyboard.keyboard.KeyboardSwitcher;
import helium314.keyboard.latin.R;
import java.util.List;

/** Synthetic input profiles only; this harness does not open third-party apps. */
public final class TypingTrial {
    private static void wordsOnly(HeliTrialInstrumentation t) {
        Throwable[] error={null};
        t.runOnMainSync(()->{try {
            View root=KeyboardSwitcher.getInstance().getStripContainer();
            View words=root.findViewById(R.id.suggestions_strip);
            t.check(words!=null&&words.isShown(),"Word strip disappeared while typing");
            t.check(!root.findViewById(R.id.pinned_keys).isShown(),"Icons replaced word suggestions mid-phrase");
            t.check(root.findViewById(R.id.teclado_bar_toggle).isShown(),"Leading handle disappeared");
        }catch(Throwable failure){error[0]=failure;}});
        if(error[0]!=null)throw new AssertionError("Typing strip state",error[0]);
    }
    public static void run(HeliTrialInstrumentation t,Bundle result)throws Exception {
        final int message=InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
        final int instagram=AppWorkarounds.INSTANCE.adjustInputType(message|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,"com.instagram.android");
        t.check((instagram&InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)==0,"Instagram prose workaround missing");
        int[] fields={message,instagram,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES};
        String[] profiles={"short_message","instagram_adjusted_input","multiline_composer"};
        int total=0;
        for(int profile=0;profile<fields.length;profile++) {
            t.trialInputType=fields[profile];t.options(false,false);t.launch();t.clear();
            Rect e=t.bounds('e',false),r=t.bounds('r',false);
            StringBuilder expected=new StringBuilder();long begin=SystemClock.uptimeMillis();
            for(int i=0;i<100;i++) {
                long down=SystemClock.uptimeMillis();
                t.event(down,MotionEvent.ACTION_DOWN,new int[]{0},new Rect[]{e});
                t.event(down,MotionEvent.ACTION_POINTER_DOWN|(1<<8),new int[]{0,1},new Rect[]{e,r});
                boolean reverse=(i%2)==1;
                t.event(down,MotionEvent.ACTION_POINTER_UP|((reverse?1:0)<<8),new int[]{0,1},new Rect[]{e,r});
                t.event(down,MotionEvent.ACTION_UP,new int[]{reverse?0:1},new Rect[]{reverse?e:r});
                expected.append("er");
            }
            long injected=SystemClock.uptimeMillis();t.expect(expected.toString());
            long settled=SystemClock.uptimeMillis();wordsOnly(t);total+=expected.length();
            result.putLong(profiles[profile]+"_injection_ms",injected-begin);
            result.putLong(profiles[profile]+"_settle_ms",settled-injected);
            t.clear();t.type("voce");
            long until=SystemClock.uptimeMillis()+15000;List<String> candidates;
            do {
                candidates=t.suggestions();
                if(candidates.stream().anyMatch(s->s.equalsIgnoreCase("você")))break;
                Thread.sleep(100);
            }while(SystemClock.uptimeMillis()<until);
            t.check(candidates.stream().anyMatch(s->s.equalsIgnoreCase("você")),"PT-BR candidate missing in "+profiles[profile]+": "+candidates);
            wordsOnly(t);Thread.sleep(650);wordsOnly(t);
            t.shot("typing-"+profiles[profile]);t.type(" ");wordsOnly(t);
            result.putString(profiles[profile],"PASS: 200 overlapping characters, reversed releases, PT-BR candidates and stable strip");
        }
        result.putInt("lossless_characters",total);
        result.putString("typing_scope","Synthetic input profiles on emulator; not actual WhatsApp, Instagram or ChatGPT apps");
    }
}
