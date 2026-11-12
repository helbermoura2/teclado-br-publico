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
    static File model(Context c) {
        return new File(directory(c),"model.gguf");
    }
    static File metadata(Context c) {
        return new File(directory(c),"model.json");
    }
    static String description(Context c) {
        if(!model(c).isFile()) return "Nenhum modelo instalado. Baixe um GGUF e importe abaixo.";
        try {
            JSONObject j=new JSONObject(new String(java.nio.file.Files.readAllBytes(metadata(c).toPath()),StandardCharsets.UTF_8));
            return j.getString("name")+" · "+(model(c).length()/1048576)+" MB";
        }
        catch(Exception e) {
            return "Modelo GGUF local · "+(model(c).length()/1048576)+" MB";
        }
    }
    static void importFile(Context c,Uri uri, Progress progress)throws Exception {
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
        File next=new File(directory(c),"importando.gguf");
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
            if(!next.renameTo(model(c)))throw new IOException("Não foi possível guardar o modelo.");
            JSONObject metadata=new JSONObject().put("name",name).put("bytes",copied).put("sha256",hash.toString());
            try(FileOutputStream out=new FileOutputStream(metadata(c))) {
                out.write(metadata.toString().getBytes(StandardCharsets.UTF_8));
            }
        }
        finally {
            next.delete();
        }
    }
}

