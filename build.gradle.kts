plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    kotlin("jvm") version "2.1.0" apply false
    id("org.jetbrains.compose") version "1.7.3" apply false
}
