package net.perfectdreams.dreamcorebungee.commands

import net.perfectdreams.commands.BaseCommand

open class SparklyBungeeCommand(override val labels: Array<out String>) : BaseCommand {

    override val subcommands: MutableList<BaseCommand> = mutableListOf()

    init {
        registerSubcommands()
    }
}