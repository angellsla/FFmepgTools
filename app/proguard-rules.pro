# 保留应用包名下的所有公共 API（保守策略，避免过度混淆导致反射/枚举问题）
-keep public class com.devobject.ffmpegtools.** { public *; }

# 保留 Kotlin 元数据、伴生对象和 data class
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings { <fields>; }
-keepclassmembers class kotlin.Metadata { *; }

# 保留所有枚举类，防止导航/下拉框等使用 name() 时出错
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    public static final ** *;
}

# Compose / Navigation
-keep,allowobfuscation,allowshrinking class androidx.navigation.** { *; }
-keep,allowobfuscation,allowshrinking class * extends androidx.navigation.NavType

# Shizuku
-keep class rikka.shizuku.** { *; }
-dontwarn rikka.shizuku.**

# Media3 / ExoPlayer（依赖库自带规则，额外保留公共接口）
-keep class androidx.media3.** { public *; }
-dontwarn androidx.media3.**

# 保留 Parcelable  Creator（MainActivity 中使用了 getParcelableExtra）
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# 保留 Application 子类与 ViewModel 构造函数
-keep class * extends android.app.Application { public <init>(...); }
-keep class * extends androidx.lifecycle.ViewModel { public <init>(...); }

# 移除日志与调试代码
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
}
