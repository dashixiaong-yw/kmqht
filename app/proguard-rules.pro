# ---- Retrofit ----
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# ---- Gson 序列化数据类 ----
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.kuaimai.pda.data.api.dto.** { *; }

# ---- Room ----
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# ---- WorkManager Worker ----
-keep class com.kuaimai.pda.data.OrderSyncWorker { *; }

# ---- 抑制警告 ----
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn coil.**
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# ---- CameraX ----
-keep class androidx.camera.** { *; }
