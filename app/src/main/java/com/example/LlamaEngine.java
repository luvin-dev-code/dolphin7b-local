package com.example;

import android.util.Log;

public class LlamaEngine {

    private static final String TAG = "LlamaEngine";
    private static boolean nativeLibraryLoaded = false;
    private static String loadError = null;

    static {
        tryLoad("c++_shared");
        tryLoad("omp");
        tryLoad("ggml-base");
        tryLoad("ggml-cpu");
        tryLoad("ggml");
        tryLoad("llama");
        try {
            System.loadLibrary("llama-android");
            nativeLibraryLoaded = true;
            Log.i(TAG, "Successfully loaded libllama-android.so");
        } catch (UnsatisfiedLinkError e) {
            nativeLibraryLoaded = false;
            loadError = e.getMessage();
            Log.w(TAG, "Native library companion dependencies pending: " + e.getMessage());
        } catch (Throwable t) {
            nativeLibraryLoaded = false;
            loadError = t.getMessage();
            Log.w(TAG, "Native library load status: " + t.getMessage());
        }
    }

    private static void tryLoad(String libName) {
        try {
            System.loadLibrary(libName);
        } catch (Throwable ignored) {
        }
    }

    public static boolean isNativeLibraryLoaded() {
        return nativeLibraryLoaded;
    }

    public static String getLoadError() {
        return loadError;
    }

    public native long loadModel(String path);

    public native String generateResponse(long ptr, String prompt, int nPredict);

    public native void freeModel(long ptr);
}
