// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;
import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.widget.*;
import android.text.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
public final class DictionaryActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private LinearLayout root,rows;
    private TextView status;
    private EditText search;
    private List<DictionaryRepository.Word> words=new ArrayList<>();
    private boolean busy;
    private Button button(String text,Runnable action) {
        Button b=new Button(this);
        b.setAllCaps(false);
        b.setText(text);
        b.setOnClickListener(v-> {
            if(!busy)action.run();
        });
        root.addView(b,new LinearLayout.LayoutParams(-1,-2));
        return b;
    }
    private TextView label(String text,int size) {
        TextView v=new TextView(this);
        v.setTextColor(Color.WHITE);
        v.setTextSize(size);
        v.setText(text);
        root.addView(v);
        return v;
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        root=new LinearLayout(this);
        root.setOrientation(1);
        float density=getResources().getDisplayMetrics().density;
        SettingsLayout.insets(root,(int)(20*density));
        root.setBackgroundColor(0xff17191e);
        setContentView(root);
        label("Dicionário pessoal",24).setContentDescription("dictionary_title");
        label("Palavras pessoais do Android. Remova uma entrada indesejada ou faça uma cópia para guardar.",14);
        search=new EditText(this);
        search.setSingleLine(true);
        search.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        search.setTextColor(Color.WHITE);
        search.setHintTextColor(Color.LTGRAY);
        search.setHint("Procurar palavra");
        search.setContentDescription("dictionary_search");
        root.addView(search);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int a,int b,int d) {
            }
            public void onTextChanged(CharSequence s,int a,int b,int d) {
                render();
            }
            public void afterTextChanged(Editable e) {
            }
        });
        button("Exportar dicionário (.json)",()->startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"teclado-br-dicionario.json"),51));
        button("Importar e mesclar dicionário",()->startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),52));
        status=label("Carregando…",13);
        ScrollView scroll=new ScrollView(this);
        rows=new LinearLayout(this);
        rows.setOrientation(1);
        scroll.addView(rows);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        button("Voltar",this::finish).setContentDescription("dictionary_back");
        reload();
    }
    private void reload() {
        busy=true;
        worker.execute(()-> {
            try {
                List<DictionaryRepository.Word> loaded=DictionaryRepository.list(this);
                runOnUiThread(()-> {
                    if(isDestroyed())return;
                    words=loaded;
                    busy=false;
                    status.setText(words.size()+" palavras pessoais");
                    render();
                });
            }
            catch(Exception e) {
                failed("Não foi possível ler o dicionário.");
            }
        });
    }
    private void render() {
        if(rows==null)return;
        rows.removeAllViews();
        String query=search.getText().toString().toLowerCase(Locale.ROOT);
        int visible=0;
        for(DictionaryRepository.Word word:words) {
            if(!word.text.toLowerCase(Locale.ROOT).contains(query))continue;
            if(visible++>=200) {
                TextView hint=new TextView(this);
                hint.setText("Refine a busca para ver outras palavras.");
                hint.setTextColor(Color.LTGRAY);
                rows.addView(hint);
                break;
            }
            Button b=new Button(this);
            b.setAllCaps(false);
            b.setGravity(android.view.Gravity.LEFT|android.view.Gravity.CENTER_VERTICAL);
            b.setText(word.text+"\n"+(word.locale==null?"Todos os idiomas":word.locale)+(word.shortcut==null?"":" · atalho: "+word.shortcut));
            b.setContentDescription("dictionary_word_"+word.text);
            b.setOnClickListener(v->new AlertDialog.Builder(this).setTitle(word.text).setMessage("Remover esta entrada do dicionário pessoal? Ela poderá continuar aparecendo se também fizer parte do dicionário básico ou do aprendizado.").setPositiveButton("Remover",(dialog,which)-> {
                busy=true;
                worker.execute(()-> {
                    try {
                        DictionaryRepository.delete(this,word.id);
                        runOnUiThread(this::reload);
                    }
                    catch(Exception e) {
                        failed("Não foi possível remover.");
                    }
                });
            }).setNegativeButton("Cancelar",null).show());
            rows.addView(b,new LinearLayout.LayoutParams(-1,-2));
        }
    }
    private void failed(String message) {
        runOnUiThread(()-> {
            if(!isDestroyed()) {
                busy=false;
                status.setText(message);
            }
        });
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK||data==null||data.getData()==null)return;
        busy=true;
        status.setText("Processando arquivo…");
        worker.execute(()-> {
            try {
                if(request==51) {
                    try(OutputStream out=getContentResolver().openOutputStream(data.getData())) {
                        if(out==null)throw new IOException();
                        DictionaryRepository.exportTo(this,out);
                    }
                    runOnUiThread(()-> {
                        busy=false;
                        status.setText("Dicionário exportado.");
                    });
                }
                else if(request==52) {
                    int imported;
                    try(InputStream in=getContentResolver().openInputStream(data.getData())) {
                        if(in==null)throw new IOException();
                        imported=DictionaryRepository.importFrom(this,in);
                    }
                    runOnUiThread(()-> {
                        Toast.makeText(this,imported+" palavras importadas.",Toast.LENGTH_LONG).show();
                        reload();
                    });
                }
            }
            catch(Exception e) {
                failed("Não foi possível processar o arquivo. Confira se é um dicionário exportado pelo Teclado BR.");
            }
        });
    }
    @Override protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}

