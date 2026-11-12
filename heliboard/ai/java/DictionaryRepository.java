// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.provider.UserDictionary;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** Personal words use the Android provider; no separate copy of the user's messages exists. */
public final class DictionaryRepository {
    public static final class Word {
        public final long id;
        public final String text,locale,shortcut;
        public final int frequency;
        Word(long id,String word,String locale,String shortcut,int frequency) {
            this.id=id;
            text=word;
            this.locale=locale;
            this.shortcut=shortcut;
            this.frequency=frequency;
        }
    }
    public static List<Word> list(Context c) {
        List<Word> words=new ArrayList<>();
        try(Cursor rows=c.getContentResolver().query(UserDictionary.Words.CONTENT_URI,new String[] {
            "_id","word","locale","shortcut","frequency"
        },null,null,"word ASC")) {
            if(rows!=null)while(rows.moveToNext())words.add(new Word(rows.getLong(0),rows.getString(1),rows.getString(2),rows.getString(3),rows.getInt(4)));
        }
        return words;
    }
    public static void delete(Context c,long id) {
        c.getContentResolver().delete(ContentUris.withAppendedId(UserDictionary.Words.CONTENT_URI,id),null,null);
    }
    public static void save(Context c,String word,int frequency,String shortcut,String locale) {
        UserDictionary.Words.addWord(c,word,Math.max(1,Math.min(255,frequency)),shortcut,locale==null?null:Locale.forLanguageTag(locale.replace('_','-')));
    }
    public static void exportTo(Context c,OutputStream out)throws Exception {
        JSONArray rows=new JSONArray();
        for(Word word:list(c))rows.put(new JSONObject().put("word",word.text).put("locale",word.locale==null?JSONObject.NULL:word.locale).put("shortcut",word.shortcut==null?JSONObject.NULL:word.shortcut).put("frequency",word.frequency));
        out.write(new JSONObject().put("format","teclado-br-personal-dictionary").put("version",1).put("words",rows).toString(2).getBytes(StandardCharsets.UTF_8));
    }
    public static int importFrom(Context c,InputStream input)throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        byte[] buffer=new byte[4096];
        int count;
        while((count=input.read(buffer))!=-1) {
            if(out.size()+count>1048576)throw new IOException("Arquivo maior que 1 MB.");
            out.write(buffer,0,count);
        }
        JSONObject json=new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));
        if(!"teclado-br-personal-dictionary".equals(json.optString("format"))||json.optInt("version")!=1)throw new IOException("Formato de dicionário não reconhecido.");
        JSONArray rows=json.getJSONArray("words");
        if(rows.length()>5000)throw new IOException("Importe no máximo 5.000 palavras por arquivo.");
        List<Word> valid=new ArrayList<>();
        Set<String> seen=new HashSet<>();
        for(int i=0;i<rows.length();i++) {
            JSONObject row=rows.getJSONObject(i);
            String word=row.getString("word"),locale=row.isNull("locale")?null:row.getString("locale"),shortcut=row.isNull("shortcut")?null:row.getString("shortcut");
            if(word.trim().isEmpty()||word.codePointCount(0,word.length())>=48||word.codePoints().anyMatch(Character::isISOControl)||locale!=null&&(locale.length()>40||!locale.matches("[A-Za-z0-9_-]+"))||shortcut!=null&&(shortcut.length()>48||shortcut.codePoints().anyMatch(Character::isISOControl)))throw new IOException("O arquivo contém uma entrada inválida.");
            String identity=word+"\u0000"+locale+"\u0000"+shortcut;
            if(seen.add(identity))valid.add(new Word(0,word,locale,shortcut,Math.max(1,Math.min(255,row.optInt("frequency",250)))));
        }
        // Validate everything before changing the provider; import merges and never clears other entries.
        for(Word word:valid)save(c,word.text,word.frequency,word.shortcut,word.locale);
        return valid.size();
    }
}

