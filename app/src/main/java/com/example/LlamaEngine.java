package com.example;

import android.util.Log;

public class LlamaEngine {

    private static final String TAG = "LlamaEngine";
    private static boolean nativeLibraryLoaded = false;
    private static String loadError = null;

    static {
        StringBuilder errors = new StringBuilder();
        tryLoadRequired("c++_shared", errors);
        tryLoadOptional("omp");
        tryLoadRequired("ggml-base", errors);
        tryLoadRequired("ggml-cpu", errors);
        tryLoadRequired("ggml", errors);
        tryLoadRequired("llama", errors);
        try {
            System.loadLibrary("llama-android");
            nativeLibraryLoaded = true;
            loadError = null;
            Log.i(TAG, "Successfully loaded libllama-android.so");
        } catch (UnsatisfiedLinkError e) {
            nativeLibraryLoaded = false;
            if (errors.length() > 0) {
                errors.append(" | ");
            }
            errors.append(e.getMessage());
            loadError = errors.toString();
            Log.e(TAG, "Failed to load native libraries: " + loadError);
        } catch (Throwable t) {
            nativeLibraryLoaded = false;
            if (errors.length() > 0) {
                errors.append(" | ");
            }
            errors.append(t.getMessage());
            loadError = errors.toString();
            Log.e(TAG, "Native library load error: " + loadError);
        }
    }

    private static void tryLoadRequired(String libName, StringBuilder errors) {
        try {
            System.loadLibrary(libName);
        } catch (Throwable t) {
            if (errors.length() > 0) {
                errors.append(" | ");
            }
            errors.append("lib").append(libName).append(".so: ").append(t.getMessage());
        }
    }

    private static void tryLoadOptional(String libName) {
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
