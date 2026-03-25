pluginManagement {
	repositories {
		mavenCentral()
		gradlePluginPortal()
		maven("https://maven.architectury.dev/") { name = "Architectury" }
		maven("https://maven.minecraftforge.net/") { name = "Forge" }
		maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
		maven("https://maven.fabricmc.net/") { name = "FabricMC" }
	}
}

plugins {
	id("dev.kikugie.stonecutter") version "0.7.10"
}

rootProject.name = "Template"

stonecutter {
	create(rootProject) {
		fun mc(mcVersion: String, name: String = mcVersion, loaders: Iterable<String>) {
			for (loader in loaders) {
				version("$name-$loader", mcVersion)
			}
		}

		mc("1.21.11", loaders = listOf("neoforge", "fabric"))
		mc("1.21.1", loaders = listOf("neoforge", "fabric"))
		mc("1.20.1", loaders = listOf("forge", "fabric"))
		mc("1.19.2", loaders = listOf("forge", "fabric"))
	}
}
