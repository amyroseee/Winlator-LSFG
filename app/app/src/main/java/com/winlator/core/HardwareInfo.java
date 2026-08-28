package com.winlator.core;

import android.content.Context;
import android.os.Build;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small, conservative hardware presentation layer for diagnostics and shared metadata. */
public final class HardwareInfo {
    private static volatile String cachedSoc;
    private static volatile String cachedGpu;
    private HardwareInfo() {}

    public static String getSoc(Context context) {
        if (cachedSoc != null) return cachedSoc;
        String raw = firstNonEmpty(buildField("SOC_MODEL"), property("ro.soc.model"), buildField("HARDWARE"));
        String manufacturer = firstNonEmpty(buildField("SOC_MANUFACTURER"), property("ro.soc.manufacturer"), Build.MANUFACTURER);
        String upper = raw.toUpperCase(Locale.ROOT);
        if (upper.matches("SM6375|MSM6375")) return cachedSoc = "Snapdragon 695";
        if (upper.matches("SM6225|MSM6225")) return cachedSoc = "Snapdragon 680";
        if (upper.matches("SM4350|MSM4350")) return cachedSoc = "Snapdragon 480";
        if (isQualcomm(manufacturer, raw)) {
            if (raw.matches("(?i)qcom|qualcomm|qualcomm technologies,? inc\\.?|msm[0-9]+|kona|lahaina|taro")) return cachedSoc = "Qualcomm Snapdragon";
            return cachedSoc = clean(raw);
        }
        return cachedSoc = clean(raw);
    }

    public static String getGpu(Context context) {
        if (cachedGpu != null) return cachedGpu;
        String renderer = GPUHelper.glGetRenderer(context);
        Matcher adreno = Pattern.compile("Adreno\\s*(?:\\(TM\\))?\\s*([0-9]{3,4})", Pattern.CASE_INSENSITIVE).matcher(renderer == null ? "" : renderer);
        if (adreno.find()) return cachedGpu = "Adreno " + adreno.group(1);
        String value = clean(renderer);
        return cachedGpu = value.isEmpty() || isRawIdentifier(value) ? "Qualcomm GPU" : value;
    }

    private static boolean isQualcomm(String manufacturer, String raw) {
        String value = (manufacturer + " " + raw).toLowerCase(Locale.ROOT);
        return value.contains("qualcomm") || value.contains("qcom") || value.matches(".*\\bsm[0-9]+\\b.*") || value.matches(".*\\bmsm[0-9]+\\b.*");
    }

    private static boolean isRawIdentifier(String value) {
        String v = value.toLowerCase(Locale.ROOT);
        return v.matches("qcom|msm[0-9]+|kona|lahaina|taro|qualcomm");
    }

    private static String clean(String value) {
        if (value == null) return "";
        String result = value.trim().replaceAll("\\s+", " ");
        return result.isEmpty() ? "" : result;
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) if (value != null && !value.trim().isEmpty()) return value.trim();
        return "";
    }

    private static String buildField(String name) {
        try {
            Field field = Build.class.getField(name);
            Object value = field.get(null);
            return value == null ? "" : value.toString();
        } catch (Exception ignored) { return ""; }
    }

    private static String property(String name) {
        try {
            Class<?> clazz = Class.forName("android.os.SystemProperties");
            Method get = clazz.getMethod("get", String.class, String.class);
            return (String)get.invoke(null, name, "");
        } catch (Exception ignored) { return ""; }
    }
}
