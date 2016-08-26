package com.hanschen.multidex.application;

import android.app.ActivityManager;
import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.support.multidex.MultiDex;
import android.util.Log;

import com.hanschen.multidex.WelcomeActivity;
import com.hanschen.multidex.utils.PackageUtil;


import java.io.IOException;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Hans on 2016/8/23.
 */
public class MultiDexApplication extends Application {

    private static final String KEY_DEX2_SHA1 = "dex2-SHA1-Digest";

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        if (isDexInstallProcess()) {
            return;
        }

        if (!isVMMultiDexCapable()) {
            if (!isMultiDexInstalled(base)) {
                waitForDexInstall(base);
            }
            long start = System.currentTimeMillis();
            MultiDex.install(base);
            Log.d("Hans", "MultiDexApplication#MultiDex.install: " + (System.currentTimeMillis() - start));
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (isDexInstallProcess()) {
            return;
        }
    }

    private boolean isDexInstallProcess() {
        return getCurProcessName(this).contains(":dexInstall");
    }

    private boolean isMultiDexInstalled(Context context) {
        String flag = get2thDexSHA1(context);
        SharedPreferences sp = context.getSharedPreferences(getPreferencesFileName(context), MODE_MULTI_PROCESS);
        String saveValue = sp.getString(KEY_DEX2_SHA1, "");
        return flag.equals(saveValue);
    }

    private boolean isVMMultiDexCapable() {
        boolean isMultiDexCapable = false;
        String versionString = System.getProperty("java.vm.version");
        if (versionString != null) {
            Matcher matcher = Pattern.compile("(\\d+)\\.(\\d+)(\\.\\d+)?").matcher(versionString);
            if (matcher.matches()) {
                try {
                    int e = Integer.parseInt(matcher.group(1));
                    int minor = Integer.parseInt(matcher.group(2));
                    isMultiDexCapable = e > 2 || e == 2 && minor >= 1;
                } catch (NumberFormatException ignore) {
                }
            }
        }

        return isMultiDexCapable;
    }

    private String get2thDexSHA1(Context context) {
        ApplicationInfo info = context.getApplicationInfo();
        String source = info.sourceDir;
        try {
            JarFile jar = new JarFile(source);
            Manifest mf = jar.getManifest();
            Map<String, Attributes> map = mf.getEntries();
            Attributes a = map.get("classes2.dex");
            return a.getValue("SHA1-Digest");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }

    private String getPreferencesFileName(Context context) {
        PackageInfo packageInfo = PackageUtil.getPackageInfo(context);
        return packageInfo.versionName;
    }

    public void installFinish(Context context) {
        SharedPreferences sp = context.getSharedPreferences(getPreferencesFileName(context), MODE_MULTI_PROCESS);
        sp.edit().putString(KEY_DEX2_SHA1, get2thDexSHA1(context)).commit(); //do not use apply here
    }


    private static String getCurProcessName(Context context) {
        int pid = android.os.Process.myPid();
        ActivityManager mActivityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningAppProcessInfo appProcess : mActivityManager.getRunningAppProcesses()) {
            if (appProcess.pid == pid) {
                return appProcess.processName;
            }
        }
        return "";
    }

    private void waitForDexInstall(Context context) {
        Intent intent = new Intent();
        ComponentName componentName = new ComponentName("com.hanschen.multidex", WelcomeActivity.class.getName());
        intent.setComponent(componentName);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);

        long startWait = System.currentTimeMillis();
        long waitTime = 10 * 1000;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB_MR1) {
            waitTime = 20 * 1000;
        }
        while (!isMultiDexInstalled(context)) {
            try {
                long nowWait = System.currentTimeMillis() - startWait;
                if (nowWait >= waitTime) {
                    break;
                }
                Thread.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
