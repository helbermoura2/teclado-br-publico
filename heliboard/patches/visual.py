# SPDX-License-Identifier: GPL-3.0-only
"""Opt-in visual geometry for the Teclado BR main keyboard.

Edits ONLY the pinned HeliBoard renderer; keeps hitboxes, dictionary,
touch dispatch, swipe typing, emoji/popup keyboards and user fonts unchanged.
Fails closed if upstream text changes.
"""
from pathlib import Path


def apply(context):
    path = context.root / 'app/src/main/java/helium314/keyboard/keyboard/KeyboardView.java'
    text = path.read_text()

    def once(old, new):
        nonlocal text
        count = text.count(old)
        if count != 1:
            raise RuntimeError('Visual overlay upstream anchor changed (%s: %s)' % (count, old[:75]))
        text = text.replace(old, new, 1)

    once(
        'import helium314.keyboard.latin.utils.TypefaceUtils;',
        'import helium314.keyboard.latin.utils.TypefaceUtils;\n'
        'import helium314.keyboard.latin.utils.DeviceProtectedUtils;'
    )
    once(
        '    protected final Typeface mTypeface;',
        '''    protected final Typeface mTypeface;
    private final boolean mTecladoBrOneUi;
    private final Paint mTecladoBrRoundedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);'''
    )
    # prepare.py already installs a condensed font fallback; keep custom font priority.
    once(
        '''        mTypeface = customTypeface != null ? customTypeface : Typeface.create("sans-serif-condensed", Typeface.NORMAL);''',
        '''        mTecladoBrOneUi = DeviceProtectedUtils.getSharedPreferences(context)
                .getBoolean("teclado_br_one_ui_active", false);
        mTypeface = customTypeface != null ? customTypeface :
                Typeface.create(mTecladoBrOneUi ? "sans-serif" : "sans-serif-condensed", Typeface.NORMAL);'''
    )
    once(
        '''        final int keyWidth = key.getDrawWidth();
        final int keyHeight = key.getHeight();
        final int bgWidth, bgHeight, bgX, bgY;''',
        '''        final int keyWidth = key.getDrawWidth();
        final int keyHeight = key.getHeight();
        // Rounded keycaps are rendered ONLY in the optional One UI-inspired
        // theme and ONLY on the main keyboard. Popup/emoji/suggestion keys
        // continue using their native drawables, as do image-backed keys.
        if (mTecladoBrOneUi
                && !(this instanceof PopupKeysKeyboardView)
                && !(this instanceof EmojiPageKeyboardView)
                && !(this instanceof MoreSuggestionsView)
                && !key.needsToKeepBackgroundAspectRatio(mDefaultKeyLabelFlags)) {
            drawTecladoBrRoundedKeycap(key, canvas, background, keyWidth, keyHeight);
            return;
        }
        final int bgWidth, bgHeight, bgX, bgY;'''
    )
    once(
        '''    // Draw key top visuals.
    protected void onDrawKeyTopVisuals''',
        '''    private void drawTecladoBrRoundedKeycap(@NonNull final Key key,
            @NonNull final Canvas canvas, @NonNull final Drawable background,
            final int width, final int height) {
        if (width <= 0 || height <= 0) return;
        final float density = getResources().getDisplayMetrics().density;
        final float insetX = 1.25f * density;
        final float insetY = 0.80f * density;
        final float radius = Math.min(11.0f * density, height * 0.27f);
        final int type = key.getBackgroundType();
        final int baseColor;
        if (type == Key.BACKGROUND_TYPE_ACTION) {
            baseColor = mColors.get(ColorType.ACTION_KEY_BACKGROUND);
        } else if (type == Key.BACKGROUND_TYPE_FUNCTIONAL) {
            baseColor = mColors.get(ColorType.FUNCTIONAL_KEY_BACKGROUND);
        } else if (type == Key.BACKGROUND_TYPE_SPACEBAR) {
            baseColor = mColors.get(ColorType.SPACE_BAR_BACKGROUND);
        } else {
            baseColor = mColors.get(ColorType.KEY_BACKGROUND);
        }
        // Honour the native key press state selected by HeliBoard.
        boolean pressed = false;
        for (int state : background.getState()) {
            if (state == android.R.attr.state_pressed) {
                pressed = true;
                break;
            }
        }
        final int fillColor = pressed
                ? Color.rgb(Math.min(255, Color.red(baseColor) + 26),
                            Math.min(255, Color.green(baseColor) + 26),
                            Math.min(255, Color.blue(baseColor) + 26))
                : baseColor;
        final Paint p = mTecladoBrRoundedPaint;
        p.setStyle(Paint.Style.FILL);
        p.setColor(fillColor);
        canvas.drawRoundRect(insetX, insetY, Math.max(insetX, width - insetX),
                Math.max(insetY, height - insetY), radius, radius, p);
        // Subtle outline improves key separation without painting on hitboxes.
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(0.55f * density);
        p.setColor(pressed ? 0xff90939e : 0xff50525a);
        canvas.drawRoundRect(insetX, insetY, Math.max(insetX, width - insetX),
                Math.max(insetY, height - insetY), radius, radius, p);
        p.setStyle(Paint.Style.FILL);
    }

    // Draw key top visuals.
    protected void onDrawKeyTopVisuals'''
    )
    once(
        'paint.setTextSize(key.selectHintTextSize(params) * helium314.keyboard.latin.Typography.symbolScale());',
        'paint.setTextSize(key.selectHintTextSize(params) * helium314.keyboard.latin.Typography.symbolScale() * (mTecladoBrOneUi ? 0.83f : 1.0f));'
    )
    once(
        'paint.setTextSize(params.mHintLetterSize);',
        'paint.setTextSize(params.mHintLetterSize * (mTecladoBrOneUi ? 0.83f : 1.0f));'
    )
    # On One UI keycaps the native HeliBoard lower-corner "..." indicator
    # gets clipped into white specks (notably comma, space and period).
    # Do not draw this decorative glyph in the opt-in theme. The key's
    # popup/long-press action, secondary symbols and touch area are unchanged.
    once(
        '''        final float hintY = keyHeight - mKeyPopupHintLetterPadding;
        canvas.drawText(mKeyPopupHintLetter, hintX, hintY, paint);''',
        '''        final float hintY = keyHeight - mKeyPopupHintLetterPadding;
        if (mTecladoBrOneUi && !(this instanceof PopupKeysKeyboardView)
                && !(this instanceof EmojiPageKeyboardView)) {
            // Only suppress the tiny decorative bottom-corner ellipsis.
            // Long-press and popup alternatives are still available.
            return;
        }
        canvas.drawText(mKeyPopupHintLetter, hintX, hintY, paint);'''
    )

    path.write_text(text)

