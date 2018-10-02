package net.perfectdreams.dreamcorebungee.listeners

import net.md_5.bungee.api.event.PostLoginEvent
import net.md_5.bungee.event.EventHandler

class PlayerListener {
	@EventHandler
	fun onLogin(e: PostLoginEvent) {
		val uniqueId = e.player.uniqueId
	}
}