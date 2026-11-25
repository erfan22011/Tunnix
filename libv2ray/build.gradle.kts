val aarFile = file("libv2ray.aar")
if (!aarFile.exists()) {
    println("Downloading libv2ray.aar (Xray-core native engine)...")
    val url = java.net.URI("https://github.com/2dust/AndroidLibXrayLite/releases/download/v26.11.9/libv2ray.aar").toURL()
    url.openStream().use { input ->
        aarFile.outputStream().use { output ->
            input.copyTo(output)
        }
    }
    println("Downloaded libv2ray.aar successfully!")
}

configurations.maybeCreate("default")
artifacts.add("default", aarFile)
