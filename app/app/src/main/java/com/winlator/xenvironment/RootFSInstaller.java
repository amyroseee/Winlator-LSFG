package com.winlator.xenvironment;

import android.content.Context;
import android.system.Os;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.winlator.MainActivity;
import com.winlator.R;
import com.winlator.SettingsFragment;
import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.core.AppUtils;
import com.winlator.core.DownloadProgressDialog;
import com.winlator.core.FileUtils;
import com.winlator.core.PreloaderDialog;
import com.winlator.core.TarCompressorUtils;
import com.winlator.core.WineInfo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public abstract class RootFSInstaller {
    public static final byte LATEST_VERSION = 22; // TODO increment it on rootfs update
    public static final byte UPDATE_WINEPREFIX_VERSION = 16; // set it if main wine version change
    public static final String FILENAME = "rootfs.tzst";
    private static final String LSFG_LIBRARY_ASSET = "lsfg-vk/liblsfg-vk.so";
    private static final String LSFG_MANIFEST_ASSET = "lsfg-vk/VkLayer_LS_frame_generation.json";

    private static String getAssetSHA256(Context context, String assetPath) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[8192];
        try (InputStream inputStream = context.getAssets().open(assetPath)) {
            int count;
            while ((count = inputStream.read(buffer)) != -1) digest.update(buffer, 0, count);
        }

        StringBuilder result = new StringBuilder();
        for (byte value : digest.digest()) result.append(String.format("%02x", value & 0xff));
        return result.toString();
    }

    private static String getFileSHA256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[8192];
        try (InputStream inputStream = new FileInputStream(file)) {
            int count;
            while ((count = inputStream.read(buffer)) != -1) digest.update(buffer, 0, count);
        }

        StringBuilder result = new StringBuilder();
        for (byte value : digest.digest()) result.append(String.format("%02x", value & 0xff));
        return result.toString();
    }

    private static boolean isLSFGStampCurrent(File stampFile, String expectedStamp) {
        try {
            return stampFile.isFile() && expectedStamp.equals(FileUtils.readString(stampFile).trim());
        }
        catch (Exception e) {
            return false;
        }
    }

    /**
     * Stages the bundled LSFG-VK implicit layer in the RootFS. The manifest keeps the layer
     * disabled unless ENABLE_LSFG=1 is explicitly supplied for an enabled LSFG-VK container.
     * The staged library is AArch64/glibc and resolves exclusively against the guest RootFS.
     */
    public static boolean installLSFGVKLayer(Context context, RootFS rootFS) {
        try {
            File libDir = rootFS.getLibDir();
            File manifestDir = new File(rootFS.getRootDir(), "usr/share/vulkan/implicit_layer.d");
            if ((!libDir.isDirectory() && !libDir.mkdirs()) ||
                (!manifestDir.isDirectory() && !manifestDir.mkdirs())) {
                Log.e("RootFSInstaller", "Failed to create LSFG-VK staging directories");
                return false;
            }

            File libraryFile = new File(libDir, "liblsfg-vk.so");
            File manifestFile = new File(manifestDir, "VkLayer_LS_frame_generation.json");
            File stampFile = new File(libDir, ".lsfg-vk-stamp");
            long librarySize = FileUtils.getSize(context, LSFG_LIBRARY_ASSET);
            long manifestSize = FileUtils.getSize(context, LSFG_MANIFEST_ASSET);
            String libraryHash = getAssetSHA256(context, LSFG_LIBRARY_ASSET);
            String manifestHash = getAssetSHA256(context, LSFG_MANIFEST_ASSET);
            String expectedStamp = libraryHash+":"+manifestHash;

            if (isLSFGStampCurrent(stampFile, expectedStamp) &&
                libraryFile.isFile() && libraryFile.length() == librarySize &&
                libraryHash.equals(getFileSHA256(libraryFile)) &&
                manifestFile.isFile() && manifestFile.length() == manifestSize &&
                manifestHash.equals(getFileSHA256(manifestFile))) {
                FileUtils.chmod(libraryFile, 0644);
                FileUtils.chmod(manifestFile, 0644);
                Log.d("RootFSInstaller", "LSFG-VK layer is already current");
                return true;
            }

            if (!libraryFile.isFile() || libraryFile.length() != librarySize || !libraryFile.canRead() ||
                !libraryHash.equals(getFileSHA256(libraryFile))) {
                File stagingFile = new File(libDir, "liblsfg-vk.so.staging");
                FileUtils.copy(context, LSFG_LIBRARY_ASSET, stagingFile);
                if (!stagingFile.isFile() || stagingFile.length() != librarySize ||
                    !libraryHash.equals(getFileSHA256(stagingFile))) {
                    stagingFile.delete();
                    Log.e("RootFSInstaller", "Failed to stage LSFG-VK library");
                    return false;
                }
                Os.rename(stagingFile.getAbsolutePath(), libraryFile.getAbsolutePath());
            }

            if (!manifestFile.isFile() || manifestFile.length() != manifestSize || !manifestFile.canRead() ||
                !manifestHash.equals(getFileSHA256(manifestFile))) {
                File stagingManifest = new File(manifestDir, "VkLayer_LS_frame_generation.json.staging");
                FileUtils.copy(context, LSFG_MANIFEST_ASSET, stagingManifest);
                if (!stagingManifest.isFile() || stagingManifest.length() != manifestSize ||
                    !manifestHash.equals(getFileSHA256(stagingManifest))) {
                    stagingManifest.delete();
                    Log.e("RootFSInstaller", "Failed to stage LSFG-VK manifest");
                    return false;
                }
                Os.rename(stagingManifest.getAbsolutePath(), manifestFile.getAbsolutePath());
            }
            FileUtils.chmod(libraryFile, 0644);
            FileUtils.chmod(manifestFile, 0644);
            if (!FileUtils.writeString(stampFile, expectedStamp)) {
                Log.e("RootFSInstaller", "Failed to write LSFG-VK staging stamp");
                return false;
            }
            Log.i("RootFSInstaller", "LSFG-VK layer installed in RootFS");
            return true;
        }
        catch (Exception e) {
            Log.e("RootFSInstaller", "Failed to stage LSFG-VK layer", e);
            return false;
        }
    }

    private static void resetContainerRFSVersions(Context context) {
        ContainerManager manager = new ContainerManager(context);
        for (Container container : manager.getContainers()) {
            String rfsVersion = container.getExtra("rfsVersion");
            String wineVersion = container.getWineVersion();
            if (!rfsVersion.isEmpty() && WineInfo.isMainWineVersion(wineVersion) && Short.parseShort(rfsVersion) <= UPDATE_WINEPREFIX_VERSION) {
                container.putExtra("wineprefixNeedsUpdate", "t");
            }

            container.putExtra("rfsVersion", null);
            container.saveData();
        }
    }

    public static void install(final MainActivity activity) {
        AppUtils.keepScreenOn(activity);
        RootFS rootFS = RootFS.find(activity);
        final File rootDir = rootFS.getRootDir();

        SettingsFragment.resetPreferenceVersions(activity);

        final DownloadProgressDialog dialog = new DownloadProgressDialog(activity);
        dialog.show(R.string.installing_system_files);
        Executors.newSingleThreadExecutor().execute(() -> {
            clearRootDir(rootDir);
            final long contentLength = TarCompressorUtils.getContentLength(TarCompressorUtils.Type.ZSTD, activity, FILENAME, rootDir);
            AtomicLong totalSizeRef = new AtomicLong();

            boolean success = TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, activity, FILENAME, rootDir, (file, size) -> {
                if (size > 0) {
                    long totalSize = totalSizeRef.addAndGet(size);
                    final int progress = (int)(((float)totalSize / contentLength) * 100);
                    activity.runOnUiThread(() -> dialog.setProgress(progress));
                }
                return file;
            });

            if (success) {
                rootFS.createRFSVersionFile(LATEST_VERSION);
                installLSFGVKLayer(activity, rootFS);
                resetContainerRFSVersions(activity);
            }
            else AppUtils.showToast(activity, R.string.unable_to_install_system_files);

            dialog.closeOnUiThread();
        });
    }

    public static void installIfNeeded(final MainActivity activity) {
        RootFS rootFS = RootFS.find(activity);
        if (!rootFS.isValid() || rootFS.getVersion() < LATEST_VERSION) install(activity);
        else Executors.newSingleThreadExecutor().execute(() -> installLSFGVKLayer(activity, rootFS));
    }

    private static void clearOptDir(File optDir) {
        File[] files = optDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.getName().equals("installed-wine")) continue;
                FileUtils.delete(file);
            }
        }
    }

    private static void clearRootDir(File rootDir) {
        if (rootDir.isDirectory()) {
            File[] files = rootDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        String name = file.getName();
                        if (name.equals("home") || name.equals("opt")) {
                            if (name.equals("opt")) clearOptDir(file);
                            continue;
                        }
                    }
                    FileUtils.delete(file);
                }
            }
        }
        else rootDir.mkdirs();
    }

    public static void generateCompactContainerPattern(final AppCompatActivity activity) {
        AppUtils.keepScreenOn(activity);
        PreloaderDialog preloaderDialog = new PreloaderDialog(activity);
        preloaderDialog.show(R.string.loading);
        Executors.newSingleThreadExecutor().execute(() -> {
            File[] srcFiles, dstFiles;
            File rootDir = RootFS.find(activity).getRootDir();
            File wineSystem32Dir = new File(rootDir, "/opt/wine/lib/wine/x86_64-windows");
            File wineSysWoW64Dir = new File(rootDir, "/opt/wine/lib/wine/i386-windows");

            File containerPatternDir = new File(activity.getCacheDir(), "container_pattern");
            FileUtils.delete(containerPatternDir);
            TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, activity, "container_pattern.tzst", containerPatternDir);

            File containerSystem32Dir = new File(containerPatternDir, ".wine/drive_c/windows/system32");
            File containerSysWoW64Dir = new File(containerPatternDir, ".wine/drive_c/windows/syswow64");

            dstFiles = containerSystem32Dir.listFiles();
            srcFiles = wineSystem32Dir.listFiles();

            ArrayList<String> system32Files = new ArrayList<>();
            ArrayList<String> syswow64Files = new ArrayList<>();

            for (File dstFile : dstFiles) {
                for (File srcFile : srcFiles) {
                    if (dstFile.getName().equals(srcFile.getName())) {
                        if (FileUtils.contentEquals(srcFile, dstFile)) system32Files.add(srcFile.getName());
                        break;
                    }
                }
            }

            dstFiles = containerSysWoW64Dir.listFiles();
            srcFiles = wineSysWoW64Dir.listFiles();

            for (File dstFile : dstFiles) {
                for (File srcFile : srcFiles) {
                    if (dstFile.getName().equals(srcFile.getName())) {
                        if (FileUtils.contentEquals(srcFile, dstFile)) syswow64Files.add(srcFile.getName());
                        break;
                    }
                }
            }

            try {
                JSONObject data = new JSONObject();

                JSONArray system32JSONArray = new JSONArray();
                for (String name : system32Files) {
                    FileUtils.delete(new File(containerSystem32Dir, name));
                    system32JSONArray.put(name);
                }
                data.put("system32", system32JSONArray);

                JSONArray syswow64JSONArray = new JSONArray();
                for (String name : syswow64Files) {
                    FileUtils.delete(new File(containerSysWoW64Dir, name));
                    syswow64JSONArray.put(name);
                }
                data.put("syswow64", syswow64JSONArray);

                FileUtils.writeString(new File(activity.getCacheDir(), "common_dlls.json"), data.toString());

                File outputFile = new File(activity.getCacheDir(), "container_pattern.tzst");
                FileUtils.delete(outputFile);
                TarCompressorUtils.compress(TarCompressorUtils.Type.ZSTD, new File(containerPatternDir, ".wine"), outputFile, 22);

                FileUtils.delete(containerPatternDir);
                preloaderDialog.closeOnUiThread();
            }
            catch (JSONException e) {}
        });
    }
}
