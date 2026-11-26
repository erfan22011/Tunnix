val aarFile = file("libv2ray.aar")
if (!aarFile.exists()) {
    try {
        println("Downloading libv2ray.aar (Xray-core native engine)...")
        val url = java.net.URI("https://github.com/2dust/AndroidLibXrayLite/releases/download/v26.11.9/libv2ray.aar").toURL()
        url.openStream().use { input ->
            aarFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        println("Downloaded libv2ray.aar successfully!")
    } catch (e: Exception) {
        println("Warning: libv2ray.aar not found and download skipped or offline: ${e.message}")
    }
}

configurations.maybeCreate("default")
if (aarFile.exists()) {
    artifacts.add("default", aarFile)
}
