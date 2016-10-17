//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package android.support.multidexs;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build.VERSION;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

import dalvik.system.DexClassLoader;
import dalvik.system.DexFile;

public final class MultiDex {
    static final         String TAG                       = "MultiDex";
    private static final String OLD_SECONDARY_FOLDER_NAME = "secondary-dexes";
    private static final String SECONDARY_FOLDER_NAME;
    private static final int MAX_SUPPORTED_SDK_VERSION      = 20;
    private static final int MIN_SDK_VERSION                = 4;
    private static final int VM_WITH_MULTIDEX_VERSION_MAJOR = 2;
    private static final int VM_WITH_MULTIDEX_VERSION_MINOR = 1;
    private static final Set<String> installedApk;
    private static final boolean     IS_VM_MULTIDEX_CAPABLE;

    private MultiDex() {
    }

    public static void install(Context context) {
        Log.i("MultiDex", "install");
        if (IS_VM_MULTIDEX_CAPABLE) {
            //VM版本大于2.1时，IS_VM_MULTIDEX_CAPABLE为true，这时候MultiDex.install什么也不用做，直接返回。因为大于2.1的VM会在安装应用的时候，就把多个dex合并到一块
        } else if (VERSION.SDK_INT < 4) {
            //Multi dex最小支持的SDK版本为4
            throw new RuntimeException("Multi dex installation failed. SDK " + VERSION.SDK_INT + " is unsupported. Min SDK version is " + 4 + ".");
        } else {
            try {
                ApplicationInfo e = getApplicationInfo(context);
                if (e == null) {
                    return;
                }

                Set var2 = installedApk;
                synchronized (installedApk) {
                    String apkPath = e.sourceDir;
                    //检测应用是否已经执行过install()了，防止重复install
                    if (installedApk.contains(apkPath)) {
                        return;
                    }

                    installedApk.add(apkPath);

                    //获取ClassLoader，后面会用它来加载second dex
                    DexClassLoader classLoader;
                    ClassLoader loader;
                    try {
                        loader = context.getClassLoader();
                    } catch (RuntimeException var9) {
                        return;
                    }

                    if (loader == null) {
                        return;
                    }

                    //清空目录：/data/data/<packagename>/files/secondary-dexes/
                    try {
                        clearOldDexDir(context);
                    } catch (Throwable var8) {
                    }

                    File dexDir = new File(e.dataDir, "code_cache/secondary-dexes");
                    //把dex文件缓存到/data/data/<packagename>/code_cache/secondary-dexes/目录
                    List files = MultiDexExtractor.load(context, e, dexDir, false);
                    if (checkValidZipFiles(files)) {
                        //进行安装
                        installSecondaryDexes(loader, dexDir, files);
                    } else {
                        //文件无效，从apk文件中再次解压secondary dex文件后进行安装
                        files = MultiDexExtractor.load(context, e, dexDir, true);
                        if (!checkValidZipFiles(files)) {
                            throw new RuntimeException("Zip files were not valid.");
                        }

                        installSecondaryDexes(loader, dexDir, files);
                    }
                }
            } catch (Exception var11) {
                throw new RuntimeException("Multi dex installation failed (" + var11.getMessage() + ").");
            }
        }
    }

    private static ApplicationInfo getApplicationInfo(Context context) throws NameNotFoundException {
        PackageManager pm;
        String packageName;
        try {
            pm = context.getPackageManager();
            packageName = context.getPackageName();
        } catch (RuntimeException var4) {
            Log.w("MultiDex", "Failure while trying to obtain ApplicationInfo from Context. Must be running in test mode. Skip patching.", var4);
            return null;
        }

        if (pm != null && packageName != null) {
            ApplicationInfo applicationInfo = pm.getApplicationInfo(packageName, 128);
            return applicationInfo;
        } else {
            return null;
        }
    }

    static boolean isVMMultidexCapable(String versionString) {
        boolean isMultidexCapable = false;
        if (versionString != null) {
            Matcher matcher = Pattern.compile("(\\d+)\\.(\\d+)(\\.\\d+)?").matcher(versionString);
            if (matcher.matches()) {
                try {
                    int e = Integer.parseInt(matcher.group(1));
                    int minor = Integer.parseInt(matcher.group(2));
                    isMultidexCapable = e > 2 || e == 2 && minor >= 1;
                } catch (NumberFormatException var5) {
                    ;
                }
            }
        }

        Log.i("MultiDex", "VM with version " + versionString + (isMultidexCapable ? " has multidex support" : " does not have multidex support"));
        return isMultidexCapable;
    }

    private static void installSecondaryDexes(ClassLoader loader,
                                              File dexDir,
                                              List<File> files) throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, InvocationTargetException, NoSuchMethodException, IOException {
        if (!files.isEmpty()) {
            if (VERSION.SDK_INT >= 19) {
                MultiDex.V19.install(loader, files, dexDir);
            } else if (VERSION.SDK_INT >= 14) {
                MultiDex.V14.install(loader, files, dexDir);
            } else {
                MultiDex.V4.install(loader, files);
            }
        }

    }

    private static boolean checkValidZipFiles(List<File> files) {
        Iterator i$ = files.iterator();

        File file;
        do {
            if (!i$.hasNext()) {
                return true;
            }

            file = (File) i$.next();
        } while (MultiDexExtractor.verifyZipFile(file));

        return false;
    }

    private static Field findField(Object instance, String name) throws NoSuchFieldException {
        Class clazz = instance.getClass();

        while (clazz != null) {
            try {
                Field e = clazz.getDeclaredField(name);
                if (!e.isAccessible()) {
                    e.setAccessible(true);
                }

                return e;
            } catch (NoSuchFieldException var4) {
                clazz = clazz.getSuperclass();
            }
        }

        throw new NoSuchFieldException("Field " + name + " not found in " + instance.getClass());
    }

    private static Method findMethod(Object instance, String name, Class... parameterTypes) throws NoSuchMethodException {
        Class clazz = instance.getClass();

        while (clazz != null) {
            try {
                Method e = clazz.getDeclaredMethod(name, parameterTypes);
                if (!e.isAccessible()) {
                    e.setAccessible(true);
                }

                return e;
            } catch (NoSuchMethodException var5) {
                clazz = clazz.getSuperclass();
            }
        }

        throw new NoSuchMethodException("Method " + name + " with parameters " + Arrays.asList(parameterTypes) + " not found in " + instance
                .getClass());
    }

    private static void expandFieldArray(Object instance,
                                         String fieldName,
                                         Object[] extraElements) throws NoSuchFieldException, IllegalArgumentException, IllegalAccessException {
        Field jlrField = findField(instance, fieldName);
        Object[] original = (Object[]) ((Object[]) jlrField.get(instance));
        Object[] combined = (Object[]) ((Object[]) Array.newInstance(original.getClass()
                                                                             .getComponentType(), original.length + extraElements.length));
        System.arraycopy(original, 0, combined, 0, original.length);
        System.arraycopy(extraElements, 0, combined, original.length, extraElements.length);
        jlrField.set(instance, combined);
    }

    private static void clearOldDexDir(Context context) throws Exception {
        File dexDir = new File(context.getFilesDir(), "secondary-dexes");
        if (dexDir.isDirectory()) {
            Log.i("MultiDex", "Clearing old secondary dex dir (" + dexDir.getPath() + ").");
            File[] files = dexDir.listFiles();
            if (files == null) {
                Log.w("MultiDex", "Failed to list secondary dex dir content (" + dexDir.getPath() + ").");
                return;
            }

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

            if (!dexDir.delete()) {
                Log.w("MultiDex", "Failed to delete secondary dex dir " + dexDir.getPath());
            } else {
                Log.i("MultiDex", "Deleted old secondary dex dir " + dexDir.getPath());
            }
        }

    }

    static {
        SECONDARY_FOLDER_NAME = "code_cache" + File.separator + "secondary-dexes";
        installedApk = new HashSet();
        IS_VM_MULTIDEX_CAPABLE = isVMMultidexCapable(System.getProperty("java.vm.version"));
    }

    private static final class V4 {
        private V4() {
        }

        private static void install(ClassLoader loader,
                                    List<File> additionalClassPathEntries) throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, IOException {
            int extraSize = additionalClassPathEntries.size();
            Field pathField = MultiDex.findField(loader, "path");
            StringBuilder path = new StringBuilder((String) pathField.get(loader));
            String[] extraPaths = new String[extraSize];
            File[] extraFiles = new File[extraSize];
            ZipFile[] extraZips = new ZipFile[extraSize];
            DexFile[] extraDexs = new DexFile[extraSize];

            String entryPath;
            int index;
            for (ListIterator iterator = additionalClassPathEntries.listIterator(); iterator.hasNext(); extraDexs[index] = DexFile.loadDex(entryPath, entryPath + ".dex", 0)) {
                File additionalEntry = (File) iterator.next();
                entryPath = additionalEntry.getAbsolutePath();
                path.append(':').append(entryPath);
                index = iterator.previousIndex();
                extraPaths[index] = entryPath;
                extraFiles[index] = additionalEntry;
                extraZips[index] = new ZipFile(additionalEntry);
            }

            pathField.set(loader, path.toString());
            MultiDex.expandFieldArray(loader, "mPaths", extraPaths);
            MultiDex.expandFieldArray(loader, "mFiles", extraFiles);
            MultiDex.expandFieldArray(loader, "mZips", extraZips);
            MultiDex.expandFieldArray(loader, "mDexs", extraDexs);
        }
    }

    private static final class V14 {
        private V14() {
        }

        private static void install(ClassLoader loader,
                                    List<File> additionalClassPathEntries,
                                    File optimizedDirectory) throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, InvocationTargetException, NoSuchMethodException {
            Field pathListField = MultiDex.findField(loader, "pathList");
            Object dexPathList = pathListField.get(loader);
            MultiDex.expandFieldArray(dexPathList, "dexElements", makeDexElements(dexPathList, new ArrayList(additionalClassPathEntries), optimizedDirectory));
        }

        private static Object[] makeDexElements(Object dexPathList,
                                                ArrayList<File> files,
                                                File optimizedDirectory) throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
            Method makeDexElements = MultiDex.findMethod(dexPathList, "makeDexElements", new Class[]{ArrayList.class, File.class});
            return (Object[]) ((Object[]) makeDexElements.invoke(dexPathList, new Object[]{files, optimizedDirectory}));
        }
    }

    private static final class V19 {
        private V19() {
        }

        private static void install(ClassLoader loader,
                                    List<File> additionalClassPathEntries,
                                    File optimizedDirectory) throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, InvocationTargetException, NoSuchMethodException {
            Field pathListField = MultiDex.findField(loader, "pathList");
            Object dexPathList = pathListField.get(loader);
            ArrayList suppressedExceptions = new ArrayList();
            MultiDex.expandFieldArray(dexPathList, "dexElements", makeDexElements(dexPathList, new ArrayList(additionalClassPathEntries), optimizedDirectory, suppressedExceptions));
            if (suppressedExceptions.size() > 0) {
                Iterator suppressedExceptionsField = suppressedExceptions.iterator();

                while (suppressedExceptionsField.hasNext()) {
                    IOException dexElementsSuppressedExceptions = (IOException) suppressedExceptionsField.next();
                    Log.w("MultiDex", "Exception in makeDexElement", dexElementsSuppressedExceptions);
                }

                Field suppressedExceptionsField1 = MultiDex.findField(loader, "dexElementsSuppressedExceptions");
                IOException[] dexElementsSuppressedExceptions1 = (IOException[]) ((IOException[]) suppressedExceptionsField1.get(loader));
                if (dexElementsSuppressedExceptions1 == null) {
                    dexElementsSuppressedExceptions1 = (IOException[]) suppressedExceptions.toArray(new IOException[suppressedExceptions
                            .size()]);
                } else {
                    IOException[] combined = new IOException[suppressedExceptions.size() + dexElementsSuppressedExceptions1.length];
                    suppressedExceptions.toArray(combined);
                    System.arraycopy(dexElementsSuppressedExceptions1, 0, combined, suppressedExceptions.size(), dexElementsSuppressedExceptions1.length);
                    dexElementsSuppressedExceptions1 = combined;
                }

                suppressedExceptionsField1.set(loader, dexElementsSuppressedExceptions1);
            }

        }

        private static Object[] makeDexElements(Object dexPathList,
                                                ArrayList<File> files,
                                                File optimizedDirectory,
                                                ArrayList<IOException> suppressedExceptions) throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
            Method makeDexElements = MultiDex.findMethod(dexPathList, "makeDexElements", new Class[]{ArrayList.class, File.class, ArrayList.class});
            return (Object[]) ((Object[]) makeDexElements.invoke(dexPathList, new Object[]{files, optimizedDirectory, suppressedExceptions}));
        }
    }
}
