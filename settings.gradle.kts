pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = java.net.URI("https://jitpack.io") }
    }
}

rootProject.name = "Tunnix"

include(":app")
include(":libv2ray")
include(":core-models")
include(":subscription-parser")
include(":config-modifier")
include(":xray-core")
include(":vpn-service")
include(":traffic-monitor")
include(":settings")
include(":ui")
