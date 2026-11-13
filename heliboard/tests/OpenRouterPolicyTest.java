// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;

import org.junit.Test;
import static org.junit.Assert.*;
import java.net.SocketTimeoutException;
/** No real key and no external network. */
public final class OpenRouterPolicyTest {
    @Test public void acceptsOnlyKnownFreeRoutingAndQwen() {
        new OpenRouterClient("fake-test-key",OpenRouterClient.FREE,()->false);
        new OpenRouterClient("fake-test-key",OpenRouterClient.QWEN,()->false);
        boolean rejected=false;
        try {new OpenRouterClient("fake","charged/model",()->false);}
        catch(IllegalArgumentException e){rejected=true;}
        assertTrue(rejected);
    }
    @Test public void rejectsExternalHttpEndpoint() {
        boolean rejected=false;
        try {
            new OpenRouterClient("http://bad.example/steal","fake-test-key",
                OpenRouterClient.FREE,()->false,1000);
        }catch(IllegalArgumentException e){rejected=true;}
        assertTrue(rejected);
    }
    @Test public void sanitizedErrorsDoNotIncludeSecrets() {
        assertTrue(OpenRouterClient.diagnostic(
            new OpenRouterClient.Failure(429)).contains("429"));
        assertTrue(OpenRouterClient.diagnostic(
            new OpenRouterClient.Failure(403)).contains("chave"));
        String message=OpenRouterClient.diagnostic(
            new SocketTimeoutException("secret fake-user-prompt"));
        assertFalse(message.contains("secret"));
        assertFalse(message.contains("fake-user-prompt"));
    }
}
