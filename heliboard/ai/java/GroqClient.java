// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CancellationException;
import java.util.function.*;
import org.json.*;
/** Bounded, single-request HTTPS transport. No redirects, tools, retries or telemetry. */
public final class GroqClient implements InferenceRouter.Backend {
    public static final String MODEL="openai/gpt-oss-20b";
    private static final String ENDPOINT="https://api.groq.com/openai/v1/chat/completions";
    private final String endpoint,key;
    private final BooleanSupplier cancelled;
    private final int timeout;
    private volatile HttpURLConnection connection;
    public static final class Failure extends IOException {
        public final int status;
        Failure(int code) {
            super(code==429?"Cota gratuita da Groq atingida.":code==401||code==403?"Chave ou acesso à Groq inválido.":"Serviço online indisponível ("+code+").");
            status=code;
        }
    }
    public GroqClient(String key,BooleanSupplier cancelled) {
        this(ENDPOINT,key,cancelled,20000);
    }
    // An explicit constructor lets the debug transport tests use a loopback server with a fake key.
    public GroqClient(String endpoint,String key,BooleanSupplier cancelled,int timeout) {
        if(!ENDPOINT.equals(endpoint)) {
            try {
                URL url=new URL(endpoint);
                if(!"http".equals(url.getProtocol())||!"127.0.0.1".equals(url.getHost())||url.getUserInfo()!=null)throw new IllegalArgumentException("Endpoint não permitido.");
            }
            catch(MalformedURLException e) {
                throw new IllegalArgumentException("Endpoint inválido.");
            }
        }
        this.endpoint=endpoint;
        this.key=key;
        this.cancelled=cancelled;
        this.timeout=timeout;
    }
    public void cancel() {
        HttpURLConnection active=connection;
        if(active!=null)active.disconnect();
    }
    @Override public InferenceResult generate(String prompt,int limit,Consumer<String> partial)throws Exception {
        InferenceRouter.check(cancelled);
        HttpURLConnection http=(HttpURLConnection)new URL(endpoint).openConnection();
        connection=http;
        try {
            http.setInstanceFollowRedirects(false);
            http.setRequestMethod("POST");
            http.setConnectTimeout(Math.min(timeout,8000));
            http.setReadTimeout(timeout);
            http.setDoOutput(true);
            http.setRequestProperty("Content-Type","application/json; charset=utf-8");
            http.setRequestProperty("Authorization","Bearer "+key);
            JSONArray messages=new JSONArray().put(new JSONObject().put("role","system").put("content","Você é um revisor e assistente de escrita em português brasileiro. Cumpra a tarefa solicitada, preserve fatos, nomes, números, negações e sentido. Devolva somente o texto final, sem raciocínio nem comentários.")).put(new JSONObject().put("role","user").put("content",prompt));
            byte[] request=new JSONObject().put("model",MODEL).put("messages",messages).put("max_completion_tokens",1024).put("reasoning_effort","low").put("include_reasoning",false).put("stream",false).toString().getBytes(StandardCharsets.UTF_8);
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
                    if(bytes.size()+n>131072)throw new IOException("Resposta online muito grande.");
                    bytes.write(buffer,0,n);
                }
            }
            JSONObject choice=new JSONObject(new String(bytes.toByteArray(),StandardCharsets.UTF_8)).getJSONArray("choices").getJSONObject(0);
            if(choice.getJSONObject("message").isNull("content"))throw new IOException("Resposta vazia.");
            String text=choice.getJSONObject("message").optString("content","").trim();
            if(text.isEmpty()||text.length()>12000||text.contains("<think>")||text.contains("<|im_end|>"))throw new IOException("Resposta online inválida. Tente uma tarefa menor.");
            InferenceRouter.check(cancelled);
            return new InferenceResult(text,"Groq · "+MODEL,"length".equals(choice.optString("finish_reason")));
        }
        catch(IOException e) {
            if(cancelled.getAsBoolean())throw new CancellationException("Cancelado.");
            if(e instanceof Failure)throw e;
            throw new IOException("Não foi possível concluir a conexão com a Groq.");
        }
        finally {
            connection=null;
            http.disconnect();
        }
    }
}

