package com.winlator.onlineconfig;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import com.winlator.core.HardwareInfo;
import java.util.Locale;

public final class OnlineConfigCompatibility {
    private OnlineConfigCompatibility() {}
    public static int score(Context c, OnlineConfig config) {
        int score=0; org.json.JSONObject h=config.json.optJSONObject("hardware"); if(h==null)return score;
        String soc=h.optString("soc","").toLowerCase(Locale.ROOT), gpu=h.optString("gpu","").toLowerCase(Locale.ROOT);
        String local=(HardwareInfo.getSoc(c)+" "+HardwareInfo.getGpu(c)+" "+Build.MODEL+" "+Build.MANUFACTURER).toLowerCase(Locale.ROOT);
        if(!soc.isEmpty()&&!isUnknown(soc)&&local.contains(soc))score+=50; if(!gpu.isEmpty()&&!isUnknown(gpu)&&local.contains(gpu))score+=30;
        ActivityManager am=(ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE); ActivityManager.MemoryInfo mi=new ActivityManager.MemoryInfo();if(am!=null)am.getMemoryInfo(mi);long ram=mi.totalMem/(1024*1024), wanted=h.optLong("ramMb",0);if(wanted>0)score+=Math.max(0,20-(int)Math.min(20,Math.abs(ram-wanted)/512));
        String v=h.optString("winlatorVersion","");String localVersion="";try{localVersion=c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName;}catch(Exception ignored){}if(v.isEmpty()||v.equals(localVersion))score+=10;return score;
    }

    private static boolean isUnknown(String value) { String v=value.toLowerCase(Locale.ROOT); return v.equals("qcom")||v.equals("qualcomm")||v.equals("msm")||v.equals("unknown"); }

    public static String label(Context c, OnlineConfig config) {
        int value = score(c, config);
        return c.getString(value >= 70 ? com.winlator.R.string.excellent_match : value >= 50 ? com.winlator.R.string.compatible_match : value >= 20 ? com.winlator.R.string.partial_match : com.winlator.R.string.unknown_compatibility);
    }
}
