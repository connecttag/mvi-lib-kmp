import java.util.Base64

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    `maven-publish`
    signing
}

val pGroup = project.findProperty("group")?.toString()?.takeIf { it.isNotBlank() }
    ?: project.findProperty("LIB_GROUP")?.toString()?.takeIf { it.isNotBlank() }
    ?: "com.github.connecttag"
group = pGroup

val pVersion = project.findProperty("version")?.toString()?.takeIf { it.isNotBlank() && it != "unspecified" }
    ?: project.findProperty("LIB_VERSION")?.toString()?.takeIf { it.isNotBlank() }
    ?: "1.0.0"
version = pVersion

kotlin {
    android {
        namespace = "org.connecttag.lib.mvi"
        compileSdk = 37
        minSdk = 24
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ConnectTagMviLib"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            api(libs.androidx.lifecycle.viewmodel.kmp)
        }

        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.lifecycle.viewmodel.savedstate)
            implementation(libs.kotlinx.coroutines.android)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

publishing {
    publications.withType<MavenPublication> {
        pom {
            name.set("ConnectTag MVI Architecture & Base Library (KMP)")
            description.set("Cross-platform MVI state machine, Base ViewModels, Clean Architecture UseCases, and coroutine pipelines for Kotlin Multiplatform")
            url.set("https://github.com/connecttag/mvi-lib-kmp")
            licenses {
                license {
                    name.set("The Apache License, Version 2.0")
                    url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                }
            }
            developers {
                developer {
                    id.set("connecttagye")
                    name.set("Connect Tag")
                    email.set("connecttagye@gmail.com")
                }
            }
            scm {
                connection.set("scm:git:https://github.com/connecttag/mvi-lib-kmp.git")
                developerConnection.set("scm:git:ssh://git@github.com:connecttag/mvi-lib-kmp.git")
                url.set("https://github.com/connecttag/mvi-lib-kmp/tree/main")
            }
        }
    }
    repositories {
        maven {
            name = "Local"
            url = uri(layout.buildDirectory.dir("repo"))
        }
    }
}

signing {
    val rawSigningKey = System.getenv("GPG_PRIVATE_KEY") ?: project.findProperty("signingKey")?.toString()
    val signingPassword = System.getenv("GPG_PASSPHRASE") ?: project.findProperty("signingPassword")?.toString()
    val signingKeyId = System.getenv("GPG_KEY_ID") ?: project.findProperty("signingKeyId")?.toString()

    if (rawSigningKey != null && signingPassword != null) {
        val signingKey = if (rawSigningKey.contains("BEGIN PGP")) {
            rawSigningKey
        } else {
            try {
                String(Base64.getDecoder().decode(rawSigningKey.trim()), Charsets.UTF_8)
            } catch (e: Exception) {
                rawSigningKey
            }
        }

        if (signingKeyId != null) {
            useInMemoryPgpKeys(signingKeyId, signingKey, signingPassword)
        } else {
            useInMemoryPgpKeys(signingKey, signingPassword)
        }
        sign(publishing.publications)
    } else if (project.hasProperty("signing.keyId")) {
        sign(publishing.publications)
    }
}
