import org.jetbrains.gradle.ext.packagePrefix
import org.jetbrains.gradle.ext.settings
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import java.util.zip.ZipFile

plugins {
	kotlin("jvm")
	id("dev.architectury.loom")
	id("architectury-plugin")
	id("me.modmuss50.mod-publish-plugin") version "0.8.3"
}

val mcVersion = stonecutter.current.version
val loaderPlatform: String = requireNotNull(findProperty("loom.platform") as? String) { "loom.platform property is required but not found" }

fun nextPatchVersion(version: String): String {
	val parts = version.split(".")
	if (parts.size < 3) return version
	val major = parts[0]
	val minor = parts[1]
	val patch = parts[2].toIntOrNull() ?: return version
	return "$major.$minor.${patch + 1}"
}

val modGroup: String by project
val modVersion: String by project
val modId: String by project

group = modGroup
version = modVersion
base { archivesName = "${modId}-${loaderPlatform}" }

architectury {
	when (loaderPlatform) {
		"neoforge" -> neoForge()
		"fabric" -> fabric()
		else -> forge()
	}
	platformSetupLoomIde()
	minecraft = mcVersion
}

loom {
	silentMojangMappingsLicense()
	log4jConfigs.from(rootProject.file("log4j2.xml"))
	accessWidenerPath = rootProject.file("src/main/resources/$modId.accesswidener")

	decompilers {
		get("vineflower").apply {
			options.put("mark-corresponding-synthetics", "1")
		}
	}

	runs {
		named("client") {
			client()
			configName = "Client"
			runDir("../../.runs/client")
			programArg("--username=Player")
			ideConfigGenerated(true)
		}
		named("server") {
			server()
			configName = "Server"
			runDir("../../.runs/server")
			ideConfigGenerated(true)
		}
	}
}

repositories {
	mavenCentral()
	maven("https://maven.architectury.dev/")
	maven("https://maven.minecraftforge.net/")
	maven("https://maven.neoforged.net/releases")
	maven("https://maven.parchmentmc.org/")
	maven("https://thedarkcolour.github.io/KotlinForForge/")
	maven("https://maven.fabricmc.net/")
}

extensions.configure<KotlinJvmProjectExtension>("kotlin") {
	sourceSets.named("main") {
		kotlin.srcDir(rootProject.file("src/$loaderPlatform/kotlin"))
	}
}

val loaderVersion: String by project

dependencies {
	val parchmentMinecraftVersion: String by project
	val parchmentMappingsVersion: String by project

	minecraft("com.mojang:minecraft:$mcVersion")
	mappings(loom.layered {
		officialMojangMappings()
		parchment("org.parchmentmc.data:parchment-$parchmentMinecraftVersion:$parchmentMappingsVersion@zip")
	})

	when (loaderPlatform) {
		"neoforge" -> {
			"neoForge"("net.neoforged:neoforge:${loaderVersion}")
			if (mcVersion == "1.21.11") {
				implementation("thedarkcolour:kotlinforforge-neoforge:6.2.0")
			} else {
				implementation("thedarkcolour:kotlinforforge-neoforge:5.6.0")
			}
		}
		"fabric" -> {
			val fabricApiVersion: String by project
			val fabricKotlinVersion: String by project
			"modImplementation"("net.fabricmc:fabric-loader:${loaderVersion}")
			"modImplementation"("net.fabricmc.fabric-api:fabric-api:${fabricApiVersion}")
			"modImplementation"("net.fabricmc:fabric-language-kotlin:${fabricKotlinVersion}")
			"compileOnly"("com.google.code.findbugs:jsr305:3.0.2")
		}
		else -> {
			"forge"("net.minecraftforge:forge:${mcVersion}-${loaderVersion}")
			if (mcVersion.startsWith("1.20")) {
				implementation("thedarkcolour:kotlinforforge:4.12.0")
			} else {
				implementation("thedarkcolour:kotlinforforge:3.12.0")
			}
		}
	}

	// bytedeco/FFmpeg — compileOnly so we compile against it but don't bundle .class files.
	// At runtime, bytedeco Java classes are provided by watermedia or another mod.
	// We ONLY bundle the native DLLs/SOs — these don't create JPMS packages and won't conflict.
	compileOnly("org.bytedeco:javacpp:1.5.13")
	compileOnly("org.bytedeco:ffmpeg:8.0.1-1.5.13")

	// Native-only deps: we extract ONLY .dll/.so files from these (no .class files)
	val nativeBundle by configurations.creating { isTransitive = false }
	nativeBundle("org.bytedeco:javacpp:1.5.13:windows-x86_64")
	nativeBundle("org.bytedeco:javacpp:1.5.13:linux-x86_64")
	nativeBundle("org.bytedeco:ffmpeg:8.0.1-1.5.13:windows-x86_64")
	nativeBundle("org.bytedeco:ffmpeg:8.0.1-1.5.13:linux-x86_64")
	// Also need the base JARs for pom.properties (version detection)
	nativeBundle("org.bytedeco:javacpp:1.5.13")
	nativeBundle("org.bytedeco:ffmpeg:8.0.1-1.5.13")
}
if (loaderPlatform == "neoforge") {
	configurations.all {
		exclude(group = "net.neoforged.fancymodloader", module = "loader")
		exclude(group = "net.neoforged.fancymodloader", module = "earlydisplay")
	}
}

val javaVersion: String by project

// Extract ONLY native libraries (DLLs/SOs) and version metadata from bytedeco JARs.
// No .class files → no JPMS package conflicts with watermedia or any other mod.
val extractNatives = tasks.register("extractNatives", Sync::class) {
	duplicatesStrategy = DuplicatesStrategy.EXCLUDE
	from(provider {
		configurations.named("nativeBundle").get().files.map { zipTree(it) }
	}) {
		// ── Native libraries only (no .class files!) ──
		// Windows
		include("org/bytedeco/ffmpeg/windows-x86_64/avcodec-*.dll")
		include("org/bytedeco/ffmpeg/windows-x86_64/avformat-*.dll")
		include("org/bytedeco/ffmpeg/windows-x86_64/avutil-*.dll")
		include("org/bytedeco/ffmpeg/windows-x86_64/swresample-*.dll")
		include("org/bytedeco/ffmpeg/windows-x86_64/jniavcodec.dll")
		include("org/bytedeco/ffmpeg/windows-x86_64/jniavformat.dll")
		include("org/bytedeco/ffmpeg/windows-x86_64/jniavutil.dll")
		include("org/bytedeco/ffmpeg/windows-x86_64/jniswresample.dll")
		include("org/bytedeco/javacpp/windows-x86_64/*.dll")
		// Linux
		include("org/bytedeco/ffmpeg/linux-x86_64/libavcodec*")
		include("org/bytedeco/ffmpeg/linux-x86_64/libavformat*")
		include("org/bytedeco/ffmpeg/linux-x86_64/libavutil*")
		include("org/bytedeco/ffmpeg/linux-x86_64/libswresample*")
		include("org/bytedeco/ffmpeg/linux-x86_64/libjniavcodec*")
		include("org/bytedeco/ffmpeg/linux-x86_64/libjniavformat*")
		include("org/bytedeco/ffmpeg/linux-x86_64/libjniavutil*")
		include("org/bytedeco/ffmpeg/linux-x86_64/libjniswresample*")
		include("org/bytedeco/javacpp/linux-x86_64/*")
		// Version metadata (needed by JavaCPP Loader for library discovery)
		include("META-INF/maven/org.bytedeco/**/pom.properties")
		includeEmptyDirs = false
	}
	into(layout.buildDirectory.dir("generated/natives"))
}
tasks.named<Jar>("jar") {
	dependsOn(extractNatives)
	from(extractNatives)
	manifest.attributes("MixinConfigs" to "musync.mixins.json")
}

val verifyReleaseJar = tasks.register("verifyReleaseJar") {
	dependsOn(tasks.named("remapJar"))
	doLast {
		val jarFile = tasks.named<org.gradle.jvm.tasks.Jar>("remapJar").get().archiveFile.get().asFile
		ZipFile(jarFile).use { zip ->
			val entries = zip.entries().asSequence().map { it.name }.toSet()
				if (loaderPlatform == "fabric") {
					require("fabric.mod.json" in entries) { "Missing fabric.mod.json in ${jarFile.name}" }
					require("musync.accesswidener" in entries) { "Missing musync.accesswidener in ${jarFile.name}" }
				}
		}
	}
}

tasks {
	processResources {
		val modName: String by project
		val modDescription: String by project
		val modLicense: String by project
		val modAuthors: String by project
		val kffLoaderVersionRange = (findProperty("kffLoaderVersionRange") as? String)
			?: if (loaderPlatform == "fabric") "*" else error("kffLoaderVersionRange property is required for $loaderPlatform")

		val packFormat: String by project
		val mcUpperBound = nextPatchVersion(mcVersion)
		val props = mutableMapOf(
			"java_version" to javaVersion,
			"minecraft_version" to mcVersion,
			"minecraft_version_range" to "[$mcVersion,$mcUpperBound)",
			"pack_format" to packFormat,

			"mod_version" to modVersion,
			"mod_group" to modGroup,
			"mod_id" to modId,

			"mod_name" to modName,
			"mod_description" to modDescription,
			"mod_license" to modLicense,
			"mod_authors" to modAuthors,

			"loader_version" to loaderVersion,
			"kff_loader_version_range" to kffLoaderVersionRange,
		)

		inputs.properties(props)
		filesMatching(listOf("META-INF/mods.toml", "META-INF/neoforge.mods.toml", "fabric.mod.json", "**.mixins.json", "pack.mcmeta")) {
			expand(props)
		}
		when (loaderPlatform) {
			"neoforge" -> {
				exclude("META-INF/mods.toml")
				exclude("fabric.mod.json")
			}
			"fabric" -> {
				exclude("META-INF/mods.toml")
				exclude("META-INF/neoforge.mods.toml")
			}
			else -> {
				exclude("META-INF/neoforge.mods.toml")
				exclude("fabric.mod.json")
			}
		}
	}

	remapJar {
		archiveFileName.set("${modId}-${loaderPlatform}-${mcVersion}+${modVersion}.jar")
		if (loaderPlatform == "fabric") {
			injectAccessWidener.set(false)
		} else {
			injectAccessWidener.set(true)
			atAccessWideners.add(loom.accessWidenerPath.get().asFile.name)
		}
	}

	named("build") {
		dependsOn(verifyReleaseJar)
	}
}

kotlin {
	jvmToolchain(javaVersion.toInt())
}

java {
	withSourcesJar()
}

publishMods {
	file.set(tasks.named<org.gradle.jvm.tasks.Jar>("remapJar").flatMap { it.archiveFile })

	val clFile = project.findProperty("changelogFile") as? String
	if (clFile != null && rootProject.file(clFile).exists()) {
		changelog.set(rootProject.file(clFile).readText())
	} else {
		changelog.set("No changelog provided.")
	}

	type.set(STABLE)
	modLoaders.add(loaderPlatform)
	
	val uploadName = "musync-${loaderPlatform}-${mcVersion}+${modVersion}"
	displayName.set(uploadName)
	version.set(uploadName)

	val cfToken = providers.environmentVariable("CURSEFORGE_TOKEN")
	val mrToken = providers.environmentVariable("MODRINTH_TOKEN")

	val publishMcVersion = if (mcVersion == "1.21.11") "1.21.1" else mcVersion

	if (cfToken.isPresent) {
		curseforge {
			projectId.set("1474038")
			accessToken.set(cfToken)
			minecraftVersions.add(publishMcVersion)
			clientRequired.set(true)
			serverRequired.set(true)
		}
	}

	if (mrToken.isPresent) {
		modrinth {
			projectId.set("Pdt1iYTy")
			accessToken.set(mrToken)
			minecraftVersions.add(publishMcVersion)
		}
	}
}
