package com.swift.sandhook;

import android.app.Application;
import android.os.Build;
import android.util.Log;

import com.swift.sandhook.test.PendingHookTest;
import com.swift.sandhook.test.TestClass;
import com.swift.sandhook.testHookers.ActivityHooker;
import com.swift.sandhook.testHookers.CtrHook;
import com.swift.sandhook.testHookers.CustmizeHooker;
import com.swift.sandhook.testHookers.JniHooker;
import com.swift.sandhook.testHookers.LogHooker;
import com.swift.sandhook.testHookers.NewAnnotationApiHooker;
import com.swift.sandhook.testHookers.ObjectHooker;
import com.swift.sandhook.wrapper.HookErrorException;
import com.swift.sandhook.xposedcompat.XposedCompat;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class MyApp extends Application {

    //for test pending hook case
    public volatile static boolean initedTest = false;
    public  static String TAG = "sanbo.App";

    @Override
    public void onCreate() {
        super.onCreate();

        SandHookConfig.DEBUG = BuildConfig.DEBUG;

        Log.i(TAG, "current sdk int:" + Build.VERSION.SDK_INT + ",preview sdk int:" + getPreviewSDKInt());

        if (Build.VERSION.SDK_INT == 29 && getPreviewSDKInt() > 0) {
            // Android R preview
            SandHookConfig.SDK_INT = 30;
        }

        SandHook.disableVMInline();
        SandHook.tryDisableProfile(getPackageName());
        SandHook.disableDex2oatInline(false);
        // SandHook.forbidUseNterp();

        if (SandHookConfig.SDK_INT >= Build.VERSION_CODES.P) {
            SandHook.passApiCheck();
        }

        try {
            SandHook.addHookClass(JniHooker.class,
                    CtrHook.class
                    ,LogHooker.class
//                    ,CustmizeHooker.class
                    ,ActivityHooker.class
//                    , ObjectHooker.class
//                    , NewAnnotationApiHooker.class
            );
        } catch (HookErrorException e) {
            e.printStackTrace();
        }

        //for xposed compat(no need xposed comapt new)
        XposedCompat.cacheDir = getCacheDir();

        //for load xp module(sandvxp)
        XposedCompat.context = this;
        XposedCompat.classLoader = getClassLoader();
        XposedCompat.isFirstApplication= true;

        HookPass.init();
    }

    public static int getPreviewSDKInt() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                return Build.VERSION.PREVIEW_SDK_INT;
            } catch (Throwable e) {
                // ignore
            }
        }
        return 0;
    }
}
