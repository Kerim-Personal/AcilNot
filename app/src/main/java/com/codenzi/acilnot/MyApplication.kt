package com.codenzi.acilnot

import androidx.multidex.MultiDexApplication
import androidx.multidex.MultiDex // Yeni import
import android.content.Context // Yeni import

class MyApplication : MultiDexApplication() { // MultiDexApplication'dan miras alın

    override fun attachBaseContext(base: Context?) { //
        super.attachBaseContext(base) //
        MultiDex.install(this) // MultiDex'i başlat
    }
}