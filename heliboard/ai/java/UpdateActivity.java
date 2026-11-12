// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.widget.*;
import androidx.core.content.FileProvider;
import helium314.keyboard.latin.BuildConfig;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.*;

/** Opt-in, update-only screen. Downloads never run in the background or while typing.
 * Only a signer-matching, SHA-256 verified APK of this package may reach Package Installer. */
public final class UpdateActivity extends Activity {
    private static final int MAX_MANIFEST=8192,MAX_APK=180*1024*1024;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private TextView status;
    private Button check,download,release;
    private JSONObject offer;
    private File verified;
    private volatile boolean closed,busy;
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xff17191e);
        LinearLayout root=new LinearLayout(this);
        root.setPadding(dp(20),dp(24),dp(20),dp(28));
        root.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(root);
        setContentView(scroll);
        label(root,"Atualizações · Teclado BR",24);
        label(root,"Versão instalada: "+BuildConfig.VERSION_NAME+" ("+version()+")",16);
        label(root,"Verificação manual, sem monitorar sua digitação. Apenas APKs assinados pelo mesmo certificado, com SHA-256 correspondente, podem ser instalados.",14);
        status=label(root,"Toque em Verificar atualização.",16);
        check=button(root,"Verificar atualização",()->checkNow());
        check.setContentDescription("ai_check_update");
        download=button(root,"Baixar e instalar atualização",()->download());
        download.setContentDescription("ai_download_update");
        release=button(root,"Ver detalhes da versão no GitHub",()->openRelease());
        release.setContentDescription("ai_update_release");
        button(root,"Voltar às configurações",this::finish);
        updateButtons();
        checkNow();
    }
    private TextView label(LinearLayout root,String text,int size){
        TextView v=new TextView(this);
        v.setText(text);v.setTextColor(Color.WHITE);v.setTextSize(size);
        v.setPadding(0,dp(12),0,dp(10));
        root.addView(v);
        return v;
    }
    private Button button(LinearLayout root,String title,Runnable action){
        Button v=new Button(this);v.setAllCaps(false);v.setText(title);
        v.setMinHeight(dp(48));v.setOnClickListener(x->action.run());root.addView(v);
        return v;
    }
    private void updateButtons(){
        check.setEnabled(!busy);
        download.setEnabled(!busy&&offer!=null);
        release.setEnabled(!busy&&offer!=null);
    }
    private void state(String message){if(!closed)runOnUiThread(()->{if(!closed)status.setText(message);});}
    private void start(String message){
        busy=true;updateButtons();state(message);
    }
    private void done(){if(!closed)runOnUiThread(()->{busy=false;updateButtons();});}
    private long version(){
        try{
            PackageInfo p=getPackageManager().getPackageInfo(getPackageName(),0);
            return Build.VERSION.SDK_INT>=28?p.getLongVersionCode():p.versionCode;
        }catch(Exception error){return 0;}
    }
    private static String fingerprint(byte[] certificate)throws Exception{
        byte[] hash=MessageDigest.getInstance("SHA-256").digest(certificate);
        StringBuilder out=new StringBuilder(hash.length*2);
        for(byte b:hash)out.append(String.format(Locale.ROOT,"%02x",b&255));
        return out.toString();
    }
    private String signer(PackageInfo p)throws Exception{
        Signature[] signatures=Build.VERSION.SDK_INT>=28?
            (p.signingInfo==null?null:p.signingInfo.getApkContentsSigners()):p.signatures;
        if(signatures==null||signatures.length!=1)throw new SecurityException("Assinatura do pacote não confirmada.");
        return fingerprint(signatures[0].toByteArray());
    }
    private int signatureFlag(){return Build.VERSION.SDK_INT>=28?
        PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;}
    private String installedSigner()throws Exception{
        return signer(getPackageManager().getPackageInfo(getPackageName(),signatureFlag()));
    }
    private static HttpURLConnection connection(String raw)throws Exception{
        HttpURLConnection http=(HttpURLConnection)new URL(raw).openConnection();
        http.setRequestMethod("GET");http.setInstanceFollowRedirects(false);
        http.setUseCaches(false);http.setRequestProperty("Cache-Control","no-cache");
        http.setConnectTimeout(8000);http.setReadTimeout(18000);
        http.setRequestProperty("Accept","application/json, application/octet-stream");
        http.setRequestProperty("User-Agent","TecladoBR-UpdateChecker/1");
        return http;
    }
    private static byte[] readBounded(InputStream input,int max)throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        byte[] buffer=new byte[4096];int n;
        while((n=input.read(buffer))!=-1){
            if(out.size()+n>max)throw new IOException("Arquivo maior que o limite permitido.");
            out.write(buffer,0,n);
        }
        return out.toByteArray();
    }
    private JSONObject loadManifest()throws Exception{
        HttpURLConnection http=connection(UpdatePolicy.MANIFEST+"?check="+System.currentTimeMillis());
        try{
            if(http.getResponseCode()!=200||http.getContentLengthLong()>MAX_MANIFEST)
                throw new IOException("O servidor de atualizações não respondeu.");
            byte[] data;
            try(InputStream in=http.getInputStream()){data=readBounded(in,MAX_MANIFEST);}
            JSONObject result=new JSONObject(new String(data,java.nio.charset.StandardCharsets.UTF_8));
            if(!"teclado-br-updates-v1".equals(result.optString("schema"))||
                !result.optBoolean("enabled")||
                !getPackageName().equals(result.optString("packageName"))||
                !UpdatePolicy.hexSha(result.optString("sha256"))||
                !UpdatePolicy.hexSha(result.optString("signerSha256"))||
                !UpdatePolicy.apkUrl(result.optString("apkUrl"))||
                !UpdatePolicy.releaseUrl(result.optString("releaseUrl"))||
                result.optLong("sizeBytes")<1_000_000||
                result.optLong("sizeBytes")>MAX_APK)
                throw new SecurityException("Metadados de atualização inválidos.");
            if(!installedSigner().equalsIgnoreCase(result.getString("signerSha256")))
                throw new SecurityException("O certificado publicado não corresponde ao instalado.");
            return result;
        }finally{http.disconnect();}
    }
    private void checkNow(){
        if(busy)return;
        start("Consultando a versão disponível…");
        worker.execute(()->{
            try{
                JSONObject candidate=loadManifest();
                if(closed)return;
                if(UpdatePolicy.newer(version(),candidate.getLong("versionCode"))){
                    offer=candidate;verified=null;
                    state("Atualização disponível: "+candidate.optString("versionName")+
                        "\nÉ possível baixar e instalar sem desinstalar o teclado.");
                }else{
                    offer=null;verified=null;
                    state("Seu teclado já está atualizado. Nenhuma versão mais recente foi publicada.");
                }
            }catch(Exception error){
                offer=null;verified=null;
                state("Não foi possível verificar com segurança. Confira a conexão e tente novamente.");
            }finally{done();}
        });
    }
    private File directory()throws IOException{
        File dir=new File(getCacheDir(),"teclado-updates");
        if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Não foi possível criar o cache.");
        return dir;
    }
    private static String sha256(File file)throws Exception{
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new FileInputStream(file)){
            byte[] buf=new byte[8192];int n;
            while((n=in.read(buf))!=-1)digest.update(buf,0,n);
        }
        StringBuilder out=new StringBuilder(64);
        for(byte b:digest.digest())out.append(String.format(Locale.ROOT,"%02x",b&255));
        return out.toString();
    }
    private File fetchApk(JSONObject m)throws Exception{
        File target=new File(directory(),"teclado-br-update.apk");
        File part=new File(directory(),"download.pending.apk");
        if(part.exists())part.delete();
        if(target.isFile()&&sha256(target).equalsIgnoreCase(m.getString("sha256"))){
            verifyPackage(target,m);return target;
        }
        target.delete();
        String current=m.getString("apkUrl");
        int redirects=0;
        HttpURLConnection http=null;
        try{
            while(true){
                http=connection(current);
                int code=http.getResponseCode();
                if((code==301||code==302||code==303||code==307||code==308)&&redirects++<3){
                    String location=http.getHeaderField("Location");
                    http.disconnect();http=null;
                    if(!UpdatePolicy.redirect(location))throw new SecurityException("Redirecionamento de atualização não autorizado.");
                    current=location;
                    continue;
                }
                if(code!=200)throw new IOException("Servidor de APK indisponível (HTTP "+code+").");
                break;
            }
            long expected=m.getLong("sizeBytes");
            long responseSize=http.getContentLengthLong();
            if(responseSize>MAX_APK||(responseSize>0&&responseSize!=expected))
                throw new IOException("O tamanho informado pelo servidor não corresponde ao esperado.");
            long count=0;
            try(InputStream in=http.getInputStream();OutputStream out=new FileOutputStream(part)){
                byte[] buf=new byte[16384];int n;
                while((n=in.read(buf))!=-1){
                    if(closed)throw new IOException("Atualização cancelada.");
                    count+=n;
                    if(count>MAX_APK||count>expected)
                        throw new IOException("O APK excede o tamanho permitido.");
                    out.write(buf,0,n);
                }
            }
            if(count!=expected||!sha256(part).equalsIgnoreCase(m.getString("sha256")))
                throw new SecurityException("O SHA-256 do APK não corresponde à versão anunciada.");
            verifyPackage(part,m);
            if(!part.renameTo(target))throw new IOException("Não foi possível finalizar o download.");
            return target;
        }finally{
            if(http!=null)http.disconnect();
            if(part.exists())part.delete();
        }
    }
    private void verifyPackage(File file,JSONObject m)throws Exception{
        PackageInfo p=getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(),signatureFlag());
        if(p==null||!getPackageName().equals(p.packageName)||
            !UpdatePolicy.newer(version(),Build.VERSION.SDK_INT>=28?p.getLongVersionCode():p.versionCode)||
            (Build.VERSION.SDK_INT>=28?p.getLongVersionCode():p.versionCode)!=m.getLong("versionCode")||
            !installedSigner().equalsIgnoreCase(signer(p)))
            throw new SecurityException("Este APK não é uma atualização assinada do Teclado BR.");
    }
    private void download(){
        if(busy||offer==null)return;
        final JSONObject candidate=offer;
        if(verified!=null&&verified.isFile()){
            install(verified);return;
        }
        start("Baixando a versão assinada e conferindo SHA-256…");
        worker.execute(()->{
            try{
                File file=fetchApk(candidate);
                verified=file;
                state("APK verificado. O Android solicitará sua autorização para instalar.");
                runOnUiThread(()->{if(!closed)install(file);});
            }catch(Exception error){
                state("Não foi possível baixar ou validar o APK. Confira a conexão e tente novamente. A versão também está disponível no GitHub público.");
            }finally{done();}
        });
    }
    private void install(File file){
        if(closed||offer==null)return;
        if(Build.VERSION.SDK_INT>=26&&!getPackageManager().canRequestPackageInstalls()){
            state("Permita instalar aplicativos desta fonte e volte para tocar novamente em Baixar e instalar.");
            Intent settings=new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:"+getPackageName()));
            try{startActivity(settings);}catch(Exception ignored){state("Abra as permissões de instalação nas configurações do Android.");}
            return;
        }
        try{
            Uri content=FileProvider.getUriForFile(this,getPackageName()+".teclado_updates",file);
            Intent intent=new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(content,"application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setClipData(ClipData.newUri(getContentResolver(),"Teclado BR",content));
            startActivity(intent);
        }catch(Exception error){state("O Android não conseguiu abrir o instalador. Use a versão no GitHub.");}
    }
    private void openRelease(){
        if(offer==null)return;
        try{
            String raw=offer.getString("releaseUrl");
            if(!UpdatePolicy.releaseUrl(raw))return;
            startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(raw)));
        }catch(Exception error){state("Não foi possível abrir o GitHub neste aparelho.");}
    }
    @Override protected void onDestroy(){
        closed=true;worker.shutdownNow();super.onDestroy();
    }
}
