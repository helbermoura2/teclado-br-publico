// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;
public final class InferenceResult {
    public final String text,backend;
    public final boolean truncated;
    public InferenceResult(String text,String backend,boolean truncated) {
        this.text=text;
        this.backend=backend;
        this.truncated=truncated;
    }
}

