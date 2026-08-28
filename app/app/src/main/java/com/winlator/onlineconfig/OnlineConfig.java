package com.winlator.onlineconfig;

import org.json.JSONObject;

/** A validated, configuration-only document from the public catalog. */
public final class OnlineConfig {
    public final JSONObject json;
    public final JSONObject settings;
    public final String id;
    public final String gameId;
    public final String gameName;

    OnlineConfig(JSONObject json, JSONObject settings, String id, String gameId, String gameName) {
        this.json = json;
        this.settings = settings;
        this.id = id;
        this.gameId = gameId;
        this.gameName = gameName;
    }

    public String getDisplayTitle() {
        JSONObject metadata = json.optJSONObject("metadata");
        String title = metadata == null ? "" : metadata.optString("title", "").trim();
        if (!title.isEmpty()) return title;
        JSONObject hardware = json.optJSONObject("hardware");
        String soc = hardware == null ? "" : hardware.optString("soc", "").trim();
        String gpu = hardware == null ? "" : hardware.optString("gpu", "").trim();
        String preset = settings.optString("box64Preset", "").trim();
        String resolution = settings.optString("screenSize", settings.optString("resolution", "")).trim();
        String device = !soc.isEmpty() && !gpu.isEmpty() && !soc.equalsIgnoreCase(gpu) ? soc + " • " + gpu : (!gpu.isEmpty() ? gpu : soc);
        String profile = !preset.isEmpty() && !resolution.isEmpty() ? preset + " • " + resolution : (!preset.isEmpty() ? preset : resolution);
        if (!device.isEmpty() && !profile.isEmpty()) return device + "\n" + profile;
        if (!device.isEmpty()) return device;
        return gameName;
    }

    @Override public String toString() { return getDisplayTitle(); }
}
