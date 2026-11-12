// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.compat;
import android.text.InputType;
import org.junit.Test;
import static org.junit.Assert.*;
/** Checks the production package-specific workaround and its privacy boundaries. */
public final class MessagingInputTypeTest {
    @Test public void instagramProseKeepsAllFlagsExceptNoSuggestions() {
        int[] prose={InputType.TYPE_TEXT_VARIATION_NORMAL,InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE,
            InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE,InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT};
        for(int variation:prose) {
            int input=InputType.TYPE_CLASS_TEXT|variation|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS|
                InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
            assertEquals(input&~InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
                AppWorkarounds.INSTANCE.adjustInputType(input,"com.instagram.android"));
        }
    }
    @Test public void privateAndNonProseFieldsKeepTheirFlags() {
        int[] privateFields={InputType.TYPE_TEXT_VARIATION_PASSWORD,InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,InputType.TYPE_TEXT_VARIATION_URI,
            InputType.TYPE_TEXT_VARIATION_FILTER};
        for(int variation:privateFields) {
            int input=InputType.TYPE_CLASS_TEXT|variation|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
            assertEquals(input,AppWorkarounds.INSTANCE.adjustInputType(input,"com.instagram.android"));
        }
        assertEquals(InputType.TYPE_CLASS_NUMBER,AppWorkarounds.INSTANCE.adjustInputType(InputType.TYPE_CLASS_NUMBER,"com.instagram.android"));
    }
    @Test public void otherAppsAreNotForcedToEnableSuggestions() {
        int input=InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
        for(String app:new String[]{"com.whatsapp","com.openai.chatgpt","other.example",null})
            assertEquals(input,AppWorkarounds.INSTANCE.adjustInputType(input,app));
    }
}
