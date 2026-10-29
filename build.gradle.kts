// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.secrets) apply false
  alias(libs.plugins.google.services) apply false
}

// Ensure debug.keystore exists by decoding debug.keystore.base64 if missing (e.g., in CI or clean checkouts)
val debugKeystore = file("${rootDir}/debug.keystore")
if (!debugKeystore.exists()) {
  val base64File = file("${rootDir}/debug.keystore.base64")
  if (base64File.exists()) {
    try {
      val cleanBase64 = base64File.readText().replace("\\s+".toRegex(), "")
      val decoded = java.util.Base64.getDecoder().decode(cleanBase64)
      debugKeystore.writeBytes(decoded)
    } catch (_: Exception) {}
  }
}
