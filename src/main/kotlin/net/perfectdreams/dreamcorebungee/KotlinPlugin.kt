package net.perfectdreams.dreamcorebungee

import net.md_5.bungee.api.plugin.Plugin
import net.perfectdreams.dreamcorebungee.commands.SparklyBungeeCommand

abstract class KotlinPlugin : Plugin() {

    val commandManager = DreamCoreBungee.commandManager

    fun registerCommand(cmd: SparklyBungeeCommand) {
        commandManager.registerCommand(cmd)
    }

    fun unregisterCommand(cmd: SparklyBungeeCommand) {
        commandManager.unregisterCommand(cmd)
    }

}