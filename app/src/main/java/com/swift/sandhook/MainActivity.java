package com.swift.sandhook;

import android.app.Activity;
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

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TextView tv =new TextView( this);
        tv.setText("测试");
        setContentView(tv);

        methodBeHooked(hashCode(), 1);




        final TestClass str = new TestClass(1);

        str.add1();
        str.add2();
        str.testNewHookApi(this, 1);

        str.jni_test();

        Log.e("dd", str.a + "");

        inter = new InterImpl();

        new Thread(new Runnable() {
            @Override
            public void run() {
                inter.dosth();
                inter = new Inter() {
                    @Override
                    public void dosth() {
                        Log.e("dosth", hashCode() + "");
                    }
                };
                Log.e("testStub", "res = " + testStub(str, 1, "origin b", false, 'x', "origin e"));
            }
        }).start();

        inter.dosth();

        testPluginHook(str);

        MyApp.initedTest = true;
        try {
            PendingHookTest.test();
        } catch (Throwable e) {

        }
    }

    public static Field getField(Class topClass, String fieldName) throws NoSuchFieldException {
        while (topClass != null && topClass != Object.class) {
            try {
                return topClass.getDeclaredField(fieldName);
            } catch (Exception e) {
            }
            topClass = topClass.getSuperclass();
        }
        throw new NoSuchFieldException(fieldName);
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

    public static int methodBeHooked(int a, int b) {
        a = a + 1 + 2;
        b = b + a + 3;
        Log.e("MainActivity", "call methodBeHooked origin");
        return a + b;
    }

    public int testPluginHook(TestClass testClass) {
        Log.e("MainActivity", "call testPluginHook origin");
        return testClass.a;
    }

    public Integer testStub(TestClass testClass, int a, String b, boolean c, char d, String e) {
        Log.e("MainActivity", "call testStub origin" + a + ("" + c) + d + e);
        return a;
    }

}

