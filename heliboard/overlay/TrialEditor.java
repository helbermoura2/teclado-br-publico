// SPDX-License-Identifier: GPL-3.0-only
package br.com.tecladobr.heli.testing;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.text.InputType;
import android.view.WindowManager;
import android.widget.*;

/** Editor isolated from the IME process for actual InputConnection testing. */
public final class TrialEditor extends Activity {
    private EditText editor;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(20,50,20,20);root.setBackgroundColor(Color.BLACK);
        TextView title=new TextView(this);title.setText("Teclado BR · teste HeliBoard");title.setTextSize(20);title.setTextColor(Color.WHITE);root.addView(title);
        TextView note=new TextView(this);note.setText("PT-BR · sugestões locais e assistente no botão de brilho. Configure Offline, Automático ou Groq Online. Lupa ainda em desenvolvimento.");note.setTextColor(Color.LTGRAY);root.addView(note);
        editor=new EditText(this);editor.setContentDescription("trial_editor");editor.setTextColor(Color.WHITE);editor.setHintTextColor(Color.GRAY);editor.setTextSize(22);editor.setHint("Experimente aqui");editor.setGravity(android.view.Gravity.TOP);
        editor.setInputType(getIntent().getIntExtra("input_type",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES));
        root.addView(editor,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);editor.requestFocus();
    }
    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if(focused&&editor!=null){editor.requestFocus();editor.postDelayed(()->{
            if(editor.hasWindowFocus())((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(editor,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        },250);}
    }
}

