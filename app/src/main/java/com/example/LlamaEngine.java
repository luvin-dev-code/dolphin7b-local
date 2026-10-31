package com.example;

public class LlamaEngine {

    private static boolean nativeLibraryLoaded = false;

    static {
        try {
            System.loadLibrary("llama-android");
            nativeLibraryLoaded = true;
        } catch (UnsatisfiedLinkError ignored) {
            nativeLibraryLoaded = false;
        }
    }

    public static boolean isNativeLibraryLoaded() {
        return nativeLibraryLoaded;
    }

    public native long loadModel(String path);

    public native String generateResponse(long ptr, String prompt, int nPredict);

    public native void freeModel(long ptr);
}
