# kotlinx.serialization keeps its generated serializers via the bundled consumer rules.
# Add app-specific keep rules here if R8 strips something needed at runtime.

# Navigation routes, the update manifest and the map JSON models are @Serializable: keep them whole
# so route names and JSON field names survive obfuscation. They are tiny.
-keep @kotlinx.serialization.Serializable class io.github.valentinaschiavon99.megyejatek.** { *; }

# Readable crash reports: keep file names and line numbers (Crashlytics de-obfuscates the rest).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
