plugins {
    // Allow blossom to mark sources root of templates
    idea
    alias(libs.plugins.blossom)
    id("geyser.publish-conventions")
    id("io.freefair.lombok")
}

dependencies {
    constraints {
        implementation(libs.raknet) // Ensure protocol does not override the RakNet version
    }

    api(projects.common)
    api(projects.api)

    // Jackson JSON and YAML serialization
    api(libs.bundles.jackson)
    api(libs.guava)

    // Fastutil Maps
    implementation(libs.bundles.fastutil)

    // Network libraries
    implementation(libs.websocket)

    api(libs.bundles.protocol)

    api(libs.mcauthlib)
    api(libs.minecraftauth)
    api(libs.mcprotocollib) {
        exclude("io.netty", "netty-all")
        exclude("net.raphimc", "MinecraftAuth")
        exclude("com.github.GeyserMC", "packetlib")
        exclude("com.github.GeyserMC", "mcauthlib")
    }

    implementation(libs.raknet) {
        exclude("io.netty", "*")
    }


    // Network dependencies we are updating ourselves
    api(libs.netty.handler)
    implementation(libs.netty.codec.haproxy)

    api(libs.netty.transport.native.epoll) { artifact { classifier = "linux-x86_64" } }
    implementation(libs.netty.transport.native.epoll) { artifact { classifier = "linux-aarch_64" } }
    // kqueue is macos only
    implementation(libs.netty.transport.native.kqueue) { artifact { classifier = "osx-x86_64" } }
    api(libs.netty.transport.native.io.uring) { artifact { classifier = "linux-x86_64" } }
    implementation(libs.netty.transport.native.io.uring) { artifact { classifier = "linux-aarch_64" } }

    // Adventure text serialization
    api(libs.bundles.adventure)

    // command library
    api(libs.cloud.core)

    api(libs.erosion.common) {
        isTransitive = false
    }

    // Test
    testImplementation(libs.junit)
    testImplementation(libs.mockito)

    // Annotation Processors
    compileOnly(projects.ap)

    annotationProcessor(projects.ap)

    api(libs.events)
}

tasks.processResources {
    // This is solely for backwards compatibility for other programs that used this file before the switch to gradle.
    // It used to be generated by the maven Git-Commit-Id-Plugin
    filesMatching("git.properties") {
        val info = GitInfo()
        expand(
            "branch" to info.branch,
            "buildNumber" to info.buildNumber,
            "projectVersion" to info.version,
            "commit" to info.commit,
            "commitAbbrev" to info.commitAbbrev,
            "commitMessage" to info.commitMessage,
            "repository" to info.repository
        )
    }
}

sourceSets {
    main {
        blossom {
            val info = GitInfo()
            javaSources {
                property("version", info.version)
                property("gitVersion", info.gitVersion)
                property("buildNumber", info.buildNumber.toString())
                property("branch", info.branch)
                property("commit", info.commit)
                property("repository", info.repository)
                property("devVersion", info.isDev.toString())
            }
        }
    }
}

fun isDevBuild(branch: String, repository: String): Boolean {
    return branch != "master" || repository.equals("https://github.com/GeyserMC/Geyser", ignoreCase = true).not()
}

inner class GitInfo {
    val branch: String
    val commit: String
    val commitAbbrev: String

    val gitVersion: String
    val version: String
    val buildNumber: Int

    val commitMessage: String
    val repository: String

    val isDev: Boolean

    init {
        branch = indraGit.branchName() ?: "DEV"

        val commit = indraGit.commit()
        this.commit = commit?.name ?: "0".repeat(40)
        commitAbbrev = commit?.name?.substring(0, 7) ?: "0".repeat(7)

        gitVersion = "git-${branch}-${commitAbbrev}"

        val git = indraGit.git()
        commitMessage = git?.commit()?.message ?: ""
        repository = git?.repository?.config?.getString("remote", "origin", "url") ?: ""

        buildNumber = buildNumber()
        isDev = isDevBuild(branch, repository)
        val projectVersion = if (isDev) project.version else projectVersion(project)
        version = "$projectVersion ($gitVersion)"
    }
}

// Manual task to download the bedrock data files from the CloudburstMC/Data repository
// Invoke with ./gradlew :core:downloadBedrockData --suffix=1_20_70
// Set suffix to the current Bedrock version
tasks.register<DownloadFilesTask>("downloadBedrockData") {
    urls = listOf(
        "https://raw.githubusercontent.com/CloudburstMC/Data/master/entity_identifiers.dat",
        "https://raw.githubusercontent.com/CloudburstMC/Data/master/biome_definitions.dat",
        "https://raw.githubusercontent.com/CloudburstMC/Data/master/block_palette.nbt",
        "https://raw.githubusercontent.com/CloudburstMC/Data/master/creative_items.json",
        "https://raw.githubusercontent.com/CloudburstMC/Data/master/runtime_item_states.json"
    )
    suffixedFiles = listOf("block_palette.nbt", "creative_items.json", "runtime_item_states.json")

    destinationDir = "$projectDir/src/main/resources/bedrock"
}
