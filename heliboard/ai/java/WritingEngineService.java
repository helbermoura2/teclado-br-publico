// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;
import android.app.Service;
import java.util.concurrent.atomic.AtomicLong;
import android.content.Intent;
import android.os.*;
import java.util.concurrent.*;
/** The UI stays in the IME; routing and inference stay in a private worker process. */
public final class WritingEngineService extends Service {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private volatile boolean busy,cancelled;
    private volatile GroqClient online;
    private volatile OpenRouterClient router;
    private volatile GeminiClient gemini;
    private volatile OfflineEngine offline;
    private final Messenger messenger=new Messenger(new Handler(Looper.getMainLooper()) {
        @Override public void handleMessage(Message message) {
            if(message.what==2) {
                stopGeneration();
                return;
            }
            if(message.what!=1)return;
            Bundle data=message.getData();
            data.setClassLoader(WritingEngineService.class.getClassLoader());
            ResultReceiver receiver=data.getParcelable("receiver");
            if(receiver==null)return;
            if(busy) {
                send(receiver,3,"Aguarde o cancelamento anterior terminar.",null);
                return;
            }
            busy=true;
            cancelled=false;
            String prompt=data.getString("prompt","");
            String requestedMode=data.getString("mode",AiPreferences.OFFLINE);
            final String mode=AiPreferences.AUTO.equals(requestedMode)||AiPreferences.ONLINE.equals(requestedMode)?requestedMode:AiPreferences.OFFLINE;
            int limit=Math.max(16,Math.min(256,data.getInt("limit",256)));
            boolean benchmark=data.getBoolean("benchmark",false);
            worker.execute(()-> {
                long start=SystemClock.elapsedRealtime();
                final AtomicLong peak=new AtomicLong();
                final BatteryManager battery=(BatteryManager)getSystemService(BATTERY_SERVICE);
                final long energyBefore=benchmark&&battery!=null?battery.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER):-1;
                ScheduledExecutorService sampler=benchmark?Executors.newSingleThreadScheduledExecutor():null;
                if(sampler!=null)sampler.scheduleAtFixedRate(()-> {
                    peak.accumulateAndGet(Debug.getPss(),Math::max);
                },0,300,TimeUnit.MILLISECONDS);
                try {
                    boolean key=SecretStore.hasGeminiKey(WritingEngineService.this)||
                        SecretStore.hasOpenRouterKey(WritingEngineService.this)||
                        SecretStore.hasKey(WritingEngineService.this);
                    final java.util.concurrent.atomic.AtomicReference<String> geminiIssue=
                        new java.util.concurrent.atomic.AtomicReference<>("");
                    final java.util.concurrent.atomic.AtomicReference<String> routerIssue=new java.util.concurrent.atomic.AtomicReference<>("");
                    offline=new OfflineEngine(WritingEngineService.this,()->cancelled);
                    InferenceRouter.Backend remote=(p,n,partial)-> {
                        if(SecretStore.hasGeminiKey(WritingEngineService.this)) {
                            try {
                                final String secret=SecretStore.readGemini(WritingEngineService.this);
                                return GeminiCascade.run(
                                    model -> {
                                        gemini=new GeminiClient(model,secret,()->cancelled);
                                        return gemini.generate(p,n,partial);
                                    },
                                    ()->cancelled,
                                    progress->send(receiver,4,progress,null),
                                    geminiIssue::set);
                            }
                            catch(Exception error) {
                                InferenceRouter.check(()->cancelled);
                                if(!SecretStore.hasOpenRouterKey(WritingEngineService.this)&&
                                    !SecretStore.hasKey(WritingEngineService.this))throw error;
                                send(receiver,4,"Gemini 3.8 e 3.6 não concluíram. Tentando reserva online…",null);
                            }
                            finally {gemini=null;}
                        }
                        if(SecretStore.hasOpenRouterKey(WritingEngineService.this)) {
                            try {
                                String model=AiPreferences.openRouterModel(WritingEngineService.this);
                                String secret=SecretStore.readOpenRouter(WritingEngineService.this);
                                send(receiver,4,"Processando com OpenRouter · "+model+"…",null);
                                router=new OpenRouterClient(secret,model,()->cancelled);
                                return router.generate(p,n,partial);
                            }
                            catch(Exception error) {
                                InferenceRouter.check(()->cancelled);
                                routerIssue.set(OpenRouterClient.diagnostic(error));
                                if(!SecretStore.hasKey(WritingEngineService.this))throw error;
                                send(receiver,4,"OpenRouter falhou ("+routerIssue.get()+"). Tentando Groq…",null);
                            }
                            finally {router=null;}
                        }
                        if(!SecretStore.hasKey(WritingEngineService.this))
                            throw new IllegalStateException("Cadastre Gemini, OpenRouter ou Groq.");
                        String secret;
                        try {secret=SecretStore.read(WritingEngineService.this);}
                        catch(Exception e) {throw new GroqClient.Failure(401);}
                        send(receiver,4,"Processando com Groq…",null);
                        online=new GroqClient(secret,()->cancelled);
                        return online.generate(p,n,partial);
                    };
                    InferenceResult result=InferenceRouter.run(mode,AiPreferences.connected(WritingEngineService.this),key,ModelStore.model(WritingEngineService.this).isFile(),prompt,limit,remote,offline,()->cancelled,s->send(receiver,4,s,null),s->send(receiver,1,s,null));
                    StringBuilder notes=new StringBuilder();
                    if(!geminiIssue.get().isEmpty()&&!result.backend.startsWith("Gemini"))
                        notes.append("\nGemini: ").append(geminiIssue.get());
                    if(!routerIssue.get().isEmpty()&&!result.backend.startsWith("OpenRouter"))
                        notes.append("\nOpenRouter: ").append(routerIssue.get());
                    if(notes.length()>0) result=new InferenceResult(result.text,
                        result.backend+notes.toString(),result.truncated);
                    Bundle extra=new Bundle();
                    extra.putBoolean("truncated",result.truncated);
                    extra.putString("backend",result.backend);
                    extra.putLong("duration_ms",SystemClock.elapsedRealtime()-start);
                    extra.putLong("peak_pss_kb",peak.get());
                    long energyAfter=benchmark&&battery!=null?battery.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER):-1;
                    extra.putLong("energy_nwh",energyBefore>0&&energyAfter>0&&energyBefore>=energyAfter?energyBefore-energyAfter:-1);
                    send(receiver,2,result.text,extra);
                }
                catch(Exception e) {
                    send(receiver,3,cancelled?"Cancelado.":safe(e),null);
                }
                catch(LinkageError e) {
                    send(receiver,3,"Motor offline indisponível nesta arquitetura.",null);
                }
                catch(OutOfMemoryError e) {
                    send(receiver,3,"Memória insuficiente. Experimente um modelo menor.",null);
                }
                finally {
                    if(sampler!=null)sampler.shutdownNow();
                    online=null;
                    router=null;
                    gemini=null;
                    offline=null;
                    busy=false;
                }
            });
        }
    });
    private static String safe(Exception e) {
        if(e instanceof GroqClient.Failure||e instanceof GeminiClient.Failure||
            e instanceof OpenRouterClient.Failure||
            e instanceof IllegalStateException)return e.getMessage();
        return "Não foi possível concluir a tarefa. Confira as configurações e tente novamente.";
    }
    private static void send(ResultReceiver receiver,int code,String text,Bundle extra) {
        Bundle data=extra==null?new Bundle():extra;
        data.putString("text",text);
        receiver.send(code,data);
    }
    private void stopGeneration() {
        cancelled=true;
        GroqClient a=online;
        if(a!=null)a.cancel();
        OpenRouterClient r=router;
        if(r!=null)r.cancel();
        GeminiClient g=gemini;
        if(g!=null)g.cancel();
        OfflineEngine b=offline;
        if(b!=null)b.cancel();
    }
    @Override public IBinder onBind(Intent i) {
        return messenger.getBinder();
    }
    @Override public boolean onUnbind(Intent i) {
        stopGeneration();
        return false;
    }
    @Override public void onDestroy() {
        stopGeneration();
        worker.shutdownNow();
        super.onDestroy();
    }
}

