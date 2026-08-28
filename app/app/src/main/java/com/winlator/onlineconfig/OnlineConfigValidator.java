package com.winlator.onlineconfig;

import com.winlator.box64.Box64Preset;
import com.winlator.container.DXWrappers;
import com.winlator.container.GraphicsDrivers;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/** Deliberately stricter than a permissive JSON parser: remote data is untrusted. */
public final class OnlineConfigValidator {
    public static final int MAX_DOCUMENT_BYTES = 256 * 1024;
    private static final Set<String> TOP = new HashSet<>(Arrays.asList("id","gameId","game","name","author","version","hardware","settings","components","metadata"));
    private static final Set<String> SETTINGS = new HashSet<>(Arrays.asList("screenSize","resolution","graphicsDriver","dxwrapper","dxwrapperConfig","graphicsDriverConfig","wineVersion","box64Version","box64Preset","cpuList","cpuListWoW64","envVars","audioDriver","audioDriverConfig","wincomponents"));
    private static final Set<String> HARDWARE = new HashSet<>(Arrays.asList("soc","gpu","ramMb","androidVersion","winlatorVersion"));

    private OnlineConfigValidator() {}

    public static OnlineConfig parse(JSONObject source, String fallbackGameId, String fallbackGameName) throws JSONException {
        rejectUnknown(source, TOP);
        JSONObject game = source.optJSONObject("game");
        String gameId = source.optString("gameId", game != null ? game.optString("id", fallbackGameId) : fallbackGameId);
        String gameName = game != null ? game.optString("name", fallbackGameName) : source.optString("game", source.optString("name", fallbackGameName));
        String id = source.optString("id", "");
        if (!safeId(gameId) || !safeId(id.isEmpty() ? "config" : id) || gameName.length() > 120) throw new JSONException("Invalid config identity");
        if (game != null) {
            for (Iterator<String> it=game.keys(); it.hasNext();) { String key=it.next(); if (!key.equals("id")&&!key.equals("name")) throw new JSONException("Invalid game field"); }
        }
        JSONObject settings = source.optJSONObject("settings");
        if (settings == null) throw new JSONException("settings required");
        rejectUnknown(settings, SETTINGS);
        validateSettings(settings);
        JSONObject components = source.optJSONObject("components");
        if (components != null) {
            for (Iterator<String> it=components.keys();it.hasNext();) {
                String key=it.next(); if (!(key.equals("box64")||key.equals("turnip")||key.equals("dxvk")||key.equals("vkd3d")||key.equals("wined3d")||key.equals("wine"))) throw new JSONException("Unknown component");
                if (!components.get(key).toString().matches("[A-Za-z0-9._+-]{1,80}")) throw new JSONException("Invalid component");
            }
        }
        JSONObject hardware = source.optJSONObject("hardware");
        if (hardware != null) rejectUnknown(hardware, HARDWARE);
        JSONObject metadata = source.optJSONObject("metadata");
        if (metadata != null) rejectUnknown(metadata, new HashSet<>(Arrays.asList("title")));
        return new OnlineConfig(source, settings, id.isEmpty() ? gameId : id, gameId, gameName);
    }

    private static void validateSettings(JSONObject s) throws JSONException {
        for (String key : new String[]{"screenSize","resolution","graphicsDriver","dxwrapper","dxwrapperConfig","graphicsDriverConfig","wineVersion","box64Version","box64Preset","cpuList","cpuListWoW64","audioDriver","audioDriverConfig","wincomponents"}) {
            if (s.has(key) && !s.get(key).toString().matches("[A-Za-z0-9_.,+|=:/ -]{1,240}")) throw new JSONException("Invalid setting: " + key);
        }
        String size = s.optString("screenSize", s.optString("resolution", ""));
        if (!size.isEmpty() && !size.matches("[1-9][0-9]{2,4}x[1-9][0-9]{2,4}")) throw new JSONException("Invalid resolution");
        String driver = s.optString("graphicsDriver", "");
        if (!driver.isEmpty()) for (String v : driver.split(",")) if (!GraphicsDrivers.isVulkanDriver(v) && !GraphicsDrivers.isOpenGLDriver(v)) throw new JSONException("Invalid graphics driver");
        String wrapper = s.optString("dxwrapper", "");
        if (!wrapper.isEmpty() && !(DXWrappers.DXVK.equals(wrapper) || DXWrappers.VKD3D.equals(wrapper) || DXWrappers.WINED3D.equals(wrapper) || DXWrappers.CNC_DDRAW.equals(wrapper) || DXWrappers.D7VK.equals(wrapper))) throw new JSONException("Invalid wrapper");
        String preset = s.optString("box64Preset", "");
        if (!preset.isEmpty() && !(Box64Preset.STABILITY.equals(preset) || Box64Preset.CONSERVATIVE.equals(preset) || Box64Preset.INTERMEDIATE.equals(preset) || Box64Preset.PERFORMANCE.equals(preset) || Box64Preset.UNITY.equals(preset))) throw new JSONException("Invalid preset");
        if (s.has("envVars")) validateEnvVars(s.get("envVars"));
        for (String dangerous : new String[]{"execArgs","command","script","url","dll","exe","zip","base64","path","save","driveC","lsfg"}) if (s.has(dangerous)) throw new JSONException("Forbidden field");
    }

    private static void validateEnvVars(Object value) throws JSONException {
        if (value instanceof JSONArray) {
            JSONArray a = (JSONArray)value; if (a.length() > 32) throw new JSONException("Too many environment variables");
            for (int i=0;i<a.length();i++) validateEnv(a.getString(i));
        } else {
            String text = value.toString(); if (text.length() > 2048) throw new JSONException("Environment variables too large");
            if (!text.isEmpty()) for (String line : text.split("[ \\n]")) validateEnv(line);
        }
    }
    private static void validateEnv(String value) throws JSONException {
        if (!value.matches("[A-Z0-9_]{1,64}=[A-Za-z0-9_.,+:/%-]{0,180}")) throw new JSONException("Invalid environment variable");
        String k=value.substring(0,value.indexOf('='));
        if (k.contains("PATH") || k.contains("CMD") || k.contains("SHELL") || k.contains("EXEC")) throw new JSONException("Forbidden environment variable");
    }
    private static void rejectUnknown(JSONObject o, Set<String> allowed) throws JSONException {
        for (Iterator<String> it=o.keys();it.hasNext();) if (!allowed.contains(it.next())) throw new JSONException("Unknown field");
    }
    public static boolean safeId(String s) { return s != null && s.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,80}"); }
}
