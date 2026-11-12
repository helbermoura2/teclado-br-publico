// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;
import android.app.Activity;
import android.os.Bundle;
import android.widget.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import helium314.keyboard.latin.TecladoBrDefaultsKt;
import android.content.SharedPreferences;
import helium314.keyboard.latin.Typography;
import helium314.keyboard.latin.settings.Settings;
import helium314.keyboard.latin.utils.DeviceProtectedUtils;
public final class AppearanceActivity extends Activity {
    private LinearLayout root;
    private SharedPreferences prefs;
    private SeekBar height,letters,symbols;
    private Switch oneUi, compactToolbar;
    private LinearLayout themePreview;
    private int dp(int n) {
        return (int)(n*getResources().getDisplayMetrics().density+.5f);
    }
    private TextView label(String text,int size) {
        TextView t=new TextView(this);
        t.setText(text);
        t.setTextSize(size);
        t.setTextColor(Color.WHITE);
        t.setPadding(0,dp(10),0,dp(6));
        root.addView(t);
        return t;
    }
    private SeekBar slider(String title,String id,int min,int max,int initial) {
        TextView value=label(title+": "+initial+"%",17);
        SeekBar bar=new SeekBar(this);
        bar.setContentDescription(id);
        bar.setMax(max-min);
        bar.setProgress(Math.max(0,Math.min(max-min,initial-min)));
        bar.setTag(min);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b,int p,boolean user) {
                value.setText(title+": "+(p+min)+"%");
            }
            public void onStartTrackingTouch(SeekBar b) {
            }
            public void onStopTrackingTouch(SeekBar b) {
            }
        });
        root.addView(bar,new LinearLayout.LayoutParams(-1,dp(48)));
        return bar;
    }
    private void refreshPreview(boolean enabled) {
        themePreview.removeAllViews();
        themePreview.setBackgroundColor(enabled?0xff14151a:0xff171719);
        for(String letter:new String[]{"Q","W","E","R","T","Y"}) {
            TextView key=new TextView(this);
            key.setText(letter);
            key.setGravity(android.view.Gravity.CENTER);
            key.setTextSize(20);
            key.setTextColor(0xfff6f6f8);
            GradientDrawable shape=new GradientDrawable();
            shape.setColor(enabled?0xff37383d:0xff313133);
            shape.setCornerRadius(dp(enabled?10:7));
            key.setBackground(shape);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(48),1);
            p.setMargins(dp(2),dp(7),dp(2),dp(7));
            themePreview.addView(key,p);
        }
        themePreview.setContentDescription(enabled?"Prévia do estilo One UI escuro":"Prévia do tema clássico");
    }
    private float value(SeekBar bar) {
        return ((Integer)bar.getTag()+bar.getProgress())/100f;
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs=DeviceProtectedUtils.getSharedPreferences(this);
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        root=new LinearLayout(this);
        root.setOrientation(1);
        SettingsLayout.insets(root,dp(20));
        root.setBackgroundColor(0xff17191e);
        scroll.addView(root);
        setContentView(scroll);
        label("Aparência do teclado",24);
        label("Ajustes separados. A altura abaixo vale para o telefone em pé. Reabra o teclado para conferir o resultado.",14);
        label("Tema visual",20);
        oneUi=new Switch(this);
        oneUi.setText("Estilo One UI 8.5 · inspirado no Samsung Keyboard");
        oneUi.setTextColor(Color.WHITE);
        oneUi.setTextSize(16);
        oneUi.setMinHeight(dp(52));
        oneUi.setContentDescription("appearance_oneui_theme");
        oneUi.setChecked(prefs.getBoolean("teclado_br_one_ui_active",false));
        root.addView(oneUi);
        label("Opcional e reversível. Não instala Galaxy AI nem usa recursos proprietários; seus modelos, histórico de prancheta, palavras, pins, fontes e tamanhos continuam preservados.",13);
        compactToolbar=new Switch(this);
        compactToolbar.setText("Barra compacta: sugestões e cinco atalhos");
        compactToolbar.setTextColor(Color.WHITE);
        compactToolbar.setTextSize(16);
        compactToolbar.setMinHeight(dp(50));
        compactToolbar.setContentDescription("appearance_compact_toolbar");
        compactToolbar.setChecked(prefs.getBoolean("teclado_br_compact_toolbar_active",false));
        compactToolbar.setEnabled(oneUi.isChecked());
        root.addView(compactToolbar);
        label("A barra compacta mostra IA, prancheta, emoji, tradução e ajustes. Toque na prancheta para abrir o histórico; segure para abrir TODOS os comandos de edição e movimento do cursor, com rolagem horizontal e botão Voltar. A seta à esquerda abre e recolhe os atalhos. Segure a barra de espaço para trocar de idioma. Selecionar texto também oferece ações contextuais. Seu histórico e suas preferências são preservados.",13);
        themePreview=new LinearLayout(this);
        themePreview.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(themePreview,new LinearLayout.LayoutParams(-1,dp(64)));
        oneUi.setOnCheckedChangeListener((button,checked)->{
            refreshPreview(checked);
            compactToolbar.setEnabled(checked);
        });
        refreshPreview(oneUi.isChecked());
        label("Dimensões e tipografia",20);
        height=slider("Altura","appearance_height",85,140,Math.round(prefs.getFloat(Settings.PREF_KEYBOARD_HEIGHT_SCALE_PREFIX+"_false",1.1f)*100));
        letters=slider("Letras","appearance_letters",80,125,Math.round(prefs.getFloat(Settings.PREF_FONT_SCALE,1f)*100));
        symbols=slider("Símbolos dos cantos","appearance_symbols",70,130,Math.round(prefs.getFloat(Typography.SYMBOL_SCALE,1f)*100));
        Button save=new Button(this);
        save.setAllCaps(false);
        save.setText("Aplicar ajustes");
        save.setContentDescription("appearance_save");
        save.setOnClickListener(v-> {
            if(!prefs.edit().putFloat(Settings.PREF_KEYBOARD_HEIGHT_SCALE_PREFIX+"_false",value(height)).putFloat(Settings.PREF_FONT_SCALE,value(letters)).putFloat(Typography.SYMBOL_SCALE,value(symbols)).commit()) {
                Toast.makeText(this,"Não foi possível salvar. Tente novamente.",Toast.LENGTH_LONG).show();
                return;
            }
            if(!TecladoBrDefaultsKt.applyTecladoBrOneUi(this,oneUi.isChecked())) {
                Toast.makeText(this,"Não foi possível aplicar o tema. Tente novamente.",Toast.LENGTH_LONG).show();
                return;
            }
            if(!TecladoBrDefaultsKt.applyTecladoBrCompactToolbar(this,
                oneUi.isChecked() && compactToolbar.isChecked())) {
                Toast.makeText(this,"Não foi possível aplicar a barra compacta. Tente novamente.",Toast.LENGTH_LONG).show();
                return;
            }
            Typography.load(this);
            Toast.makeText(this,"Aparência aplicada. Reabra o teclado para ver o novo tema.",Toast.LENGTH_LONG).show();
            finish();
        });
        root.addView(save);
        Button reset=new Button(this);
        reset.setAllCaps(false);
        reset.setText("Restaurar tamanhos recomendados");
        reset.setOnClickListener(v-> {
            height.setProgress(110-85);
            letters.setProgress(100-80);
            symbols.setProgress(100-70);
        });
        root.addView(reset);
        Button back=new Button(this);
        back.setText("Voltar");
        back.setAllCaps(false);
        back.setOnClickListener(v->finish());
        root.addView(back);
    }
}

