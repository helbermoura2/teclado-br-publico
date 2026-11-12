// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;
import org.junit.Test;
import static org.junit.Assert.*;
import java.net.SocketTimeoutException;
/** Synthetic diagnostics tests; never use or transmit real API credentials. */
public final class GeminiDiagnosticTest {
    @Test public void safeStatusExplainsFallbackWithoutSecrets() {
        assertTrue(GeminiClient.diagnostic(new GeminiClient.Failure(400)).contains("400"));
        assertTrue(GeminiClient.diagnostic(new GeminiClient.Failure(403)).contains("chave"));
        assertTrue(GeminiClient.diagnostic(new GeminiClient.Failure(404)).contains("modelo"));
        assertTrue(GeminiClient.diagnostic(new GeminiClient.Failure(429)).contains("cota"));
        assertTrue(GeminiClient.diagnostic(new GeminiClient.Failure(503)).contains("servidor"));
        assertTrue(GeminiClient.diagnostic(new SocketTimeoutException()).contains("tempo"));
        assertTrue(GeminiClient.diagnostic(
            new SocketTimeoutException("Read timed out")).contains("aguardando resposta"));
        assertTrue(GeminiClient.diagnostic(
            new SocketTimeoutException("connect timed out")).contains("conectar"));
    }
    @Test public void onlyTransientOrModelSpecificErrorsTrySecondGemini() {
        for(int status:new int[]{404,429,500,502,503,504})
            assertTrue("HTTP "+status,GeminiClient.tryAlternate(new GeminiClient.Failure(status)));
        for(int status:new int[]{400,401,403})
            assertFalse("HTTP "+status,GeminiClient.tryAlternate(new GeminiClient.Failure(status)));
        assertTrue(GeminiClient.tryAlternate(new SocketTimeoutException()));
    }
    @Test public void restrictModelsToTwoTrustedOfficialEndpoints() {
        new GeminiClient(GeminiClient.MODEL,"fake-key-not-used",()->false);
        new GeminiClient(GeminiClient.BACKUP_MODEL,"fake-key-not-used",()->false);
        boolean blocked=false;
        try {new GeminiClient("https://evil.example/steal","fake-key-not-used",()->false);}
        catch(IllegalArgumentException e){blocked=true;}
        assertTrue("Untrusted endpoint/model accepted",blocked);
        boolean blockedProbe=false;
        try {
            GeminiClient.verifyAccess("https://evil.invalid","fake-key-not-used",()->false);
        }catch(IllegalArgumentException e){blockedProbe=true;}
        catch(Exception e){throw new AssertionError("Unauthorized network attempted",e);}
        assertTrue("Untrusted model access attempted",blockedProbe);
    }
}

