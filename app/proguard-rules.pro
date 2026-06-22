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

# ---- Room Entity 类（不混淆字段名） ----
-keep class com.kuaimai.pda.data.db.entity.** { *; }

# ---- Sealed/Enum 类（R8全模式需要） ----
-keep class com.kuaimai.pda.update.CheckResult { *; }
-keep class com.kuaimai.pda.update.DownloadState { *; }
-keep class com.kuaimai.pda.update.CheckResult$* { *; }
-keep class com.kuaimai.pda.update.DownloadState$* { *; }
-keep enum com.kuaimai.pda.scanner.ScanFeedbackType { *; }
-keep enum com.kuaimai.pda.util.NetworkMonitor$Status { *; }
-keep class com.kuaimai.pda.ui.settings.UpdateCheckUiState { *; }

# ---- Hilt/Dagger 组件 ----
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# ---- 抑制警告 ----
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn coil.**
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# ---- Tink（加密库）----
-dontwarn com.google.errorprone.annotations.**
