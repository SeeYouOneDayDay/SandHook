package com.swift.sandhook.test;

import android.util.Log;

public class TestClass {

    String HOOK_IN = "sanbo.TestClass.HOOK_IN";
    String ORIGIN = "sanbo.TestClass.ORIGIN";
    public int a = 1;
    int b = 2;

    public TestClass() {
    }

    public TestClass(int a) {
        this.a = a + 1;
    }

    public boolean add1() {
        a++;
        b++;
        return false;
    }

    public boolean add2() {
        Log.i(ORIGIN, "add2 result:" + (a + b));
        return false;
    }

    public Integer testStub(int a) {
        Log.e(ORIGIN, "call testStub origin" + a);
        return a;
    }

    public native void jni_test();


}
