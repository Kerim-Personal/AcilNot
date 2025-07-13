# Hilt (Dependency Injection)
-keep class dagger.hilt.internal.aggregatedroot.codegen.*
-keep class *..HiltComponents.*
-keep class *..Dagger*HiltComponents_SingletonC*
-keep class hilt_aggregated_deps.*
-keep class *..HiltWrapper_HiltViewModelFactory_ViewModelFactoriesEntryPoint
-keep class *..HiltViewModelFactory
-keep class *..HiltWrapper_HiltViewModelFactory_ActivityCreatorEntryPoint
-dontwarn dagger.hilt.internal.aggregatedroot.codegen.*

# Room (Veritabanı)
-keep class androidx.room.paging.LimitOffsetDataSource
-keep class * extends androidx.room.RoomDatabase
-keep class * extends androidx.room.TypeConverter
-keep class * extends androidx.room.migration.Migration

# GSON (JSON işlemleri)
-keep class com.google.gson.reflect.TypeToken
-keep class * extends com.google.gson.reflect.TypeToken
-keep class com.codenzi.snapnote.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keepnames class kotlinx.coroutines.flow.** { *; }
-keepclassmembers class kotlinx.coroutines.flow.internal.AbortFlow { *; }

# Lisans Kütüphanesi (ANA ÇÖKME SEBEBİ)
-keep class com.pairip.licensecheck.** { *; }
-dontwarn com.pairip.licensecheck.**

# Android'in kendi sınıfları
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public void set*(...);
}