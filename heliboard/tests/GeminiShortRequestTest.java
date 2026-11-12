// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;

import org.junit.Test;
import java.io.IOException;
import java.net.SocketTimeoutException;
import static org.junit.Assert.*;

/** Synthetic tests: no Google API key and no remote network traffic. */
public final class GeminiShortRequestTest {
    @Test public void short38Uses15SecondReadTimeout() {
        assertEquals(15000,GeminiClient.responseTimeoutMs(GeminiClient.MODEL,20,60000));
        assertEquals(15000,GeminiClient.responseTimeoutMs(GeminiClient.MODEL,600,60000));
        assertEquals(60000,GeminiClient.responseTimeoutMs(GeminiClient.MODEL,601,60000));
        assertEquals(45000,GeminiClient.responseTimeoutMs(GeminiClient.BACKUP_MODEL,20,45000));
        assertEquals(9000,GeminiClient.responseTimeoutMs(GeminiClient.MODEL,20,9000));
    }
    @Test public void tracedFailuresShowOnlySafePhaseAndTime() {
        Exception timeout=new SocketTimeoutException("Read timed out / fake private text");
        Exception error=new GeminiClient.TracedFailure(timeout,
            "etapa=aguardando HTTP; conexão/TLS=300 ms; envio=10 ms; espera HTTP=não iniciada; leitura=não iniciada");
        String message=GeminiClient.diagnostic(error);
        assertTrue(message.contains("tempo limite"));
        assertTrue(message.contains("etapa=aguardando HTTP"));
        assertTrue(message.contains("conexão/TLS=300 ms"));
        assertFalse(message.contains("private text"));
        assertFalse(message.contains("Read timed out"));
    }
    @Test public void tracedHttpErrorIsStillDiagnosable() {
        Exception error=new GeminiClient.TracedFailure(new GeminiClient.Failure(429),
            "etapa=aguardando HTTP; HTTP 429");
        assertTrue(GeminiClient.diagnostic(error).contains("HTTP 429"));
        assertTrue(GeminiClient.tryAlternate(error));
    }
}

