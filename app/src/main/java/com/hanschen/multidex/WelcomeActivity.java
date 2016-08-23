package com.hanschen.multidex;

import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.multidex.MultiDex;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.hanschen.multidex.application.MultiDexApplication;

/**
 * Created by Hans on 2016/8/23.
 */
public class WelcomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_load);
        new LoadDexTask().execute();
    }

    class LoadDexTask extends AsyncTask {
        @Override
        protected Object doInBackground(Object[] params) {
            try {
                long start = System.currentTimeMillis();
                MultiDex.install(getApplication());
                Log.d("Hans", "WelcomeActivity#MultiDex.install: " + (System.currentTimeMillis() - start));
                ((MultiDexApplication) getApplication()).installFinish(getApplication());
            } catch (Exception ignore) {
            }
            return null;
        }

        @Override
        protected void onPostExecute(Object o) {
            finish();
            System.exit(0);
        }
    }

    @Override
    public void onBackPressed() {

    }
}
