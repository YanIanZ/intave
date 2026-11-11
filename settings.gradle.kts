rootProject.name = "Intave"

pluginManagement {
  repositories {
    gradlePluginPortal()
    maven("https://papermc.io/repo/repository/maven-public/")
  }
}

plugins {
  id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":wureta-proxy-common", ":wureta-proxy-velocity", ":wureta-proxy-bungee")

project(":wureta-proxy-common").projectDir = file("proxy/common")
project(":wureta-proxy-velocity").projectDir = file("proxy/velocity")
project(":wureta-proxy-bungee").projectDir = file("proxy/bungee")