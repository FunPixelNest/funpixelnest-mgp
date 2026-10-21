rootProject.name = "FunPixelNestMGP"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        // Mintegral SDK (needed by the AdMob Mintegral adapter); only Mintegral's own packages come from here.
        maven("https://dl-maven-android.mintegral.com/repository/mbridge_android_sdk_oversea/") {
            content { includeGroupAndSubgroups("com.mbridge.msdk") }
        }
    }
}

include(":composeApp")
