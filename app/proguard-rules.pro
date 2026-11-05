-keepattributes Signature,*Annotation*

# The JVM download codec is retained in core for diagnostics and future download UI work.
# Android does not execute this path; R8 removes it from the current application surface.
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn com.twelvemonkeys.**
