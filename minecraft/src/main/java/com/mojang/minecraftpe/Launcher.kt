package com.mojang.minecraftpe

import android.annotation.SuppressLint
import android.os.Bundle
import java.lang.reflect.Method

class Launcher : MainActivity() {
    @SuppressLint("DiscouragedPrivateApi")
    override fun onCreate(bundle: Bundle?) {
        try {
            val addAssetPath: Method = assets.javaClass.getDeclaredMethod(
                "addAssetPath",
                String::class.java
            )
            val mcSource = intent.getStringExtra("MC_SRC")
            addAssetPath.invoke(assets, mcSource)

            val mcSplitSrc = intent.getStringArrayListExtra("MC_SPLIT_SRC")
            if (mcSplitSrc != null) {
                for (splitSource in mcSplitSrc) {
                    addAssetPath.invoke(assets, splitSource)
                }
            }
            super.onCreate(bundle)
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }
    init {
        System.out.println("Loading native libraries")
        System.loadLibrary("mc")
    }
}