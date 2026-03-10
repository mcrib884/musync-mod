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
	if (loaderPlatform == "neoforge") neoForge() else forge()
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
	maven("https://maven.neoforged.net/releases")
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

	if (loaderPlatform == "neoforge") {
		"neoForge"("net.neoforged:neoforge:${loaderVersion}")
		implementation("thedarkcolour:kotlinforforge-neoforge:5.6.0")
	} else {
		"forge"("net.minecraftforge:forge:${mcVersion}-${loaderVersion}")
		if (mcVersion.startsWith("1.20")) {
			implementation("thedarkcolour:kotlinforforge:4.12.0")
		} else {
			implementation("thedarkcolour:kotlinforforge:3.12.0")
		}
	}
}

// NeoForge dev run: the Loom-remapped loader jar pulls in net.neoforged.fancymodloader:loader
// via a transitive range dep, putting two jars providing the same fml_loader Java module on the
// module path. Exclude the redundant Maven artifact so only the Loom-remapped version is used.
if (loaderPlatform == "neoforge") {
	configurations.all {
		exclude(group = "net.neoforged.fancymodloader", module = "loader")
		exclude(group = "net.neoforged.fancymodloader", module = "earlydisplay")
	}
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
		filesMatching(listOf("META-INF/mods.toml", "META-INF/neoforge.mods.toml", "**.mixins.json", "pack.mcmeta")) {
			expand(props)
		}
		if (loaderPlatform == "neoforge") {
			exclude("META-INF/mods.toml")
		} else {
			exclude("META-INF/neoforge.mods.toml")
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