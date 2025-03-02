import org.gradle.internal.os.OperatingSystem
import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.Constants
import kotlin.io.path.name

plugins {
    id("java") // Java support
    alias(libs.plugins.kotlin) // Kotlin support
    alias(libs.plugins.intelliJPlatform) // IntelliJ Platform Gradle Plugin
    alias(libs.plugins.changelog) // Gradle Changelog Plugin
    alias(libs.plugins.qodana) // Gradle Qodana Plugin
    alias(libs.plugins.kover) // Gradle Kover Plugin
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

// Set the JVM language level used to build the project.
kotlin {
    jvmToolchain(17)
}

// Configure project's dependencies
repositories {
    mavenCentral()

    // IntelliJ Platform Gradle Plugin Repositories Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-repositories-extension.html
    intellijPlatform {
        defaultRepositories()
    }
}

// Dependencies are managed with Gradle version catalog - read more: https://docs.gradle.org/current/userguide/platforms.html#sub:version-catalog
dependencies {
    testImplementation(libs.junit)

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        /**
         * << 플러그인 기본 설정 (gradle.properties 참조) >>
         * [name] : 플러그인 이름 설정
         * [group] : 템플릿 사용 후, 설정된 패키지 네임으로 설정
         * [version] : 플러그인 버저닝(Semver 형식)
         */
        create(
            name = providers.gradleProperty("pluginName").get(),
            group = providers.gradleProperty("pluginGroup").get(),
            version = providers.gradleProperty("pluginVersion").get(),
        )

        /**
         * << OS 에 따른 IDE 설치 경로 설정 (gradle.properties 참조) >>
         * [localPath] : Android Studio 설치 경로 (OS에 따른 분기)
         *
         * Android Studio Version Format Check : https://developer.android.com/studio/archive
         */
        if (project.hasProperty("StudioCompilePath")) {
            // Android Studio Compile Path 설정한 경우
            // Compile Path : AI-242.23339.11.2421.12483815 <- 이런거 있음.
            local(property("StudioCompilePath").toString())
        } else {
            // Android Studio Version 등록 시
            // Version format : 2024.2.1.10
            androidStudio(property("platformVersion").toString())
        }
//        if (OperatingSystem.current().isMacOsX) {
//            local(localPath = providers.gradleProperty("androidStudioPathMacOS").get())
//        } else { // isWindows
//            local(localPath = providers.gradleProperty("androidStudioPathWindows").get())
//        }


        /**
         * << IntelliJ Platform plugins 추가 >>
         * (android)
         */
        // Plugin Dependencies. Uses `platformBundledPlugins` property from the gradle.properties file for bundled IntelliJ Platform plugins.
        bundledPlugins(
            providers.gradleProperty("platformBundledPlugins")
                .map { it.split(',').map(String::trim).filter(String::isNotEmpty) })

        instrumentationTools()
        /**
         * << JetBrains Marketplace plugins 추가 >>
         * gradle.properties 파일에 platformPlugins로 설정된 문자열을 , 구분자를 사용해서 리스트 형태를 여러 플러그인 설정
         * (kotlin, java)
         */
        // Plugin Dependencies. Uses `platformPlugins` property from the gradle.properties file for plugin from JetBrains Marketplace.
//        plugins(
//            providers.gradleProperty("platformPlugins")
//                .map { it.split(',').map(String::trim).filter(String::isNotEmpty) })
    }

}

// Configure IntelliJ Platform Gradle Plugin - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html
intellijPlatform {
    pluginConfiguration {
        version = providers.gradleProperty("pluginVersion")

        // Extract the <!-- Plugin description --> section from README.md and provide for the plugin's manifest
        description = providers.fileContents(layout.projectDirectory.file("README.md")).asText.map {
            val start = "<!-- Plugin description -->"
            val end = "<!-- Plugin description end -->"

            with(it.lines()) {
                if (!containsAll(listOf(start, end))) {
                    throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
                }
                subList(indexOf(start) + 1, indexOf(end)).joinToString("\n").let(::markdownToHTML)
            }
        }

        val changelog = project.changelog // local variable for configuration cache compatibility
        // Get the latest available change notes from the changelog file
        changeNotes = providers.gradleProperty("pluginVersion").map { pluginVersion ->
            with(changelog) {
                renderItem(
                    (getOrNull(pluginVersion) ?: getUnreleased())
                        .withHeader(false)
                        .withEmptySections(false),
                    Changelog.OutputType.HTML,
                )
            }
        }

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = providers.gradleProperty("pluginUntilBuild")
        }
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        // The pluginVersion is based on the SemVer (https://semver.org) and supports pre-release labels, like 2.1.7-alpha.3
        // Specify pre-release label to publish the plugin in a custom Release Channel automatically. Read more:
        // https://plugins.jetbrains.com/docs/intellij/deployment.html#specifying-a-release-channel
        channels = providers.gradleProperty("pluginVersion")
            .map { listOf(it.substringAfter('-', "").substringBefore('.').ifEmpty { "default" }) }
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}

// Configure Gradle Changelog Plugin - read more: https://github.com/JetBrains/gradle-changelog-plugin
changelog {
    groups.empty()
    repositoryUrl = providers.gradleProperty("pluginRepositoryUrl")
}

// Configure Gradle Kover Plugin - read more: https://github.com/Kotlin/kotlinx-kover#configuration
kover {
    reports {
        total {
            xml {
                onCheck = true
            }
        }
    }
}

tasks {
    wrapper {
        gradleVersion = providers.gradleProperty("gradleVersion").get()
    }

    publishPlugin {
        dependsOn(patchChangelog)
    }
}

intellijPlatformTesting {
    runIde {
        register("runIdeForUiTests") {
            task {
                jvmArgumentProviders += CommandLineArgumentProvider {
                    listOf(
                        "-Drobot-server.port=8082",
                        "-Dide.mac.message.dialogs.as.sheets=false",
                        "-Djb.privacy.policy.text=<!--999.999-->",
                        "-Djb.consents.confirmation.enabled=false",
                    )
                }
            }

            plugins {
                robotServerPlugin()
            }
        }
    }
}

// https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/1738
configurations {
    named(Constants.Configurations.INTELLIJ_PLATFORM_BUNDLED_MODULES) {
        exclude(Constants.Configurations.Dependencies.BUNDLED_MODULE_GROUP, "com.jetbrains.performancePlugin")
    }
}