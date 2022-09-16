package com.swift.sandhook;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.material.snackbar.Snackbar;
import com.swift.sandhook.test.Inter;
import com.swift.sandhook.test.InterImpl;
import com.swift.sandhook.test.PendingHookTest;
import com.swift.sandhook.test.TestClass;

import java.lang.reflect.Field;

public class MainActivity extends Activity {

    Inter inter;
    public static final String TAG = "sanbo.MainActivity";

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "MainActivity onCreate");

        TextView tv = new TextView(this);
        setContentView(tv);



        StringBuilder hookTestResult = new StringBuilder();
        hookTestResult.append("当前安卓版本：").append(Build.VERSION.SDK_INT).append(":").append(Build.VERSION.PREVIEW_SDK_INT).append("\r\n");
        hookTestResult.append("静态方法Hook：").append(HookPass.getStaticMethodHookResult()).append("\r\n");
        hookTestResult.append("App实例方法Hook：").append(HookPass.getAppMethodHookResult()).append("\r\n");
        hookTestResult.append("系统类实例方法Hook：").append(HookPass.getSystemMethodHookResult()).append("\r\n");
        hookTestResult.append("APP类构造方法Hook：").append(HookPass.getAppConstructorHookResult()).append("\r\n");
        hookTestResult.append("系统类构造方法Hook：").append(HookPass.getSystemConstructorHookResult()).append("\r\n");
        hookTestResult.append("实例方法Inline模式Hook：").append(HookPass.getInstanceMethodInlineResult()).append("\r\n");
        hookTestResult.append("实例方法Replace模式Hook：").append(HookPass.getInstanceMethodReplaceResult()).append("\r\n");
        tv.setText(hookTestResult);
    }


    @Override
    protected void onPause() {
        super.onPause();
        inter = new Inter() {
            @Override
            public void dosth() {
                Log.e("dosth", hashCode() + "");
            }
        };
    }
}

