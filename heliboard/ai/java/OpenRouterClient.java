// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CancellationException;
import java.util.function.*;
import org.json.*;

/**
 * OpenRouter chat-completions transport. Runs only when an explicit writing
 * action requests online inference. No telemetry, hidden retry or redirects.
 * Never logs credentials, user prompts or raw HTTP errors.
 */
public final class OpenRouterClient implements InferenceRouter.Backend {
    public static final String FREE="openrouter/free";
    public static final String QWEN="qwen/qwen3-32b:free";
    private static final String ENDPOINT="https://openrouter.ai/api/v1/chat/completions";
    private final String endpoint,key,model;
    private final BooleanSupplier cancelled;
    private final int timeoutMs;
    private volatile HttpURLConnection connection;

    public static final class Failure extends IOException {
        public final int status;
        Failure(int code) {
            super(code==429?"Cota gratuita do OpenRouter atingida.":
                code==401||code==403?"Chave ou acesso ao OpenRouter inválido.":
                code==404?"Modelo indisponível no OpenRouter.":
                "OpenRouter indisponível (HTTP "+code+").");
            status=code;
        }
    }

    public OpenRouterClient(String key,String model,BooleanSupplier cancelled) {
        this(ENDPOINT,key,model,cancelled,22000);
    }
    /** Synthetic transport tests may use only localhost with fake keys. */
    public OpenRouterClient(String endpoint,String key,String model,
                            BooleanSupplier cancelled,int timeoutMs) {
        if(!FREE.equals(model)&&!QWEN.equals(model))
            throw new IllegalArgumentException("Modelo OpenRouter não permitido.");
        if(!ENDPOINT.equals(endpoint)) {
            try {
                URL url=new URL(endpoint);
                if(!"http".equals(url.getProtocol())||!"127.0.0.1".equals(url.getHost())||
                    url.getPort()<1||url.getUserInfo()!=null||url.getRef()!=null||
                    url.getQuery()!=null)
                    throw new IllegalArgumentException("Endpoint não permitido.");
            }
            catch(MalformedURLException e) {
                throw new IllegalArgumentException("Endpoint OpenRouter inválido.");
            }
        }
        this.endpoint=endpoint;
        this.key=key;
        this.model=model;
        this.cancelled=cancelled;
        this.timeoutMs=timeoutMs;
    }
    public void cancel() {
        HttpURLConnection active=connection;
        if(active!=null)active.disconnect();
    }
    public static String diagnostic(Exception error) {
        if(error instanceof Failure) {
            int status=((Failure)error).status;
            if(status==429)return "HTTP 429 (cota gratuita)";
            if(status==401||status==403)return "HTTP "+status+" (chave ou permissão)";
            if(status==404)return "HTTP 404 (modelo sem disponibilidade)";
            return "HTTP "+status+" (serviço)";
        }
        if(error instanceof SocketTimeoutException)return "tempo limite de resposta";
        if(error instanceof UnknownHostException)return "DNS indisponível";
        if(error instanceof ConnectException)return "conexão indisponível";
        return "falha de conexão ou resposta";
    }
    @Override public InferenceResult generate(String prompt,int limit,
                                                Consumer<String> partial)throws Exception {
        InferenceRouter.check(cancelled);
        if(prompt==null||prompt.length()>12000)throw new IOException("Texto muito longo.");
        HttpURLConnection http=(HttpURLConnection)new URL(endpoint).openConnection();
        connection=http;
        try {
            http.setRequestMethod("POST");
            http.setInstanceFollowRedirects(false);
            http.setConnectTimeout(8000);
            http.setReadTimeout(timeoutMs);
            http.setDoOutput(true);
            http.setRequestProperty("Content-Type","application/json; charset=utf-8");
            http.setRequestProperty("Accept","application/json");
            http.setRequestProperty("Authorization","Bearer "+key);
            JSONObject body=new JSONObject()
                .put("model",model)
                .put("stream",false)
                .put("max_tokens",Math.max(256,Math.min(2048,limit*6)))
                .put("messages",new JSONArray()
                    .put(new JSONObject().put("role","system").put("content",
                        "Você é um assistente de escrita em português brasileiro. "+
                        "Siga apenas a tarefa solicitada, preserve fatos, nomes, "+
                        "números, sentido e tom; retorne somente o texto final."))
                    .put(new JSONObject().put("role","user").put("content",prompt)));
            byte[] request=body.toString().getBytes(StandardCharsets.UTF_8);
            if(request.length>40000)throw new IOException("Pedido grande demais.");
            http.setFixedLengthStreamingMode(request.length);
            try(OutputStream out=http.getOutputStream()) {
                InferenceRouter.check(cancelled);
                out.write(request);
            }
            InferenceRouter.check(cancelled);
            int status=http.getResponseCode();
            if(status!=200)throw new Failure(status);
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(InputStream in=http.getInputStream()) {
                byte[] buffer=new byte[4096];
                int n;
                while((n=in.read(buffer))!=-1) {
                    InferenceRouter.check(cancelled);
                    if(bytes.size()+n>131072)throw new IOException("Resposta muito grande.");
                    bytes.write(buffer,0,n);
                }
            }
            JSONObject result=new JSONObject(new String(
                bytes.toByteArray(),StandardCharsets.UTF_8));
            JSONArray choices=result.optJSONArray("choices");
            JSONObject first=choices==null?null:choices.optJSONObject(0);
            JSONObject message=first==null?null:first.optJSONObject("message");
            String output=message==null?"":message.optString("content","").trim();
            if(output.isEmpty()||output.length()>12000||
                output.contains("<think>")||output.contains("<|im_end|>"))
                throw new IOException("OpenRouter não retornou texto válido.");
            InferenceRouter.check(cancelled);
            String used=result.optString("model",model);
            if(used.length()>90||!used.matches("[a-zA-Z0-9._:/-]+"))used=model;
            return new InferenceResult(output,"OpenRouter · "+used,
                first!=null&&"length".equals(first.optString("finish_reason","")));
        }
        catch(IOException e) {
            if(cancelled.getAsBoolean())throw new CancellationException("Cancelado.");
            if(e instanceof Failure||e instanceof SocketTimeoutException||
                e instanceof UnknownHostException||e instanceof ConnectException)throw e;
            throw new IOException("Conexão ou resposta do OpenRouter inválida.");
        }
        finally {
            connection=null;
            http.disconnect();
        }
    }
}
