package com.winlator.onlineconfig;

import com.winlator.container.Shortcut;
import org.json.JSONArray;
import org.json.JSONObject;
import android.content.Context;
import androidx.preference.PreferenceManager;

import java.util.Iterator;

public final class OnlineConfigApplier {
    private OnlineConfigApplier() {}
    public static void apply(Shortcut shortcut, OnlineConfig config) {
        JSONObject s=config.settings;
        copy(s, shortcut, "screenSize", "resolution");
        copy(s, shortcut, "graphicsDriver", null); copy(s, shortcut, "dxwrapper", null);
        copy(s, shortcut, "dxwrapperConfig", null); copy(s, shortcut, "graphicsDriverConfig", null);
        copy(s, shortcut, "box64Preset", null); copy(s, shortcut, "cpuList", null); copy(s, shortcut, "cpuListWoW64", null);
        copy(s, shortcut, "audioDriver", null); copy(s, shortcut, "audioDriverConfig", null); copy(s, shortcut, "wincomponents", null);
        if (s.has("envVars")) { Object value=s.opt("envVars"); StringBuilder b=new StringBuilder(); if(value instanceof JSONArray){JSONArray a=(JSONArray)value;for(int i=0;i<a.length();i++){if(i>0)b.append(' ');b.append(a.optString(i));}}else b.append(value); shortcut.putExtra("envVars", b.toString()); }
        // LSFG, executable arguments, files and all unknown fields are intentionally untouched.
        shortcut.saveData();
    }
    public static void apply(Context context, Shortcut shortcut, OnlineConfig config) {
        apply(shortcut, config);
        JSONObject s = config.settings;
        if (s.has("wineVersion")) { shortcut.container.setWineVersion(s.optString("wineVersion")); shortcut.container.saveData(); }
        if (s.has("box64Version")) PreferenceManager.getDefaultSharedPreferences(context).edit().putString("box64_version", s.optString("box64Version")).remove("current_box64_version").apply();
    }
    private static void copy(JSONObject o, Shortcut s, String key, String alias) { String k=o.has(key)?key:alias; if(k!=null && o.has(k)) s.putExtra(key, o.optString(k)); }
}
