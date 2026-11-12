// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.provider.UserDictionary;
import android.view.View;
import android.view.inputmethod.InputConnection;
import android.widget.PopupMenu;
import android.widget.Toast;
import helium314.keyboard.latin.settings.Settings;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
/** Explicitly save the actual editor word, independently of the suggestion candidates. */
public final class PersonalWords {
    private static final ExecutorService writer=Executors.newSingleThreadExecutor();
    private static boolean wordChar(int c) {
        int kind=Character.getType(c);
        return Character.isLetter(c)||kind==Character.NON_SPACING_MARK||kind==Character.COMBINING_SPACING_MARK||c=='\''||c=='-';
    }
    public static String wordAtCursor(InputConnection ic) {
        if(ic==null)return "";
        CharSequence selected=ic.getSelectedText(0);
        if(selected!=null&&selected.length()>0) {
            String value=selected.toString().trim();
            return valid(value)?value:"";
        }
        CharSequence before=ic.getTextBeforeCursor(256,0),after=ic.getTextAfterCursor(128,0);
        String left=before==null?"":before.toString(),right=after==null?"":after.toString();
        int end=left.length();
        while(end>0&&Character.isWhitespace(left.codePointBefore(end)))end-=Character.charCount(left.codePointBefore(end));
        boolean trailing=end!=left.length();
        int start=end;
        while(start>0&&wordChar(left.codePointBefore(start)))start-=Character.charCount(left.codePointBefore(start));
        int tail=0;
        if(!trailing)while(tail<right.length()&&wordChar(right.codePointAt(tail)))tail+=Character.charCount(right.codePointAt(tail));
        String value=left.substring(start,end)+(trailing?"":right.substring(0,tail));
        return valid(value)?value:"";
    }
    private static boolean valid(String value) {
        return !value.isEmpty()&&value.codePointCount(0,value.length())<48&&value.codePoints().allMatch(PersonalWords::wordChar)&&value.codePoints().anyMatch(Character::isLetter);
    }
    public static void open(LatinIME ime,View anchor) {
        if(anchor==null||Settings.getValues().mIncognitoModeEnabled||Settings.getValues().mInputAttributes.mIsPasswordField)return;
        final String word=wordAtCursor(ime.getCurrentInputConnection());
        final Locale locale=Settings.getValues().mLocale;
        PopupMenu menu=new PopupMenu(anchor.getContext(),anchor);
        if(!word.isEmpty())menu.getMenu().add("Adicionar “"+word+"” ao dicionário").setContentDescription("Salvar palavra no dicionário").setOnMenuItemClickListener(item-> {
            save(ime,word,locale);
            return true;
        });
        menu.getMenu().add("Gerenciar palavras e backup").setContentDescription("dictionary_manage").setOnMenuItemClickListener(item-> {
            ime.startActivity(new android.content.Intent(ime,helium314.keyboard.latin.ai.DictionaryActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        });
        menu.getMenu().add("Aparência do teclado").setContentDescription("keyboard_appearance").setOnMenuItemClickListener(item-> {
            ime.startActivity(new android.content.Intent(ime,helium314.keyboard.latin.ai.AppearanceActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        });
        menu.show();
    }
    public static void save(Context context,String word,Locale locale) {
        final Context app=context.getApplicationContext();
        writer.execute(()-> {
            String message;
            try {
                UserDictionary.Words.addWord(app,word,250,null,locale);
                message="Palavra adicionada: "+word;
            }
            catch(RuntimeException error) {
                message="Não foi possível salvar a palavra no dicionário.";
            }
            final String result=message;
            new Handler(Looper.getMainLooper()).post(()->Toast.makeText(app,result,Toast.LENGTH_LONG).show());
        });
    }
}

