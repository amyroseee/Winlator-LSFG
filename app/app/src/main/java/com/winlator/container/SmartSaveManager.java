package com.winlator.container;

import android.os.Environment;

import com.winlator.core.FileUtils;
import com.winlator.xenvironment.RootFS;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** On-demand, container-scoped save backup. No watcher or persistent worker is used. */
public final class SmartSaveManager {
    public static final int SCHEMA_VERSION = 1;
    private static final long MAX_ENTRY_SIZE = 256L * 1024L * 1024L;
    private static final long MAX_BACKUP_SIZE = 1024L * 1024L * 1024L;
    private static final int MAX_SCAN_DEPTH = 8;
    private static final int MAX_SCAN_FILES = 10000;
    private static final String[] ROOT_PATHS = {
        ".wine/drive_c/users/"+RootFS.USER+"/Saved Games",
        ".wine/drive_c/users/"+RootFS.USER+"/Documents",
        ".wine/drive_c/users/"+RootFS.USER+"/AppData/Roaming",
        ".wine/drive_c/users/"+RootFS.USER+"/AppData/Local",
        ".wine/drive_c/users/"+RootFS.USER+"/AppData/LocalLow",
        ".wine/drive_c/ProgramData"
    };
    private static final Set<String> SAVE_EXTENSIONS = new HashSet<>(Arrays.asList(
        "sav", "save", "cfg", "ini", "json", "xml", "profile", "dat", "bin"
    ));
    private static final Set<String> BLOCKED_EXTENSIONS = new HashSet<>(Arrays.asList(
        "exe", "dll", "so", "msi", "cab", "pak", "vpk", "wad", "bsa", "ba2",
        "iso", "img", "zip", "7z", "rar", "apk"
    ));

    public static final class Entry {
        public final File source;
        public final String relativePath;
        public final long size;

        Entry(File source, String relativePath, long size) {
            this.source = source;
            this.relativePath = relativePath;
            this.size = size;
        }
    }

    public static final class ScanResult {
        public final List<Entry> entries;
        public final long totalSize;

        ScanResult(List<Entry> entries, long totalSize) {
            this.entries = entries;
            this.totalSize = totalSize;
        }
    }

    public static final class BackupInfo {
        public final File directory;
        public final String displayName;
        public final int entryCount;
        public final long totalSize;

        BackupInfo(File directory, String displayName, int entryCount, long totalSize) {
            this.directory = directory;
            this.displayName = displayName;
            this.entryCount = entryCount;
            this.totalSize = totalSize;
        }
    }

    private SmartSaveManager() {}

    public static ScanResult scan(Container container) throws IOException {
        File containerRoot = container.getRootDir().getCanonicalFile();
        ArrayList<Entry> entries = new ArrayList<>();
        long totalSize = 0;

        for (String rootPath : ROOT_PATHS) {
            File root = new File(containerRoot, rootPath);
            if (!root.isDirectory() || FileUtils.isSymlink(root)) continue;
            File[] children = sortedChildren(root);
            if (children == null) continue;
            boolean trustedSavedGames = rootPath.endsWith("/Saved Games");

            for (File child : children) {
                if (isBlocked(child) || FileUtils.isSymlink(child)) continue;
                ScanStats stats = inspectCandidate(child, trustedSavedGames, 0, new int[]{0});
                if (!stats.hasEvidence || stats.size <= 0 || stats.size > MAX_ENTRY_SIZE) continue;
                String relative = relativePath(containerRoot, child.getCanonicalFile());
                if (relative == null || !isAllowedRestorePath(relative)) continue;
                entries.add(new Entry(child, relative, stats.size));
                totalSize += stats.size;
                if (totalSize >= MAX_BACKUP_SIZE) break;
            }
            if (totalSize >= MAX_BACKUP_SIZE) break;
        }
        return new ScanResult(entries, totalSize);
    }

    public static File createBackup(Container container, ScanResult scanResult) throws IOException, JSONException {
        if (scanResult == null || scanResult.entries.isEmpty()) throw new IOException("No save entries");
        File base = getContainerBackupRoot(container);
        if (!base.isDirectory() && !base.mkdirs()) throw new IOException("Cannot create backup root");

        String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(new Date());
        File staging = new File(base, "."+stamp+".staging");
        File output = new File(base, stamp);
        if ((!staging.mkdirs()) || output.exists()) throw new IOException("Cannot create backup directory");

        try {
            File dataDir = new File(staging, "data");
            if (!dataDir.mkdir()) throw new IOException("Cannot create data directory");
            JSONArray manifestEntries = new JSONArray();
            long copiedSize = 0;
            int index = 0;
            for (Entry entry : scanResult.entries) {
                String dataPath = String.format(Locale.US, "data/%04d", index++);
                File destination = new File(staging, dataPath);
                copyFiltered(entry.source, destination);
                long size = calculateSize(destination);
                copiedSize += size;
                if (copiedSize > MAX_BACKUP_SIZE) throw new IOException("Backup size limit exceeded");

                JSONObject item = new JSONObject();
                item.put("source", entry.relativePath);
                item.put("restore_target", entry.relativePath);
                item.put("data_path", dataPath);
                item.put("type", destination.isDirectory() ? "directory" : "file");
                item.put("size", size);
                item.put("hash", hash(destination));
                manifestEntries.put(item);
            }

            JSONObject manifest = new JSONObject();
            manifest.put("schema_version", SCHEMA_VERSION);
            manifest.put("container_id", container.id);
            manifest.put("container_name", container.getName());
            manifest.put("created_at", System.currentTimeMillis());
            manifest.put("total_size", copiedSize);
            manifest.put("entries", manifestEntries);
            if (!FileUtils.writeString(new File(staging, "backup.json"), manifest.toString(2)))
                throw new IOException("Cannot write manifest");
            if (!staging.renameTo(output)) throw new IOException("Cannot finalize backup");
            return output;
        }
        catch (IOException | JSONException e) {
            FileUtils.delete(staging);
            throw e;
        }
    }

    public static List<BackupInfo> listBackups(Container container) {
        ArrayList<BackupInfo> backups = new ArrayList<>();
        File[] containerDirs = sortedChildren(getBackupRoot());
        if (containerDirs == null) return backups;
        for (File containerDir : containerDirs) {
            File allGames = new File(containerDir, "AllGames");
            File[] directories = sortedChildren(allGames);
            if (directories == null) continue;
            for (int i = directories.length - 1; i >= 0; i--) {
                File directory = directories[i];
                if (!directory.isDirectory() || directory.getName().startsWith(".")) continue;
                try {
                    JSONObject manifest = readManifest(directory);
                    JSONArray entries = manifest.getJSONArray("entries");
                    String displayName = manifest.optString("container_name", containerDir.getName())+" — "+directory.getName();
                    backups.add(new BackupInfo(directory, displayName, entries.length(), manifest.optLong("total_size", 0)));
                }
                catch (Exception ignored) {}
            }
        }
        return backups;
    }

    public static BackupInfo validateBackup(Container container, File backupDir) throws IOException, JSONException {
        File expectedRoot = getBackupRoot().getCanonicalFile();
        File canonicalBackup = backupDir.getCanonicalFile();
        if (!isInside(expectedRoot, canonicalBackup)) throw new IOException("Backup outside root");
        JSONObject manifest = readManifest(canonicalBackup);
        if (manifest.getInt("schema_version") != SCHEMA_VERSION) throw new IOException("Unsupported schema");
        manifest.getInt("container_id");
        JSONArray entries = manifest.getJSONArray("entries");
        if (entries.length() == 0) throw new IOException("Empty manifest");
        long totalSize = 0;
        File dataRoot = new File(canonicalBackup, "data").getCanonicalFile();

        for (int i = 0; i < entries.length(); i++) {
            JSONObject item = entries.getJSONObject(i);
            String source = requiredString(item, "source");
            String restoreTarget = requiredString(item, "restore_target");
            String dataPath = requiredString(item, "data_path");
            String type = requiredString(item, "type");
            String expectedHash = requiredString(item, "hash");
            if (!source.equals(restoreTarget) || !isAllowedRestorePath(restoreTarget))
                throw new IOException("Invalid restore target");
            File data = new File(canonicalBackup, dataPath).getCanonicalFile();
            if (!isInside(dataRoot, data) || !(type.equals("file") || type.equals("directory")))
                throw new IOException("Invalid data path");
            if ((type.equals("file") && !data.isFile()) || (type.equals("directory") && !data.isDirectory()))
                throw new IOException("Missing backup data");
            long size = calculateSize(data);
            if (size != item.getLong("size") || !hash(data).equalsIgnoreCase(expectedHash))
                throw new IOException("Backup integrity check failed");
            totalSize += size;
            if (totalSize > MAX_BACKUP_SIZE) throw new IOException("Backup size limit exceeded");
            resolveRestoreTarget(container, restoreTarget);
        }
        return new BackupInfo(canonicalBackup, canonicalBackup.getName(), entries.length(), totalSize);
    }

    public static void restore(Container container, File backupDir) throws IOException, JSONException {
        validateBackup(container, backupDir);
        JSONObject manifest = readManifest(backupDir);
        JSONArray entries = manifest.getJSONArray("entries");
        for (int i = 0; i < entries.length(); i++) {
            JSONObject item = entries.getJSONObject(i);
            File data = new File(backupDir, item.getString("data_path")).getCanonicalFile();
            File target = resolveRestoreTarget(container, item.getString("restore_target"));
            copyFiltered(data, target);
        }
    }

    public static File getBackupRoot() {
        return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Winlator/Saves");
    }

    private static File getContainerBackupRoot(Container container) {
        return new File(new File(getBackupRoot(), "Container-"+container.id), "AllGames");
    }

    private static JSONObject readManifest(File backupDir) throws IOException, JSONException {
        File manifestFile = new File(backupDir, "backup.json");
        if (!manifestFile.isFile() || manifestFile.length() <= 0 || manifestFile.length() > 1024L * 1024L)
            throw new IOException("Invalid manifest");
        String text = FileUtils.readString(manifestFile);
        if (text == null) throw new IOException("Cannot read manifest");
        return new JSONObject(text);
    }

    private static String requiredString(JSONObject item, String key) throws JSONException, IOException {
        String value = item.getString(key);
        if (value.isEmpty()) throw new IOException("Missing manifest value");
        return value;
    }

    private static File resolveRestoreTarget(Container container, String relativePath) throws IOException {
        if (!isAllowedRestorePath(relativePath)) throw new IOException("Disallowed restore target");
        File root = container.getRootDir().getCanonicalFile();
        File target = new File(root, relativePath).getCanonicalFile();
        if (!isInside(root, target)) throw new IOException("Path traversal");
        boolean insideAllowedRoot = false;
        for (String allowed : ROOT_PATHS) {
            File allowedRoot = new File(root, allowed).getCanonicalFile();
            if (target.equals(allowedRoot) || isInside(allowedRoot, target)) {
                insideAllowedRoot = true;
                break;
            }
        }
        if (!insideAllowedRoot) throw new IOException("Target outside save roots");
        return target;
    }

    private static boolean isAllowedRestorePath(String path) {
        if (path == null || path.isEmpty() || path.startsWith("/") || path.startsWith("\\") || path.contains("..")) return false;
        String normalized = path.replace('\\', '/');
        for (String root : ROOT_PATHS)
            if (normalized.equals(root) || normalized.startsWith(root+"/")) return true;
        return false;
    }

    private static boolean isInside(File root, File child) {
        String rootPath = root.getPath()+File.separator;
        return child.getPath().startsWith(rootPath);
    }

    private static String relativePath(File root, File child) {
        if (!isInside(root, child)) return null;
        return child.getPath().substring(root.getPath().length()+1).replace(File.separatorChar, '/');
    }

    private static final class ScanStats {
        long size;
        boolean hasEvidence;
    }

    private static ScanStats inspectCandidate(File file, boolean trusted, int depth, int[] filesSeen) {
        ScanStats result = new ScanStats();
        if (depth > MAX_SCAN_DEPTH || filesSeen[0] >= MAX_SCAN_FILES || isBlocked(file) || FileUtils.isSymlink(file)) return result;
        if (file.isFile()) {
            filesSeen[0]++;
            result.size = file.length();
            result.hasEvidence = trusted || hasSaveEvidence(file);
            return result;
        }
        File[] children = sortedChildren(file);
        if (children == null) return result;
        result.hasEvidence = trusted || hasSaveName(file.getName());
        for (File child : children) {
            ScanStats childStats = inspectCandidate(child, trusted, depth+1, filesSeen);
            result.size += childStats.size;
            result.hasEvidence |= childStats.hasEvidence;
            if (result.size > MAX_ENTRY_SIZE || filesSeen[0] >= MAX_SCAN_FILES) break;
        }
        return result;
    }

    private static boolean hasSaveEvidence(File file) {
        String name = file.getName().toLowerCase(Locale.US);
        if (hasSaveName(name)) return true;
        int dot = name.lastIndexOf('.');
        return dot >= 0 && SAVE_EXTENSIONS.contains(name.substring(dot+1));
    }

    private static boolean hasSaveName(String name) {
        String lower = name.toLowerCase(Locale.US);
        return lower.contains("save") || lower.contains("profile") || lower.contains("progress") ||
            lower.contains("checkpoint") || lower.contains("career") || lower.contains("slot") ||
            lower.contains("config") || lower.contains("setting");
    }

    private static boolean isBlocked(File file) {
        String name = file.getName().toLowerCase(Locale.US);
        if (name.startsWith(".")) return true;
        if (name.contains("cache") || name.contains("shader") || name.equals("temp") || name.equals("tmp") ||
            name.equals("logs") || name.equals("log") || name.contains("crash") || name.contains("dxvk") ||
            name.contains("vkd3d") || name.contains("mesa") || name.contains("installer") ||
            name.contains("redistributable") || name.equals("microsoft")) return true;
        if (file.isFile()) {
            int dot = name.lastIndexOf('.');
            if (dot >= 0 && BLOCKED_EXTENSIONS.contains(name.substring(dot+1))) return true;
        }
        return false;
    }

    private static void copyFiltered(File source, File destination) throws IOException {
        if (FileUtils.isSymlink(source) || isBlocked(source)) return;
        if (source.isDirectory()) {
            if (!destination.isDirectory() && !destination.mkdirs()) throw new IOException("Cannot create directory");
            File[] children = sortedChildren(source);
            if (children != null) for (File child : children)
                if (!isBlocked(child) && !FileUtils.isSymlink(child)) copyFiltered(child, new File(destination, child.getName()));
        }
        else if (source.isFile()) {
            File parent = destination.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IOException("Cannot create parent");
            try (BufferedInputStream input = new BufferedInputStream(new FileInputStream(source));
                 BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(destination))) {
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            }
        }
    }

    private static long calculateSize(File file) {
        if (file.isFile()) return file.length();
        long size = 0;
        File[] children = sortedChildren(file);
        if (children != null) for (File child : children) size += calculateSize(child);
        return size;
    }

    private static String hash(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            updateHash(digest, file, file);
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest.digest()) result.append(String.format(Locale.US, "%02x", value & 0xff));
            return result.toString();
        }
        catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    private static void updateHash(MessageDigest digest, File root, File file) throws IOException {
        String relative = file.equals(root) ? "" : relativePath(root, file);
        digest.update(relative.getBytes(StandardCharsets.UTF_8));
        digest.update((byte)(file.isDirectory() ? 1 : 0));
        if (file.isDirectory()) {
            File[] children = sortedChildren(file);
            if (children != null) for (File child : children) updateHash(digest, root, child);
        }
        else {
            try (BufferedInputStream input = new BufferedInputStream(new FileInputStream(file))) {
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
            }
        }
    }

    private static File[] sortedChildren(File directory) {
        File[] children = directory.listFiles();
        if (children != null) Arrays.sort(children,
            Comparator.comparing((File file) -> file.getName().toLowerCase(Locale.US)).thenComparing(File::getName));
        return children;
    }
}
