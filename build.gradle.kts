import org.jetbrains.gradle.ext.packagePrefix
import org.jetbrains.gradle.ext.settings
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
	kotlin("jvm")
	id("dev.architectury.loom")
	id("architectury-plugin")
}

val mcVersion = stonecutter.current.version
val loaderPlatform: String = requireNotNull(findProperty("loom.platform") as? String) { "loom.platform property is required but not found" }

val modGroup: String by project
val modVersion: String by project
val modId: String by project

group = modGroup
version = modVersion
base { archivesName = "${modId}-${loaderPlatform}" }

architectury {
	forge()
	platformSetupLoomIde()
	minecraft = mcVersion
}

loom {
	silentMojangMappingsLicense()
	log4jConfigs.from(rootProject.file("log4j2.xml"))
	accessWidenerPath = rootProject.file("src/main/resources/$modId.accesswidener")

	decompilers {
		get("vineflower").apply { // Adds names to lambdas - useful for mixins
			options.put("mark-corresponding-synthetics", "1")
		}
	}

	runs {
		named("client") {
			client()
			configName = "Client"
			runDir("../../.runs/client")
			programArg("--username=Dev")
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
	maven("https://maven.parchmentmc.org/")
	maven("https://thedarkcolour.github.io/KotlinForForge/")
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

	"forge"("net.minecraftforge:forge:${mcVersion}-${loaderVersion}")

	implementation("thedarkcolour:kotlinforforge:3.12.0")
}

val javaVersion: String by project

tasks {
	processResources {
		val modName: String by project
		val modDescription: String by project
		val modLicense: String by project
		val modAuthors: String by project

		val packFormat: String by project
		val props = mutableMapOf(
			"java_version" to javaVersion,
			"minecraft_version" to mcVersion,
			"pack_format" to packFormat,

			"mod_version" to modVersion,
			"mod_group" to modGroup,
			"mod_id" to modId,

			"mod_name" to modName,
			"mod_description" to modDescription,
			"mod_license" to modLicense,
			"mod_authors" to modAuthors,

			"loader_version" to loaderVersion,
		)

		inputs.properties(props)
		filesMatching(listOf("META-INF/mods.toml", "**.mixins.json", "pack.mcmeta")) {
			expand(props)
		}
	}

	remapJar {
		injectAccessWidener.set(true)
		atAccessWideners.add(loom.accessWidenerPath.get().asFile.name)
	}
}

kotlin {
	jvmToolchain(javaVersion.toInt())
}

java {
	withSourcesJar()
}