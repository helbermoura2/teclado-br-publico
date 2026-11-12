// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;
import java.nio.charset.StandardCharsets;
public final class OfflineNative {
    static {
        System.loadLibrary("tecladoai");
    }
    public interface Progress {
        void onProgress(byte[] text);
    }
    public static native byte[] generate(byte[] path, byte[] prompt, Progress progress, int maximum);
    public static native void cancel();
    public static native void reset();
    static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}

