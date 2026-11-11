plugins {
  java
  id("com.gradleup.shadow") version "9.4.1"
}

repositories {
  mavenCentral()
  maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
  implementation(project(":wureta-proxy-common"))
  compileOnly("com.velocitypowered:velocity-api:4.3.1")
}

tasks.withType<JavaCompile>().configureEach {
  options.encoding = "UTF-8"
  options.release.set(25)
}

tasks.shadowJar {
  archiveFileName.set("WuretaProxy-Velocity.jar")
  archiveClassifier.set("")
  destinationDirectory.set(layout.buildDirectory.dir("libs"))
}
