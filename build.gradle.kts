import org.jetbrains.gradle.ext.packagePrefix
import org.jetbrains.gradle.ext.settings
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

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

	implementation("com.googlecode.soundlibs:jlayer:1.0.1.4")
	if (loaderPlatform == "fabric") {
		include("com.googlecode.soundlibs:jlayer:1.0.1.4")
	}
}
if (loaderPlatform == "neoforge") {
	configurations.all {
		exclude(group = "net.neoforged.fancymodloader", module = "loader")
		exclude(group = "net.neoforged.fancymodloader", module = "earlydisplay")
	}
}

val javaVersion: String by project

val extractJlayer = tasks.register("extractJlayer", Copy::class) {
	from(provider {
		val jar = configurations.runtimeClasspath.get().files.find { it.name.startsWith("jlayer-") }
		if (jar != null) zipTree(jar) else files()
	}) {
		exclude("META-INF/**")
	}
	into(layout.buildDirectory.dir("classes/java/main"))
}
tasks.named("classes") {
	dependsOn(extractJlayer)
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
