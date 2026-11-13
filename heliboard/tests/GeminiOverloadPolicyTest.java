// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;

import org.junit.Test;
import java.net.SocketTimeoutException;
import static org.junit.Assert.*;

/** No real credentials or network calls; validates the one-retry policy. */
public final class GeminiOverloadPolicyTest {
    @Test public void retryOnly503() {
        assertTrue(GeminiClient.overloaded(new GeminiClient.Failure(503)));
        assertTrue(GeminiClient.overloaded(new GeminiClient.TracedFailure(
            new GeminiClient.Failure(503),"etapa=aguardando HTTP; HTTP 503")));
        for(int status:new int[]{400,401,403,404,429,500,502,504})
            assertFalse("HTTP "+status+" must not retry",
                GeminiClient.overloaded(new GeminiClient.Failure(status)));
        assertFalse(GeminiClient.overloaded(new SocketTimeoutException("timeout")));
    }

    @Test public void diagnosticStillPreservesHttp503AndDoesNotLeakRawDetails() {
        String diagnostic=GeminiClient.diagnostic(new GeminiClient.TracedFailure(
            new GeminiClient.Failure(503),
            "etapa=aguardando HTTP; conexão/TLS=233 ms; envio=0 ms; espera HTTP=150 ms; HTTP 503"));
        assertTrue(diagnostic.contains("HTTP 503"));
        assertTrue(diagnostic.contains("conexão/TLS=233 ms"));
    }
}
