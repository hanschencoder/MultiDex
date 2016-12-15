package com.hanschen.multidex.application;

import android.app.Application;
import android.content.Context;
import android.support.multidex.MultiDex;
import android.util.Log;

import static com.hanschen.multidex.utils.DexInstallHelper.isDexInstallProcess;
import static com.hanschen.multidex.utils.DexInstallHelper.isMultiDexInstalled;
import static com.hanschen.multidex.utils.DexInstallHelper.isVMMultiDexCapable;
import static com.hanschen.multidex.utils.DexInstallHelper.waitForDexInstall;

/**
 * Created by Hans on 2016/8/23.
 */
public class MultiDexApplication extends Application {

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        if (isDexInstallProcess(base)) {
            return;
        }
        // if VM has multi dex support, MultiDex support library is disabled
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
        if (isDexInstallProcess(this)) {
            return;
        }

        // init application...
    }
}
