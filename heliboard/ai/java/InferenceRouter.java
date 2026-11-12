// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;
import java.util.concurrent.CancellationException;
import java.util.function.*;
/** Pure routing policy: only explicit assistant requests enter this component. */
public final class InferenceRouter {
    public interface Backend {
        InferenceResult generate(String prompt,int limit,Consumer<String> partial)throws Exception;
    }
    public static InferenceResult run(String mode,boolean connected,boolean key,boolean localModel,String prompt,int limit,Backend online,Backend offline,BooleanSupplier cancelled,Consumer<String> status,Consumer<String> partial)throws Exception {
        check(cancelled);
        if(!AiPreferences.OFFLINE.equals(mode)&&connected&&key) {
            status.accept("Processando online…");
            try {
                InferenceResult result=online.generate(prompt,limit,partial);
                check(cancelled);
                return result;
            }
            catch(Exception error) {
                check(cancelled);
                if(AiPreferences.ONLINE.equals(mode))throw error;
                status.accept(reason(error)+" Usando o modelo do aparelho…");
            }
        }
        else if(AiPreferences.ONLINE.equals(mode))throw new IllegalStateException(!key?"Cadastre a chave do Gemini ou da Groq nas configurações.":"Sem acesso à internet. Selecione Automático ou Offline.");
        else status.accept(AiPreferences.AUTO.equals(mode)?(!key?"IA online não configurada. Usando o aparelho…":"Sem internet. Usando o aparelho…"):"Processando no aparelho…");
        check(cancelled);
        if(!localModel)throw new IllegalStateException("Importe um modelo GGUF para usar o modo offline ou o retorno automático.");
        InferenceResult result=offline.generate(prompt,limit,partial);
        check(cancelled);
        return result;
    }
    public static void check(BooleanSupplier cancelled) {
        if(cancelled.getAsBoolean())throw new CancellationException("Cancelado.");
    }
    private static String reason(Exception e) {
        if(e instanceof GeminiClient.Failure) {
            switch(((GeminiClient.Failure)e).status) {
                case 429:return "Cota do Gemini atingida.";
                case 401:case 403:return "Confira a chave ou o acesso ao Gemini.";
                default:return "Gemini indisponível.";
            }
        }
        if(e instanceof GroqClient.Failure) {
            switch(((GroqClient.Failure)e).status) {
                case 429:return "Cota online atingida.";
                case 401:case 403:return "Confira a chave ou o acesso à Groq.";
                default:return "Serviço online indisponível.";
            }
        }
        return "Conexão online interrompida.";
    }
}

