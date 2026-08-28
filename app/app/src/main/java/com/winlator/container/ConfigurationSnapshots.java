package com.winlator.container;

import org.json.JSONException;
import org.json.JSONObject;

/** Persistent configuration-only snapshots. Files in the prefix and user data are never included. */
public final class ConfigurationSnapshots {
    public static final String PREVIOUS = "previousConfiguration";
    public static final String WORKING = "workingConfiguration";

    private ConfigurationSnapshots() {}

    public static String captureContainer(Container c) {
        JSONObject o = new JSONObject();
        put(o, "screenSize", c.getScreenSize()); put(o, "envVars", c.getEnvVars());
        put(o, "graphicsDriver", c.getGraphicsDriver()); put(o, "dxwrapper", c.getDXWrapper());
        put(o, "dxwrapperConfig", c.getDXWrapperConfig()); put(o, "graphicsDriverConfig", c.getGraphicsDriverConfig());
        put(o, "audioDriverConfig", c.getAudioDriverConfig()); put(o, "audioDriver", c.getAudioDriver());
        put(o, "wincomponents", c.getWinComponents()); put(o, "drives", c.getDrives());
        put(o, "hudMode", c.getHUDMode()); put(o, "startupSelection", c.getStartupSelection());
        put(o, "cpuList", c.getCPUList()); put(o, "cpuListWoW64", c.getCPUListWoW64());
        put(o, "box64Preset", c.getBox64Preset()); put(o, "wineVersion", c.getWineVersion());
        put(o, "desktopTheme", c.getDesktopTheme());
        put(o, "lsfgEnabled", c.getExtra("lsfgEnabled")); put(o, "lsfgMultiplier", c.getExtra("lsfgMultiplier"));
        put(o, "lsfgFlowScale", c.getExtra("lsfgFlowScale")); put(o, "lsfgPreset", c.getExtra("lsfgPreset"));
        put(o, "lsfgPerformanceMode", c.getExtra("lsfgPerformanceMode"));
        try { o.put("extraData", c.getConfigurationExtras()); } catch (JSONException ignored) {}
        return o.toString();
    }

    public static void applyContainer(Container c, String snapshot) throws JSONException {
        JSONObject o = new JSONObject(snapshot);
        if (o.has("screenSize")) c.setScreenSize(o.getString("screenSize"));
        if (o.has("envVars")) c.setEnvVars(o.getString("envVars"));
        if (o.has("graphicsDriver")) c.setGraphicsDriver(o.getString("graphicsDriver"));
        if (o.has("dxwrapper")) c.setDXWrapper(o.getString("dxwrapper"));
        if (o.has("dxwrapperConfig")) c.setDXWrapperConfig(o.getString("dxwrapperConfig"));
        if (o.has("graphicsDriverConfig")) c.setGraphicsDriverConfig(o.getString("graphicsDriverConfig"));
        if (o.has("audioDriverConfig")) c.setAudioDriverConfig(o.getString("audioDriverConfig"));
        if (o.has("audioDriver")) c.setAudioDriver(o.getString("audioDriver"));
        if (o.has("wincomponents")) c.setWinComponents(o.getString("wincomponents"));
        if (o.has("drives")) c.setDrives(o.getString("drives"));
        if (o.has("hudMode")) c.setHUDMode((byte)o.getInt("hudMode"));
        if (o.has("startupSelection")) c.setStartupSelection((byte)o.getInt("startupSelection"));
        if (o.has("cpuList")) c.setCPUList(nullableString(o, "cpuList"));
        if (o.has("cpuListWoW64")) c.setCPUListWoW64(nullableString(o, "cpuListWoW64"));
        if (o.has("box64Preset")) c.setBox64Preset(o.getString("box64Preset"));
        if (o.has("wineVersion")) c.setWineVersion(o.getString("wineVersion"));
        if (o.has("desktopTheme")) c.setDesktopTheme(o.getString("desktopTheme"));
        if (o.has("extraData")) c.setConfigurationExtras(o.getJSONObject("extraData"));
        else {
            String[] extras = {"lsfgEnabled", "lsfgMultiplier", "lsfgFlowScale", "lsfgPreset", "lsfgPerformanceMode"};
            for (String key : extras) if (o.has(key)) c.putExtra(key, o.getString(key)); else c.putExtra(key, null);
        }
    }

    public static String captureShortcut(Shortcut s) {
        return s.getConfigurationExtras().toString();
    }

    public static void applyShortcut(Shortcut s, String snapshot) throws JSONException {
        s.setConfigurationExtras(new JSONObject(snapshot));
    }

    private static void put(JSONObject o, String key, Object value) {
        try { o.put(key, value == null ? JSONObject.NULL : value); } catch (JSONException ignored) {}
    }

    private static String nullableString(JSONObject o, String key) {
        return o.isNull(key) ? null : o.optString(key, null);
    }
}
