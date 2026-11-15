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
        // Pleos Connect SDK(공개 저장소)
        maven("https://nexus-playground.pleos.ai/repository/maven-releases/") {
            content {
                includeGroup("ai.pleos.playground")
                includeGroup("ai.pleos.playground.security")      // SpeechToText 가 끌어오는 universal-access
            }
        }
    }
}

rootProject.name = "music_android"
include(":app")
 