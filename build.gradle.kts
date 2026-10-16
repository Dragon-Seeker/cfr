import org.teavm.gradle.api.JSModuleType
import org.teavm.gradle.api.OptimizationLevel

plugins {
    `java-library`
    alias(libs.plugins.teavm) // order matters?
}

val thisVersion = "0.2.0"

group = "run.slicer"
version = "$thisVersion-${libs.versions.cfr.get()}"
description = "A JavaScript port of the CFR decompiler."

repositories {
    mavenCentral()
    maven("https://teavm.org/maven/repository")
}

// CFR classes this project ships patched copies of (src/main/java/org/benf/cfr/...). They are stripped
// from the CFR jar rather than relying on classpath order: TeaVM puts dependency jars ahead of the
// project's own classes, so upstream's copy would otherwise win in the JS/WASM build.
val shadowedCfrClasses = listOf(
    "org/benf/cfr/reader/util/output/BytecodeTrackingDumper*.class",
)

val cfrUpstream: Configuration = configurations.create("cfrUpstream") {
    isTransitive = false
}

val cfrStripped = tasks.register<Jar>("cfrStripped") {
    description = "CFR with the classes patched by this project removed."
    archiveFileName = "cfr-stripped.jar"
    destinationDirectory = layout.buildDirectory.dir("cfr")
    from({ cfrUpstream.map { zipTree(it) } })
    exclude(shadowedCfrClasses)
}

dependencies {
    cfrUpstream(libs.cfr)
    api(files(cfrStripped))
    teavmAnnotationProcessor(libs.teavm.extension.annotation.processor) // hmm?
    testImplementation(platform(libs.junit.bom))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java.toolchain {
    languageVersion = JavaLanguageVersion.of(21)
}

val debugging = false // set to true if you want an unobfuscated build for debugging
teavm {
    js {
        mainClass = "run.slicer.cfr.Main"
        moduleType = JSModuleType.ES2015
        obfuscated = !debugging
        if (debugging) {
            optimization = OptimizationLevel.NONE
        }
    }

    wasmGC {
        mainClass = "run.slicer.cfr.Main"
        modularRuntime = true
        obfuscated = !debugging
        disassembly = debugging
        if (debugging) {
            optimization = OptimizationLevel.NONE
        }
    }
}

tasks {
    disasmWasmGC {
        html = false
    }

    register<Copy>("copyDist") {
        group = "build"
        description = "Copies the necessary files to the dist directory for distribution."
        dependsOn(generateJavaScript)

        from(
            "README.md", "LICENSE", "LICENSE-CFR", "cfr.js", "cfr.d.ts",
            generateWasmGC, copyWasmGCRuntime
        )
        into("dist")

        duplicatesStrategy = DuplicatesStrategy.INCLUDE

        doLast {
            copy {
                from(generateJavaScript)
                into("dist")
                rename("cfr.js", "cfr.runtime.js")

                duplicatesStrategy = DuplicatesStrategy.INCLUDE
            }
        }
        doLast {
            file("dist/package.json").writeText(
                """
                    {
                      "name": "@dragon-seeker/cfr",
                      "version": "${project.version}",
                      "description": "A JavaScript port of the CFR decompiler (https://github.com/leibnitz27/cfr).",
                      "main": "cfr.js",
                      "types": "cfr.d.ts",
                      "keywords": [
                        "decompiler",
                        "java",
                        "decompilation",
                        "cfr"
                      ],
                      "author": "dragon-seeker (fork of run-slicer/cfr)",
                      "repository": {
                        "type": "git",
                        "url": "git+https://github.com/Dragon-Seeker/cfr.git"
                      },
                      "license": "MIT"
                    }
                """.trimIndent()
            )
        }
    }

    test {
        useJUnitPlatform()

        dependsOn("copyDist") // comment out if you want to edit the output and test
    }

    build {
        dependsOn("copyDist")
    }

    clean {
        delete("dist")
    }
}
