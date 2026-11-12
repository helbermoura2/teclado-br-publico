// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;
import android.content.Context;
import java.nio.charset.StandardCharsets;
import java.util.function.*;
public final class OfflineEngine implements InferenceRouter.Backend {
    private final Context context;
    private final BooleanSupplier cancelled;
    private volatile boolean loaded;
    public OfflineEngine(Context c,BooleanSupplier cancelled) {
        context=c;
        this.cancelled=cancelled;
    }
    public void cancel() {
        if(loaded)OfflineNative.cancel();
    }
    @Override public InferenceResult generate(String prompt,int limit,Consumer<String> partial)throws Exception {
        InferenceRouter.check(cancelled);
        OfflineNative.reset();
        loaded=true;
        InferenceRouter.check(cancelled);
        byte[] bytes=OfflineNative.generate(OfflineNative.utf8(ModelStore.model(context).getAbsolutePath()),OfflineNative.utf8(prompt),p-> {
            if(!cancelled.getAsBoolean())partial.accept(new String(p,StandardCharsets.UTF_8));
        },limit);
        InferenceRouter.check(cancelled);
        String result=new String(bytes,StandardCharsets.UTF_8);
        if(result.length()<2)throw new IllegalStateException("O modelo não produziu uma resposta.");
        return new InferenceResult(result.substring(2),"Offline · "+ModelStore.description(context),result.startsWith("T\n"));
    }
}

