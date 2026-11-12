// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;

import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Fixed online Gemini chain. The saved model preference is ONLY for the manual
 * connection test; real writing requests always try 3.8 then 3.6.
 * Groq is invoked by the caller after both fail, never between these attempts.
 */
public final class GeminiCascade {
    private GeminiCascade() {}
    @FunctionalInterface public interface ModelCall {
        InferenceResult generate(String model) throws Exception;
    }

    public static InferenceResult run(ModelCall call, BooleanSupplier cancelled,
                                      Consumer<String> status,
                                      Consumer<String> failure) throws Exception {
        Exception last=null;
        StringBuilder reasons=new StringBuilder();
        for (String model : new String[]{GeminiClient.MODEL,GeminiClient.BACKUP_MODEL}) {
            InferenceRouter.check(cancelled);
            status.accept("Processando com "+model+"…");
            try {
                InferenceResult output=call.generate(model);
                InferenceRouter.check(cancelled);
                if(reasons.length()==0)return output;
                return new InferenceResult(output.text,output.backend+
                    "\n"+reasons,output.truncated);
            }
            catch(Exception error) {
                if(error instanceof CancellationException)throw error;
                InferenceRouter.check(cancelled);
                if(reasons.length()>0)reasons.append("; ");
                String reason=GeminiClient.diagnostic(error);
                reasons.append(model).append(": ").append(reason);
                failure.accept(reasons.toString());
                if(GeminiClient.MODEL.equals(model))
                    status.accept("Gemini 3.8 falhou ("+reason+"). Tentando Gemini 3.6…");
                last=error;
            }
        }
        // The caller may now try Groq, but no earlier Gemini failure can skip 3.6.
        throw last;
    }
}

