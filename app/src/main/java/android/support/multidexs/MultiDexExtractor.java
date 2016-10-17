//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package android.support.multidexs;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.ApplicationInfo;
import android.os.Build.VERSION;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

final class MultiDexExtractor {
    private static final String TAG                  = "MultiDex";
    private static final String DEX_PREFIX           = "classes";
    private static final String DEX_SUFFIX           = ".dex";
    private static final String EXTRACTED_NAME_EXT   = ".classes";
    private static final String EXTRACTED_SUFFIX     = ".zip";
    private static final int    MAX_EXTRACT_ATTEMPTS = 3;
    private static final String PREFS_FILE           = "multidex.version";
    private static final String KEY_TIME_STAMP       = "timestamp";
    private static final String KEY_CRC              = "crc";
    private static final String KEY_DEX_NUMBER       = "dex.number";
    private static final int    BUFFER_SIZE          = 16384;
    private static final long   NO_VALUE             = -1L;
    private static Method sApplyMethod;

    MultiDexExtractor() {
    }

    /**
     * 解压apk文件中的classes2.dex、classes3.dex等文件解压到dexDir目录中
     *
     * @param dexDir      解压目录
     * @param forceReload 是否需要强制从apk文件中解压，否的话会直接读取旧文件
     * @return 解压后的文件列表
     * @throws IOException
     */
    static List<File> load(Context context,
                           ApplicationInfo applicationInfo,
                           File dexDir,
                           boolean forceReload) throws IOException {
        File sourceApk = new File(applicationInfo.sourceDir);
        long currentCrc = getZipCrc(sourceApk);
        List files;
        if (!forceReload && !isModified(context, sourceApk, currentCrc)) {
            try {
                //从缓存目录中直接查找缓存文件，跳过解压
                files = loadExistingExtractions(context, sourceApk, dexDir);
            } catch (IOException var9) {
                files = performExtractions(sourceApk, dexDir);
                putStoredApkInfo(context, getTimeStamp(sourceApk), currentCrc, files.size() + 1);
            }
        } else {
            //把apk中的secondary dex文件解压到缓存目录，并把解压后的文件返回
            files = performExtractions(sourceApk, dexDir);
            //把解压信息保存到sharedPreferences中
            putStoredApkInfo(context, getTimeStamp(sourceApk), currentCrc, files.size() + 1);
        }

        return files;
    }

    private static List<File> loadExistingExtractions(Context context, File sourceApk, File dexDir) throws IOException {
        Log.i("MultiDex", "loading existing secondary dex files");
        String extractedFilePrefix = sourceApk.getName() + ".classes";
        int totalDexNumber = getMultiDexPreferences(context).getInt("dex.number", 1);
        ArrayList files = new ArrayList(totalDexNumber);

        for (int secondaryNumber = 2; secondaryNumber <= totalDexNumber; ++secondaryNumber) {
            String fileName = extractedFilePrefix + secondaryNumber + ".zip";
            File extractedFile = new File(dexDir, fileName);
            if (!extractedFile.isFile()) {
                throw new IOException("Missing extracted secondary dex file \'" + extractedFile.getPath() + "\'");
            }

            files.add(extractedFile);
            if (!verifyZipFile(extractedFile)) {
                Log.i("MultiDex", "Invalid zip file: " + extractedFile);
                throw new IOException("Invalid ZIP file.");
            }
        }

        return files;
    }

    private static boolean isModified(Context context, File archive, long currentCrc) {
        SharedPreferences prefs = getMultiDexPreferences(context);
        return prefs.getLong("timestamp", -1L) != getTimeStamp(archive) || prefs.getLong("crc", -1L) != currentCrc;
    }

    private static long getTimeStamp(File archive) {
        long timeStamp = archive.lastModified();
        if (timeStamp == -1L) {
            --timeStamp;
        }

        return timeStamp;
    }

    private static long getZipCrc(File archive) throws IOException {
        long computedValue = ZipUtil.getZipCrc(archive);
        if (computedValue == -1L) {
            --computedValue;
        }

        return computedValue;
    }

    private static List<File> performExtractions(File sourceApk, File dexDir) throws IOException {
        String extractedFilePrefix = sourceApk.getName() + ".classes";
        //确保缓存目录被创建，删除缓存目录下非extractedFilePrefix开头的文件
        prepareDexDir(dexDir, extractedFilePrefix);
        ArrayList files = new ArrayList();
        ZipFile apk = new ZipFile(sourceApk);

        try {
            int e = 2;

            for (ZipEntry dexFile = apk.getEntry("classes" + e + ".dex"); dexFile != null; dexFile = apk.getEntry("classes" + e + ".dex")) {
                String fileName = extractedFilePrefix + e + ".zip";
                File extractedFile = new File(dexDir, fileName);
                files.add(extractedFile);
                Log.i("MultiDex", "Extraction is needed for file " + extractedFile);
                int numAttempts = 0;
                boolean isExtractionSuccessful = false;

                while (numAttempts < 3 && !isExtractionSuccessful) {
                    ++numAttempts;
                    extract(apk, dexFile, extractedFile, extractedFilePrefix);
                    isExtractionSuccessful = verifyZipFile(extractedFile);
                    Log.i("MultiDex", "Extraction " + (isExtractionSuccessful ? "success" : "failed") + " - length " + extractedFile
                            .getAbsolutePath() + ": " + extractedFile.length());
                    if (!isExtractionSuccessful) {
                        extractedFile.delete();
                        if (extractedFile.exists()) {
                            Log.w("MultiDex", "Failed to delete corrupted secondary dex \'" + extractedFile.getPath() + "\'");
                        }
                    }
                }

                if (!isExtractionSuccessful) {
                    throw new IOException("Could not create zip file " + extractedFile.getAbsolutePath() + " for secondary dex (" + e + ")");
                }

                ++e;
            }
        } finally {
            try {
                apk.close();
            } catch (IOException var16) {
                Log.w("MultiDex", "Failed to close resource", var16);
            }

        }

        return files;
    }

    private static void putStoredApkInfo(Context context, long timeStamp, long crc, int totalDexNumber) {
        SharedPreferences prefs = getMultiDexPreferences(context);
        Editor edit = prefs.edit();
        edit.putLong("timestamp", timeStamp);
        edit.putLong("crc", crc);
        edit.putInt("dex.number", totalDexNumber);
        apply(edit);
    }

    private static SharedPreferences getMultiDexPreferences(Context context) {
        return context.getSharedPreferences("multidex.version", VERSION.SDK_INT < 11 ? 0 : 4);
    }

    private static void prepareDexDir(File dexDir, final String extractedFilePrefix) throws IOException {
        File cache = dexDir.getParentFile();
        mkdirChecked(cache);
        mkdirChecked(dexDir);
        FileFilter filter = new FileFilter() {
            public boolean accept(File pathname) {
                return !pathname.getName().startsWith(extractedFilePrefix);
            }
        };
        File[] files = dexDir.listFiles(filter);
        if (files == null) {
            Log.w("MultiDex", "Failed to list secondary dex dir content (" + dexDir.getPath() + ").");
        } else {
            File[] arr$ = files;
            int len$ = files.length;

            for (int i$ = 0; i$ < len$; ++i$) {
                File oldFile = arr$[i$];
                Log.i("MultiDex", "Trying to delete old file " + oldFile.getPath() + " of size " + oldFile.length());
                if (!oldFile.delete()) {
                    Log.w("MultiDex", "Failed to delete old file " + oldFile.getPath());
                } else {
                    Log.i("MultiDex", "Deleted old file " + oldFile.getPath());
                }
            }

        }
    }

    private static void mkdirChecked(File dir) throws IOException {
        dir.mkdir();
        if (!dir.isDirectory()) {
            File parent = dir.getParentFile();
            if (parent == null) {
                Log.e("MultiDex", "Failed to create dir " + dir.getPath() + ". Parent file is null.");
            } else {
                Log.e("MultiDex", "Failed to create dir " + dir.getPath() + ". parent file is a dir " + parent.isDirectory() + ", a file " + parent
                        .isFile() + ", exists " + parent.exists() + ", readable " + parent.canRead() + ", writable " + parent.canWrite());
            }

            throw new IOException("Failed to create cache directory " + dir.getPath());
        }
    }

    /**
     * 把apk文件中的dexFile解压为extractTo文件
     *
     * @throws IOException
     * @throws FileNotFoundException
     */
    private static void extract(ZipFile apk,
                                ZipEntry dexFile,
                                File extractTo,
                                String extractedFilePrefix) throws IOException, FileNotFoundException {
        InputStream in = apk.getInputStream(dexFile);
        ZipOutputStream out = null;
        File tmp = File.createTempFile(extractedFilePrefix, ".zip", extractTo.getParentFile());
        Log.i("MultiDex", "Extracting " + tmp.getPath());

        try {
            out = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(tmp)));

            try {
                ZipEntry classesDex = new ZipEntry("classes.dex");
                classesDex.setTime(dexFile.getTime());
                out.putNextEntry(classesDex);
                byte[] buffer = new byte[16384];

                for (int length = in.read(buffer); length != -1; length = in.read(buffer)) {
                    out.write(buffer, 0, length);
                }

                out.closeEntry();
            } finally {
                out.close();
            }

            Log.i("MultiDex", "Renaming to " + extractTo.getPath());
            if (!tmp.renameTo(extractTo)) {
                throw new IOException("Failed to rename \"" + tmp.getAbsolutePath() + "\" to \"" + extractTo.getAbsolutePath() + "\"");
            }
        } finally {
            closeQuietly(in);
            tmp.delete();
        }

    }

    static boolean verifyZipFile(File file) {
        try {
            ZipFile ex = new ZipFile(file);

            try {
                ex.close();
                return true;
            } catch (IOException var3) {
                Log.w("MultiDex", "Failed to close zip file: " + file.getAbsolutePath());
            }
        } catch (ZipException var4) {
            Log.w("MultiDex", "File " + file.getAbsolutePath() + " is not a valid zip file.", var4);
        } catch (IOException var5) {
            Log.w("MultiDex", "Got an IOException trying to open zip file: " + file.getAbsolutePath(), var5);
        }

        return false;
    }

    private static void closeQuietly(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException var2) {
            Log.w("MultiDex", "Failed to close resource", var2);
        }

    }

    private static void apply(Editor editor) {
        if (sApplyMethod != null) {
            try {
                sApplyMethod.invoke(editor, new Object[0]);
                return;
            } catch (InvocationTargetException var2) {
                ;
            } catch (IllegalAccessException var3) {
                ;
            }
        }

        editor.commit();
    }

    static {
        try {
            Class unused = Editor.class;
            sApplyMethod = unused.getMethod("apply", new Class[0]);
        } catch (NoSuchMethodException var1) {
            sApplyMethod = null;
        }

    }
}
