// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CancellationException;
import java.util.function.*;
import org.json.*;

/** Single explicit, bounded Gemini request; no streaming, redirects, telemetry or hidden retries. */
public final class GeminiClient implements InferenceRouter.Backend {
    public static final String MODEL="gemini-3.8-flash";
    public static final String BACKUP_MODEL="gemini-3.6-flash";
    private static final String ENDPOINT="https://generativelanguage.googleapis.com/v1beta/models/"+MODEL+":generateContent";
    private static final String BACKUP_ENDPOINT="https://generativelanguage.googleapis.com/v1beta/models/"+BACKUP_MODEL+":generateContent";
    private final String endpoint,key,model;
    private final BooleanSupplier cancelled;
    private final int timeout;
    private volatile HttpURLConnection connection;
    private volatile String lastTiming="Sem solicitação anterior.";

    /** Timings only: never stores prompt, API key, response body or headers. */
    private static final class TransportTiming {
        String stage="preparação";
        long connectMs=-1,sendMs=-1,waitMs=-1,readMs=-1;
        int httpStatus=-1;
        String summary() {
            return "etapa="+stage+"; conexão/TLS="+ms(connectMs)+
                "; envio="+ms(sendMs)+"; espera HTTP="+ms(waitMs)+
                "; leitura="+ms(readMs)+
                (httpStatus<0?"":"; HTTP "+httpStatus);
        }
        private String ms(long n) {return n<0?"não iniciada":n+" ms";}
    }
    /** Safe, typed failure with transport timings. Does not include raw HTTP response data. */
    public static final class TracedFailure extends IOException {
        final Exception original;
        final String timing;
        TracedFailure(Exception original,String timing) {
            super("Falha no transporte Gemini.");
            this.original=original;
            this.timing=timing;
        }
    }
    private static long elapsedMs(long started) {
        return Math.max(0L,(System.nanoTime()-started)/1_000_000L);
    }
    public String timing() {return lastTiming;}
    public static int responseTimeoutMs(String model,int promptLength,int configuredTimeout) {
        return MODEL.equals(model)&&promptLength<=600?
            Math.min(15000,configuredTimeout):configuredTimeout;
    }


    public static final class Failure extends IOException {
        public final int status;
        Failure(int code) {
            super(code==429?"Cota do Gemini atingida.":code==401||code==403?
                "Chave ou acesso ao Gemini inválido.":code==404?
                "Modelo Gemini indisponível nesta conta.":"Serviço Gemini indisponível ("+code+").");
            status=code;
        }
    }
    public GeminiClient(String key,BooleanSupplier cancelled) {
        this(MODEL,key,cancelled);
    }
    /** Only fixed and officially documented model IDs may be requested. */
    public GeminiClient(String model,String key,BooleanSupplier cancelled) {
        this(BACKUP_MODEL.equals(model)?BACKUP_ENDPOINT:ENDPOINT,key,cancelled,
            BACKUP_MODEL.equals(model)?45000:60000);
        if(!MODEL.equals(model)&&!BACKUP_MODEL.equals(model))
            throw new IllegalArgumentException("Modelo Gemini não permitido.");
    }
    public static boolean tryAlternate(Exception error) {
        if(error instanceof TracedFailure)error=((TracedFailure)error).original;
        if(error instanceof Failure) {
            int status=((Failure)error).status;
            return status==404||status==429||status==500||status==502||status==503||status==504;
        }
        return error instanceof java.net.SocketTimeoutException ||
            error instanceof java.net.ConnectException;
    }
    /** Safe, bounded diagnostic: never includes the key, prompt or API response body. */
    public static String diagnostic(Exception error) {
        if(error instanceof TracedFailure) {
            TracedFailure d=(TracedFailure)error;
            return diagnostic(d.original)+"; "+d.timing;
        }
        if(error instanceof Failure) {
            int status=((Failure)error).status;
            switch(status) {
                case 400:return "HTTP 400 (requisição rejeitada)";
                case 401:case 403:return "HTTP "+status+" (chave ou permissão)";
                case 404:return "HTTP 404 (modelo não disponível para a chave)";
                case 429:return "HTTP 429 (cota ou limite de requisições)";
                case 500:case 502:case 503:case 504:return "HTTP "+status+" (servidor sobrecarregado)";
                default:return "HTTP "+status+" (erro do serviço)";
            }
        }
        if(error instanceof java.net.SocketTimeoutException) {
            String detail=error.getMessage();
            return detail!=null&&detail.toLowerCase(java.util.Locale.ROOT).contains("connect")?
                "tempo limite ao conectar":"tempo limite aguardando resposta";
        }
        if(error instanceof java.net.UnknownHostException) return "DNS indisponível";
        if(error instanceof java.net.ConnectException) return "conexão indisponível";
        if(error instanceof org.json.JSONException) return "resposta JSON inesperada";
        if(error instanceof java.io.IOException) return "conexão ou resposta inválida";
        return "falha de processamento";
    }
    /** Checks the Google models.get endpoint, not text generation. No prompt is transmitted. */
    public static void verifyAccess(String selected,String credential,
                                    BooleanSupplier cancelled)throws Exception {
        InferenceRouter.check(cancelled);
        if(!MODEL.equals(selected)&&!BACKUP_MODEL.equals(selected))
            throw new IllegalArgumentException("Modelo Gemini não permitido.");
        HttpURLConnection http=(HttpURLConnection)new URL(
            "https://generativelanguage.googleapis.com/v1beta/models/"+selected)
            .openConnection();
        try {
            http.setRequestMethod("GET");
            http.setInstanceFollowRedirects(false);
            http.setConnectTimeout(8000);
            http.setReadTimeout(12000);
            http.setRequestProperty("Accept","application/json");
            http.setRequestProperty("x-goog-api-key",credential);
            int status=http.getResponseCode();
            InferenceRouter.check(cancelled);
            if(status!=200)throw new Failure(status);
        }
        finally {http.disconnect();}
    }
    /** Controlled loopback is reserved for synthetic transport tests with a fake key. */
    public GeminiClient(String endpoint,String key,BooleanSupplier cancelled,int timeout) {
        if(!ENDPOINT.equals(endpoint)&&!BACKUP_ENDPOINT.equals(endpoint)) {
            try {
                URL uri=new URL(endpoint);
                if(!"http".equals(uri.getProtocol())||!"127.0.0.1".equals(uri.getHost())||
                    uri.getUserInfo()!=null||uri.getPort()<1||uri.getQuery()!=null||uri.getRef()!=null)
                    throw new IllegalArgumentException("Endpoint não permitido.");
            }
            catch(MalformedURLException e){throw new IllegalArgumentException("Endpoint inválido.");}
        }
        this.endpoint=endpoint;
        this.model=BACKUP_ENDPOINT.equals(endpoint)?BACKUP_MODEL:MODEL;
        this.key=key;
        this.cancelled=cancelled;
        this.timeout=timeout;
    }
    public void cancel() {
        HttpURLConnection active=connection;
        if(active!=null)active.disconnect();
    }
    @Override public InferenceResult generate(String prompt,int limit,Consumer<String> partial)throws Exception {
        return generateInternal(prompt,limit,partial,false);
    }
    /** Very small synthetic text request: no user content and no system instruction. */
    public InferenceResult generateMinimalProbe() throws Exception {
        return generateInternal("Responda apenas OK.",32,s->{},true);
    }
    private InferenceResult generateInternal(String prompt,int limit,Consumer<String> partial,
                                             boolean probe)throws Exception {
        InferenceRouter.check(cancelled);
        if(prompt==null||prompt.length()>12000)throw new IOException("Texto muito longo para a revisão.");
        HttpURLConnection http=(HttpURLConnection)new URL(endpoint).openConnection();
        connection=http;
        TransportTiming timing=new TransportTiming();
        try {
            http.setInstanceFollowRedirects(false);
            http.setRequestMethod("POST");
            http.setConnectTimeout(Math.min(timeout,8000));
            http.setReadTimeout(responseTimeoutMs(model,prompt.length(),timeout));
            http.setDoOutput(true);
            http.setRequestProperty("Content-Type","application/json; charset=utf-8");
            http.setRequestProperty("Accept","application/json");
            http.setRequestProperty("x-goog-api-key",key);
            JSONObject instruction=new JSONObject().put("parts",new JSONArray().put(new JSONObject()
                .put("text","Você é um revisor e assistente de escrita em português brasileiro. "
                    +"Cumpra estritamente a tarefa solicitada. Não acrescente informações, "
                    +"não altere fatos, nomes, números, negações ou o tom sem solicitação. "
                    +"Retorne somente o texto final, sem comentários, explicações ou raciocínio.")));
            JSONObject body=new JSONObject()
                .put("contents",new JSONArray().put(new JSONObject().put("role","user")
                    .put("parts",new JSONArray().put(new JSONObject().put("text",prompt)))))
                .put("generationConfig",new JSONObject()
                    .put("maxOutputTokens",probe?256:Math.max(512,Math.min(4096,limit*8)))
                    .put("thinkingConfig",new JSONObject().put("thinkingLevel","low")));
            // Probe isolates basic text generation from the larger revision instruction.
            if(!probe)body.put("systemInstruction",instruction);
            byte[] request=body.toString().getBytes(StandardCharsets.UTF_8);
            if(request.length>40000)throw new IOException("Pedido online muito grande.");
            http.setFixedLengthStreamingMode(request.length);
            long mark=System.nanoTime();
            timing.stage="conectando";
            OutputStream stream=http.getOutputStream();
            timing.connectMs=elapsedMs(mark);
            mark=System.nanoTime();
            timing.stage="enviando";
            try(OutputStream out=stream) {
                InferenceRouter.check(cancelled);
                out.write(request);
            }
            timing.sendMs=elapsedMs(mark);
            InferenceRouter.check(cancelled);
            mark=System.nanoTime();
            timing.stage="aguardando HTTP";
            int status=http.getResponseCode();
            timing.waitMs=elapsedMs(mark);
            timing.httpStatus=status;
            if(status!=200)throw new Failure(status);
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            mark=System.nanoTime();
            timing.stage="lendo resposta";
            try(InputStream in=http.getInputStream()) {
                byte[] buffer=new byte[4096];int n;
                while((n=in.read(buffer))!=-1) {
                    InferenceRouter.check(cancelled);
                    if(bytes.size()+n>131072)throw new IOException("Resposta online muito grande.");
                    bytes.write(buffer,0,n);
                }
            }
            timing.readMs=elapsedMs(mark);
            timing.stage="validando resposta";
            JSONObject reply=new JSONObject(new String(bytes.toByteArray(),StandardCharsets.UTF_8));
            JSONArray choices=reply.optJSONArray("candidates");
            if(choices==null||choices.length()==0)throw new IOException("Gemini não retornou resposta.");
            JSONObject candidate=choices.optJSONObject(0);
            if(candidate==null)throw new IOException("Resposta inválida.");
            String finish=candidate.optString("finishReason","");
            if(!finish.isEmpty()&&!"STOP".equals(finish)&&!"MAX_TOKENS".equals(finish))
                throw new IOException("Gemini bloqueou ou interrompeu a resposta.");
            JSONObject content=candidate.optJSONObject("content");
            JSONArray parts=content==null?null:content.optJSONArray("parts");
            if(parts==null||parts.length()==0)throw new IOException("Resposta online vazia.");
            StringBuilder output=new StringBuilder();
            for(int i=0;i<parts.length();i++) {
                JSONObject item=parts.optJSONObject(i);
                if(item==null||item.optBoolean("thought",false))continue;
                String value=item.optString("text","");
                if(value.length()>12000||output.length()+value.length()>12000)
                    throw new IOException("Resposta online muito longa.");
                output.append(value);
            }
            String result=output.toString().trim();
            if(result.isEmpty()||result.contains("<think>")||result.contains("<|im_end|>"))
                throw new IOException("Resposta online inválida.");
            InferenceRouter.check(cancelled);
            timing.stage="concluído";
            return new InferenceResult(result,"Gemini · "+model,"MAX_TOKENS".equals(finish));
        }
        catch(IOException e) {
            if(cancelled.getAsBoolean())throw new CancellationException("Cancelado.");
            throw new TracedFailure(e,timing.summary());
        }
        catch(JSONException e) {
            if(cancelled.getAsBoolean())throw new CancellationException("Cancelado.");
            throw new TracedFailure(e,timing.summary());
        }
        finally {
            lastTiming=timing.summary();
            connection=null;
            http.disconnect();
        }
    }
}

