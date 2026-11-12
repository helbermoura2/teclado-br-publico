// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;
import android.content.Context;
import android.os.Bundle;
import org.json.*;
import java.util.Locale;
final class MeasurementHistory {
    static void add(Context c,Bundle data) {
        try {
            JSONArray previous=new JSONArray(c.getSharedPreferences("writing-options",0).getString("measurements","[]"));
            JSONArray next=new JSONArray();
            JSONObject row=new JSONObject().put("backend",data.getString("backend","")).put("ms",data.getLong("duration_ms")).put("pss",data.getLong("peak_pss_kb")).put("energy",data.getLong("energy_nwh",-1)).put("time",System.currentTimeMillis());
            next.put(row);
            for(int i=0;i<Math.min(4,previous.length());i++)next.put(previous.get(i));
            c.getSharedPreferences("writing-options",0).edit().putString("measurements",next.toString()).commit();
        }
        catch(JSONException ignored) {
        }
    }
    static String describe(Context c) {
        try {
            JSONArray rows=new JSONArray(c.getSharedPreferences("writing-options",0).getString("measurements","[]"));
            StringBuilder text=new StringBuilder();
            for(int i=0;i<rows.length();i++) {
                JSONObject row=rows.getJSONObject(i);
                text.append(row.getString("backend")).append("\n").append(String.format(Locale.ROOT,"%.1f s · %.0f MB de memória amostrada",row.getLong("ms")/1000.0,row.getLong("pss")/1024.0));
                if(row.optLong("energy",-1)>=0)text.append(String.format(Locale.ROOT," · %.3f mWh do aparelho",row.getLong("energy")/1000000.0));
                text.append("\n\n");
            }
            return text.length()==0?"Nenhuma medição ainda.":text.toString();
        }
        catch(Exception e) {
            return "Nenhuma medição ainda.";
        }
    }
}

