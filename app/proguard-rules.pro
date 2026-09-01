# iTantra ProGuard Rules

# ==================== ONNX Runtime ====================
-keep class ai.onnxruntime.** { *; }
-keepclassmembers class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# ==================== Protocol Buffers ====================
-keep class com.google.protobuf.** { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite { *; }
-keep class com.itantra.core.proto.** { *; }
-dontwarn com.google.protobuf.**

# ==================== Kotlin ====================
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keepclassmembers class kotlinx.coroutines.** { *; }
-dontwarn kotlin.**

# ==================== Android ====================
-keep class androidx.** { *; }
-keepclassmembers class * implements android.os.Parcelable { *; }
-keepclassmembers class * implements java.io.Serializable { *; }

# ==================== iTantra Domain Models ====================
-keep class com.itantra.domain.model.** { *; }
-keep class com.itantra.domain.contracts.** { *; }
-keep class com.itantra.core.service.ITantraForegroundService { *; }
-keep class com.itantra.core.service.ITantraForegroundService$ITantraBinder { *; }

# ==================== Compose ====================
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Keep enum names (used in error mapping)
-keepclassmembers enum * { *; }
