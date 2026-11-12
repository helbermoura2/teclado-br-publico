// SPDX-License-Identifier: GPL-3.0-only
package br.com.tecladobr.heli.testing;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.view.*;
import android.view.accessibility.*;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.widget.*;
import helium314.keyboard.keyboard.*;
import helium314.keyboard.latin.settings.Settings;
import helium314.keyboard.latin.RichInputMethodManager;
import helium314.keyboard.latin.database.ClipboardDao;
import helium314.keyboard.latin.database.Database;
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode;
import android.database.Cursor;
import helium314.keyboard.latin.suggestions.SuggestionStripView;
import helium314.keyboard.latin.utils.DeviceProtectedUtils;
import helium314.keyboard.latin.utils.ToolbarKey;
import java.io.*;
import java.util.*;
import java.util.function.Predicate;

/** Debug-only real touch injection; no calls to the IME text insertion methods. */
public final class HeliTrialInstrumentation extends Instrumentation {
    UiAutomation ui; File shots; final Bundle result=new Bundle();String phase="full";
    int trialInputType=android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
    static final String PIN_TEXT="Texto fixado para usar em outros dias.";
    static final String TEMP_TEXT="Texto temporário do teste de expiração.";
    @Override public void onCreate(Bundle args){super.onCreate(args);if(args!=null)phase=args.getString("phase","full");start();}
    void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    void shell(String command)throws Exception{try(InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(ui.executeShellCommand(command))){byte[] b=new byte[4096];while(in.read(b)>=0){}}}
    AccessibilityNodeInfo walk(AccessibilityNodeInfo n,Predicate<AccessibilityNodeInfo> test){if(n==null)return null;if(test.test(n))return n;for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo found=walk(n.getChild(i),test);if(found!=null)return found;}return null;}
    AccessibilityNodeInfo find(Predicate<AccessibilityNodeInfo> test){for(AccessibilityWindowInfo w:ui.getWindows()){AccessibilityNodeInfo n=walk(w.getRoot(),test);if(n!=null)return n;}return null;}
    AccessibilityNodeInfo field()throws Exception{long deadline=SystemClock.uptimeMillis()+60000;do{AccessibilityNodeInfo n=find(v->"trial_editor".equals(String.valueOf(v.getContentDescription())));if(n!=null)return n;Thread.sleep(150);}while(SystemClock.uptimeMillis()<deadline);throw new AssertionError("No editor");}
    String text()throws Exception{CharSequence value=field().getText();String s=value==null?"":value.toString();return s.equals("Experimente aqui")?"":s;}
    void expect(String value)throws Exception{long deadline=SystemClock.uptimeMillis()+12000;String actual="";do{actual=text();if(actual.equals(value))return;Thread.sleep(100);}while(SystemClock.uptimeMillis()<deadline);throw new AssertionError("Expected: "+value+"; actual: "+actual);}
    void options(boolean caps,boolean correction){DeviceProtectedUtils.getSharedPreferences(getTargetContext()).edit().putBoolean(Settings.PREF_AUTO_CAP,caps).putBoolean(Settings.PREF_AUTO_CORRECTION,correction).commit();}
    void launch()throws Exception{
        try{launchOnce();}catch(AssertionError error){if(!String.valueOf(error.getMessage()).contains("window did not settle"))throw error;result.putInt("editor_window_retries",result.getInt("editor_window_retries",0)+1);Thread.sleep(500);launchOnce();}
    }
    void launchOnce()throws Exception{
        getTargetContext().startActivity(new Intent(getTargetContext(),TrialEditor.class).putExtra("input_type",trialInputType).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));Thread.sleep(2000);
        // Only dismiss an unrelated launcher ANR; never hide a keyboard/app ANR.
        AccessibilityNodeInfo launcher=find(n->n.getText()!=null&&n.getText().toString().contains("Quickstep")&&n.getText().toString().contains("respond"));
        if(launcher!=null){AccessibilityNodeInfo close=find(n->"Close app".contentEquals(n.getText()==null?"":n.getText()));if(close!=null)close.performAction(AccessibilityNodeInfo.ACTION_CLICK);}
        Rect area=new Rect();field().getBoundsInScreen(area);area.bottom=Math.min(area.bottom,area.top+80);tap(area);
        Rect previous=null;int stable=0;long keyboardDeadline=SystemClock.uptimeMillis()+30000;long nextFocusTap=SystemClock.uptimeMillis()+1500;
        while(stable<3&&SystemClock.uptimeMillis()<keyboardDeadline){
            Rect key=bounds('e',false);boolean inside=false;
            for(AccessibilityWindowInfo window:ui.getWindows())if(window.getType()==AccessibilityWindowInfo.TYPE_INPUT_METHOD){Rect ime=new Rect();window.getBoundsInScreen(ime);if(ime.contains(key.centerX(),key.centerY()))inside=true;}
            if(inside&&key.equals(previous))stable++;else stable=0;
            if(!inside&&SystemClock.uptimeMillis()>=nextFocusTap){Rect target=new Rect();field().getBoundsInScreen(target);target.bottom=Math.min(target.bottom,target.top+80);tap(target);nextFocusTap=SystemClock.uptimeMillis()+1500;}
            previous=key;Thread.sleep(200);
        }
        check(stable>=3,"Keyboard window did not settle after opening");
    }
    View findView(View v,Predicate<View> test){if(v==null)return null;if(test.test(v))return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View found=findView(g.getChildAt(i),test);if(found!=null)return found;}}return null;}
    MainKeyboardView main(){return KeyboardSwitcher.getInstance().getMainKeyboardView();}
    Rect bounds(char letter,boolean popup)throws Exception{return keyBounds(letter,popup);}
    Rect keyBounds(int letter,boolean popup)throws Exception{
        long deadline=SystemClock.uptimeMillis()+12000;
        do{Rect[] found={null};runOnMainSync(()->{
            MainKeyboardView m=main();if(m==null||m.getKeyboard()==null||!m.isShown())return;
            View start=m.getRootView();
            KeyboardView view=popup?(KeyboardView)findView(start,v->v instanceof KeyboardView&&!(v instanceof MainKeyboardView)&&v.isShown()):m;
            if(view==null||view.getKeyboard()==null)return;
            for(Key key:view.getKeyboard().getSortedKeys())if(Character.toLowerCase(key.getCode())==Character.toLowerCase(letter)||String.valueOf((char)letter).equalsIgnoreCase(key.getLabel())){
                int[] origin=new int[2];view.getLocationOnScreen(origin);int x=origin[0]+view.getPaddingLeft()+key.getX(),y=origin[1]+view.getPaddingTop()+key.getY();found[0]=new Rect(x,y,x+key.getWidth(),y+key.getHeight());break;
            }
        });if(found[0]!=null)return found[0];Thread.sleep(100);}while(SystemClock.uptimeMillis()<deadline);
        throw new AssertionError("Key not available: "+letter+" popup="+popup);
    }
    void event(long down,int action,int[] ids,Rect[] positions)throws Exception{
        MotionEvent.PointerProperties[] p=new MotionEvent.PointerProperties[ids.length];MotionEvent.PointerCoords[] c=new MotionEvent.PointerCoords[ids.length];
        for(int i=0;i<ids.length;i++){p[i]=new MotionEvent.PointerProperties();p[i].id=ids[i];p[i].toolType=MotionEvent.TOOL_TYPE_FINGER;c[i]=new MotionEvent.PointerCoords();c[i].x=positions[i].exactCenterX();c[i].y=positions[i].exactCenterY();c[i].pressure=1;c[i].size=.08f;}
        MotionEvent e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,ids.length,p,c,0,0,1,1,0,0,InputDevice.SOURCE_TOUCHSCREEN,0);check(ui.injectInputEvent(e,false),"Rejected input");e.recycle();Thread.sleep(5);
    }
    void tap(Rect rect)throws Exception{long d=SystemClock.uptimeMillis();event(d,MotionEvent.ACTION_DOWN,new int[]{0},new Rect[]{rect});event(d,MotionEvent.ACTION_UP,new int[]{0},new Rect[]{rect});}
    void type(String value)throws Exception{for(char letter:value.toCharArray()){tap(bounds(letter,false));Thread.sleep(20);}}
    void clear()throws Exception{Bundle args=new Bundle();args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"");check(field().performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args),"Cannot clear editor");expect("");Thread.sleep(300);}
    void shot(String name)throws Exception{Thread.sleep(600);Bitmap bitmap=ui.takeScreenshot();if(bitmap!=null){try(OutputStream out=new FileOutputStream(new File(shots,name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();}}
    List<String> suggestions()throws Exception{List<String> words=new ArrayList<>();runOnMainSync(()->{View strip=KeyboardSwitcher.getInstance().getStripContainer();findView(strip,v->{if(v instanceof TextView){String s=((TextView)v).getText().toString();if(!s.isEmpty())words.add(s);}return false;});});return words;}
    void toolbar(ToolbarKey key)throws Exception{
        long deadline=SystemClock.uptimeMillis()+10000;Rect previous=null;int stable=0;
        do{
            runOnMainSync(()->{View root=KeyboardSwitcher.getInstance().getStripContainer();SuggestionStripView strip=(SuggestionStripView)findView(root,v->v instanceof SuggestionStripView);if(strip!=null)strip.setToolbarVisibility(true);View target=findView(root,v->v.getTag()==key);if(target!=null)target.requestRectangleOnScreen(new Rect(0,0,target.getWidth(),target.getHeight()),true);});Thread.sleep(350);
            Rect rect=new Rect();runOnMainSync(()->{View target=findView(KeyboardSwitcher.getInstance().getStripContainer(),v->v.getTag()==key&&v.isShown());if(target!=null){int[] xy=new int[2];target.getLocationOnScreen(xy);rect.set(xy[0],xy[1],xy[0]+target.getWidth(),xy[1]+target.getHeight());}});
            if(!rect.isEmpty()&&rect.equals(previous))stable++;else stable=0;
            if(stable>=1){tap(rect);Thread.sleep(350);return;}previous=rect;Thread.sleep(100);
        }while(SystemClock.uptimeMillis()<deadline);throw new AssertionError("Visible shortcut missing after settling: "+key);
    }
    void tagged(ToolbarKey key)throws Exception{
        long deadline=SystemClock.uptimeMillis()+10000;
        do{Rect[] rect={null};runOnMainSync(()->{View root=KeyboardSwitcher.getInstance().getWrapperView().getRootView();View v=findView(root,n->n.getTag()==key&&n.isShown());if(v!=null){int[] xy=new int[2];v.getLocationOnScreen(xy);rect[0]=new Rect(xy[0],xy[1],xy[0]+v.getWidth(),xy[1]+v.getHeight());}});
            if(rect[0]!=null){tap(rect[0]);Thread.sleep(350);return;}Thread.sleep(100);
        }while(SystemClock.uptimeMillis()<deadline);throw new AssertionError("Visible shortcut missing: "+key);
    }
    void language(String wanted)throws Exception{
        long deadline=SystemClock.uptimeMillis()+8000;
        do{String[] tag={""};runOnMainSync(()->tag[0]=RichInputMethodManager.getInstance().getCurrentSubtypeLocale().getLanguage());if(tag[0].equals(wanted))return;Thread.sleep(100);}while(SystemClock.uptimeMillis()<deadline);
        throw new AssertionError("Language did not switch to "+wanted);
    }
    boolean storedPinned(){
        try(Cursor c=Database.Companion.getInstance(getTargetContext()).getReadableDatabase().rawQuery("SELECT PINNED FROM CLIPBOARD WHERE TEXT = ?",new String[]{PIN_TEXT})){
            return c.moveToFirst()&&c.getInt(0)==1;
        }
    }
    void pinnedEntry(boolean verifyOnly)throws Exception{
        if(!verifyOnly){
            ClipboardManager clip=(ClipboardManager)getTargetContext().getSystemService(Context.CLIPBOARD_SERVICE);
            runOnMainSync(()->clip.setPrimaryClip(ClipData.newPlainText("Teste",PIN_TEXT)));Thread.sleep(400);
            clear();type("oi"); // word suggestions collapse the toolbar; pinned shortcut must remain accessible
        }
        tagged(ToolbarKey.CLIPBOARD);
        long deadline=SystemClock.uptimeMillis()+8000;AccessibilityNodeInfo item;
        do{item=find(n->PIN_TEXT.contentEquals(n.getText()==null?"":n.getText()));if(item!=null)break;Thread.sleep(100);}while(SystemClock.uptimeMillis()<deadline);
        check(item!=null,"Pinned text is missing in clipboard UI");Rect itemRect=new Rect();item.getBoundsInScreen(itemRect);
        if(verifyOnly){
            check(storedPinned(),"Pin flag did not survive restart/update");tap(itemRect);expect(PIN_TEXT);shot("12-pin-colado-apos-reiniciar");return;
        }
        long d=SystemClock.uptimeMillis();event(d,MotionEvent.ACTION_DOWN,new int[]{0},new Rect[]{itemRect});Thread.sleep(850);event(d,MotionEvent.ACTION_UP,new int[]{0},new Rect[]{itemRect});Thread.sleep(350);
        check(storedPinned(),"Long press did not pin the copied text");shot("11-texto-fixado");tagged(ToolbarKey.CLOSE_HISTORY);
        runOnMainSync(()->{
            ClipboardDao dao=ClipboardDao.Companion.getInstance(getTargetContext());check(dao!=null,"No clipboard database");
            dao.addClip(System.currentTimeMillis(),false,TEMP_TEXT);
            long old=System.currentTimeMillis()-30L*24*60*60*1000;
            for(int i=0;i<dao.count();i++)if(dao.getAt(i).getText().equals(PIN_TEXT)||dao.getAt(i).getText().equals(TEMP_TEXT))dao.getAt(i).setTimeStamp(old);
            Database.Companion.getInstance(getTargetContext()).getWritableDatabase().execSQL("UPDATE CLIPBOARD SET TIMESTAMP = ? WHERE TEXT IN (?, ?)",new Object[]{old,PIN_TEXT,TEMP_TEXT});
            dao.clearOldClips(true);check(storedPinned(),"Pinned text expired after aging");
            for(int i=0;i<dao.count();i++)check(!dao.getAt(i).getText().equals(TEMP_TEXT),"Unpinned expired text was retained");
        });
        result.putString("pin_retention","Pinned text survives 30-day timestamp aging; unpinned text expires");
    }
    AccessibilityNodeInfo aiNode(String description)throws Exception {
        long until=SystemClock.uptimeMillis()+10000;
        do {AccessibilityNodeInfo n=find(v->description.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()));if(n!=null&&n.isVisibleToUser())return n;Thread.sleep(100);}while(SystemClock.uptimeMillis()<until);
        throw new AssertionError("AI control missing: "+description);
    }
    void aiClick(String description)throws Exception {AccessibilityNodeInfo n=aiNode(description);Rect bounds=new Rect();n.getBoundsInScreen(bounds);check(!bounds.isEmpty(),"AI control has no touch area: "+description);check(bounds.bottom<=getTargetContext().getResources().getDisplayMetrics().heightPixels,"AI control clipped: "+description);tap(bounds);Thread.sleep(600);}
    // ScrollView can reposition child switches while a visual preview redraws.
    // Use Android's accessibility click for deterministic settings activation,
    // then verify the real checked state instead of assuming a touch worked.
    void aiEnsureChecked(String description,boolean expected)throws Exception {
        AccessibilityNodeInfo node=aiNode(description);
        if(node.isChecked()!=expected){
            check(node.isEnabled(),"Settings switch disabled: "+description);
            check(node.performAction(AccessibilityNodeInfo.ACTION_CLICK),"Settings switch rejected click: "+description);
            Thread.sleep(450);
        }
        AccessibilityNodeInfo updated=aiNode(description);
        check(updated.isChecked()==expected,"Settings switch did not change: "+description+
            " expected="+expected+" actual="+updated.isChecked());
    }
    void aiPress(String description)throws Exception {
        AccessibilityNodeInfo node=aiNode(description);
        check(node.isEnabled(),"Settings button disabled: "+description);
        check(node.performAction(AccessibilityNodeInfo.ACTION_CLICK),
            "Settings button rejected accessibility action: "+description);
        Thread.sleep(450);
    }
    void aiSet(String description,String value)throws Exception {Bundle args=new Bundle();args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value);check(aiNode(description).performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args),"AI edit rejected");}
    String aiText(String description)throws Exception {CharSequence t=aiNode(description).getText();return t==null?"":t.toString();}
    AccessibilityNodeInfo aiOption(String label)throws Exception {
        long until=SystemClock.uptimeMillis()+10000;
        do {AccessibilityNodeInfo n=find(v->v.getText()!=null&&label.contentEquals(v.getText())&&v.isVisibleToUser());if(n!=null)return n;Thread.sleep(100);}while(SystemClock.uptimeMillis()<until);
        throw new AssertionError("AI menu option missing: "+label);
    }
    AccessibilityNodeInfo panelNode(AccessibilityNodeInfo n){if("ai_panel".equals(String.valueOf(n.getContentDescription())))return n;for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo c=n.getChild(i);if(c!=null){AccessibilityNodeInfo found=panelNode(c);if(found!=null)return found;}}return null;}
    String aiComplete()throws Exception {
        long until=SystemClock.uptimeMillis()+240000;
        String state;
        do {state=aiText("ai_status");if(aiNode("ai_edit_result").isEnabled())break;Thread.sleep(300);}while(SystemClock.uptimeMillis()<until);
        String response=aiText("ai_output");
        check(state.contains("Revise")||state.startsWith("Limite de geração"),"Native generation did not finish: "+state);
        check(!response.trim().isEmpty(),"Native inference produced no final output");
        check(!response.contains("<think>")&&!response.contains("</think>")&&!response.contains("<|im_end|>"),"Control tokens leaked");return response;
    }
    void setOriginal(String value)throws Exception {Bundle b=new Bundle();b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value);check(field().performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,b),"Cannot update original");Thread.sleep(300);}
    void writing(boolean withModel)throws Exception {
        String[] permissions=getTargetContext().getPackageManager().getPackageInfo(getTargetContext().getPackageName(),android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions;
        if(permissions!=null)for(String permission:permissions)check(!permission.equals("android.permission.SEND_SMS"),"Unexpected messaging permission");
        helium314.keyboard.latin.ai.AiPreferences.mode(getTargetContext(),helium314.keyboard.latin.ai.AiPreferences.OFFLINE);
        helium314.keyboard.latin.ai.WritingLauncher.syntheticTest=true;
        clear();type("texto original");toolbar(ToolbarKey.AI);aiNode("ai_review");expect("texto original");
        boolean imePanel=false;for(AccessibilityWindowInfo w:ui.getWindows())if(w.getType()==AccessibilityWindowInfo.TYPE_INPUT_METHOD){AccessibilityNodeInfo root=w.getRoot();if(root!=null&&panelNode(root)!=null)imePanel=true;}
        check(imePanel,"Assistant must be inside IME window");shot("13-ia-menu-no-teclado");
        aiClick("ai_translate");
        for(String target:new String[]{"português","inglês","espanhol","francês","italiano"})aiNode("ai_translate_"+target);
        shot("13b-ia-traducao-idiomas");
        aiClick("ai_home");
        aiClick("ai_style");aiNode("ai_style_Profissional");aiNode("ai_style_Casual");aiNode("ai_style_Mídia");aiNode("ai_style_Educado");aiNode("ai_style_Com emojis");shot("14-ia-estilos");aiClick("ai_back");
        toolbar(ToolbarKey.AI);aiClick("ai_compositor");aiClick("ai_prompt");aiNode("ai_local_edit");type("ola");expect("texto original");check(!aiText("ai_local_edit").isEmpty(),"Native keys did not edit prompt");
        aiSet("ai_local_edit","Escreva uma saudação curta em português, sem nomes.");shot("15-ia-prompt-com-teclas");aiClick("ai_done_edit");aiNode("ai_compose_tone");shot("16-ia-compositor");
        if(!withModel){aiClick("ai_back");toolbar(ToolbarKey.AI);aiNode("ai_missing_model");result.putString("model_path",new File(getTargetContext().getNoBackupFilesDir(),"offline-ai/model.gguf").getAbsolutePath());aiClick("ai_back");expect("texto original");return;}
        aiClick("ai_generate");String response=aiComplete();result.putString("offline_model_output",response);shot("17-ia-resposta-offline");
        aiClick("ai_edit_result");aiNode("ai_local_edit");type("ola");expect("texto original");aiSet("ai_local_edit","Texto revisado no aparelho.");aiClick("ai_done_edit");aiClick("ai_copy");aiClick("ai_replace");expect("Texto revisado no aparelho.");
        clear();type("Eu nao sei oque fazer.");toolbar(ToolbarKey.AI);aiClick("ai_review");String reviewed=aiComplete();result.putString("offline_review_output",reviewed);aiClick("ai_diff");aiNode("ai_original");check(aiText("ai_output").equals(reviewed),"Diff changed the result");shot("18-ia-revisao-no-teclado");aiClick("ai_diff");
        setOriginal("texto alterado");aiClick("ai_replace");check(aiText("ai_status").startsWith("O texto original mudou"),"Changed source was not rejected");check(aiText("ai_output").equals(reviewed),"Result lost");check(!aiNode("ai_replace").isEnabled(),"Stale replacement enabled");shot("19-ia-texto-alterado-protegido");aiClick("ai_back");expect("texto alterado");
        toolbar(ToolbarKey.AI);aiClick("ai_review");aiClick("ai_cancel");check(aiText("ai_status").startsWith("Cancelado"),"Cancellation failed");aiClick("ai_back");expect("texto alterado");
    }
    static final String CUSTOM_WORD="maravilhosaaaa";
    boolean dictionaryContains(String word)throws Exception {
        try(Cursor rows=getTargetContext().getContentResolver().query(android.provider.UserDictionary.Words.CONTENT_URI,new String[]{android.provider.UserDictionary.Words.WORD},android.provider.UserDictionary.Words.WORD+" = ?",new String[]{word},null)){return rows!=null&&rows.moveToFirst();}
    }
    Rect personalCandidate(String word)throws Exception {
        Rect[] rect={null};runOnMainSync(()->{
            View root=KeyboardSwitcher.getInstance().getStripContainer();
            SuggestionStripView strip=(SuggestionStripView)findView(root,v->v instanceof SuggestionStripView);
            if(strip==null)return;
            try{
                java.lang.reflect.Field field=SuggestionStripView.class.getDeclaredField("suggestedWords");field.setAccessible(true);
                helium314.keyboard.latin.SuggestedWords words=(helium314.keyboard.latin.SuggestedWords)field.get(strip);
                View candidate=findView(strip,v->{if(!(v instanceof TextView)||!(v.getTag() instanceof Integer)||!v.isShown())return false;int index=(Integer)v.getTag();return index>=0&&index<words.size()&&word.equalsIgnoreCase(words.getInfo(index).mWord);});
                if(candidate!=null){int[] xy=new int[2];candidate.getLocationOnScreen(xy);rect[0]=new Rect(xy[0],xy[1],xy[0]+candidate.getWidth(),xy[1]+candidate.getHeight());}
            }catch(ReflectiveOperationException error){throw new AssertionError(error);}
        });return rect[0];
    }
    void predict(String prefix,String word)throws Exception {
        clear();type(prefix);long deadline=SystemClock.uptimeMillis()+20000;Rect candidate;
        do{candidate=personalCandidate(word);if(candidate!=null)break;Thread.sleep(250);}while(SystemClock.uptimeMillis()<deadline);
        check(candidate!=null,"Missing personal prediction for "+prefix+": "+suggestions());shot("21-completar-"+prefix);tap(candidate);
        deadline=SystemClock.uptimeMillis()+10000;while(!text().trim().equalsIgnoreCase(word)&&SystemClock.uptimeMillis()<deadline)Thread.sleep(200);
        check(text().trim().equalsIgnoreCase(word),"Prediction did not insert complete word: "+text());
    }
    void personalDictionary(boolean restored)throws Exception {
        if(!restored){
            clear();type(CUSTOM_WORD);aiClick("Adicionar palavra ao dicionário");aiNode("Salvar palavra no dicionário");shot("20-adicionar-palavra");aiClick("Salvar palavra no dicionário");
            long deadline=SystemClock.uptimeMillis()+10000;while(!dictionaryContains(CUSTOM_WORD)&&SystemClock.uptimeMillis()<deadline)Thread.sleep(200);
        }
        check(dictionaryContains(CUSTOM_WORD),"Manual word was not persisted");predict("maravilhos",CUSTOM_WORD);shot("21-palavra-pessoal-sugerida");
        if(!restored){
            clear();type("maravilhos");long wait=SystemClock.uptimeMillis()+15000;Rect candidate;do{candidate=personalCandidate(CUSTOM_WORD);if(candidate!=null)break;Thread.sleep(200);}while(SystemClock.uptimeMillis()<wait);check(candidate!=null,"Full word candidate missing");long down=SystemClock.uptimeMillis();event(down,MotionEvent.ACTION_DOWN,new int[]{0},new Rect[]{candidate});Thread.sleep(750);event(down,MotionEvent.ACTION_UP,new int[]{0},new Rect[]{candidate});check(aiText("suggestion_full_word").equalsIgnoreCase(CUSTOM_WORD),"Long word preview was abbreviated");shot("26-palavra-completa");aiClick("Usar palavra");
            check(Settings.getValues().mUsePersonalizedDicts,"Personalization disabled");check(Settings.getValues().mBigramPredictionEnabled,"Next word prediction disabled");
            for(int i=0;i<8;i++){clear();type("zunibravo cafezinho ");Thread.sleep(250);}
        }
        predict("zuni","zunibravo");shot("22-palavra-aprendida");
        clear();type("zunibravo ");long deadline=SystemClock.uptimeMillis()+20000;Rect next;
        do{next=personalCandidate("cafezinho");if(next!=null)break;Thread.sleep(250);}while(SystemClock.uptimeMillis()<deadline);
        check(next!=null,"Learned next word missing: "+suggestions());result.putString("learned_next_word","cafezinho");shot("23-proxima-palavra-aprendida");tap(next);
        deadline=SystemClock.uptimeMillis()+10000;while(!text().trim().equalsIgnoreCase("zunibravo cafezinho")&&SystemClock.uptimeMillis()<deadline)Thread.sleep(200);
        check(text().trim().equalsIgnoreCase("zunibravo cafezinho"),"Next-word tap inserted wrong text: "+text());
        shell("input keyevent 3");Thread.sleep(2000); // leave the field normally so native history flushes before process death
        result.putString("dictionary_learning",restored?"Manual word, learned prefix and learned next word persisted after reinstall":"Explicit save of editor word, prefix completion and learning from repeated typing");
    }
    @Override public void onStart(){try{
        ui=getUiAutomation();AccessibilityServiceInfo info=ui.getServiceInfo();info.flags|=AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;ui.setServiceInfo(info);
        shots=new File(getTargetContext().getCacheDir(),"heli-shots");shots.mkdirs();
        shell("settings put secure show_ime_with_hard_keyboard 1");shell("ime enable br.com.tecladobr.heli.debug/helium314.keyboard.latin.LatinIME");shell("ime set br.com.tecladobr.heli.debug/helium314.keyboard.latin.LatinIME");
        Thread.sleep(10000);options(false,false);launch();
        if(phase.equals("verify_hybrid")){HybridTrial.run(this,result);result.putString("result","PASS");finish(Activity.RESULT_OK,result);return;}
        if(phase.equals("verify_dictionary")||phase.equals("verify_learning_persisted")){personalDictionary(phase.equals("verify_learning_persisted"));result.putString("result","PASS");finish(Activity.RESULT_OK,result);return;}
        if(phase.equals("verify_ai")||phase.equals("verify_ai_missing")){writing(phase.equals("verify_ai"));result.putString("result","PASS");result.putString("coverage","Offline native generation, compositor prompt, editable result, safe replacement and cancellation; missing-model state");finish(Activity.RESULT_OK,result);return;}
        if(phase.equals("verify_pins")){check(storedPinned(),"Stored pin lost after process restart");pinnedEntry(true);result.putString("result","PASS");result.putString("coverage","Pinned clipboard text survives app restart and reinstall with same certificate, and can be pasted");finish(Activity.RESULT_OK,result);return;}
        check(bounds('a',false).left>bounds('q',false).left,"A-L row must be centered");
        runOnMainSync(()->{for(Key key:main().getKeyboard().getSortedKeys())check(key.getCode()!=KeyCode.EMOJI&&key.getCode()!=KeyCode.LANGUAGE_SWITCH,"Bottom row contains emoji or globe");});
        shot("01-base-ptbr");
        long cedillaDown=SystemClock.uptimeMillis();Rect c=bounds('c',false);
        event(cedillaDown,MotionEvent.ACTION_DOWN,new int[]{0},new Rect[]{c});Thread.sleep(700);
        Rect cedilla=bounds('ç',true);event(cedillaDown,MotionEvent.ACTION_MOVE,new int[]{0},new Rect[]{cedilla});
        event(cedillaDown,MotionEvent.ACTION_UP,new int[]{0},new Rect[]{cedilla});expect("ç");shot("01-cedilha-subtecla");clear();
        Rect e=bounds('e',false),r=bounds('r',false);StringBuilder expected=new StringBuilder();long begin=SystemClock.uptimeMillis();
        for(int i=0;i<100;i++){long d=SystemClock.uptimeMillis();event(d,MotionEvent.ACTION_DOWN,new int[]{0},new Rect[]{e});event(d,MotionEvent.ACTION_POINTER_DOWN|(1<<8),new int[]{0,1},new Rect[]{e,r});boolean reverse=i%2==1;event(d,MotionEvent.ACTION_POINTER_UP|((reverse?1:0)<<8),new int[]{0,1},new Rect[]{e,r});event(d,MotionEvent.ACTION_UP,new int[]{reverse?0:1},new Rect[]{reverse?e:r});expected.append("er");}
        result.putLong("touch_injection_ms",SystemClock.uptimeMillis()-begin);expect(expected.toString());result.putInt("overlapping_characters",200);shot("02-toques-sobrepostos");
        toolbar(ToolbarKey.SELECT_ALL);
        long selectedDeadline=SystemClock.uptimeMillis()+5000;boolean selected=false;do{AccessibilityNodeInfo f=field();selected=Math.abs(f.getTextSelectionEnd()-f.getTextSelectionStart())==200;if(selected)break;Thread.sleep(100);}while(SystemClock.uptimeMillis()<selectedDeadline);check(selected,"SELECT_ALL did not select 200 characters");shot("03-texto-selecionado");
        toolbar(ToolbarKey.COPY);ClipboardManager clipboard=(ClipboardManager)getTargetContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData copied=clipboard.getPrimaryClip();check(copied!=null&&copied.getItemCount()>0&&expected.toString().contentEquals(copied.getItemAt(0).getText()),"COPY did not preserve the text");
        clear();toolbar(ToolbarKey.PASTE);expect(expected.toString());shot("03-edicao");
        clear();type("brzaplexia");Thread.sleep(1200);
        Rect savedWord=new Rect();runOnMainSync(()->{View v=findView(KeyboardSwitcher.getInstance().getStripContainer(),n->n instanceof TextView&&"brzaplexia".contentEquals(((TextView)n).getText())&&n.isShown());check(v!=null,"Typed word missing from suggestions");int[] xy=new int[2];v.getLocationOnScreen(xy);savedWord.set(xy[0],xy[1],xy[0]+v.getWidth(),xy[1]+v.getHeight());});
        long wordDown=SystemClock.uptimeMillis();event(wordDown,MotionEvent.ACTION_DOWN,new int[]{0},new Rect[]{savedWord});Thread.sleep(850);event(wordDown,MotionEvent.ACTION_UP,new int[]{0},new Rect[]{savedWord});Thread.sleep(500);
        AccessibilityNodeInfo add=find(n->n.getText()!=null&&"Adicionar ao dicionário".contentEquals(n.getText()));check(add!=null,"No add-to-dictionary menu");Rect addRect=new Rect();add.getBoundsInScreen(addRect);tap(addRect);Thread.sleep(800);
        try(android.database.Cursor cursor=getTargetContext().getContentResolver().query(android.provider.UserDictionary.Words.CONTENT_URI,new String[]{android.provider.UserDictionary.Words.WORD},"word = ?",new String[]{"brzaplexia"},null)){check(cursor!=null&&cursor.moveToFirst(),"Personal dictionary did not save word");}
        shot("04-palavra-adicionada");
        clear();type("voce");long deadline=SystemClock.uptimeMillis()+10000;List<String> words;
        do{words=suggestions();if(words.stream().anyMatch(s->s.equalsIgnoreCase("você")))break;Thread.sleep(200);}while(SystemClock.uptimeMillis()<deadline);
        check(words.stream().anyMatch(s->s.equalsIgnoreCase("você")),"PT-BR dictionary did not suggest você: "+words);result.putString("ptbr_suggestions",words.toString());shot("04-dicionario-portugues");
        clear();long d=SystemClock.uptimeMillis();event(d,MotionEvent.ACTION_DOWN,new int[]{0},new Rect[]{e});Thread.sleep(700);Rect accent=bounds('ê',true);event(d,MotionEvent.ACTION_MOVE,new int[]{0},new Rect[]{accent});shot("05-deslizando-subtecla");event(d,MotionEvent.ACTION_UP,new int[]{0},new Rect[]{accent});expect("ê");shot("06-acento-soltar");
        clear();type("brxnovissima.");expect("brxnovissima. ");
        options(true,true);launch();shot("07-inicio-maiusculas");type("bom.");expect("Bom. ");shot("07-espaco-imediato");
        type(" ");expect("Bom. ");type("dia ");expect("Bom. Dia ");
        clear();type("bom...");expect("Bom... ");
        clear();type("3.14 ");expect("3.14 ");
        clear();type("bom,dia ");expect("Bom, dia ");
        clear();type("bom.dia ");expect("Bom. Dia ");shot("07-maiusculas");
        clear();type("voce ");expect("Você ");shot("08-autocorrecao");
        options(false,false);launch();language("pt");toolbar(ToolbarKey.LANGUAGE);language("en");clear();type("hello ");expect("hello ");shot("09-ingles-globo");
        toolbar(ToolbarKey.LANGUAGE);language("pt");check(bounds('a',false).left>bounds('q',false).left,"Centered Portuguese layout lost after switch");shot("10-portugues-globo");pinnedEntry(false);
        result.putString("result","PASS");result.putString("coverage","Globe PT-BR/English switching, pinned clipboard shortcut, long-press pin, expiration exclusion, PT-BR dictionary, centered A-L layout, C long-press cedilla, emoji/globe removed from bottom row, toolbar language switch, add-word menu persisted in personal dictionary, autospace after punctuation, 200 overlapping letters including reversed release order, copy/paste, slide-to-select accent, sentence caps, autocorrection");finish(Activity.RESULT_OK,result);
    }catch(Throwable failure){try{shot("failure");}catch(Exception ignored){}StringWriter trace=new StringWriter();failure.printStackTrace(new PrintWriter(trace));result.putString("failure",trace.toString());result.putString("result","FAIL");finish(Activity.RESULT_CANCELED,result);}}
}

