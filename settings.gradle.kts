pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        maven {
            name = "linphone"
            url = uri("https://download.linphone.org/maven_repository/")
        }
    }
}

rootProject.name = "CallOnLinesSoftphone"
include(":app")
