// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;
import android.content.Context;
import android.net.Uri;
import android.os.StatFs;
import android.provider.OpenableColumns;
import android.database.Cursor;
import java.io.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;
final class ModelStore {
    interface Progress {
        boolean update(long copied, long total);
    }
    static File directory(Context c) {
        File f=new File(c.getNoBackupFilesDir(),"offline-ai");
        f.mkdirs();
        return f;
    }
    // The legacy slot keeps its original filenames so previously downloaded
    // Gemma 4 E2B weights survive upgrades and selection of other models.
    static final String CURRENT="existing", QWEN="qwen35-2b", E4B="gemma4-e4b";
    static final String[] SLOTS={CURRENT,QWEN,E4B};
    private static String checked(String slot) {
        for(String allowed:SLOTS)if(allowed.equals(slot))return slot;
        throw new IllegalArgumentException("Tipo de modelo desconhecido.");
    }
    private static String filename(String slot,String ext) {
        return CURRENT.equals(checked(slot))?"model."+ext:"model-"+slot+"."+ext;
    }
    static File model(Context c,String slot) {
        return new File(directory(c),filename(slot,"gguf"));
    }
    static File metadata(Context c,String slot) {
        return new File(directory(c),filename(slot,"json"));
    }
    static String selected(Context c) {
        // The writing service runs in another Android process. SharedPreferences
        // cache can be stale there; use a tiny atomic file for the active slot.
        File active=new File(directory(c),"active-slot.txt");
        try {
            if(active.isFile()) {
                String value=new String(java.nio.file.Files.readAllBytes(active.toPath()),
                    StandardCharsets.US_ASCII).trim();
                for(String slot:SLOTS)if(slot.equals(value))return slot;
            }
        }catch(IOException ignored){}
        String pref=c.getSharedPreferences("writing-options",0)
            .getString("offline-slot",CURRENT);
        for(String slot:SLOTS)if(slot.equals(pref))return pref;
        return CURRENT;
    }
    private static boolean setActive(Context c,String slot) {
        checked(slot);
        android.util.AtomicFile active=new android.util.AtomicFile(
            new File(directory(c),"active-slot.txt"));
        FileOutputStream out=null;
        try {
            out=active.startWrite();
            out.write(slot.getBytes(StandardCharsets.US_ASCII));
            active.finishWrite(out);
            return c.getSharedPreferences("writing-options",0).edit()
                .putString("offline-slot",slot).commit();
        }catch(Exception error) {
            if(out!=null)active.failWrite(out);
            return false;
        }
    }
    static boolean select(Context c,String slot) {
        checked(slot);
        if(!model(c,slot).isFile())return false;
        return setActive(c,slot);
    }
    static File model(Context c) {return model(c,selected(c));}
    static File metadata(Context c) {return metadata(c,selected(c));}
    static String description(Context c) {return description(c,selected(c));}
    static String description(Context c,String slot) {
        File file=model(c,slot);
        if(!file.isFile())return "Não instalado: "+label(slot);
        try {
            JSONObject j=new JSONObject(new String(java.nio.file.Files.readAllBytes(
                metadata(c,slot).toPath()),StandardCharsets.UTF_8));
            return label(slot)+" · "+j.getString("name")+" · "+(file.length()/1048576)+" MB";
        }
        catch(Exception e) {
            return label(slot)+" · GGUF local · "+(file.length()/1048576)+" MB";
        }
    }
    static String label(String slot) {
        switch(checked(slot)) {
            case QWEN:return "Qwen 3.5 2B";
            case E4B:return "Gemma 4 E4B";
            default:return "Modelo atual (Gemma 4 E2B)";
        }
    }
    static void removeSlot(Context c,String slot) {
        checked(slot);
        model(c,slot).delete();
        metadata(c,slot).delete();
        if(slot.equals(selected(c))) {
            for(String other:SLOTS) {
                if(model(c,other).isFile()) {select(c,other);return;}
            }
            setActive(c,CURRENT);
        }
    }
    static void importFile(Context c,Uri uri,Progress progress)throws Exception {
        importFile(c,uri,progress,selected(c));
    }
    static void importFile(Context c,Uri uri,Progress progress,String slot)throws Exception {
        checked(slot);
        long size=-1;
        String name="modelo.gguf";
        try(Cursor cursor=c.getContentResolver().query(uri,null,null,null,null)) {
            if(cursor!=null&&cursor.moveToFirst()) {
                int n=cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME),s=cursor.getColumnIndex(OpenableColumns.SIZE);
                if(n>=0)name=cursor.getString(n);
                if(s>=0&&!cursor.isNull(s))size=cursor.getLong(s);
            }
        }
        final long maximum=6L*1024*1024*1024;
        if(size>maximum)throw new IOException("Arquivo maior que 6 GB. Escolha um GGUF quantizado menor.");
        if(size>0&&new StatFs(directory(c).getPath()).getAvailableBytes()<size+256L*1024*1024)throw new IOException("Espaço insuficiente para importar o modelo.");
        File next=new File(directory(c),"importando-"+slot+".gguf");
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            long copied=0,last=0;
            try(InputStream input=c.getContentResolver().openInputStream(uri);
            FileOutputStream output=new FileOutputStream(next)) {
                if(input==null)throw new IOException("Não foi possível abrir o arquivo.");
                byte[] header=new byte[4];
                int have=0;
                while(have<4) {
                    int n=input.read(header,have,4-have);
                    if(n<0)throw new IOException("Arquivo vazio ou incompleto.");
                    have+=n;
                }
                if(header[0]!='G'||header[1]!='G'||header[2]!='U'||header[3]!='F')throw new IOException("O arquivo precisa ser um modelo GGUF.");
                output.write(header);
                digest.update(header);
                copied=4;
                byte[] buffer=new byte[1024*1024];
                int count;
                while((count=input.read(buffer))!=-1) {
                    copied+=count;
                    if(copied>maximum)throw new IOException("Arquivo maior que 6 GB.");
                    output.write(buffer,0,count);
                    digest.update(buffer,0,count);
                    if(copied-last>=4*1024*1024) {
                        last=copied;
                        if(!progress.update(copied,size))throw new IOException("Importação cancelada.");
                    }
                }
                if(size>=0&&copied!=size)throw new IOException("Arquivo incompleto. Baixe o modelo novamente.");
                if(copied<1024)throw new IOException("Modelo inválido ou incompleto.");
                output.getFD().sync();
            }
            if(!progress.update(copied,size))throw new IOException("Importação cancelada.");
            StringBuilder hash=new StringBuilder();
            for(byte b:digest.digest())hash.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
            if(!next.renameTo(model(c,slot)))throw new IOException("Não foi possível guardar o modelo.");
            JSONObject metadata=new JSONObject().put("name",name).put("bytes",copied).put("sha256",hash.toString());
            try(FileOutputStream out=new FileOutputStream(metadata(c,slot))) {
                out.write(metadata.toString().getBytes(StandardCharsets.UTF_8));
            }
        }
        finally {
            next.delete();
        }
    }
}
