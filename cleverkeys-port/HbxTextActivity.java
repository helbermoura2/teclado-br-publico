// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/**
 * HBX writing assistant for the CleverKeys engine.
 *
 * Two entry points:
 *  (1) Android ACTION_PROCESS_TEXT: the existing CleverKeys textAssist trampoline
 *      can return the result safely to the original editor.
 *  (2) Explicit HBX toolbar: works with an optional selection and offers Copy,
 *      but never overwrites an arbitrary editor from an Activity.
 *
 * No keystrokes, clipboard contents or passwords are ever read automatically.
 */
public final class HbxTextActivity extends Activity {
    private final ExecutorService work=Executors.newSingleThreadExecutor();
    private volatile Future<?> task;
    private volatile boolean cancelled;
    private volatile Object active;
    private boolean busy,processText,readOnly;
    private String selected="",generated="";
    private TextView status,output;
    private EditText source;
    private Button replace,copy;
    private LinearLayout root;
    private int dp(int n) {
        return (int)(getResources().getDisplayMetrics().density*n+0.5f);
    }
    private GradientDrawable bg(int color,int radius) {
        GradientDrawable d=new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }
    private TextView label(String value,int sp) {
        TextView view=new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(Color.WHITE);
        view.setPadding(dp(4),dp(6),dp(4),dp(6));
        root.addView(view,new LinearLayout.LayoutParams(-1,-2));
        return view;
    }
    private Button button(String title,Runnable action) {
        Button view=new Button(this);
        view.setText(title);
        view.setAllCaps(false);
        view.setTextColor(Color.WHITE);
        view.setBackground(bg(0xff30333d,14));
        view.setOnClickListener(v->action.run());
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(45));
        p.bottomMargin=dp(7);
        root.addView(view,p);
        return view;
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        processText=Intent.ACTION_PROCESS_TEXT.equals(getIntent().getAction());
        readOnly=getIntent().getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY,false);
        CharSequence input=getIntent().getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
        if(input!=null&&input.length()<=4000)selected=input.toString();

        ScrollView scroll=new ScrollView(this);
        scroll.setBackgroundColor(0xff17191e);
        root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18),dp(18),dp(18),dp(26));
        scroll.addView(root);
        setContentView(scroll);
        label("✦ HBX | ASSISTENTE DE ESCRITA",21);
        label("Motor CleverKeys · Samsung-style · PT-BR",13);
        label("Seu texto só é enviado à IA online depois que você toca em uma ação. O modo Offline nunca usa a rede.",13);

        source=new EditText(this);
        source.setText(selected);
        source.setTextColor(Color.WHITE);
        source.setHintTextColor(0xffaaaaaa);
        source.setHint("Selecione um texto ou escreva aqui…");
        source.setMinLines(3);
        source.setMaxLines(7);
        source.setPadding(dp(14),dp(10),dp(14),dp(10));
        source.setBackground(bg(0xff272c39,18));
        source.setInputType(android.text.InputType.TYPE_CLASS_TEXT|
            android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        source.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        root.addView(source,new LinearLayout.LayoutParams(-1,-2));

        label("MODO DE IA: "+AiPreferences.label(this),13);
        button("Corrigir ortografia e gramática",()->generate("Ortografia e gramática"));
        button("Traduzir para inglês",()->generate("Traduzir para inglês"));
        button("Traduzir para português",()->generate("Traduzir para português"));
        button("Resumir",()->generate("Resumir"));
        button("Reescrever profissionalmente",()->generate("Estilo de escrita"));
        button("Gerar texto a partir do pedido",()->generate("Compositor"));
        button("Configurar Gemini, OpenRouter e modelos GGUF",()->
            startActivity(new Intent(this,HbxSettingsActivity.class)));
        status=label("Pronto. A escrita normal do CleverKeys continua local.",13);
        output=label("",17);
        output.setTextIsSelectable(true);
        output.setBackground(bg(0xff233044,18));
        copy=button("Copiar resultado",this::copyResult);
        replace=button("Substituir seleção",this::replaceResult);
        copy.setEnabled(false);
        replace.setEnabled(false);
        button("Cancelar processamento",this::cancelGeneration);
        button("Voltar",this::finish);
        String preset=getIntent().getStringExtra(EXTRA_PRESET_ACTION);
        if("Traduzir para inglês".equals(preset) && !selected.isEmpty())
            source.post(()->generate(preset));
    }
    public static final String EXTRA_PRESET_ACTION="br.com.hbx.extra.PRESET_ACTION";
    private void generate(String action) {
        if(busy)return;
        String input=source.getText().toString();
        if(input.trim().isEmpty()) {
            status.setText("Escreva ou selecione um texto antes de usar a IA.");
            return;
        }
        if(input.length()>4000) {
            status.setText("O limite é de 4.000 caracteres.");
            return;
        }
        final String mode=AiPreferences.mode(this);
        final String prompt=PromptBuilder.build(action,
            "Estilo de escrita".equals(action)?"Profissional":"Padrão",
            "Compositor".equals(action)?input:"",input);
        final boolean connected=AiPreferences.connected(this);
        final boolean key=SecretStore.hasGeminiKey(this)||
            SecretStore.hasOpenRouterKey(this);
        final boolean local=ModelStore.model(this).isFile();
        busy=true;
        cancelled=false;
        generated="";
        output.setText("");
        copy.setEnabled(false);
        replace.setEnabled(false);
        status.setText("Processando…");
        final BooleanSupplier stop=()->cancelled||Thread.currentThread().isInterrupted();
        task=work.submit(()->{
            try {
                InferenceRouter.Backend online=(text,limit,partial)-> {
                    Exception last=null;
                    if(SecretStore.hasGeminiKey(this)) {
                        try {
                            status("Tentando Gemini 3.6…");
                            GeminiClient gemini=new GeminiClient(GeminiClient.BACKUP_MODEL,
                                SecretStore.readGemini(this),stop);
                            active=gemini;
                            return gemini.generate(text,limit,partial);
                        }catch(Exception error) {
                            InferenceRouter.check(stop);
                            last=error;
                            status("Gemini não concluiu. Tentando OpenRouter…");
                        }
                    }
                    if(SecretStore.hasOpenRouterKey(this)) {
                        OpenRouterClient router=new OpenRouterClient(
                            SecretStore.readOpenRouter(this),
                            AiPreferences.openRouterModel(this),stop);
                        active=router;
                        return router.generate(text,limit,partial);
                    }
                    if(last!=null)throw last;
                    throw new IllegalStateException("Cadastre uma chave online.");
                };
                InferenceRouter.Backend offline=(text,limit,partial)->{
                    status("Executando modelo offline "+ModelStore.description(this)+"…");
                    OfflineEngine localEngine=new OfflineEngine(this,stop);
                    active=localEngine;
                    return localEngine.generate(text,limit,partial);
                };
                InferenceResult result=InferenceRouter.run(
                    mode,connected,key,local,prompt,256,
                    online,offline,stop,this::status,s->{});
                InferenceRouter.check(stop);
                runOnUiThread(()-> {
                    if(isFinishing()||isDestroyed())return;
                    busy=false;
                    generated=result.text;
                    output.setText(generated);
                    copy.setEnabled(true);
                    replace.setEnabled(processText&&!readOnly&&!selected.isEmpty());
                    status.setText(result.backend+
                        (processText&&!readOnly?" · Revise antes de substituir.":
                            " · Use Copiar para levar a outro aplicativo."));
                });
            }catch(Exception error) {
                if(stop.getAsBoolean())status("Cancelado.");
                else status("Não foi possível concluir. Confira o modelo, a chave ou a rede. "+
                    "Você pode tentar Offline nas configurações.");
            }finally {
                active=null;
                runOnUiThread(()->busy=false);
            }
        });
    }
    private void status(String text) {
        runOnUiThread(()-> {
            if(!isFinishing()&&!isDestroyed()&&status!=null)status.setText(text);
        });
    }
    private void copyResult() {
        if(generated.isEmpty())return;
        ClipboardManager c=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        if(c!=null)c.setPrimaryClip(ClipData.newPlainText("HBX",generated));
        status.setText("Resultado copiado. Revise antes de colar.");
    }
    private void replaceResult() {
        // Only ACTION_PROCESS_TEXT can return a selection safely to the original
        // CleverKeys receipt/field. Direct toolbar launches are copy-only.
        if(!processText||readOnly||generated.isEmpty())return;
        Intent result=new Intent();
        result.putExtra(Intent.EXTRA_PROCESS_TEXT,generated);
        setResult(RESULT_OK,result);
        finish();
    }
    private void cancelGeneration() {
        cancelled=true;
        Object engine=active;
        if(engine instanceof GeminiClient)((GeminiClient)engine).cancel();
        if(engine instanceof OpenRouterClient)((OpenRouterClient)engine).cancel();
        if(engine instanceof OfflineEngine)((OfflineEngine)engine).cancel();
        Future<?> pending=task;
        if(pending!=null)pending.cancel(true);
        busy=false;
        status("Cancelado.");
    }
    @Override protected void onDestroy() {
        cancelGeneration();
        work.shutdownNow();
        super.onDestroy();
    }
}
