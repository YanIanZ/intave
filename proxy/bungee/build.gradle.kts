plugins {
  java
  id("com.gradleup.shadow") version "9.4.1"
}

repositories {
  mavenCentral()
  maven("https://repo.papermc.io/repository/maven-public/")
  maven("https://libraries.minecraft.net")
}

dependencies {
  implementation(project(":wureta-proxy-common"))
  compileOnly("net.md-5:bungeecord-api:1.21-R0.4")
}

tasks.withType<JavaCompile>().configureEach {
  options.encoding = "UTF-8"
  options.release.set(8)
}

tasks.shadowJar {
  archiveFileName.set("WuretaProxy-Bungee.jar")
  archiveClassifier.set("")
  destinationDirectory.set(layout.buildDirectory.dir("libs"))
}
