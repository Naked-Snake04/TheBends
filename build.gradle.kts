import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    maven { url = uri("https://jitpack.io") }
    google()
}

dependencies {
    // Note, if you develop a library, you should use compose.desktop.common.
    // compose.desktop.currentOs should be used in launcher-sourceSet
    // (in a separate module for demo project and in testMain).
    // With compose.desktop.common you will also lose @Preview functionality
    implementation(compose.desktop.currentOs)
    // JTransforms
    implementation("com.github.wendykierp:JTransforms:3.1")

    // Apache Commons Math
    implementation("org.apache.commons:commons-math3:3.6.1")

    // JFreeChart
    implementation("org.jfree:jfreechart:1.5.3")
}

compose.desktop {
    application {
        mainClass = "MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Exe)
            packageName = "TheBends"
            packageVersion = "1.0.0"

            windows {
                menu = true
                console = false
                upgradeUuid = "f25d7c2c-7c11-4db8-b1ae-8f8608e1a2a5"
            }

            buildTypes.release.proguard {
                isEnabled.set(false)
            }
        }
    }
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "MainKt"
    }
    from({
        configurations["compileClasspath"].map { zipTree(it) }
    })
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}
