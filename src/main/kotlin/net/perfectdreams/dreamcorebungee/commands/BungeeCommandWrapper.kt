package net.perfectdreams.dreamcorebungee.commands

import net.md_5.bungee.api.CommandSender
import net.md_5.bungee.api.plugin.Command

class BungeeCommandWrapper(val commandManager: BungeeCommandManager, val sparklyCommand: SparklyBungeeCommand) : Command(
        sparklyCommand.labels.first(), // Label
        "", // Description (nobody cares)
        "/${sparklyCommand.labels.first()}" // Usage Message (nobody cares²)
) {

    override fun execute(p0: CommandSender, p1: Array<String>) {
        commandManager.dispatchBlocking(p0, sparklyCommand, sparklyCommand.labels.first(), p1)
    }
}