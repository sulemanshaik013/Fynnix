# ============================================================
# Fynnix ProGuard / R8 rules
# ============================================================
# Preserve stack-trace line numbers in crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---- Kotlin ------------------------------------------------
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings { <fields>; }
-keepclassmembers class kotlin.coroutines.** { *; }

# ---- Hilt / Dagger -----------------------------------------
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keep @javax.inject.Singleton class * { *; }
-keepclasseswithmembers class * {
    @dagger.* <fields>;
    @dagger.* <methods>;
    @javax.inject.* <fields>;
    @javax.inject.* <methods>;
}

# ---- Room --------------------------------------------------
# Keep all Room Entity, Dao, and Database classes intact so
# the generated code references survive shrinking.
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Database class * { *; }
-keep @androidx.room.TypeConverter class * { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase {
    abstract ** *Dao();
}
# Keep data classes used as Room query results (e.g. CategorySpendRow).
-keep class com.fynnix.app.features.transactions.data.CategorySpendRow { *; }
-keep class com.fynnix.app.features.transactions.data.MerchantGroupRow { *; }

# ---- SQLCipher ---------------------------------------------
-keep class net.sqlcipher.** { *; }
-dontwarn net.sqlcipher.**

# ---- WorkManager + HiltWorker ------------------------------
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.CoroutineWorker { *; }
-keep @androidx.hilt.work.HiltWorker class * { *; }
-keep class androidx.work.** { *; }

# ---- Biometric ---------------------------------------------
-keep class androidx.biometric.** { *; }

# ---- EncryptedSharedPreferences ----------------------------
-keep class androidx.security.crypto.** { *; }

# ---- Kotlin Serialization (if added later) -----------------
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }

# ---- App domain classes ------------------------------------
# Keep all parser models — they use reflection-free data classes but
# the sealed interface name must survive for logging.
-keep class com.fynnix.app.core.parser.** { *; }

# Keep enums used in Room type converters — their .name() / .valueOf()
# must be stable across builds.
-keepclassmembers enum com.fynnix.app.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    public final java.lang.String name();
}

# Keep all Entity / DAO / Repository classes by package.
-keep class com.fynnix.app.features.**.data.** { *; }
-keep class com.fynnix.app.core.database.** { *; }
-keep class com.fynnix.app.core.security.** { *; }
