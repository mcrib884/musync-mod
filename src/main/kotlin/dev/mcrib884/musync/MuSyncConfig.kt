package dev.mcrib884.musync

import org.apache.logging.log4j.LogManager
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import java.io.File

object MuSyncConfig {
    private val configPath: Path by lazy {
        val server = currentServer()
        val dir = if (server != null) serverDir(server) else File("config")
        Path.of(dir.toPath().toString(), "musync.properties")
    }

    @Volatile
    var logging: Boolean = false
        private set

    fun initialize() {
        val props = Properties()
        try {
            val parent = configPath.parent
            if (parent != null) Files.createDirectories(parent)
            if (Files.exists(configPath)) {
                Files.newInputStream(configPath).use { props.load(it) }
            } else {
                Files.writeString(configPath, "logging=false\n")
                props.setProperty("logging", "false")
            }
            logging = props.getProperty("logging", "false").trim().equals("true", ignoreCase = true)
        } catch (_: Exception) {
            logging = false
        }
    }
}

object MuSyncLog {
    private val logger = LogManager.getLogger("MuSync")

    fun debug(message: String, vararg args: Any?) {
        if (MuSyncConfig.logging) logger.debug(message, *args)
    }

    fun info(message: String, vararg args: Any?) {
        logger.info(message, *args)
    }

    fun warn(message: String, vararg args: Any?) {
        logger.warn(message, *args)
    }

    fun error(message: String, vararg args: Any?) {
        logger.error(message, *args)
    }
}
