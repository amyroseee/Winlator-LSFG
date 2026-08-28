package com.winlator.onlineconfig;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import com.winlator.R;
import com.winlator.container.ContainerManager;
import com.winlator.container.Shortcut;
import com.winlator.core.AppUtils;
import com.winlator.core.GeneralComponents;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Browser only; it deliberately delegates component installation to GeneralComponents. */
public final class OnlineConfigBrowserDialog {
    private OnlineConfigBrowserDialog() {}
    public static void show(Activity activity, ContainerManager manager) {
        LinearLayout box=new LinearLayout(activity); box.setOrientation(LinearLayout.VERTICAL); int p=16; box.setPadding(p,p,p,p);
        EditText query=new EditText(activity); query.setHint(R.string.search); box.addView(query);
        Button refreshButton=new Button(activity); refreshButton.setText(R.string.refresh); box.addView(refreshButton);
        TextView status=new TextView(activity); box.addView(status); ListView list=new ListView(activity); box.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        ArrayList<OnlineConfig> all=new ArrayList<>(); ArrayAdapter<String> adapter=new ArrayAdapter<>(activity,android.R.layout.simple_list_item_1,new ArrayList<>()); list.setAdapter(adapter);
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(R.string.online_game_configs).setView(box).setNegativeButton(android.R.string.cancel,null).create(); dialog.show();
        load(activity, status, adapter, all, query);
        refreshButton.setOnClickListener(v -> load(activity, status, adapter, all, query));
        query.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int d){}public void onTextChanged(CharSequence s,int a,int b,int c){refresh(adapter,all,s.toString());}public void afterTextChanged(android.text.Editable e){}});
        list.setOnItemClickListener((parent,v,pos,id)->{String shown=adapter.getItem(pos);for(OnlineConfig c:all)if(c.toString().equals(shown)){showConfigDetails(activity,manager,c);break;}});
    }
    private static void load(Activity activity, TextView status, ArrayAdapter<String> adapter, ArrayList<OnlineConfig> all, EditText query) {
        all.clear(); adapter.clear(); status.setText(R.string.loading);
        new OnlineConfigRepository(activity).refresh(items -> activity.runOnUiThread(() -> {
            all.addAll(items); status.setText(items.isEmpty() ? R.string.no_online_configs : R.string.online_configs_found); refresh(adapter, all, query.getText().toString());
        }));
    }
    private static void refresh(ArrayAdapter<String> a,List<OnlineConfig> all,String q){a.clear();String x=q.toLowerCase();Collections.sort(all,(l,r)->Integer.compare(OnlineConfigCompatibility.score(a.getContext(),r),OnlineConfigCompatibility.score(a.getContext(),l)));for(OnlineConfig c:all)if(c.toString().toLowerCase().contains(x)||c.gameName.toLowerCase().contains(x))a.add(c.toString());a.notifyDataSetChanged();}
    public static void showConfigDetails(Activity activity,ContainerManager manager,OnlineConfig c){
        org.json.JSONObject hardware=c.json.optJSONObject("hardware"), components=c.json.optJSONObject("components");
        StringBuilder b=new StringBuilder(activity.getString(R.string.device)).append("\n")
                .append(activity.getString(R.string.soc)).append(": ").append(hardware==null?activity.getString(R.string.not_specified):hardware.optString("soc",activity.getString(R.string.not_specified))).append("\n")
                .append(activity.getString(R.string.gpu)).append(": ").append(hardware==null?activity.getString(R.string.not_specified):hardware.optString("gpu",activity.getString(R.string.not_specified))).append("\n")
                .append(activity.getString(R.string.ram)).append(": ").append(hardware==null?activity.getString(R.string.not_specified):hardware.optString("ramMb",activity.getString(R.string.not_specified))).append("\n\n")
                .append(activity.getString(R.string.graphics)).append("\n")
                .append(activity.getString(R.string.screen_size)).append(": ").append(c.settings.optString("screenSize",c.settings.optString("resolution",activity.getString(R.string.not_specified)))).append("\n")
                .append(activity.getString(R.string.graphics_driver)).append(": ").append(c.settings.optString("graphicsDriver",activity.getString(R.string.not_specified))).append("\n")
                .append(activity.getString(R.string.dxwrapper)).append(": ").append(c.settings.optString("dxwrapper",activity.getString(R.string.not_specified))).append("\n\n")
                .append(activity.getString(R.string.cpu_box64)).append("\n")
                .append(activity.getString(R.string.box64_preset)).append(": ").append(c.settings.optString("box64Preset",activity.getString(R.string.not_specified))).append("\n")
                .append(activity.getString(R.string.compatibility)).append(": ").append(OnlineConfigCompatibility.label(activity,c));
        if (components != null) appendComponents(activity, b, components);
        new AlertDialog.Builder(activity).setTitle(R.string.preview_changes).setMessage(b.toString()).setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.apply, (d,w)->ensureComponents(activity,manager,c)).show();
    }
    private static void appendComponents(Activity activity, StringBuilder b, org.json.JSONObject components) {
        b.append("\n\n").append(activity.getString(R.string.required_components));
        for (java.util.Iterator<String> it = components.keys(); it.hasNext();) {
            String key = it.next();
            String value = components.optString(key, activity.getString(R.string.not_specified));
            if (key.equals("wine")) {
                b.append("\n").append(key).append(" ").append(value).append(" (" ).append(activity.getString(R.string.information_only)).append(")");
            } else {
                boolean installed = isInstalled(activity, key, value);
                b.append("\n").append(key).append(" ").append(value).append(" — ").append(activity.getString(installed ? R.string.installed : R.string.missing));
            }
        }
    }
    private static boolean isInstalled(Activity activity, String key, String value) {
        try {
            if (key.equals("wine")) return true;
            GeneralComponents.Type type = GeneralComponents.Type.valueOf(key.toUpperCase());
            return GeneralComponents.isBuiltinComponent(type, value) || GeneralComponents.getInstalledComponentNames(type, activity).contains(value);
        } catch (Exception ignored) { return false; }
    }
    private static void ensureComponents(Activity a,ContainerManager m,OnlineConfig c){
        org.json.JSONObject req=c.json.optJSONObject("components"); if(req==null){chooseShortcut(a,m,c);return;}
        String missing=null; GeneralComponents.Type type=null;
        try { for(java.util.Iterator<String> it=req.keys();it.hasNext();){String k=it.next(),v=req.optString(k);if(k.equals("wine")) continue;GeneralComponents.Type t=GeneralComponents.Type.valueOf(k.toUpperCase());if(!GeneralComponents.isBuiltinComponent(t,v)&&!GeneralComponents.getInstalledComponentNames(t,a).contains(v)){missing=v;type=t;break;}} } catch(Exception ignored) {}
        // Wine is environment metadata in this fork, not an installable dependency.
        if(missing==null){chooseShortcut(a,m,c);return;}
        if(type==null){new AlertDialog.Builder(a).setTitle(R.string.missing_component).setMessage(a.getString(R.string.config_requires_component,missing)).setPositiveButton(android.R.string.ok,null).show();return;}
        final GeneralComponents.Type downloadType=type; final String id=missing;
        new AlertDialog.Builder(a).setTitle(R.string.missing_component).setMessage(a.getString(R.string.config_requires_component,id)).setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.download,(d,w)->GeneralComponents.downloadKnownComponent(a,downloadType,id,ok->{if(ok)ensureComponents(a,m,c);else AppUtils.showToast(a,R.string.component_not_available);})).show();
    }
    private static void chooseShortcut(Activity a,ContainerManager m,OnlineConfig c){List<Shortcut> source=m.loadShortcuts(null);ArrayList<Shortcut> ss=new ArrayList<>();ArrayList<String> names=new ArrayList<>();for(Shortcut s:source)if(!s.file.isDirectory()&&matchesShortcut(c,s)){ss.add(s);names.add(s.name+" · "+s.container.getName());}if(ss.isEmpty()){AppUtils.showToast(a,R.string.no_compatible_shortcut);return;}new AlertDialog.Builder(a).setTitle(R.string.select_shortcut).setItems(names.toArray(new String[0]),(d,which)->{OnlineConfigApplier.apply(a,ss.get(which),c);AppUtils.showToast(a,R.string.configuration_applied);}).setNegativeButton(android.R.string.cancel,null).show();}
    private static boolean matchesShortcut(OnlineConfig config, Shortcut shortcut) { String game = compact(config.gameName); String name = compact(shortcut.name); String id = compact(config.gameId); String executable = compact(com.winlator.core.FileUtils.getBasename(shortcut.path == null ? "" : shortcut.path)); return game.equals(name) || game.contains(name) || name.contains(game) || id.equals(name) || id.contains(name) || name.contains(id) || (!executable.isEmpty() && (game.contains(executable) || executable.contains(game))); }
    private static String compact(String value) { return value == null ? "" : value.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", ""); }
    public static void publish(Activity activity, Shortcut shortcut) {
        try {
            org.json.JSONObject json = OnlineConfigExporter.create(shortcut, activity);
            OnlineConfigValidator.parse(json, json.getJSONObject("game").getString("id"), shortcut.name);
            new AlertDialog.Builder(activity).setTitle(R.string.publish_config).setMessage(publishSummary(activity, json)).setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.confirm, (dialog, which) -> {
                AppUtils.showToast(activity, R.string.publishing);
                OnlineConfigApi.publish(json.toString(), result -> activity.runOnUiThread(() -> AppUtils.showToast(activity, result.success ? R.string.published_successfully : (result.statusCode == 0 ? R.string.offline : R.string.publication_failed))));
            }).show();
        } catch (Exception e) { AppUtils.showToast(activity, R.string.invalid_configuration); }
    }

    private static String publishSummary(Activity activity, org.json.JSONObject json) {
        org.json.JSONObject game = json.optJSONObject("game");
        org.json.JSONObject hardware = json.optJSONObject("hardware");
        org.json.JSONObject settings = json.optJSONObject("settings");
        return activity.getString(R.string.game) + ": " + (game == null ? "" : game.optString("name")) + "\n\n"
                + activity.getString(R.string.device) + ": " + (hardware == null ? activity.getString(R.string.not_specified) : hardware.optString("soc", activity.getString(R.string.not_specified))) + "\n"
                + activity.getString(R.string.gpu) + ": " + (hardware == null ? activity.getString(R.string.not_specified) : hardware.optString("gpu", activity.getString(R.string.not_specified))) + "\n"
                + activity.getString(R.string.ram) + ": " + (hardware == null ? activity.getString(R.string.not_specified) : hardware.optString("ramMb", activity.getString(R.string.not_specified))) + "\n\n"
                + activity.getString(R.string.screen_size) + ": " + (settings == null ? "" : settings.optString("screenSize", activity.getString(R.string.not_specified))) + "\n"
                + activity.getString(R.string.graphics_driver) + ": " + (settings == null ? "" : settings.optString("graphicsDriver", activity.getString(R.string.not_specified))) + "\n"
                + activity.getString(R.string.dxwrapper) + ": " + (settings == null ? "" : settings.optString("dxwrapper", activity.getString(R.string.not_specified))) + "\n"
                + activity.getString(R.string.box64_preset) + ": " + (settings == null ? "" : settings.optString("box64Preset", activity.getString(R.string.not_specified)));
    }
}
