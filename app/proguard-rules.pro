# Glide
-keep class * extends com.bumptech.glide.module.AppGlideModule
-keep class * implements com.bumptech.glide.module.GlideModule
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}

# Coroutines
-keepclassmembernames class kotlinx.coroutines.internal.MainDispatcherFactory {
    public java.util.List getMainDispatcherFactory();
}

# Data Binding
-keep class com.hitbha.photo.databinding.** { *; }

# Custom views used in editor
-keep class com.hitbha.photo.ui.editor.CropOverlayView { *; }
-keep class com.hitbha.photo.ui.editor.EraseOverlayView { *; }
-keep class com.hitbha.photo.ui.editor.StampOverlayView { *; }
-keep class com.hitbha.photo.ui.passport.FullPageOverlayView { *; }
-keep class com.hitbha.photo.ui.cut.CutRectangleOverlayView { *; }
-keep class com.hitbha.photo.ui.editor.ToolMode { *; }

# ML Kit
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
-keep class com.google.android.gms.tasks.** { *; }

# Background Removal
-keep class com.hitbha.photo.utils.bgremover.** { *; }

-dontwarn com.sun.**
-dontwarn javax.xml.**


