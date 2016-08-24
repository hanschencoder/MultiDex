package com.hanschen.multidex;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.multidex.MultiDex;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.hanschen.multidex.application.MultiDexApplication;

/**
 * Created by Hans on 2016/8/23.
 */
public class WelcomeActivity extends AppCompatActivity implements DexInstallCallback {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_load);

        new Thread(new DexInstall(WelcomeActivity.this, WelcomeActivity.this)).start();
    }

    @Override
    public void onBackPressed() {
        //do nothing
    }

    @Override
    public void onInstallStart() {
        Log.d("Hans", "onInstallStart");
    }

    @Override
    public void onInstallComplete() {
        ((MultiDexApplication) getApplication()).installFinish(getApplication());
        finish();
        System.exit(0);
    }

    static class DexInstall implements Runnable {

        private final Context            context;
        private final DexInstallCallback callback;

        public DexInstall(Context context, DexInstallCallback callback) {
            if (context == null || callback == null) {
                throw new IllegalArgumentException("context == null || callback == null");
            }
            this.context = context.getApplicationContext();
            this.callback = callback;
        }

        @Override
        public void run() {

            callback.onInstallStart();
            long start = System.currentTimeMillis();
            MultiDex.install(context);
            Log.d("Hans", "WelcomeActivity#MultiDex.install: " + (System.currentTimeMillis() - start));
            callback.onInstallComplete();
        }
    }

}
