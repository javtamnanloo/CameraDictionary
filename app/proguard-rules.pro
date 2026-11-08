# Extra rules for R8, the tool that shrinks the release build (see isMinifyEnabled in build.gradle.kts).
# R8 removes code that looks unused. Code that libraries find "by name" at runtime must be kept here.

# ML Kit (text recognition) creates its internal parts by name when the app starts. In R8's
# default "full mode", the library's own rule keeps these classes but not their constructors
# or methods, and the release build then crashes on start. Keep them whole (they are tiny).
-keep class * implements com.google.firebase.components.ComponentRegistrar { *; }

# Keep file names and line numbers in crash reports (Google Play shows them in readable form).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
