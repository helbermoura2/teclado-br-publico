// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;
import android.app.*;
import android.os.*;
import android.content.*;
import android.net.Uri;
import android.graphics.Color;
import android.text.InputType;
import android.view.WindowManager;
import android.widget.*;
import java.util.concurrent.*;
/** Configuration and synthetic measurements. Real prompts never appear in this activity. */
public final class ModelSettingsActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private volatile boolean cancelled;
    private boolean busy,measuring,bound;
    private LinearLayout root;
    private TextView description,status,keyState,routerKeyState,geminiKeyState,geminiTestState,metrics;
    private Button add,remove,cancel,save,geminiSave,groqSave,routerSave,routerModelSave,routerTest,geminiTest,geminiProbe,geminiModelSave,measure,cancelMeasure;
    private EditText key,routerKey,geminiKey;
    private Spinner geminiModelSelector,routerModelSelector;
    private RadioGroup modes;
    private Messenger engine;
    private int dp(int n) {
        return (int)(n*getResources().getDisplayMetrics().density+.5f);
    }
    private TextView label(String text,int size) {
        TextView v=new TextView(this);
        v.setText(text);
        v.setTextSize(size);
        v.setTextColor(Color.WHITE);
        v.setPadding(0,dp(10),0,dp(8));
        root.addView(v);
        return v;
    }
    private Button button(String text,String id,Runnable action) {
        Button b=new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setMinHeight(dp(48));
        if(id!=null)b.setContentDescription(id);
        b.setOnClickListener(v->action.run());
        root.addView(b,new LinearLayout.LayoutParams(-1,-2));
        return b;
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xff17191e);
        root=new LinearLayout(this);
        root.setOrientation(1);
        SettingsLayout.insets(root,dp(20));
        scroll.addView(root);
        setContentView(scroll);
        label("Assistente · configurações",24);
        label("Somente tarefas iniciadas por você enviam texto à IA. Ordem online fixa: Gemini 3.8 → Gemini 3.6 → OpenRouter → Groq. No modo Automático, o Gemma local é o último recurso. Digitação comum nunca envia texto.",14);
        modes=new RadioGroup(this);
        String[] names= {
            "Offline · somente no aparelho","Automático · 3.8 → 3.6 → OpenRouter → Groq → aparelho","Online · 3.8 → 3.6 → OpenRouter → Groq"
        };
        String[] values= {
            AiPreferences.OFFLINE,AiPreferences.AUTO,AiPreferences.ONLINE
        };
        for(int i=0;i<3;i++) {
            RadioButton radio=new RadioButton(this);
            radio.setId(android.view.View.generateViewId());
            radio.setText(names[i]);
            radio.setTextColor(Color.WHITE);
            radio.setTag(values[i]);
            radio.setContentDescription("ai_setting_"+values[i]);
            radio.setMinHeight(dp(48));
            modes.addView(radio);
            if(values[i].equals(AiPreferences.mode(this)))radio.setChecked(true);
        }
        root.addView(modes);
        save=button("Salvar modo de IA","ai_save_settings",this::saveMode);
        label("Gemini 3.8 → 3.6 · ordem automática",20);
        label("Você já pode usar sua própria chave do Google AI Studio. Não cole aqui no GitHub ou no chat. A chave é cifrada no aparelho e não entra no APK. Os textos enviados usam a política da sua conta Google; contas pagas podem ter custos.",14);
        geminiKeyState=label(SecretStore.hasGeminiKey(this)?"Chave Gemini protegida já cadastrada.":"Nenhuma chave Gemini cadastrada.",14);
        geminiKeyState.setContentDescription("ai_gemini_key_state");
        geminiKey=new EditText(this);
        geminiKey.setSingleLine(true);
        geminiKey.setTextColor(Color.WHITE);
        geminiKey.setHintTextColor(Color.LTGRAY);
        geminiKey.setHint("Cole a chave do Gemini aqui");
        geminiKey.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        geminiKey.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        geminiKey.setContentDescription("ai_gemini_key");
        root.addView(geminiKey);
        geminiSave=button("Salvar chave Gemini","ai_save_gemini_key",this::saveGeminiKey);
        label("Modelo para teste individual (não altera a ordem automática)",16);
        geminiModelSelector=new Spinner(this);
        geminiModelSelector.setContentDescription("ai_gemini_model");
        String[] geminiChoices={
            "Gemini 3.8 Flash · mais avançado",
            "Gemini 3.6 Flash · alternativa"
        };
        ArrayAdapter<String> geminiAdapter=new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item,geminiChoices);
        geminiAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        geminiModelSelector.setAdapter(geminiAdapter);
        geminiModelSelector.setSelection(GeminiClient.BACKUP_MODEL.equals(
            AiPreferences.geminiModel(this))?1:0);
        root.addView(geminiModelSelector,new LinearLayout.LayoutParams(-1,dp(48)));
        geminiModelSave=button("Lembrar modelo do teste (opcional)","ai_save_gemini_model",this::saveGeminiModel);
        geminiTest=button("Testar conexão Gemini","ai_test_gemini",this::testGemini);
        geminiProbe=button("Diagnóstico rápido do Gemini 3.8","ai_probe_gemini_38",this::probeGemini38);
        geminiTestState=label("Testes independentes e fictícios, sem mensagens pessoais ou Groq. O diagnóstico rápido envia somente 'Responda apenas OK'.",14);
        geminiTestState.setContentDescription("ai_gemini_test_state");
        geminiModelSelector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent,android.view.View view,
                                                 int position,long id) {
                if(geminiTestState!=null&&!isDestroyed()) {
                    String model=position==1?GeminiClient.BACKUP_MODEL:GeminiClient.MODEL;
                    geminiTestState.setText("Selecionado: "+model+
                        ". Este modelo ainda não foi testado. Toque em Testar conexão Gemini.");
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        button("Abrir minhas chaves Gemini",null,()->open("https://aistudio.google.com/api-keys"));
        button("Remover só a chave Gemini","ai_remove_gemini_key",()-> {
            stopMeasurement();
            SecretStore.removeGemini(this);
            geminiKey.setText("");
            geminiKeyState.setText("Chave Gemini removida.");
            ensureUsableMode();
            status.setText("Chave Gemini removida. As chaves OpenRouter e Groq e o modelo offline foram mantidos.");
        });

        label("OpenRouter · segunda IA online",20);
        label("Gratuito com limites de uso. No roteamento openrouter/free, o texto pode ser processado por provedores diferentes. Selecione Qwen3 32B quando disponível. O envio só ocorre quando você aciona a IA. Groq permanece como última reserva online e sua chave continua armazenada.",14);
        routerKeyState=label(SecretStore.hasOpenRouterKey(this)?
            "Chave OpenRouter protegida já cadastrada.":
            "Nenhuma chave OpenRouter cadastrada.",14);
        routerKeyState.setContentDescription("ai_openrouter_key_state");
        routerKey=new EditText(this);
        routerKey.setSingleLine(true);
        routerKey.setTextColor(Color.WHITE);
        routerKey.setHintTextColor(Color.LTGRAY);
        routerKey.setHint("Cole sua chave do OpenRouter");
        routerKey.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        routerKey.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        routerKey.setContentDescription("ai_openrouter_key");
        root.addView(routerKey);
        routerSave=button("Salvar chave OpenRouter","ai_save_openrouter_key",this::saveOpenRouterKey);
        label("Modelo OpenRouter para revisão e teste",16);
        routerModelSelector=new Spinner(this);
        String[] routerChoices={"Roteamento gratuito (openrouter/free)",
            "Qwen3 32B gratuito (se disponível)"};
        ArrayAdapter<String> routerAdapter=new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item,routerChoices);
        routerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        routerModelSelector.setAdapter(routerAdapter);
        routerModelSelector.setSelection(OpenRouterClient.QWEN.equals(
            AiPreferences.openRouterModel(this))?1:0);
        root.addView(routerModelSelector,new LinearLayout.LayoutParams(-1,dp(48)));
        routerModelSave=button("Salvar modelo OpenRouter","ai_save_openrouter_model",
            this::saveOpenRouterModel);
        routerTest=button("Testar conexão OpenRouter","ai_test_openrouter",this::testOpenRouter);
        button("Criar minha chave OpenRouter",null,
            ()->open("https://openrouter.ai/settings/keys"));
        button("Remover só a chave OpenRouter","ai_remove_openrouter_key",()-> {
            stopMeasurement();
            SecretStore.removeOpenRouter(this);
            routerKey.setText("");
            routerKeyState.setText("Chave OpenRouter removida.");
            ensureUsableMode();
            status.setText("OpenRouter removido. Chave Gemini, Groq e GGUF preservados.");
        });
        label("Groq · última reserva online",20);
        label("Opcional. Groq só é tentada quando Gemini e OpenRouter não concluem. Se Groq também falhar, o modo Automático usa o Gemma offline. Serviços online podem ter cotas e custos.",14);
        keyState=label(SecretStore.hasKey(this)?"Chave Groq protegida já cadastrada.":"Nenhuma chave Groq cadastrada.",14);
        keyState.setContentDescription("ai_key_state");
        key=new EditText(this);
        key.setSingleLine(true);
        key.setTextColor(Color.WHITE);
        key.setHintTextColor(Color.LTGRAY);
        key.setHint("Cole a chave da Groq (opcional)");
        key.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        key.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        key.setContentDescription("ai_groq_key");
        root.addView(key);
        groqSave=button("Salvar chave Groq","ai_save_groq_key",this::saveGroqKey);
        button("Abrir minhas chaves Groq",null,()->open("https://console.groq.com/keys"));
        button("Remover só a chave Groq","ai_remove_key",()-> {
            stopMeasurement();
            SecretStore.remove(this);
            key.setText("");
            keyState.setText("Chave Groq removida.");
            ensureUsableMode();
            status.setText("Chave Groq removida. As chaves Gemini e OpenRouter e o modelo offline foram mantidos.");
        });
        label("Modelo offline",20);
        description=label(ModelStore.description(this),15);
        add=button("Importar modelo GGUF",null,()-> {
            if(!busy&&!measuring)startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),41);
        });
        button("Onde baixar os modelos",null,()->new AlertDialog.Builder(this).setTitle("Modelos offline").setMessage("Baixe no navegador e importe o GGUF. Qwen3.5 2B Q4_K_M: cerca de 1,4 GB. Gemma 4 E2B Q4_0: cerca de 2,84 GB; exige mais memória. Qualidade e velocidade dependem do aparelho e do modelo.").setPositiveButton("Qwen3.5 2B",(d,w)->open("https://huggingface.co/bartowski/Qwen_Qwen3.5-2B-GGUF/blob/7d26695454df6de5fbcce2e58681e62dae06ce43/Qwen_Qwen3.5-2B-Q4_K_M.gguf")).setNeutralButton("Gemma 4 E2B",(d,w)->open("https://huggingface.co/ggml-org/gemma-4-E2B-it-GGUF/blob/6938c58e698502037401a5f19f6905f2fb2f7dee/gemma-4-E2B-it-Q4_0.gguf")).setNegativeButton("Fechar",null).show());
        remove=button("Remover somente o modelo",null,()-> {
            if(!busy&&!measuring)new AlertDialog.Builder(this).setTitle("Remover o modelo?").setMessage("Seu dicionário e seus pins serão mantidos.").setPositiveButton("Remover",(d,w)-> {
                ModelStore.model(this).delete();
                ModelStore.metadata(this).delete();
                description.setText(ModelStore.description(this));
                controls(false);
            }).setNegativeButton("Cancelar",null).show();
        });
        status=label("O GGUF já importado é mantido ao atualizar o teclado.",14);
        cancel=button("Cancelar importação",null,()-> {
            cancelled=true;
            status.setText("Cancelando…");
        });
        label("Medições no seu aparelho",20);
        label("Teste com uma frase sintética, usando o modo salvo acima. Mede tempo total e pico de memória amostrado. A energia, quando informada pelo Android, é do aparelho inteiro e não isola o teclado. Compare nas mesmas condições.",14);
        measure=button("Medir IA selecionada","ai_measure",this::startMeasurement);
        cancelMeasure=button("Cancelar medição","ai_cancel_measure",this::stopMeasurement);
        metrics=label(MeasurementHistory.describe(this),14);
        metrics.setContentDescription("ai_measurements");
        button("Copiar medições",null,()-> {
            ((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Medições de IA",MeasurementHistory.describe(this)));
            Toast.makeText(this,"Medições copiadas.",Toast.LENGTH_SHORT).show();
        });
        label("Atualizações do Teclado BR",20);
        label("Verifique uma nova versão e instale por cima da atual, sem perder os modelos, palavras ou configurações. O download é feito pelo aplicativo, sem login no GitHub. A instalação é confirmada pelo Android.",14);
        button("Verificar e instalar atualização","ai_open_updater",()->
            startActivity(new Intent(this,UpdateActivity.class)));
        button("Aparência do teclado",null,()->startActivity(new Intent(this,AppearanceActivity.class)));
        button("Dicionário pessoal e backup",null,()->startActivity(new Intent(this,DictionaryActivity.class)));
        button("Voltar ao aplicativo",null,this::finish);
        controls(false);
    }
    private void ensureUsableMode() {
        if(AiPreferences.ONLINE.equals(AiPreferences.mode(this))&&
            !SecretStore.hasGeminiKey(this)&&!SecretStore.hasOpenRouterKey(this)&&!SecretStore.hasKey(this)) {
            AiPreferences.mode(this,AiPreferences.OFFLINE);
            for(int i=0;i<modes.getChildCount();i++) {
                RadioButton r=(RadioButton)modes.getChildAt(i);
                if(AiPreferences.OFFLINE.equals(r.getTag()))r.setChecked(true);
            }
        }
    }
    private void saveMode() {
        if(busy||measuring)return;
        int checked=modes.getCheckedRadioButtonId();
        if(checked==-1)return;
        String selected=(String)modes.findViewById(checked).getTag();
        if(AiPreferences.ONLINE.equals(selected)&&
            !SecretStore.hasGeminiKey(this)&&!SecretStore.hasOpenRouterKey(this)&&!SecretStore.hasKey(this)) {
            status.setText("Cadastre e salve sua chave Gemini, OpenRouter ou Groq.");
            return;
        }
        save.setEnabled(false);
        worker.execute(()-> {
            boolean saved=false;
            try {AiPreferences.mode(this,selected);saved=true;}
            catch(Exception ignored){}
            final boolean result=saved;
            runOnUiThread(()-> {
                if(isDestroyed())return;
                status.setText(result?"Modo salvo: "+AiPreferences.label(this):
                    "Não foi possível salvar o modo.");
                save.setEnabled(true);
            });
        });
    }
    private void saveGeminiModel() {
        if(busy||measuring)return;
        String model=geminiModelSelector.getSelectedItemPosition()==1?
            GeminiClient.BACKUP_MODEL:GeminiClient.MODEL;
        if(AiPreferences.geminiModel(this,model))
            geminiTestState.setText("Modelo selecionado: "+model+
                ". Esse ajuste vale apenas para testes. A revisão sempre tenta 3.8, 3.6, OpenRouter e Groq.");
        else geminiTestState.setText("Não foi possível salvar o modelo Gemini.");
    }
    private void testGemini() {
        if(busy||measuring||!geminiTest.isEnabled())return;
        if(!SecretStore.hasGeminiKey(this)) {
            geminiTestState.setText("Salve sua chave Gemini antes de testar.");
            return;
        }
        // Test exactly what is currently selected. Do not silently reuse the prior model.
        final String model=geminiModelSelector.getSelectedItemPosition()==1?
            GeminiClient.BACKUP_MODEL:GeminiClient.MODEL;
        if(!AiPreferences.geminiModel(this,model)) {
            geminiTestState.setText("Não foi possível salvar o modelo selecionado.");
            return;
        }
        geminiTest.setEnabled(false);
        geminiProbe.setEnabled(false);
        geminiModelSave.setEnabled(false);
        geminiModelSelector.setEnabled(false);
        geminiTestState.setText("Verificando acesso à API do Google para "+model+"…");
        worker.execute(()-> {
            String message;
            boolean modelAccessConfirmed=false;
            long started=SystemClock.elapsedRealtime();
            try {
                String key=SecretStore.readGemini(this);
                java.util.function.BooleanSupplier interrupted=
                    ()->Thread.currentThread().isInterrupted();
                GeminiClient.verifyAccess(model,key,interrupted);
                modelAccessConfirmed=true;
                runOnUiThread(()-> {
                    if(!isDestroyed())geminiTestState.setText(
                        "API do Google respondeu para "+model+
                        ". Testando geração de uma frase fictícia…");
                });
                GeminiClient client=new GeminiClient(model,key,interrupted);
                InferenceResult answer=client.generate(
                    "Corrija apenas ortografia e pontuação: Eu nao sei.",96,s->{});
                if(answer.text==null||answer.text.trim().isEmpty())
                    throw new java.io.IOException("Resposta vazia.");
                long elapsed=Math.max(1,(SystemClock.elapsedRealtime()-started)/1000);
                message="Gemini FUNCIONOU: "+model+" respondeu em "+elapsed+
                    " s. A chave e a geração de texto estão funcionando. "+
                    "Tempos: "+client.timing()+". Ordem automática 3.8 → 3.6 → Groq fixa. Sem Groq neste teste.";
            }
            catch(Exception error) {
                String reason=GeminiClient.diagnostic(error);
                message=(modelAccessConfirmed?
                    "A API do Google respondeu, mas a geração falhou: ":
                    "Não foi possível confirmar acesso à API: ")+
                    reason+". Modelo testado: "+model+
                    ". O teste NÃO acionou Groq. Nenhuma chave ou texto pessoal foi exibido.";
            }
            final String info=message;
            runOnUiThread(()-> {
                if(isDestroyed())return;
                geminiTestState.setText(info);
                geminiTest.setEnabled(!busy&&!measuring);
                geminiProbe.setEnabled(!busy&&!measuring);
                geminiModelSave.setEnabled(!busy&&!measuring);
                geminiModelSelector.setEnabled(!busy&&!measuring);
            });
        });
    }
    /** Uses only a fixed synthetic prompt; never reads any user composition. */
    private void probeGemini38() {
        if(busy||measuring||!geminiProbe.isEnabled())return;
        if(!SecretStore.hasGeminiKey(this)) {
            geminiTestState.setText("Salve sua chave Gemini antes do diagnóstico.");
            return;
        }
        geminiTest.setEnabled(false);
        geminiProbe.setEnabled(false);
        geminiModelSave.setEnabled(false);
        geminiModelSelector.setEnabled(false);
        geminiTestState.setText("Diagnóstico mínimo: Gemini 3.8, texto fictício curto, sem Groq…");
        worker.execute(()-> {
            String message;
            try {
                String key=SecretStore.readGemini(this);
                java.util.function.BooleanSupplier interrupted=
                    ()->Thread.currentThread().isInterrupted();
                GeminiClient client=new GeminiClient(GeminiClient.MODEL,key,interrupted);
                InferenceResult response=client.generateMinimalProbe();
                if(response.text==null||response.text.trim().isEmpty())
                    throw new java.io.IOException("Resposta vazia");
                message="Gemini 3.8 GEROU texto no teste mínimo. "+
                    client.timing()+". Sem acesso a textos pessoais e sem Groq.";
            }
            catch(Exception error) {
                message="Gemini 3.8 não concluiu o teste mínimo: "+
                    GeminiClient.diagnostic(error)+
                    ". Sem Groq e sem conteúdo pessoal. O teste não confirma a causa externa.";
            }
            final String result=message;
            runOnUiThread(()-> {
                if(isDestroyed())return;
                geminiTestState.setText(result);
                geminiTest.setEnabled(!busy&&!measuring);
                geminiProbe.setEnabled(!busy&&!measuring);
                geminiModelSave.setEnabled(!busy&&!measuring);
                geminiModelSelector.setEnabled(!busy&&!measuring);
            });
        });
    }

    private void saveGeminiKey() {
        if(busy||measuring)return;
        String secret=geminiKey.getText().toString().trim();
        if(secret.isEmpty()) {
            geminiKeyState.setText("Cole sua chave Gemini antes de salvar.");
            return;
        }
        geminiSave.setEnabled(false);
        geminiKeyState.setText("Salvando chave Gemini no aparelho…");
        worker.execute(()-> {
            boolean stored=false;
            try {
                SecretStore.saveGemini(this,secret);
                stored=secret.equals(SecretStore.readGemini(this));
            } catch(Exception ignored){}
            final boolean success=stored;
            runOnUiThread(()-> {
                if(isDestroyed())return;
                if(success) {
                    geminiKey.setText("");
                    geminiKeyState.setText("Chave Gemini salva e protegida no aparelho.");
                    status.setText("Gemini configurado. Para usar online, selecione Automático e toque em Salvar modo de IA.");
                } else geminiKeyState.setText("Não foi possível salvar a chave Gemini. Confira a chave e tente novamente.");
                geminiSave.setEnabled(true);
            });
        });
    }
    private void saveGroqKey() {
        if(busy||measuring)return;
        String secret=key.getText().toString().trim();
        if(secret.isEmpty()) {
            keyState.setText("Cole uma chave Groq para salvar.");
            return;
        }
        groqSave.setEnabled(false);
        keyState.setText("Salvando chave Groq no aparelho…");
        worker.execute(()-> {
            boolean stored=false;
            try {
                SecretStore.save(this,secret);
                stored=secret.equals(SecretStore.read(this));
            } catch(Exception ignored){}
            final boolean success=stored;
            runOnUiThread(()-> {
                if(isDestroyed())return;
                if(success) {
                    key.setText("");
                    keyState.setText("Chave Groq salva e protegida no aparelho.");
                    status.setText("Groq configurada como reserva online.");
                } else keyState.setText("Não foi possível salvar a chave Groq. Confira a chave e tente novamente.");
                groqSave.setEnabled(true);
            });
        });
    }
    private String chosenRouterModel() {
        return routerModelSelector.getSelectedItemPosition()==1?
            OpenRouterClient.QWEN:OpenRouterClient.FREE;
    }
    private void saveOpenRouterModel() {
        if(busy||measuring)return;
        status.setText(AiPreferences.openRouterModel(this,chosenRouterModel())?
            "Modelo OpenRouter escolhido: "+chosenRouterModel():
            "Não foi possível salvar o modelo OpenRouter.");
    }
    private void saveOpenRouterKey() {
        if(busy||measuring)return;
        String secret=routerKey.getText().toString().trim();
        if(secret.isEmpty()) {
            routerKeyState.setText("Cole sua chave OpenRouter antes de salvar.");
            return;
        }
        routerSave.setEnabled(false);
        worker.execute(()-> {
            boolean success=false;
            try {
                SecretStore.saveOpenRouter(this,secret);
                success=secret.equals(SecretStore.readOpenRouter(this));
            }catch(Exception ignored){}
            final boolean stored=success;
            runOnUiThread(()-> {
                if(isDestroyed())return;
                routerKey.setText("");
                routerKeyState.setText(stored?
                    "Chave OpenRouter salva e protegida no aparelho.":
                    "Não foi possível salvar a chave OpenRouter.");
                if(stored)status.setText("OpenRouter pronto. Groq preservado para reserva seguinte.");
                routerSave.setEnabled(true);
            });
        });
    }
    private void testOpenRouter() {
        if(busy||measuring||!routerTest.isEnabled())return;
        if(!SecretStore.hasOpenRouterKey(this)) {
            status.setText("Salve a chave OpenRouter antes de testar.");
            return;
        }
        final String model=chosenRouterModel();
        if(!AiPreferences.openRouterModel(this,model)) {
            status.setText("Não foi possível salvar o modelo do teste.");
            return;
        }
        routerTest.setEnabled(false);
        routerModelSave.setEnabled(false);
        routerModelSelector.setEnabled(false);
        status.setText("Testando OpenRouter com frase fictícia…");
        worker.execute(()-> {
            String output;
            try {
                String secret=SecretStore.readOpenRouter(this);
                InferenceResult response=new OpenRouterClient(secret,model,
                    ()->Thread.currentThread().isInterrupted())
                    .generate("Corrija apenas: Eu nao sei.",80,s->{});
                output="OpenRouter respondeu: "+response.backend+
                    ". Teste fictício concluído sem acionar Gemini ou Groq.";
            }
            catch(Exception e) {
                output="OpenRouter não concluiu: "+OpenRouterClient.diagnostic(e)+
                    ". Nenhuma chave ou texto pessoal foi exibido.";
            }
            final String info=output;
            runOnUiThread(()-> {
                if(isDestroyed())return;
                status.setText(info);
                routerTest.setEnabled(!busy&&!measuring);
                routerModelSave.setEnabled(!busy&&!measuring);
                routerModelSelector.setEnabled(!busy&&!measuring);
            });
        });
    }
    private void open(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));
        }
        catch(ActivityNotFoundException e) {
            status.setText("Abra o link em um navegador.");
        }
    }
    private void controls(boolean importing) {
        busy=importing;
        add.setEnabled(!busy&&!measuring);
        remove.setEnabled(!busy&&!measuring&&ModelStore.model(this).isFile());
        cancel.setEnabled(busy);
        save.setEnabled(!busy&&!measuring);
        geminiSave.setEnabled(!busy&&!measuring);
        groqSave.setEnabled(!busy&&!measuring);
        routerSave.setEnabled(!busy&&!measuring);
        routerTest.setEnabled(!busy&&!measuring);
        routerModelSave.setEnabled(!busy&&!measuring);
        routerModelSelector.setEnabled(!busy&&!measuring);
        geminiTest.setEnabled(!busy&&!measuring);
        geminiProbe.setEnabled(!busy&&!measuring);
        geminiModelSave.setEnabled(!busy&&!measuring);
        geminiModelSelector.setEnabled(!busy&&!measuring);
        measure.setEnabled(!busy&&!measuring);
        cancelMeasure.setEnabled(measuring);
    }
    private final ServiceConnection connection=new ServiceConnection() {
        public void onServiceConnected(ComponentName name,IBinder binder) {
            engine=new Messenger(binder);
            if(measuring&&!isDestroyed())sendMeasurement();
        }
        public void onServiceDisconnected(ComponentName name) {
            engine=null;
            if(measuring) {
                measuring=false;
                controls(busy);
                status.setText("Medição interrompida.");
            }
        }
    };
    private void startMeasurement() {
        if(busy||measuring)return;
        measuring=true;
        controls(false);
        status.setText("Preparando medição…");
        bound=bindService(new Intent(this,WritingEngineService.class),connection,BIND_AUTO_CREATE);
        if(!bound) {
            measuring=false;
            controls(false);
            status.setText("Não foi possível iniciar o motor.");
        }
    }
    private void sendMeasurement() {
        ResultReceiver receiver=new ResultReceiver(new Handler(Looper.getMainLooper())) {
            protected void onReceiveResult(int code,Bundle data) {
                if(!measuring||isDestroyed())return;
                if(code==4)status.setText(data.getString("text"));
                else if(code==2) {
                    MeasurementHistory.add(ModelSettingsActivity.this,data);
                    metrics.setText(MeasurementHistory.describe(ModelSettingsActivity.this));
                    status.setText("Medição concluída. Isso não é uma avaliação automática da qualidade da resposta.");
                    stopMeasurement();
                }
                else if(code==3) {
                    status.setText(data.getString("text"));
                    stopMeasurement();
                }
            }
        };
        Parcel parcel=Parcel.obtain();
        try {
            receiver.writeToParcel(parcel,0);
            parcel.setDataPosition(0);
            Bundle data=new Bundle();
            data.putParcelable("receiver",ResultReceiver.CREATOR.createFromParcel(parcel));
            data.putString("mode",AiPreferences.mode(this));
            data.putString("prompt",PromptBuilder.build("Ortografia e gramática","","","Eu nao sei o que fazer hoje."));
            data.putInt("limit",128);
            data.putBoolean("benchmark",true);
            Message message=Message.obtain(null,1);
            message.setData(data);
            engine.send(message);
        }
        catch(Exception e) {
            status.setText("Não foi possível iniciar a medição.");
            stopMeasurement();
        }
        finally {
            parcel.recycle();
        }
    }
    private void stopMeasurement() {
        measuring=false;
        if(engine!=null)try {
            engine.send(Message.obtain(null,2));
        }
        catch(RemoteException ignored) {
        }
        if(bound) {
            try {
                unbindService(connection);
            }
            catch(IllegalArgumentException ignored) {
            }
            bound=false;
        }
        engine=null;
        if(add!=null&&measure!=null)controls(busy);
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request!=41||result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        cancelled=false;
        controls(true);
        status.setText("Importando…");
        worker.execute(()-> {
            try {
                ModelStore.importFile(this,uri,(n,total)-> {
                    runOnUiThread(()-> {
                        if(!isDestroyed())status.setText("Importando: "+n/1048576+" MB"+(total>0?" de "+total/1048576+" MB":""));
                    });
                    return !cancelled;
                });
                runOnUiThread(()-> {
                    if(!isDestroyed()) {
                        description.setText(ModelStore.description(this));
                        status.setText("Modelo pronto para usar no painel.");
                        controls(false);
                    }
                });
            }
            catch(Exception e) {
                runOnUiThread(()-> {
                    if(!isDestroyed()) {
                        status.setText("Não foi possível importar: "+e.getMessage());
                        controls(false);
                    }
                });
            }
        });
    }
    @Override protected void onDestroy() {
        cancelled=true;
        stopMeasurement();
        worker.shutdownNow();
        super.onDestroy();
    }
}

