// SPDX-License-Identifier: GPL-3.0-only
package br.com.tecladobr.heli.testing;
import android.content.*;
import android.os.*;
import android.text.Spanned;
import android.text.style.StrikethroughSpan;
import android.view.accessibility.AccessibilityNodeInfo;
import helium314.keyboard.latin.ai.*;
import helium314.keyboard.latin.Typography;
import helium314.keyboard.latin.utils.DeviceProtectedUtils;
import helium314.keyboard.latin.settings.Settings;
import org.json.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
/** Debug-only transport contract tests use loopback and synthetic text, never a real provider key. */
public final class HybridTrial {
    private static final String FAKE_KEY="gsk_test_fixture_not_a_real_provider_key_123456";
    private static void check(boolean pass,String message) {
        if(!pass)throw new AssertionError(message);
    }
    /** The real editing row is opened by holding the clipboard FAVORITE. */
    private static void enterCompactEdit(HeliTrialInstrumentation test)throws Exception {
        test.runOnMainSync(()-> {
            android.view.View root=helium314.keyboard.keyboard.KeyboardSwitcher.getInstance().getStripContainer();
            helium314.keyboard.latin.suggestions.SuggestionStripView strip=
                (helium314.keyboard.latin.suggestions.SuggestionStripView)
                    test.findView(root,v->v instanceof helium314.keyboard.latin.suggestions.SuggestionStripView);
            check(strip!=null,"Missing compact strip before clipboard long press");
            strip.setToolbarVisibility(false);
            android.view.View back=test.findView(root,
                v->"Voltar aos atalhos principais".equals(v.getContentDescription())&&v.isShown());
            if(back!=null)back.performClick();
            android.view.View clip=test.findView(root,
                v->v.getTag()==helium314.keyboard.latin.utils.ToolbarKey.CLIPBOARD&&v.isShown());
            check(clip!=null,"Clipboard favorite not available before typing");
            strip.onLongClick(clip);
        });
    }
    /** Invokes the existing + listener in the scrollable clipboard editor. */
    private static void addWordFromToolbar(HeliTrialInstrumentation test,boolean compact)throws Exception {
        if(!compact) {
            test.aiClick("Adicionar palavra ao dicionário");
            return;
        }
        test.runOnMainSync(()-> {
            android.view.View root=helium314.keyboard.keyboard.KeyboardSwitcher.getInstance().getStripContainer();
            android.view.View dictionary=test.findView(root,
                v->v.getId()==helium314.keyboard.latin.R.id.teclado_add_word);
            check(dictionary!=null && dictionary.isShown() && dictionary.hasOnClickListeners(),
                "Add word action not accessible in clipboard editor");
            dictionary.requestRectangleOnScreen(new android.graphics.Rect(
                0,0,dictionary.getWidth(),dictionary.getHeight()),true);
            check(dictionary.performClick(),"Add word action failed");
        });
    }
    private static final class Server implements AutoCloseable {
        final ServerSocket listener;
        final ExecutorService worker=Executors.newSingleThreadExecutor();
        final CountDownLatch received=new CountDownLatch(1);
        final AtomicReference<JSONObject> request=new AtomicReference<>();
        final AtomicReference<String> authorization=new AtomicReference<>("");
        final AtomicReference<String> geminiAuthorization=new AtomicReference<>("");
        final AtomicInteger count=new AtomicInteger();
        Server(int status,String body,int delay)throws Exception {
            listener=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"));
            worker.execute(()-> {
                try(Socket socket=listener.accept()) {
                    socket.setSoTimeout(3000);
                    InputStream in=socket.getInputStream();
                    ByteArrayOutputStream header=new ByteArrayOutputStream();
                    int value;
                    while((value=in.read())!=-1) {
                        header.write(value);
                        String h=header.toString("US-ASCII");
                        if(h.endsWith("\r\n\r\n"))break;
                        if(header.size()>16384)throw new IOException();
                    }
                    String head=header.toString("US-ASCII");
                    int length=0;
                    for(String line:head.split("\r\n")) {
                        if(line.toLowerCase(Locale.ROOT).startsWith("content-length:"))length=Integer.parseInt(line.substring(15).trim());
                        if(line.toLowerCase(Locale.ROOT).startsWith("authorization:"))authorization.set(line.substring(14).trim());
                        if(line.toLowerCase(Locale.ROOT).startsWith("x-goog-api-key:"))geminiAuthorization.set(line.substring(15).trim());
                    }
                    check(length<32768,"Unexpected request size");
                    byte[] payload=new byte[length];
                    new DataInputStream(in).readFully(payload);
                    request.set(new JSONObject(new String(payload,StandardCharsets.UTF_8)));
                    count.incrementAndGet();
                    received.countDown();
                    if(delay>0)Thread.sleep(delay);
                    byte[] response=body.getBytes(StandardCharsets.UTF_8);
                    String headers="HTTP/1.1 "+status+" Result\r\nContent-Type: application/json\r\nContent-Length: "+response.length+"\r\nConnection: close\r\n"+(status==302?"Location: https://not-groq.invalid/\r\n":"")+"\r\n";
                    socket.getOutputStream().write(headers.getBytes(StandardCharsets.US_ASCII));
                    socket.getOutputStream().write(response);
                }
                catch(Exception ignored) {
                    received.countDown();
                }
            });
        }
        String endpoint() {
            return "http://127.0.0.1:"+listener.getLocalPort()+"/openai/v1/chat/completions";
        }
        String geminiEndpoint() {
            return "http://127.0.0.1:"+listener.getLocalPort()+"/v1beta/models/gemini-3.8-flash:generateContent";
        }
        public void close()throws Exception {
            listener.close();
            worker.shutdownNow();
        }
    }
    private static InferenceResult route(String mode,boolean net,boolean key,InferenceRouter.Backend online,AtomicInteger fallbacks,AtomicBoolean cancelled)throws Exception {
        return InferenceRouter.run(mode,net,key,true,"Revise apenas: Ola tudo bem?",32,online,(p,n,partial)-> {
            fallbacks.incrementAndGet();
            return new InferenceResult("Resultado local.","Offline · fixture",false);
        },cancelled::get,s-> {
        },s-> {
        });
    }
    private static void serviceModes(Context c)throws Exception {
        CountDownLatch bound=new CountDownLatch(1);
        AtomicReference<Messenger> service=new AtomicReference<>();
        ServiceConnection connection=new ServiceConnection() {
            public void onServiceConnected(ComponentName name,IBinder binder) { service.set(new Messenger(binder)); bound.countDown(); }
            public void onServiceDisconnected(ComponentName name) { service.set(null); }
        };
        check(c.bindService(new Intent(c,WritingEngineService.class),connection,Context.BIND_AUTO_CREATE),"Cannot bind hybrid engine");
        try {
            check(bound.await(10,TimeUnit.SECONDS),"Engine binding timed out");
            for(String mode:new String[]{AiPreferences.ONLINE,AiPreferences.OFFLINE}) {
                CountDownLatch done=new CountDownLatch(1);
                AtomicReference<String> response=new AtomicReference<>("");
                ResultReceiver receiver=new ResultReceiver(new Handler(Looper.getMainLooper())) {
                    protected void onReceiveResult(int code,Bundle data) {
                        if(code==2||code==3) { response.set(data.getString("text","")); done.countDown(); }
                    }
                };
                Parcel parcel=Parcel.obtain();
                try {
                    receiver.writeToParcel(parcel,0); parcel.setDataPosition(0);
                    Bundle data=new Bundle();
                    data.putParcelable("receiver",ResultReceiver.CREATOR.createFromParcel(parcel));
                    data.putString("mode",mode); data.putString("prompt","Texto sintético.");
                    Message message=Message.obtain(null,1); message.setData(data); service.get().send(message);
                } finally { parcel.recycle(); }
                check(done.await(10,TimeUnit.SECONDS),"Engine mode request timed out");
                check(response.get().contains(AiPreferences.ONLINE.equals(mode)?"Cadastre a chave":"Importe um modelo"),"Engine did not honor per-request mode: "+response.get());
                Thread.sleep(200);
            }
        } finally { c.unbindService(connection); }
    }
    private static void instagram(HeliTrialInstrumentation test,Bundle result)throws Exception {
        final int text=android.text.InputType.TYPE_CLASS_TEXT;
        final int blocked=android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
        int[] prose={0,android.text.InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE,android.text.InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE,android.text.InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT};
        for(int variation:prose) {
            android.view.inputmethod.EditorInfo info=new android.view.inputmethod.EditorInfo();
            info.packageName="com.instagram.android"; info.inputType=text|variation|blocked;
            info.imeOptions=android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING;
            helium314.keyboard.latin.InputAttributes attrs=new helium314.keyboard.latin.InputAttributes(info,false,"br.com.tecladobr.heli.debug");
            check(attrs.mShouldShowSuggestions&&!attrs.mIsPasswordField&&attrs.mNoLearning,"Instagram prose compatibility or privacy failed");
            check(info.inputType==(text|variation|blocked),"App EditorInfo was mutated");
        }
        int[] excluded={text|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD,text|android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,text|android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,text|android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,text|android.text.InputType.TYPE_TEXT_VARIATION_URI,text|android.text.InputType.TYPE_TEXT_VARIATION_FILTER,android.text.InputType.TYPE_CLASS_NUMBER,android.text.InputType.TYPE_CLASS_PHONE,0};
        for(int type:excluded)check(helium314.keyboard.compat.AppWorkarounds.INSTANCE.adjustInputType(type|blocked,"com.instagram.android")== (type|blocked),"Sensitive/special field changed");
        check(helium314.keyboard.compat.AppWorkarounds.INSTANCE.adjustInputType(text|blocked,"other.app")== (text|blocked),"Another app was changed");
        // Real editor/InputConnection with the normalized type, not an Instagram account.
        int original=test.trialInputType;
        try {
            test.trialInputType=helium314.keyboard.compat.AppWorkarounds.INSTANCE.adjustInputType(text|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|blocked,"com.instagram.android");
            test.launch(); test.clear();
            test.runOnMainSync(()-> {
                android.view.View root=helium314.keyboard.keyboard.KeyboardSwitcher.getInstance().getStripContainer();
                helium314.keyboard.latin.suggestions.SuggestionStripView strip=(helium314.keyboard.latin.suggestions.SuggestionStripView)test.findView(root,v->v instanceof helium314.keyboard.latin.suggestions.SuggestionStripView);
                strip.setToolbarVisibility(true);
            });
            test.type("voce");
            long until=android.os.SystemClock.uptimeMillis()+20000;
            final android.graphics.Rect candidate=new android.graphics.Rect();
            do {
                test.runOnMainSync(()-> {
                    android.view.View root=helium314.keyboard.keyboard.KeyboardSwitcher.getInstance().getStripContainer();
                    android.view.View word=test.findView(root,v->v instanceof android.widget.TextView&&v.isShown()&&"você".equalsIgnoreCase(((android.widget.TextView)v).getText().toString()));
                    if(word!=null){int[] xy=new int[2];word.getLocationOnScreen(xy);candidate.set(xy[0],xy[1],xy[0]+word.getWidth(),xy[1]+word.getHeight());}
                });
                if(!candidate.isEmpty())break;
                Thread.sleep(200);
            }while(android.os.SystemClock.uptimeMillis()<until);
            check(!candidate.isEmpty(),"Visible suggestions did not replace toolbar: "+test.suggestions());
            test.shot("27-sugestoes-campo-compativel");test.tap(candidate);
            until=android.os.SystemClock.uptimeMillis()+10000;
            while(!test.text().trim().equalsIgnoreCase("você")&&android.os.SystemClock.uptimeMillis()<until)Thread.sleep(150);
            check(test.text().trim().equalsIgnoreCase("você"),"Candidate tap did not commit the word");
            result.putString("instagram_compatibility","PASS: prose flags, excluded fields, no-learning retained, visible candidate and tap in synthetic editor; actual Instagram not accessed");
        } finally {test.trialInputType=original;}
    }
    public static void run(HeliTrialInstrumentation test,Bundle result)throws Exception {
        Context c=test.getTargetContext();
        AtomicBoolean cancelled=new AtomicBoolean();
        AtomicInteger calls=new AtomicInteger(),fallbacks=new AtomicInteger();
        InferenceRouter.Backend spy=(p,n,partial)-> {
            calls.incrementAndGet();
            return new InferenceResult("Online.","Groq",false);
        };
        route(AiPreferences.OFFLINE,true,true,spy,fallbacks,cancelled);
        route(AiPreferences.AUTO,false,true,spy,fallbacks,cancelled);
        route(AiPreferences.AUTO,true,false,spy,fallbacks,cancelled);
        check(calls.get()==0&&fallbacks.get()==3,"Offline/unconfigured mode attempted the network");
        boolean rejected=false;
        try {
            route(AiPreferences.ONLINE,false,true,spy,fallbacks,cancelled);
        }
        catch(IllegalStateException e) {
            rejected=true;
        }
        check(rejected&&fallbacks.get()==3,"Online-only silently used offline");
        String success="{\"choices\":[{\"message\":{\"content\":\"Olá, tudo bem?\",\"reasoning\":\"not displayed\"},\"finish_reason\":\"stop\"}]}";
        try(Server server=new Server(200,success,0)) {
            GroqClient client=new GroqClient(server.endpoint(),FAKE_KEY,cancelled::get,2000);
            InferenceResult answer=route(AiPreferences.AUTO,true,true,client,fallbacks,cancelled);
            check(answer.text.equals("Olá, tudo bem?"),"Transport returned wrong final text");
            check(server.count.get()==1,"Unexpected retries");
            check(server.authorization.get().equals("Bearer "+FAKE_KEY),"Authorization contract mismatch");
            JSONObject request=server.request.get();
            check(GroqClient.MODEL.equals(request.getString("model")),"Wrong online model");
            check(!request.getBoolean("include_reasoning")&&!request.getBoolean("stream"),"Reasoning/streaming contract mismatch");
            check(request.getJSONArray("messages").getJSONObject(1).getString("content").equals("Revise apenas: Ola tudo bem?"),"Task text changed in transit");
        }
        String geminiSuccess="{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Olá, tudo bem?\"}]},\"finishReason\":\"STOP\"}]}";
        try(Server server=new Server(200,geminiSuccess,0)) {
            GeminiClient client=new GeminiClient(server.geminiEndpoint(),FAKE_KEY,cancelled::get,2000);
            InferenceResult answer=route(AiPreferences.AUTO,true,true,client,fallbacks,cancelled);
            check(answer.text.equals("Olá, tudo bem?")&&answer.backend.contains("Gemini"),"Gemini parsed response incorrectly");
            check(server.count.get()==1,"Gemini unexpected extra requests");
            check(server.geminiAuthorization.get().equals(FAKE_KEY),"Gemini API key header not sent");
            JSONObject request=server.request.get();
            check(request.getJSONArray("contents").getJSONObject(0).getJSONArray("parts")
                .getJSONObject(0).getString("text").equals("Revise apenas: Ola tudo bem?"),
                "Gemini request changed the selected task");
            check("low".equals(request.getJSONObject("generationConfig")
                .getJSONObject("thinkingConfig").getString("thinkingLevel")),"Gemini thinking effort differs");
        }
        for(int code:new int[]{429,401,403,500,302}) {
            int previous=fallbacks.get();
            try(Server server=new Server(code,"{}",0)) {
                InferenceResult answer=route(AiPreferences.AUTO,true,true,
                    new GeminiClient(server.geminiEndpoint(),FAKE_KEY,cancelled::get,2000),
                    fallbacks,cancelled);
                check(answer.backend.startsWith("Offline")&&fallbacks.get()==previous+1,
                    "Gemini AUTO fallback failed for HTTP "+code);
            }
        }
        try(Server server=new Server(200,"not-json",0)) {
            int previous=fallbacks.get();
            route(AiPreferences.AUTO,true,true,new GeminiClient(server.geminiEndpoint(),FAKE_KEY,cancelled::get,2000),fallbacks,cancelled);
            check(fallbacks.get()==previous+1,"Malformed Gemini response did not use offline fallback");
        }
        boolean geminiEndpointRejected=false;
        try{new GeminiClient("http://127.0.0.1:80@not-gemini.invalid/",FAKE_KEY,cancelled::get,100);}
        catch(IllegalArgumentException e){geminiEndpointRejected=true;}
        check(geminiEndpointRejected,"Gemini credential redirect endpoint allowed");
        check(!android.security.NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted("generativelanguage.googleapis.com"),
            "Gemini production transport allows cleartext");
        check(!SecretStore.hasGeminiKey(c),"This test requires no installed Gemini API key");
        try {
            SecretStore.saveGemini(c,FAKE_KEY);
            check(SecretStore.hasGeminiKey(c),"Gemini key not saved");
            byte[] encrypted=java.nio.file.Files.readAllBytes(new File(c.getNoBackupFilesDir(),"gemini-key.enc").toPath());
            check(!new String(encrypted,StandardCharsets.ISO_8859_1).contains(FAKE_KEY),"Gemini key stored in plaintext");
            java.lang.reflect.Method method=SecretStore.class.getDeclaredMethod("readGemini",Context.class);
            method.setAccessible(true);
            check(FAKE_KEY.equals(method.invoke(null,c)),"Gemini Keystore round trip failed");
        }finally{SecretStore.removeGemini(c);}
        for(int code:new int[] {
            429,401,403,500,302
        }) {
            int previous=fallbacks.get();
            try(Server server=new Server(code,"{}",0)) {
                InferenceResult answer=route(AiPreferences.AUTO,true,true,new GroqClient(server.endpoint(),FAKE_KEY,cancelled::get,2000),fallbacks,cancelled);
                check(answer.backend.startsWith("Offline")&&fallbacks.get()==previous+1,"Auto fallback failed for HTTP "+code);
            }
        }
        try(Server server=new Server(429,"{}",0)) {
            int previous=fallbacks.get();
            rejected=false;
            try {
                route(AiPreferences.ONLINE,true,true,new GroqClient(server.endpoint(),FAKE_KEY,cancelled::get,2000),fallbacks,cancelled);
            }
            catch(GroqClient.Failure e) {
                rejected=e.status==429;
            }
            check(rejected&&fallbacks.get()==previous,"Online-only quota failure was masked");
        }
        try(Server server=new Server(200,"not-json",0)) {
            int previous=fallbacks.get();
            route(AiPreferences.AUTO,true,true,new GroqClient(server.endpoint(),FAKE_KEY,cancelled::get,2000),fallbacks,cancelled);
            check(fallbacks.get()==previous+1,"Malformed response did not fall back");
        }
        try(Server server=new Server(200,success,600)) {
            int previous=fallbacks.get();
            route(AiPreferences.AUTO,true,true,new GroqClient(server.endpoint(),FAKE_KEY,cancelled::get,100),fallbacks,cancelled);
            check(fallbacks.get()==previous+1,"Timeout did not fall back");
        }
        try(Server server=new Server(200,success,2000)) {
            ExecutorService work=Executors.newSingleThreadExecutor();
            GroqClient client=new GroqClient(server.endpoint(),FAKE_KEY,cancelled::get,3000);
            int previous=fallbacks.get();
            Future<Boolean> stopped=work.submit(()-> {
                try {
                    route(AiPreferences.AUTO,true,true,client,fallbacks,cancelled);
                    return false;
                }
                catch(CancellationException e) {
                    return true;
                }
            });
            check(server.received.await(3,TimeUnit.SECONDS),"Mock request did not start");
            cancelled.set(true);
            client.cancel();
            check(stopped.get(3,TimeUnit.SECONDS),"HTTP cancellation failed");
            check(fallbacks.get()==previous,"Cancellation started offline generation");
            work.shutdownNow();
            cancelled.set(false);
        }
        rejected=false;
        try {
            new GroqClient("http://127.0.0.1:80@not-groq.invalid/",FAKE_KEY,cancelled::get,100);
        }
        catch(IllegalArgumentException e) {
            rejected=true;
        }
        check(rejected,"Credential redirect endpoint allowed");
        check(!android.security.NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted("api.groq.com"),"Production transport allows cleartext");
        check(!SecretStore.hasKey(c),"This test requires a fresh installation without a real API key");
        try {
            SecretStore.save(c,FAKE_KEY);
            check(SecretStore.hasKey(c),"Encrypted key not stored");
            byte[] encrypted=java.nio.file.Files.readAllBytes(new File(c.getNoBackupFilesDir(),"groq-key.enc").toPath());
            check(!new String(encrypted,StandardCharsets.ISO_8859_1).contains(FAKE_KEY),"Key stored as plaintext");
            java.lang.reflect.Method read=SecretStore.class.getDeclaredMethod("read",Context.class);
            read.setAccessible(true);
            check(FAKE_KEY.equals(read.invoke(null,c)),"Keystore round trip failed");
            for(Object value:c.getSharedPreferences("writing-options",0).getAll().values())check(!String.valueOf(value).contains(FAKE_KEY),"Key leaked to preferences");
        }
        finally {
            SecretStore.remove(c);
            AiPreferences.mode(c,AiPreferences.OFFLINE);
        }
        serviceModes(c);
        TextChanges.Pair diff=TextChanges.compare("Eu nao sei.","Eu não sei!");
        check(diff.before.toString().equals("Eu nao sei.")&&diff.after.toString().equals("Eu não sei!"),"Diff changed the original or result");
        check(((Spanned)diff.before).getSpans(0,diff.before.length(),StrikethroughSpan.class).length>0,"Removed text not marked");
        String saved="backupvocabteste";
        DictionaryRepository.save(c,saved,250,null,"pt_BR");
        ByteArrayOutputStream backup=new ByteArrayOutputStream();
        DictionaryRepository.exportTo(c,backup);
        check(!backup.toString("UTF-8").contains(FAKE_KEY),"Dictionary export leaked a key");
        for(DictionaryRepository.Word word:DictionaryRepository.list(c))if(word.text.equals(saved))DictionaryRepository.delete(c,word.id);
        DictionaryRepository.importFrom(c,new ByteArrayInputStream(backup.toByteArray()));
        check(DictionaryRepository.list(c).stream().anyMatch(w->w.text.equals(saved)),"Dictionary backup round trip failed");
        int before=DictionaryRepository.list(c).size();
        rejected=false;
        try {
            DictionaryRepository.importFrom(c,new ByteArrayInputStream("{\"format\":\"wrong\",\"version\":1}".getBytes(StandardCharsets.UTF_8)));
        }
        catch(Exception e) {
            rejected=true;
        }
        check(rejected&&DictionaryRepository.list(c).size()==before,"Invalid backup changed the dictionary");
        for(DictionaryRepository.Word word:DictionaryRepository.list(c))if(word.text.equals(saved))DictionaryRepository.delete(c,word.id);
        // Exercise the actual new settings screens using the same touch helpers as the keyboard tests.
        test.clear();
        test.type("teste");
        test.aiClick("Adicionar palavra ao dicionário");
        test.aiClick("keyboard_appearance");
        test.aiNode("appearance_height");
        test.aiNode("appearance_letters");
        test.aiNode("appearance_symbols");
        test.aiNode("appearance_oneui_theme");
        test.aiNode("appearance_compact_toolbar");
        test.shot("24-ajustes-de-aparencia");
        android.content.SharedPreferences themes=DeviceProtectedUtils.getSharedPreferences(c);
        boolean themePreviouslyActive=themes.getBoolean("teclado_br_one_ui_active",false);
        String previousTheme=themes.getString(Settings.PREF_THEME_COLORS,"darker");
        String previousToolbar=themes.getString(Settings.PREF_TOOLBAR_KEYS, "");
        String previousPinned=themes.getString(Settings.PREF_PINNED_TOOLBAR_KEYS, "");
        boolean previousAutoShow=themes.getBoolean(Settings.PREF_AUTO_SHOW_TOOLBAR,false);
        boolean toolbarPreviouslyCompact=themes.getBoolean("teclado_br_compact_toolbar_active",false);
        test.aiEnsureChecked("appearance_oneui_theme",true);
        test.aiEnsureChecked("appearance_compact_toolbar",true);
        test.aiEnsureChecked("appearance_oneui_theme",true);
        test.shot("24b-preview-visual-oneui");
        test.aiPress("appearance_save");
        check(themes.getBoolean("teclado_br_one_ui_active",false),
            "One UI theme was not saved; theme="+themes.getString(Settings.PREF_THEME_COLORS,"")+
            ", compact="+themes.getBoolean("teclado_br_compact_toolbar_active",false)+
            ", prevActive="+themePreviouslyActive+
            ", prevCompact="+toolbarPreviouslyCompact);
        check("Teclado BR · Estilo One UI".equals(themes.getString(Settings.PREF_THEME_COLORS,"")),"One UI palette not selected");
        check(themes.getBoolean("teclado_br_compact_toolbar_active",false),"One UI compact toolbar was not saved");
        check(themes.getString(Settings.PREF_TOOLBAR_KEYS,"").contains("AI:true"),"AI shortcut hidden in compact toolbar");
        check(themes.getString(Settings.PREF_TOOLBAR_KEYS,"").contains("SELECT_ALL:true"),"Select-all missing from overflow");
        check(themes.getString(Settings.PREF_TOOLBAR_KEYS,"").contains("COPY:true"),"Copy missing from overflow");
        check(themes.getString(Settings.PREF_TOOLBAR_KEYS,"").contains("CUT:true"),"Cut missing from overflow");
        check(themes.getString(Settings.PREF_TOOLBAR_KEYS,"").contains("PASTE:true"),"Paste missing from overflow");
        check(themes.getString(Settings.PREF_PINNED_TOOLBAR_KEYS,"").contains("CLIPBOARD:true"),"Pinned clipboard missing");
        check(themes.getString(Settings.PREF_PINNED_TOOLBAR_KEYS,"").contains("AI:true"),"Pinned AI missing");
        check(!themes.getBoolean(Settings.PREF_AUTO_SHOW_TOOLBAR,true),"Compact toolbar must not auto-expand");
        test.launch();
        test.shot("24c-teclado-oneui");
        // Regression from actual beta4 device screenshots: an empty editor
        // exposed native "! ? , : ;" suggestions rather than five favorites.
        // PunctuationSuggestions is not an actively composed word.
        test.runOnMainSync(()-> {
            android.view.View root=helium314.keyboard.keyboard.KeyboardSwitcher.getInstance().getStripContainer();
            helium314.keyboard.latin.suggestions.SuggestionStripView strip=
                (helium314.keyboard.latin.suggestions.SuggestionStripView)
                test.findView(root,v->v instanceof helium314.keyboard.latin.suggestions.SuggestionStripView);
            android.view.View pinned=test.findView(root,
                v->v.getId()==helium314.keyboard.latin.R.id.pinned_keys);
            android.view.View words=test.findView(root,
                v->v.getId()==helium314.keyboard.latin.R.id.suggestions_strip);
            check(strip!=null && pinned!=null && words!=null,
                "Missing toolbar views for idle punctuation regression");
            strip.setSuggestions(
                helium314.keyboard.latin.PunctuationSuggestions.newPunctuationSuggestions(
                    new String[]{"!", "?", ",", ":", ";"}), false);
            strip.setToolbarVisibility(false);
            check(pinned.isShown() && !words.isShown(),
                "Idle punctuation candidates are incorrectly hiding circular favorites");
            check(((android.view.ViewGroup)pinned).getChildCount()==5,
                "Idle favorites missing after punctuation suggestions");
            // Android's layout transition crossfaded the two rows while
            // typing in the beta 5 device recording. Compact mode must
            // disable the parent transition completely.
            android.view.View stripRow=test.findView(root,
                v->v.getId()==helium314.keyboard.latin.R.id.suggestions_strip_wrapper);
            check(stripRow instanceof android.view.ViewGroup,
                "Missing compact suggestion strip wrapper");
            check(((android.view.ViewGroup)stripRow).getLayoutTransition()==null,
                "Compact toolbar animates favorites and words over each other");
            check(pinned.isShown()!=words.isShown(),
                "Idle favorites and suggestions are simultaneously visible");
        });
        test.shot("24c-favoritos-sem-texto");
        // Favorites must match the requested Samsung-style order, in circles.
        // Long pressing Clipboard opens editing only; a normal tap still
        // retains HeliBoard's native clipboard history action.
        test.clear();
        test.runOnMainSync(()-> {
            android.view.View root=helium314.keyboard.keyboard.KeyboardSwitcher.getInstance().getStripContainer();
            android.view.View pinned=test.findView(root,
                v->v.getId()==helium314.keyboard.latin.R.id.pinned_keys);
            check(pinned instanceof android.view.ViewGroup && pinned.isShown(),"Compact favorites missing");
            android.view.ViewGroup row=(android.view.ViewGroup)pinned;
            helium314.keyboard.latin.utils.ToolbarKey[] favorites={
                helium314.keyboard.latin.utils.ToolbarKey.AI,
                helium314.keyboard.latin.utils.ToolbarKey.CLIPBOARD,
                helium314.keyboard.latin.utils.ToolbarKey.EMOJI,
                helium314.keyboard.latin.utils.ToolbarKey.TRANSLATE,
                helium314.keyboard.latin.utils.ToolbarKey.SETTINGS};
            check(row.getChildCount()==favorites.length,"Incorrect count of compact favorites");
            for(int i=0;i<favorites.length;i++) {
                android.view.View button=row.getChildAt(i);
                check(button.getTag()==favorites[i],"Favorite order changed at "+i);
                check(button.getBackground() instanceof android.graphics.drawable.InsetDrawable,
                    "Missing round background at "+i);
            }
            helium314.keyboard.latin.suggestions.SuggestionStripView strip=
                (helium314.keyboard.latin.suggestions.SuggestionStripView)
                test.findView(root,v->v instanceof helium314.keyboard.latin.suggestions.SuggestionStripView);
            check(strip!=null,"SuggestionStripView missing for clipboard editing");
            check(strip.onLongClick(row.getChildAt(1)),"Clipboard long press not consumed");
            for(helium314.keyboard.latin.utils.ToolbarKey key:new helium314.keyboard.latin.utils.ToolbarKey[]{
                helium314.keyboard.latin.utils.ToolbarKey.SELECT_ALL,
                helium314.keyboard.latin.utils.ToolbarKey.SELECT_WORD,
                helium314.keyboard.latin.utils.ToolbarKey.COPY,
                helium314.keyboard.latin.utils.ToolbarKey.CUT,
                helium314.keyboard.latin.utils.ToolbarKey.PASTE,
                helium314.keyboard.latin.utils.ToolbarKey.UNDO,
                helium314.keyboard.latin.utils.ToolbarKey.REDO,
                helium314.keyboard.latin.utils.ToolbarKey.LEFT,
                helium314.keyboard.latin.utils.ToolbarKey.RIGHT,
                helium314.keyboard.latin.utils.ToolbarKey.UP,
                helium314.keyboard.latin.utils.ToolbarKey.DOWN,
                helium314.keyboard.latin.utils.ToolbarKey.WORD_LEFT,
                helium314.keyboard.latin.utils.ToolbarKey.WORD_RIGHT,
                helium314.keyboard.latin.utils.ToolbarKey.FULL_LEFT,
                helium314.keyboard.latin.utils.ToolbarKey.FULL_RIGHT,
                helium314.keyboard.latin.utils.ToolbarKey.PAGE_UP,
                helium314.keyboard.latin.utils.ToolbarKey.PAGE_DOWN,
                helium314.keyboard.latin.utils.ToolbarKey.PAGE_START,
                helium314.keyboard.latin.utils.ToolbarKey.PAGE_END}) {
                android.view.View action=test.findView(row,v->v.getTag()==key && v.isShown());
                check(action!=null,"Edit action missing inside Clipboard: "+key);
            }
            // The clipboard editing panel NEVER repeats the five favorites.
            for(helium314.keyboard.latin.utils.ToolbarKey key:favorites) {
                android.view.View duplicate=test.findView(row,v->v.getTag()==key);
                check(duplicate==null,"Main favorite duplicated in clipboard: "+key);
            }
            android.view.View addWord=test.findView(row,
                v->v.getId()==helium314.keyboard.latin.R.id.teclado_add_word);
            check(addWord!=null && addWord.isShown() && addWord.hasOnClickListeners(),
                "Add-word button missing from clipboard editing row");
            android.view.View horizontal=test.findView(row,
                v->v instanceof android.widget.HorizontalScrollView);
            check(horizontal!=null && horizontal.isShown(),
                "Editing actions are not scrollable horizontally");
            android.view.View back=test.findView(row,
                v->"Voltar aos atalhos principais".equals(v.getContentDescription()));
            check(back!=null && back.isShown(),"No back control in clipboard editing toolbar");
            back.performClick();
            check(row.getChildAt(0).getTag()==helium314.keyboard.latin.utils.ToolbarKey.AI,
                "Favorites did not return after editing mode");
        });
        // Translation is a dedicated shortcut, not a decorative icon.
        test.tagged(helium314.keyboard.latin.utils.ToolbarKey.TRANSLATE);
        test.aiNode("ai_translate_inglês");
        test.aiClick("ai_back");
        // Verify the Samsung-like placement and that '+' was MOVED, not
        // removed. The exact same dictionary TextView retains its listener.
        test.runOnMainSync(()-> {
            android.view.View root=helium314.keyboard.keyboard.KeyboardSwitcher.getInstance().getStripContainer();
            android.view.View row=test.findView(root,
                v->v.getId()==helium314.keyboard.latin.R.id.suggestions_strip_wrapper);
            android.view.View more=test.findView(root,
                v->v.getId()==helium314.keyboard.latin.R.id.suggestions_strip_toolbar_key);
            android.view.View dictionary=test.findView(root,
                v->v.getId()==helium314.keyboard.latin.R.id.teclado_add_word);
            android.view.View toolbox=test.findView(root,
                v->v.getId()==helium314.keyboard.latin.R.id.toolbar);
            check(row instanceof android.view.ViewGroup && more!=null &&
                ((android.view.ViewGroup)row).indexOfChild(more)==((android.view.ViewGroup)row).getChildCount()-1,
                "More button must be at the right edge");
            check(dictionary!=null && dictionary.getParent()==toolbox &&
                dictionary.hasOnClickListeners(),
                "Dictionary action must remain reusable but hidden outside clipboard editing");
            check(!dictionary.isShown(),"Dictionary button crowds word predictions");
            helium314.keyboard.latin.suggestions.SuggestionStripView strip=
                (helium314.keyboard.latin.suggestions.SuggestionStripView)
                test.findView(root,v->v instanceof helium314.keyboard.latin.suggestions.SuggestionStripView);
            check(strip!=null,"Suggestion strip missing for overflow test");
            strip.setToolbarVisibility(true);
            check(!dictionary.isShown(),"Dictionary add-word should not appear in ⋯");
            for(helium314.keyboard.latin.utils.ToolbarKey key:new helium314.keyboard.latin.utils.ToolbarKey[]{
                helium314.keyboard.latin.utils.ToolbarKey.AI,
                helium314.keyboard.latin.utils.ToolbarKey.CLIPBOARD,
                helium314.keyboard.latin.utils.ToolbarKey.EMOJI,
                helium314.keyboard.latin.utils.ToolbarKey.TRANSLATE,
                helium314.keyboard.latin.utils.ToolbarKey.SETTINGS,
                helium314.keyboard.latin.utils.ToolbarKey.SELECT_ALL,
                helium314.keyboard.latin.utils.ToolbarKey.SELECT_WORD,
                helium314.keyboard.latin.utils.ToolbarKey.COPY,
                helium314.keyboard.latin.utils.ToolbarKey.CUT,
                helium314.keyboard.latin.utils.ToolbarKey.PASTE,
                helium314.keyboard.latin.utils.ToolbarKey.UNDO,
                helium314.keyboard.latin.utils.ToolbarKey.REDO,
                helium314.keyboard.latin.utils.ToolbarKey.LEFT,
                helium314.keyboard.latin.utils.ToolbarKey.RIGHT}) {
                check(test.findView(toolbox,v->v.getTag()==key)==null,
                    "Duplicated main or editing action still in ⋯: "+key);
            }
            strip.setToolbarVisibility(false);
        });
        // While a Portuguese word is being typed, predictions must have the
        // full strip. Previously five pinned favorites truncated every word.
        test.clear();test.type("voce");
        Thread.sleep(900);
        test.runOnMainSync(()-> {
            android.view.View root=helium314.keyboard.keyboard.KeyboardSwitcher.getInstance().getStripContainer();
            android.view.View pinned=test.findView(root,v->v.getId()==helium314.keyboard.latin.R.id.pinned_keys);
            android.view.View predictions=test.findView(root,v->v.getId()==helium314.keyboard.latin.R.id.suggestions_strip);
            check(predictions!=null && predictions.isShown(),"Prediction strip missing during typing");
            check(pinned!=null && !pinned.isShown(),"Pinned favorites are crowding word predictions");
            check(pinned.isShown()!=predictions.isShown(),
                "Words and pinned shortcuts overlap while composing");
        });
        test.shot("24c-predicoes-sem-icones");
        // Exercise the REAL text actions through CLIPBOARD long-press,
        // not ⋯, and verify selection still offers contextual copy/cut.
        test.clear();
        test.runOnMainSync(()-> {
            android.view.View root=helium314.keyboard.keyboard.KeyboardSwitcher.getInstance().getStripContainer();
            helium314.keyboard.latin.suggestions.SuggestionStripView strip=
                (helium314.keyboard.latin.suggestions.SuggestionStripView)
                test.findView(root,v->v instanceof helium314.keyboard.latin.suggestions.SuggestionStripView);
            android.view.View clipboard=test.findView(root,
                v->v.getTag()==helium314.keyboard.latin.utils.ToolbarKey.CLIPBOARD && v.isShown());
            check(strip!=null && clipboard!=null,"Clipboard favorite missing before edit");
            strip.onLongClick(clipboard);
        });
        test.type("teste");
        test.tagged(helium314.keyboard.latin.utils.ToolbarKey.SELECT_ALL);
        Thread.sleep(450);
        test.runOnMainSync(()-> {
            android.view.View root=helium314.keyboard.keyboard.KeyboardSwitcher.getInstance().getStripContainer();
            helium314.keyboard.latin.suggestions.SuggestionStripView strip=
                (helium314.keyboard.latin.suggestions.SuggestionStripView)
                test.findView(root,v->v instanceof helium314.keyboard.latin.suggestions.SuggestionStripView);
            check(strip!=null,"Missing suggestion strip");
            strip.setToolbarVisibility(false);
            android.view.View copy=test.findView(root,
                v->v.getTag()==helium314.keyboard.latin.utils.ToolbarKey.COPY && v.isShown());
            android.view.View cut=test.findView(root,
                v->v.getTag()==helium314.keyboard.latin.utils.ToolbarKey.CUT && v.isShown());
            check(copy!=null && cut!=null,"Contextual copy/cut shortcuts not visible after selection");
        });
        test.shot("24d-atalhos-contextuais");
        test.tagged(helium314.keyboard.latin.utils.ToolbarKey.COPY);
        android.content.ClipboardManager copiedText=(android.content.ClipboardManager)
            c.getSystemService(Context.CLIPBOARD_SERVICE);
        check(copiedText.hasPrimaryClip() && copiedText.getPrimaryClip().getItemAt(0).getText()
            .toString().equalsIgnoreCase("teste"),"Contextual copy did not preserve selected text");
        if(!themePreviouslyActive) {
            test.clear();
            enterCompactEdit(test);
            test.type("teste");
            addWordFromToolbar(test,true);
            test.aiClick("keyboard_appearance");
            test.aiEnsureChecked("appearance_oneui_theme",false);
            test.aiPress("appearance_save");
            check(!themes.getBoolean("teclado_br_one_ui_active",false),"Classic theme was not restored");
            check(previousTheme.equals(themes.getString(Settings.PREF_THEME_COLORS,"")),"Previous user theme was not restored");
            check(!themes.getBoolean("teclado_br_compact_toolbar_active",false),"Compact toolbar not disabled with theme");
            check(previousToolbar.equals(themes.getString(Settings.PREF_TOOLBAR_KEYS,"")),"Original toolbar shortcuts were not restored");
            check(previousPinned.equals(themes.getString(Settings.PREF_PINNED_TOOLBAR_KEYS,"")),"Original pinned keys were not restored");
            check(previousAutoShow==themes.getBoolean(Settings.PREF_AUTO_SHOW_TOOLBAR,!previousAutoShow),"Toolbar auto-show preference changed");
            test.launch();
        }
        test.clear();
        boolean compactForDictionary=themes.getBoolean("teclado_br_compact_toolbar_active",false);
        if(compactForDictionary)enterCompactEdit(test);
        test.type("teste");
        addWordFromToolbar(test,compactForDictionary);
        android.app.Instrumentation.ActivityMonitor monitor=test.addMonitor("helium314.keyboard.latin.ai.DictionaryActivity",null,false);
        test.aiClick("dictionary_manage");
        android.app.Activity dictionary=monitor.waitForActivityWithTimeout(5000);
        test.removeMonitor(monitor);
        check(dictionary!=null,"Dictionary activity not observed");
        test.aiNode("dictionary_search");
        android.graphics.Rect titleBounds=new android.graphics.Rect(),backBounds=new android.graphics.Rect();
        test.aiNode("dictionary_title").getBoundsInScreen(titleBounds);
        test.aiNode("dictionary_back").getBoundsInScreen(backBounds);
        // Display.getRealMetrics is scoped to app bounds on recent target SDKs.
        // Accessibility bounds use physical screenshot coordinates, so use that same frame.
        android.graphics.Bitmap screen=test.ui.takeScreenshot();
        check(screen!=null,"Cannot inspect the screen frame");
        int screenHeight=screen.getHeight();
        screen.recycle();
        AtomicReference<android.graphics.Insets> actualBars=new AtomicReference<>();
        test.runOnMainSync(()-> {
            android.view.WindowInsets insets=dictionary.getWindow().getDecorView().getRootWindowInsets();
            actualBars.set(insets.getInsets(android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.displayCutout()));
        });
        check(actualBars.get()!=null,"Cannot inspect window insets");
        int top=actualBars.get().top,bottom=actualBars.get().bottom;
        check(titleBounds.top>=top&&backBounds.bottom<=screenHeight-bottom,"Dictionary controls overlap system bars: title="+titleBounds+", back="+backBounds+", screen="+screenHeight+", status="+top+", nav="+bottom);
        test.shot("25-dicionario-e-backup");
        instagram(test,result);
        result.putString("hybrid_contract","PASS: HTTPS payload, explicit modes, quota/auth/server/malformed/timeout fallback, no network offline, cancellation without fallback, encrypted key, diff, dictionary backup, settings touch flow");
    }
}

