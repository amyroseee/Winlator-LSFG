package com.winlator.onlineconfig;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import com.winlator.container.Shortcut;
import com.winlator.core.FileUtils;
import com.winlator.core.DefaultVersion;
import com.winlator.container.GraphicsDrivers;
import com.winlator.container.DXWrappers;
import com.winlator.core.KeyValueSet;
import com.winlator.core.HardwareInfo;
import androidx.preference.PreferenceManager;
import org.json.JSONObject;
import java.io.File;

/** Produces the same v1 document accepted by the Worker and reader. */
public final class OnlineConfigExporter {
    private OnlineConfigExporter() {}

    public static JSONObject create(Shortcut shortcut, Context context) {
        JSONObject document = new JSONObject();
        JSONObject game = new JSONObject();
        JSONObject hardware = new JSONObject();
        JSONObject settings = new JSONObject();
        JSONObject components = new JSONObject();
        try {
            String gameId = slug(shortcut.name);
            game.put("id", gameId);
            game.put("name", shortcut.name);
            document.put("version", 1);
            document.put("game", game);

            hardware.put("soc", HardwareInfo.getSoc(context));
            hardware.put("gpu", HardwareInfo.getGpu(context));
            ActivityManager manager = (ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
            if (manager != null) manager.getMemoryInfo(memory);
            hardware.put("ramMb", memory.totalMem / (1024 * 1024));
            hardware.put("winlatorVersion", context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName);
            document.put("hardware", hardware);

            put(settings, "screenSize", shortcut.getExtra("screenSize", shortcut.container.getScreenSize()));
            put(settings, "graphicsDriver", shortcut.getExtra("graphicsDriver", shortcut.container.getGraphicsDriver()));
            put(settings, "dxwrapper", shortcut.getExtra("dxwrapper", shortcut.container.getDXWrapper()));
            put(settings, "dxwrapperConfig", shortcut.getExtra("dxwrapperConfig", shortcut.container.getDXWrapperConfig()));
            put(settings, "graphicsDriverConfig", shortcut.getExtra("graphicsDriverConfig", shortcut.container.getGraphicsDriverConfig()));
            put(settings, "box64Preset", shortcut.getExtra("box64Preset", shortcut.container.getBox64Preset()));
            put(settings, "cpuList", shortcut.getExtra("cpuList", shortcut.container.getCPUList()));
            put(settings, "cpuListWoW64", shortcut.getExtra("cpuListWoW64", shortcut.container.getCPUListWoW64()));
            put(settings, "audioDriver", shortcut.getExtra("audioDriver", shortcut.container.getAudioDriver()));
            put(settings, "wincomponents", shortcut.getExtra("wincomponents", shortcut.container.getWinComponents()));
            String envVars = shortcut.getExtra("envVars", "");
            if (!envVars.isEmpty()) settings.put("envVars", envVars);
            document.put("settings", settings);
            String box64 = PreferenceManager.getDefaultSharedPreferences(context).getString("box64_version", DefaultVersion.BOX64);
            components.put("box64", box64);
            String[] drivers = GraphicsDrivers.parseIdentifiers(shortcut.getExtra("graphicsDriver", shortcut.container.getGraphicsDriver()));
            if (GraphicsDrivers.TURNIP.equals(drivers[0])) components.put("turnip", GraphicsDrivers.parseConfigs(drivers[0], shortcut.getExtra("graphicsDriverConfig", shortcut.container.getGraphicsDriverConfig()))[0].get("version", DefaultVersion.TURNIP));
            String wrapper = shortcut.getExtra("dxwrapper", shortcut.container.getDXWrapper());
            KeyValueSet[] wrapperConfig = DXWrappers.parseConfigs(wrapper, shortcut.getExtra("dxwrapperConfig", shortcut.container.getDXWrapperConfig()));
            if (DXWrappers.DXVK.equals(wrapper)) components.put("dxvk", wrapperConfig[0].get("version", DefaultVersion.DXVK(drivers[0])));
            if (DXWrappers.VKD3D.equals(wrapper)) components.put("vkd3d", wrapperConfig[1].get("version", DefaultVersion.VKD3D));
            components.put("wine", shortcut.container.getWineVersion());
            document.put("components", components);
        } catch (Exception ignored) {}
        return document;
    }

    public static File save(Context context, Shortcut shortcut) {
        File directory = new File(context.getExternalFilesDir(null), "online-configs");
        if (!directory.isDirectory()) directory.mkdirs();
        File file = new File(directory, slug(shortcut.name) + ".json");
        try { FileUtils.writeString(file, create(shortcut, context).toString(2)); } catch (Exception ignored) {}
        return file;
    }

    private static void put(JSONObject object, String key, String value) throws Exception {
        if (value != null && !value.isEmpty()) object.put(key, value);
    }

    public static String slug(String value) {
        String result = value.replaceAll("[^A-Za-z0-9]+", "-").replaceAll("^-+|-+$", "").toLowerCase();
        return result.isEmpty() ? "game" : result.substring(0, Math.min(80, result.length()));
    }
}
