// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.text.InputType;
import android.view.WindowManager;
import android.widget.*;
import java.util.concurrent.*;

/** Independent HBX preferences for the CleverKeys migration preview. */
public final class HbxSettingsActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private volatile boolean cancelled;
    private boolean busy;
    private LinearLayout root;
    private TextView status,modelState,geminiState,routerState;
    private EditText geminiInput,routerInput;
    private Spinner routerModel,offlineSlot;
    private RadioGroup modes;
    private Button importModel,measureModel;
    private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+0.5f);}
    private GradientDrawable bg(int color){
        GradientDrawable d=new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(16));
        return d;
    }
    private TextView label(String text,int size){
        TextView t=new TextView(this);
        t.setText(text);t.setTextColor(Color.WHITE);t.setTextSize(size);
        t.setPadding(dp(4),dp(7),dp(4),dp(7));
        root.addView(t);return t;
    }
    private Button button(String text,Runnable click){
        Button b=new Button(this);
        b.setAllCaps(false);b.setText(text);b.setTextColor(Color.WHITE);
        b.setBackground(bg(0xff343842));
        b.setOnClickListener(v->click.run());
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(46));
        p.bottomMargin=dp(7);
        root.addView(b,p);
        return b;
    }
    private EditText secretInput(String hint){
        EditText e=new EditText(this);
        e.setTextColor(Color.WHITE);e.setHintTextColor(0xffaaaaaa);
        e.setHint(hint);
        e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        e.setSingleLine(true);
        e.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        root.addView(e,new LinearLayout.LayoutParams(-1,dp(55)));
        return e;
    }
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(0xff17191e);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16),dp(20),dp(16),dp(24));
        scroll.addView(root);setContentView(scroll);
        label("✦ HBX | CONFIGURAÇÕES",22);
        label("Modo CleverKeys experimental. Só comandos que você iniciar usam a rede.",13);
        label("Modo de IA",18);
        modes=new RadioGroup(this);
        String[] captions={"Offline · somente no aparelho",
            "Automático · Gemini 3.6 → OpenRouter → GGUF",
            "Online · Gemini 3.6 → OpenRouter"};
        String[] values={AiPreferences.OFFLINE,AiPreferences.AUTO,AiPreferences.ONLINE};
        for(int i=0;i<3;i++){
            RadioButton r=new RadioButton(this);
            r.setId(android.view.View.generateViewId());
            r.setText(captions[i]);r.setTextColor(Color.WHITE);r.setTag(values[i]);
            r.setChecked(values[i].equals(AiPreferences.mode(this)));
            modes.addView(r);
        }
        root.addView(modes);
        button("Salvar modo",()->{
            int id=modes.getCheckedRadioButtonId();
            if(id<0)return;
            String mode=(String)modes.findViewById(id).getTag();
            AiPreferences.mode(this,mode);
            status.setText("Modo: "+AiPreferences.label(this));
        });
        label("Gemini 3.6 Flash · principal online",18);
        geminiState=label(SecretStore.hasGeminiKey(this)?
            "Chave Gemini já cadastrada e protegida.":
            "Nenhuma chave Gemini cadastrada.",13);
        geminiInput=secretInput("Chave Gemini (nunca compartilhe)");
        button("Salvar chave Gemini",()->saveKey(true));
        button("Testar Gemini 3.6 com frase fictícia",()->testOnline(true));
        button("Remover somente chave Gemini",()->{
            SecretStore.removeGemini(this);
            geminiInput.setText("");
            geminiState.setText("Chave Gemini removida.");
        });

        label("OpenRouter · reserva online",18);
        label("No modo openrouter/free, provedores externos podem processar o texto. Os limites e a disponibilidade variam.",13);
        routerState=label(SecretStore.hasOpenRouterKey(this)?
            "Chave OpenRouter já cadastrada e protegida.":
            "Nenhuma chave OpenRouter cadastrada.",13);
        routerInput=secretInput("Chave OpenRouter (nunca compartilhe)");
        button("Salvar chave OpenRouter",()->saveKey(false));
        routerModel=new Spinner(this);
        String[] routerChoices={"Roteamento gratuito","Qwen3 32B gratuito (quando disponível)"};
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item,routerChoices);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        routerModel.setAdapter(adapter);
        routerModel.setSelection(OpenRouterClient.QWEN.equals(
            AiPreferences.openRouterModel(this))?1:0);
        root.addView(routerModel,new LinearLayout.LayoutParams(-1,dp(48)));
        button("Salvar modelo OpenRouter",()->{
            String value=routerModel.getSelectedItemPosition()==1?
                OpenRouterClient.QWEN:OpenRouterClient.FREE;
            AiPreferences.openRouterModel(this,value);
            status.setText("OpenRouter configurado para "+value);
        });
        button("Testar OpenRouter com frase fictícia",()->testOnline(false));
        button("Criar chave OpenRouter",()->
            startActivity(new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://openrouter.ai/settings/keys"))));
        button("Remover somente chave OpenRouter",()->{
            SecretStore.removeOpenRouter(this);
            routerInput.setText("");
            routerState.setText("Chave OpenRouter removida.");
        });

        label("Modelos GGUF offline",18);
        label("O Gemma atual fica em sua própria posição. Baixe Qwen 3.5 2B e Gemma 4 E4B separadamente. O E4B exige muita RAM.",13);
        offlineSlot=new Spinner(this);
        String[] options={"Gemma 4 E2B (posição original)",
            "Qwen 3.5 2B","Gemma 4 E4B (experimental)"};
        ArrayAdapter<String> slots=new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item,options);
        slots.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        offlineSlot.setAdapter(slots);
        for(int i=0;i<ModelStore.SLOTS.length;i++)
            if(ModelStore.SLOTS[i].equals(ModelStore.selected(this)))
                offlineSlot.setSelection(i);
        root.addView(offlineSlot,new LinearLayout.LayoutParams(-1,dp(48)));
        modelState=label(ModelStore.description(this),13);
        offlineSlot.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(android.widget.AdapterView<?> p,
                                                   android.view.View v,int index,long id){
                modelState.setText(ModelStore.description(HbxSettingsActivity.this,
                    ModelStore.SLOTS[index])+"\nAtivo: "+
                    ModelStore.label(ModelStore.selected(HbxSettingsActivity.this)));
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p){}
        });
        importModel=button("Importar GGUF na posição selecionada",()->{
            if(busy)return;
            Intent file=new Intent(Intent.ACTION_OPEN_DOCUMENT);
            file.setType("*/*");file.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(file,91);
        });
        button("Ativar GGUF selecionado",()->{
            String slot=ModelStore.SLOTS[offlineSlot.getSelectedItemPosition()];
            status.setText(ModelStore.select(this,slot)?
                "Modelo ativo: "+ModelStore.label(slot):
                "Importe primeiro o GGUF nessa posição.");
            modelState.setText(ModelStore.description(this));
        });
        measureModel=button("Medir somente GGUF selecionado",this::measureOffline);
        button("Remover somente este GGUF",()->{
            String slot=ModelStore.SLOTS[offlineSlot.getSelectedItemPosition()];
            new AlertDialog.Builder(this)
                .setTitle("Remover "+ModelStore.label(slot)+"?")
                .setMessage("Outros GGUFs e chaves continuarão no aparelho.")
                .setPositiveButton("Remover",(dialog,index)->{
                    ModelStore.removeSlot(this,slot);
                    modelState.setText(ModelStore.description(this));
                }).setNegativeButton("Cancelar",null).show();
        });
        button("Ver os modelos para baixar",()->{
            String[] choices={"Qwen3.5 2B GGUF","Gemma 4 E4B GGUF","Gemma 4 E2B GGUF"};
            String[] links={
                "https://huggingface.co/bartowski/Qwen_Qwen3.5-2B-GGUF",
                "https://huggingface.co/ggml-org/gemma-4-E4B-it-GGUF",
                "https://huggingface.co/ggml-org/gemma-4-E2B-it-GGUF"};
            new AlertDialog.Builder(this).setItems(choices,(d,i)->
                startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(links[i])))).show();
        });
        label("Atualização dentro do HBX CleverKeys",18);
        label("Este canal de atualização pertence à nova instalação experimental. Ele só aceita APKs deste mesmo pacote e assinados pelo mesmo certificado. O HBX HeliBoard atual continua separado e protegido.",13);
        button("Verificar e instalar atualização",()->
            startActivity(new Intent(this,HbxUpdateActivity.class)));
        button("Consultar versões no GitHub",()->
            startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(
                "https://github.com/helbermoura2/teclado-br-publico/releases"))));
        status=label("Pronto para configurar.",14);
        button("Voltar",this::finish);
    }
    private String slot(){
        return ModelStore.SLOTS[offlineSlot.getSelectedItemPosition()];
    }
    private void saveKey(boolean gemini){
        if(busy)return;
        final String value=(gemini?geminiInput:routerInput).getText().toString().trim();
        if(value.isEmpty()){
            status.setText("Cole a chave antes de salvar.");
            return;
        }
        busy=true;
        status.setText("Protegendo a chave no Android Keystore…");
        worker.execute(()->{
            boolean ok=false;
            try {
                if(gemini) {
                    SecretStore.saveGemini(this,value);
                    ok=value.equals(SecretStore.readGemini(this));
                }else {
                    SecretStore.saveOpenRouter(this,value);
                    ok=value.equals(SecretStore.readOpenRouter(this));
                }
            }catch(Exception ignored){}
            final boolean success=ok;
            runOnUiThread(()->{
                if(isDestroyed())return;
                (gemini?geminiInput:routerInput).setText("");
                (gemini?geminiState:routerState).setText(success?
                    "Chave salva e protegida no aparelho.":"Não foi possível salvar a chave.");
                status.setText(success?"Credencial configurada.":"Confira sua chave.");
                busy=false;
            });
        });
    }
    private void testOnline(boolean gemini){
        if(busy)return;
        if(gemini?!SecretStore.hasGeminiKey(this):
           !SecretStore.hasOpenRouterKey(this)){
            status.setText("Cadastre uma chave antes do teste.");
            return;
        }
        busy=true;
        status.setText("Testando somente com frase fictícia…");
        worker.execute(()->{
            String result;
            try {
                InferenceResult response;
                if(gemini)
                    response=new GeminiClient(GeminiClient.BACKUP_MODEL,
                        SecretStore.readGemini(this),()->cancelled)
                        .generateMinimalProbe();
                else
                    response=new OpenRouterClient(SecretStore.readOpenRouter(this),
                        AiPreferences.openRouterModel(this),()->cancelled)
                        .generate("Responda apenas OK.",32,s->{});
                result="Teste concluído: "+response.backend+".";
            }catch(Exception e) {
                result="Modelo não respondeu. "+
                    (gemini?GeminiClient.diagnostic(e):OpenRouterClient.diagnostic(e));
            }
            final String message=result;
            runOnUiThread(()->{
                if(isDestroyed())return;
                status.setText(message);
                busy=false;
            });
        });
    }
    private void measureOffline(){
        if(busy)return;
        String selected=slot();
        if(!ModelStore.model(this,selected).isFile()) {
            status.setText("Importe o GGUF desta posição antes de medir.");
            return;
        }
        if(!ModelStore.select(this,selected)) {
            status.setText("Não foi possível ativar o modelo para medição.");
            return;
        }
        busy=true;
        status.setText("Carregando "+ModelStore.label(selected)+
            " e executando frase de teste…");
        worker.execute(()->{
            String result;
            long t=SystemClock.elapsedRealtime();
            try {
                InferenceResult response=new OfflineEngine(this,()->cancelled)
                    .generate(PromptBuilder.build("Ortografia e gramática","","",
                        "Eu nao sei o que fazer hoje."),128,s->{});
                result=ModelStore.label(selected)+" · "+
                    (SystemClock.elapsedRealtime()-t)+" ms\n"+
                    "Esperado: Eu não sei o que fazer hoje.\n"+
                    "Obtido: "+response.text;
            }catch(Throwable error) {
                result="Modelo não concluiu a medição. Confira se há memória RAM suficiente.";
            }
            final String message=result;
            runOnUiThread(()->{
                if(isDestroyed())return;
                status.setText(message);
                busy=false;
            });
        });
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=91||result!=RESULT_OK||data==null||data.getData()==null)return;
        if(busy)return;
        final Uri uri=data.getData();
        final String selected=slot();
        cancelled=false;busy=true;
        status.setText("Importando "+ModelStore.label(selected)+"…");
        worker.execute(()->{
            String message;
            try {
                ModelStore.importFile(this,uri,(now,total)->{
                    final String part="Importando: "+(now/1048576)+" MB"+
                        (total>0?" de "+(total/1048576)+" MB":"");
                    runOnUiThread(()->{if(!isDestroyed())status.setText(part);});
                    return !cancelled;
                },selected);
                ModelStore.select(this,selected);
                message="Modelo importado: "+ModelStore.description(this,selected);
            }catch(Exception error){
                message="Importação não concluída. Confira o espaço e o arquivo GGUF.";
            }
            final String text=message;
            runOnUiThread(()->{
                if(isDestroyed())return;
                modelState.setText(ModelStore.description(this));
                status.setText(text);
                busy=false;
            });
        });
    }
    @Override protected void onDestroy(){
        cancelled=true;
        worker.shutdownNow();
        super.onDestroy();
    }
}
