val expectedSha256 = "cdfef146218233a31a8b52c8b1016557730d3b63fc121d4c84cacea50e669155"
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

if (aarFile.exists()) {
    val md = java.security.MessageDigest.getInstance("SHA-256")
    aarFile.inputStream().use { input ->
        val buffer = ByteArray(8192)
        var bytesRead = input.read(buffer)
        while (bytesRead != -1) {
            md.update(buffer, 0, bytesRead)
            bytesRead = input.read(buffer)
        }
    }
    val actualSha256 = md.digest().joinToString("") { "%02x".format(it) }
    if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
        throw GradleException("Security Integrity Check Failed: libv2ray.aar checksum mismatch!\nExpected: $expectedSha256\nActual:   $actualSha256")
    }
    println("Verified libv2ray.aar SHA-256 checksum integrity: MATCH")
    configurations.maybeCreate("default")
    artifacts.add("default", aarFile)
} else {
    throw GradleException("libv2ray.aar is missing and could not be retrieved securely!")
}
