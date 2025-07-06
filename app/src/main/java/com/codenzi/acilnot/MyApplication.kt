package com.codenzi.acilnot

import android.app.Application

// MultiDexApplication'dan miras almaya gerek yok.
// Standart Application sınıfını kullanın.
class MyApplication : Application() {

    // attachBaseContext ve MultiDex.install() metodlarına da gerek yok.
    // Bu metodu tamamen silebilirsiniz.
    override fun onCreate() {
        super.onCreate()
        // Gelecekte uygulama genelinde bir başlangıç kodunuz olursa buraya ekleyebilirsiniz.
    }
}