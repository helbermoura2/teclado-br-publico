# SPDX-License-Identifier: GPL-3.0-only
"""Add a first-class translation shortcut without replacing the AI panel.

Executed after assistant.py and toolbar.py against pinned HeliBoard 3.6.
The same existing IME writing panel handles the translation picker.
"""

def apply(context):
    root = context.root

    def once(text, old, new, label):
        count = text.count(old)
        if count != 1:
            raise RuntimeError(f'Translation toolbar anchor {label}: {count} matches')
        return text.replace(old, new, 1)

    utils = root / 'app/src/main/java/helium314/keyboard/latin/utils/ToolbarUtils.kt'
    code = utils.read_text()
    code = once(code,
        '    AI -> -11050',
        '    TRANSLATE -> -11052\n    AI -> -11050',
        'translation action code')
    code = once(code,
        'PAGE_START, PAGE_END, LANGUAGE, AI\n}',
        'PAGE_START, PAGE_END, LANGUAGE, AI, TRANSLATE\n}',
        'translation enum')
    utils.write_text(code)

    logic = root / 'app/src/main/java/helium314/keyboard/latin/inputlogic/InputLogic.java'
    code = logic.read_text()
    code = once(code,
        '''            case -11050:
                helium314.keyboard.latin.ai.WritingLauncher.open(mLatinIME);
                break;''',
        '''            case -11052:
                helium314.keyboard.latin.ai.WritingPanel.openTranslation(mLatinIME);
                break;
            case -11050:
                helium314.keyboard.latin.ai.WritingLauncher.open(mLatinIME);
                break;''',
        'dispatch translation action')
    logic.write_text(code)

    icons = root / 'app/src/main/java/helium314/keyboard/keyboard/internal/KeyboardIconsSet.kt'
    code = icons.read_text()
    for style in ('holo', 'lxx', 'rounded'):
        code = once(code,
            f'                    ToolbarKey.AI -> R.drawable.teclado_br_ai\n'
            f'                    ToolbarKey.EMOJI -> R.drawable.sym_keyboard_smiley_{style}',
            f'                    ToolbarKey.TRANSLATE -> R.drawable.teclado_br_translate\n'
            f'                    ToolbarKey.AI -> R.drawable.teclado_br_ai\n'
            f'                    ToolbarKey.EMOJI -> R.drawable.sym_keyboard_smiley_{style}',
            f'icon {style}')
    icons.write_text(code)

    (root / 'app/src/main/res/drawable/teclado_br_translate.xml').write_text(
        '''<vector xmlns:android="http://schemas.android.com/apk/res/android"
            android:width="24dp" android:height="24dp"
            android:viewportWidth="24" android:viewportHeight="24">
            <path android:fillColor="#FFF5F5F7"
                android:pathData="M2,4 L13,4 L13,6 L9,6 L9,8 L12,8 L12,10 L10.5,10 C10.2,11 9.6,12.1 8.7,13.2 L11.5,16.1 L10.1,17.6 L7.3,14.5 L4.2,17.5 L2.8,16.1 L5.9,13.1 C5,12 4.4,11 4,10 L6,10 C6.4,10.7 6.8,11.3 7.4,12 C8,11.3 8.4,10.7 8.7,10 L2,10 L2,8 L7,8 L7,6 L2,6 Z M16,7 L18,7 L23,20 L20.8,20 L19.8,17.2 L14.1,17.2 L13,20 L10.8,20 Z M17,10.7 L14.8,15.4 L19.1,15.4 Z"/>
        </vector>''')
    strings = root / 'app/src/main/res/values/strings.xml'
    strings.write_text(once(strings.read_text(), '</resources>',
        '<string name="translate">Tradução com IA</string>\n</resources>',
        'translation accessible label'))

