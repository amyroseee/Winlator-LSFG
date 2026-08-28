package com.winlator.onlineconfig;

import android.content.Context;
import com.winlator.core.Callback;
import com.winlator.core.FileUtils;
import com.winlator.core.HttpUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Reads the public GitHub tree: main index -> game index -> config document. */
public final class OnlineConfigRepository {
    public static final String RAW_ROOT = "https://raw.githubusercontent.com/amyroosee6/winlator-online-configs/main/";
    private final Context context;
    private volatile boolean usedCache;

    public OnlineConfigRepository(Context context) { this.context = context.getApplicationContext(); }
    public void refresh(Callback<List<OnlineConfig>> callback) { loadAll(callback); }
    public boolean isUsingCache() { return usedCache; }

    public void loadAll(Callback<List<OnlineConfig>> callback) {
        usedCache = false;
        fetch("index.json", text -> {
            ArrayList<String> ids = new ArrayList<>();
            try {
                JSONObject root = new JSONObject(text == null ? "{}" : text);
                JSONArray games = root.optJSONArray("games");
                if (games != null) for (int i = 0; i < games.length(); i++) {
                    JSONObject game = games.optJSONObject(i);
                    String id = game != null ? game.optString("id", "") : games.optString(i, "");
                    if (OnlineConfigValidator.safeId(id) && !isTestId(id) && !ids.contains(id)) ids.add(id);
                }
            } catch (Exception ignored) {}
            ArrayList<OnlineConfig> result = new ArrayList<>();
            if (ids.isEmpty()) { callback.call(result); return; }
            final int[] pending = {ids.size()};
            for (String id : ids) loadGame(id, items -> { result.addAll(items); if (--pending[0] == 0) callback.call(result); });
        });
    }

    private void loadGame(String gameId, Callback<List<OnlineConfig>> callback) {
        fetch("games/" + gameId + "/index.json", text -> {
            ArrayList<OnlineConfig> result = new ArrayList<>();
            try {
                JSONObject root = new JSONObject(text == null ? "{}" : text);
                JSONArray configs = root.optJSONArray("configs");
                if (configs != null) {
                    final int[] pending = {configs.length()};
                    if (pending[0] == 0) { callback.call(result); return; }
                    for (int i = 0; i < configs.length(); i++) {
                        JSONObject item = configs.optJSONObject(i);
                        String path = item != null ? item.optString("path", "") : "";
                        String id = item != null ? item.optString("id", "") : "";
                        if (path.isEmpty() && OnlineConfigValidator.safeId(id)) path = "games/" + gameId + "/configs/" + id + ".json";
                        final String configPath = path;
                        if (configPath.isEmpty() || !configPath.startsWith("games/" + gameId + "/configs/")) { if (--pending[0] == 0) callback.call(result); continue; }
                        fetch(configPath, configText -> {
                            try { if (configText != null) { OnlineConfig config = OnlineConfigValidator.parse(new JSONObject(configText), gameId, gameId); if (config.gameId.equals(gameId) && !isTestId(config.gameId) && !containsId(result, config.id)) result.add(config); } } catch (Exception ignored) {}
                            if (--pending[0] == 0) callback.call(result);
                        });
                    }
                    return;
                }
            } catch (Exception ignored) {}
            callback.call(result);
        });
    }

    private static boolean containsId(List<OnlineConfig> configs, String id) { for (OnlineConfig config : configs) if (config.id.equals(id)) return true; return false; }
    private static boolean isTestId(String id) { return id != null && (id.equalsIgnoreCase("worker-smoke-test") || id.toLowerCase().contains("smoke-test")); }

    private void fetch(String relativePath, Callback<String> callback) {
        if (relativePath == null || relativePath.contains("..") || relativePath.startsWith("http") || relativePath.startsWith("/")) { callback.call(null); return; }
        File directory = new File(context.getCacheDir(), "online_configs");
        if (!directory.isDirectory()) directory.mkdirs();
        File cache = new File(directory, relativePath.replace('/', '_'));
        HttpUtils.download(RAW_ROOT + relativePath, text -> {
            if (text != null && text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= OnlineConfigValidator.MAX_DOCUMENT_BYTES) {
                FileUtils.writeString(cache, text); callback.call(text);
            } else { usedCache = true; callback.call(cache.isFile() ? FileUtils.readString(cache) : null); }
        });
    }
}
