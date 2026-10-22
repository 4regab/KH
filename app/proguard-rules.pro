# ponytail: app code is not shrunk, only libraries. Moshi reads our model classes by reflection
# (moshi-kotlin), Retrofit reads the API interfaces by reflection, and the WebView calls our
# @JavascriptInterface methods by name. Keeping every com.kisskh.tv class whole is the safe shortcut.
# Upgrade path: Moshi codegen (@JsonClass(generateAdapter = true)) and narrower keeps, so R8 can
# shrink and rename our own code too.
-keep class com.kisskh.tv.** { *; }
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*, Exceptions

# Kotlin: Moshi's reflection adapter reads this to find constructors and property names.
-keep class kotlin.Metadata { *; }

# Retrofit (2.11 ships most of its own rules; these are the documented extras).
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.*

# OkHttp: optional TLS providers that are not on Android.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Moshi
-dontwarn org.jetbrains.annotations.**

# Coroutines: the debug agent and service loader entries.
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.debug.**