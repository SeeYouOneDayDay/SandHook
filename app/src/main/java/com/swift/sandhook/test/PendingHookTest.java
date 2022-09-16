package com.swift.sandhook.test;

import android.util.Log;

import com.swift.sandhook.MyApp;

public class PendingHookTest {

    static {
        if (!MyApp.initedTest) {
            throw new RuntimeException("PendingHookTest.class may can not init this time!");
        }
    }

    public static boolean test() {
        Log.e("PendingHookTest", "hook failure!");
        return false;
    }

}
