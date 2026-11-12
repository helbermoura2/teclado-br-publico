// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;

import org.junit.Test;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

/** All scenarios are synthetic; no real key, message or network. */
public final class GeminiCascadeTest {
    @Test public void primarySuccessDoesNotInvokeBackup() throws Exception {
        List<String> attempts=new ArrayList<>();
        InferenceResult r=GeminiCascade.run(model->{
            attempts.add(model);
            return new InferenceResult("ok","Gemini · "+model,false);
        },()->false,s->{},s->{});
        assertEquals(Collections.singletonList(GeminiClient.MODEL),attempts);
        assertEquals("Gemini · "+GeminiClient.MODEL,r.backend);
    }

    @Test public void primaryErrorsOfAnyTypeTry36BeforeGroq() throws Exception {
        for(int http:new int[]{400,401,403,404,429,500,503}) {
            List<String> attempts=new ArrayList<>();
            List<String> issues=new ArrayList<>();
            InferenceResult r=GeminiCascade.run(model->{
                attempts.add(model);
                if(model.equals(GeminiClient.MODEL))throw new GeminiClient.Failure(http);
                return new InferenceResult("corrigido","Gemini · "+model,false);
            },()->false,s->{},issues::add);
            assertEquals(Arrays.asList(GeminiClient.MODEL,GeminiClient.BACKUP_MODEL),attempts);
            assertEquals("corrigido",r.text);
            assertTrue(r.backend.contains("gemini-3.8-flash: HTTP "+http));
            assertEquals(1,issues.size());
        }
    }

    @Test public void emptyResponseAndTimeoutAlsoTry36() throws Exception {
        for(Exception primary:new Exception[]{
            new java.io.IOException("Resposta vazia"),
            new java.net.SocketTimeoutException("Read timed out")}) {
            List<String> attempts=new ArrayList<>();
            GeminiCascade.run(model->{
                attempts.add(model);
                if(attempts.size()==1)throw primary;
                return new InferenceResult("ok","Gemini 3.6",false);
            },()->false,s->{},s->{});
            assertEquals(Arrays.asList(GeminiClient.MODEL,GeminiClient.BACKUP_MODEL),attempts);
        }
    }

    @Test public void bothFailBeforeGroqIsAllowedToRun() {
        List<String> attempts=new ArrayList<>();
        try {
            GeminiCascade.run(model->{
                attempts.add(model);
                throw new GeminiClient.Failure(503);
            },()->false,s->{},s->{});
            fail("Deveria entregar erro ao chamador, que tenta Groq.");
        }catch(Exception expected) {
            assertEquals(Arrays.asList(GeminiClient.MODEL,GeminiClient.BACKUP_MODEL),attempts);
        }
    }

    @Test public void cancellationNeverAdvancesTo36() {
        List<String> attempts=new ArrayList<>();
        AtomicBoolean cancelled=new AtomicBoolean(false);
        try {
            GeminiCascade.run(model->{
                attempts.add(model);
                cancelled.set(true);
                throw new java.io.IOException("cancelled");
            },cancelled::get,s->{},s->{});
            fail("Deveria cancelar.");
        }catch(CancellationException expected) {
            assertEquals(Collections.singletonList(GeminiClient.MODEL),attempts);
        }catch(Exception error) {
            fail("A exceção deveria ser de cancelamento: "+error.getClass());
        }
    }
}

