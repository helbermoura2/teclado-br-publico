// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;
import android.text.*;
import android.text.style.*;
import java.util.*;
import java.util.regex.*;
/** Bounded word-level diff. Original and result text are never modified by presentation. */
public final class TextChanges {
    public static final class Pair {
        public final CharSequence before,after;
        Pair(CharSequence a,CharSequence b) {
            before=a;
            after=b;
        }
    }
    private static List<String> tokens(String s) {
        List<String> out=new ArrayList<>();
        Matcher m=Pattern.compile("\\s+|[\\p{L}\\p{N}\\p{M}]+|[^\\s]").matcher(s);
        while(m.find())out.add(m.group());
        return out;
    }
    public static Pair compare(String original,String revised) {
        List<String> a=tokens(original),b=tokens(revised);
        boolean[] keepA=new boolean[a.size()],keepB=new boolean[b.size()];
        if((long)a.size()*b.size()<=200000) {
            int[][] lcs=new int[a.size()+1][b.size()+1];
            for(int i=a.size()-1;i>=0;i--)for(int j=b.size()-1;j>=0;j--)lcs[i][j]=a.get(i).equals(b.get(j))?1+lcs[i+1][j+1]:Math.max(lcs[i+1][j],lcs[i][j+1]);
            int i=0,j=0;
            while(i<a.size()&&j<b.size()) {
                if(a.get(i).equals(b.get(j))) {
                    keepA[i++]=true;
                    keepB[j++]=true;
                }
                else if(lcs[i+1][j]>=lcs[i][j+1])i++;
                else j++;
            }
        }
        else {
            int start=0;
            while(start<a.size()&&start<b.size()&&a.get(start).equals(b.get(start))) {
                keepA[start]=keepB[start]=true;
                start++;
            }
            int i=a.size()-1,j=b.size()-1;
            while(i>=start&&j>=start&&a.get(i).equals(b.get(j))) {
                keepA[i--]=true;
                keepB[j--]=true;
            }
        }
        return new Pair(mark(a,keepA,true),mark(b,keepB,false));
    }
    private static CharSequence mark(List<String> tokens,boolean[] keep,boolean removed) {
        SpannableStringBuilder out=new SpannableStringBuilder();
        for(int i=0;i<tokens.size();i++) {
            int start=out.length();
            out.append(tokens.get(i));
            if(!keep[i]) {
                out.setSpan(new BackgroundColorSpan(removed?0x885c2737:0xbb174f50),start,out.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                if(removed)out.setSpan(new StrikethroughSpan(),start,out.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return out;
    }
}

