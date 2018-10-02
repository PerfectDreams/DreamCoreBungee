package net.perfectdreams.dreamcorebungee.utils.commands

import net.md_5.bungee.api.ChatColor

class ExecutedCommandException(val minecraftMessage: String? = null, message: String? = null) : RuntimeException(message ?: ChatColor.stripColor(minecraftMessage))