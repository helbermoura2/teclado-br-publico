// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;
import android.view.inputmethod.*;
import java.util.Objects;
import helium314.keyboard.latin.LatinIME;
final class TextSnapshot {
    String original,source,pkg;
    int field,start,end,oldStart,oldEnd;
    boolean complete,highlighted;
    static TextSnapshot capture(LatinIME ime) {
        InputConnection ic=ime.getCurrentInputConnection();
        EditorInfo info=ime.getCurrentInputEditorInfo();
        if(ic==null||info==null)return null;
        TextSnapshot s=new TextSnapshot();
        s.pkg=info.packageName;
        s.field=info.fieldId;
        ExtractedText t=ic.getExtractedText(new ExtractedTextRequest(),0);
        CharSequence selected=ic.getSelectedText(0);
        s.complete=t!=null&&t.text!=null&&t.startOffset==0&&t.partialStartOffset<0&&t.text.length()<=6000;
        if(s.complete) {
            s.original=t.text.toString();
            s.oldStart=t.selectionStart;
            s.oldEnd=t.selectionEnd;
            s.start=Math.min(s.oldStart,s.oldEnd);
            s.end=Math.max(s.oldStart,s.oldEnd);
            s.complete=s.start>=0&&s.end<=s.original.length();
            if(s.start==s.end) {
                s.start=0;
                s.end=s.original.length();
            }
        }
        s.source=selected!=null?selected.toString():s.complete?s.original:"";
        return s;
    }
    boolean matches(LatinIME ime) {
        EditorInfo e=ime.getCurrentInputEditorInfo();
        InputConnection ic=ime.getCurrentInputConnection();
        if(!complete||e==null||ic==null||!Objects.equals(pkg,e.packageName)||e.fieldId!=field)return false;
        ExtractedText t=ic.getExtractedText(new ExtractedTextRequest(),0);
        return t!=null&&t.text!=null&&t.startOffset==0&&original.contentEquals(t.text);
    }
    void highlight(LatinIME ime) {
        if(complete&&matches(ime))highlighted=ime.getCurrentInputConnection().setSelection(start,end);
    }
    void restore(LatinIME ime) {
        if(highlighted&&matches(ime)) {
            ExtractedText t=ime.getCurrentInputConnection().getExtractedText(new ExtractedTextRequest(),0);
            if(t.selectionStart==start&&t.selectionEnd==end)ime.getCurrentInputConnection().setSelection(oldStart,oldEnd);
        }
    }
    boolean apply(LatinIME ime,String value) {
        if(!matches(ime))return false;
        InputConnection ic=ime.getCurrentInputConnection();
        ic.beginBatchEdit();
        try {
            return ic.setSelection(start,end)&&ic.commitText(value,1);
        }
        finally {
            ic.endBatchEdit();
        }
    }
}

