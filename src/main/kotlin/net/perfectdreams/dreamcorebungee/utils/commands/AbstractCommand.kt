package net.perfectdreams.dreamcorebungee.utils.commands

import net.md_5.bungee.api.CommandSender
import net.md_5.bungee.api.connection.ProxiedPlayer
import net.md_5.bungee.api.plugin.Command
import net.md_5.bungee.api.plugin.Plugin
import net.perfectdreams.dreamcorebungee.utils.commands.annotation.Subcommand
import net.perfectdreams.dreamcorebungee.DreamCoreBungee
import net.perfectdreams.dreamcorebungee.utils.commands.annotation.ArgumentType
import net.perfectdreams.dreamcorebungee.utils.commands.annotation.InjectArgument
import net.perfectdreams.dreamcorebungee.utils.commands.annotation.SubcommandPermission
import net.perfectdreams.dreamcorebungee.utils.extensions.toTextComponent
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import javax.activation.CommandMap
import kotlin.reflect.full.valueParameters
import kotlin.reflect.jvm.kotlinFunction

open class AbstractCommand(
		val label: String,
		val aliases: Array<String> = arrayOf(),
		val permission: String? = null,
		val withoutPermission: String? = null,
		val withoutPermissionCallback: ((CommandSender, Array<String>) -> (Unit))? = null
) {
	lateinit var reflectCommand: Command
	val withoutPermissionCallbacks = mutableMapOf<String, ((CommandSender, Array<String>) -> (Unit))>()

	fun register(plugin: Plugin): AbstractCommand {
		val cmd = ReflectCommand(this.label, this, aliases)
		reflectCommand = cmd
		DreamCoreBungee.INSTANCE.proxy.pluginManager.registerCommand(plugin, cmd)
		return this
	}

	class ReflectCommand constructor(command: String, val abstractCommand: AbstractCommand, aliases: Array<String>) : Command(command, null, *aliases) {
		override fun execute(sender: CommandSender, args: Array<String>) {
			val baseClass = abstractCommand::class.java

			if (abstractCommand.permission != null && !sender!!.hasPermission(abstractCommand.permission)) {
				if (abstractCommand.withoutPermissionCallback != null) {
					abstractCommand.withoutPermissionCallback.invoke(sender, args)
					return
				}
				sender.sendMessage(abstractCommand.withoutPermission?.toTextComponent() ?: DreamCoreBungee.dreamConfig.withoutPermission?.toTextComponent())
				return
			}

			// Ao executar, nós iremos pegar várias anotações para ver o que devemos fazer agora
			val methods = this.abstractCommand::class.java.methods

			for (method in methods.filter { it.isAnnotationPresent(Subcommand::class.java) }.sortedByDescending { it.parameterCount }) {
				val subcommandAnnotation = method.getAnnotation(Subcommand::class.java)
				val values = subcommandAnnotation.values
				for (value in values.map { it.split(" ") }) {
					var matchedCount = 0
					for ((index, text) in value.withIndex()) {
						val arg = args.getOrNull(index)
						if (text == arg)
							matchedCount++
					}
					val matched = matchedCount == value.size
					if (matched) {
						if (executeMethod(baseClass, method, sender, args, matchedCount))
							return
					}
				}
			}

			// Nenhum comando foi executado... #chateado
			for (method in methods.filter { it.isAnnotationPresent(Subcommand::class.java) }.sortedByDescending { it.parameterCount }) {
				val subcommandAnnotation = method.getAnnotation(Subcommand::class.java)
				if (subcommandAnnotation.values.isEmpty()) {
					if (executeMethod(baseClass, method, sender, args, 0))
						return
				}
			}
			return
		}

		fun executeMethod(baseClass: Class<out AbstractCommand>, method: Method, sender: CommandSender, args: Array<String>, skipArgs: Int): Boolean {
			if (!checkPermission(baseClass, method, sender, args))
				return false

			// check method arguments
			val arguments = args.toMutableList()
			for (i in 0 until skipArgs)
				arguments.removeAt(0)

			val params = getContextualArgumentList(method, sender, arguments)

			// Agora iremos "validar" o argument list antes de executar
			for ((index, parameter) in method.kotlinFunction!!.valueParameters.withIndex()) {
				if (!parameter.type.isMarkedNullable && params.getOrNull(index) == null)
					return false
			}

			if (params.size != method.parameterCount)
				return false

			try {
				method.invoke(abstractCommand, *params.toTypedArray())
			} catch (e: InvocationTargetException) {
				val targetException = e.targetException
				if (targetException is ExecutedCommandException) {
					sender.sendMessage(targetException.minecraftMessage?.toTextComponent() ?: e.message?.toTextComponent() ?: "§cAlgo de errado aconteceu ao usar o comando...".toTextComponent())
				} else {
					throw e
				}
			}
			return true
		}

		fun checkPermission(baseClass: Class<out AbstractCommand>, annotatedElement: AnnotatedElement, sender: CommandSender?, args: Array<String>): Boolean {
			val permissionAnnotation = annotatedElement.getAnnotation(SubcommandPermission::class.java)
			// println("Has permission annotation? $permissionAnnotation")

			if (permissionAnnotation != null && sender?.hasPermission(permissionAnnotation.permission) == false) {
				// Se o usuário não tem permissão...
				if (permissionAnnotation.callbackName.isNotEmpty()) {
					val callback = abstractCommand.withoutPermissionCallbacks[permissionAnnotation.callbackName] ?: throw RuntimeException("Callback ${permissionAnnotation.callbackName} não encontrado!")
					callback.invoke(sender, args)
					return false
				}
				val message = permissionAnnotation.message.replace("{UseDefaultMessage}", DreamCoreBungee.dreamConfig.withoutPermission)
				sender.sendMessage(message.toTextComponent())
				return false
			}
			return true
		}

		fun getContextualArgumentList(method: Method, sender: CommandSender, arguments: MutableList<String>): List<Any?> {
			var dynamicArgIdx = 0
			val params = mutableListOf<Any?>()

			for ((index, param) in method.parameters.withIndex()) {
				val typeName = param.type.simpleName.toLowerCase()
				val injectArgumentAnnotation = param.getAnnotation(InjectArgument::class.java)
				when {
					injectArgumentAnnotation != null && injectArgumentAnnotation.type == ArgumentType.PLAYER -> {
						val argument = arguments.getOrNull(dynamicArgIdx)
						dynamicArgIdx++
						if (argument != null) {
							val player = DreamCoreBungee.INSTANCE.proxy.getPlayer(argument)
							params.add(player)
						}
					}
					injectArgumentAnnotation != null && injectArgumentAnnotation.type == ArgumentType.CUSTOM_ARGUMENT -> {
						// Suporte a injected arguments personalizados
						val argument = arguments.getOrNull(dynamicArgIdx)
						val customInjector = CommandManager.argumentContexts.firstOrNull { it.clazz == param.type }
						dynamicArgIdx++
						if (customInjector == null || argument == null) {
							params.add(null)
						} else {
							val value = customInjector.callback.invoke(sender, argument)
							params.add(value)
						}
					}
					injectArgumentAnnotation != null && injectArgumentAnnotation.type == ArgumentType.CUSTOM -> {
						val customInjector = CommandManager.contexts.firstOrNull { it.clazz == param.type && it.name == injectArgumentAnnotation.name }
						if (customInjector == null) {
							params.add(null)
						} else {
							val value = customInjector.callback.invoke(sender)
							params.add(value)
						}
					}
					injectArgumentAnnotation != null && injectArgumentAnnotation.type == ArgumentType.ARGUMENT_LIST -> {
						if (arguments.isNotEmpty()) {
							val duplicated = arguments.toMutableList()
							for (idx in 0 until dynamicArgIdx) {
								duplicated.removeAt(0)
							}
							if (duplicated.isNotEmpty())
								params.add(duplicated.joinToString(" "))
						}
					}
					injectArgumentAnnotation != null && injectArgumentAnnotation.type == ArgumentType.ALL_ARGUMENTS_LIST -> {
						params.add(arguments.joinToString(" "))
					}
					CommandManager.contexts.any { it.clazz == param.type && it.name == null } -> {
						val customInjector = CommandManager.contexts.firstOrNull { it.clazz == param.type && it.name == null }
						if (customInjector == null) {
							params.add(null)
						} else {
							val value = customInjector.callback.invoke(sender)
							params.add(value)
						}
					}
					param.type.isAssignableFrom(ProxiedPlayer::class.java) && sender is ProxiedPlayer -> { params.add(sender) }
					param.type.isAssignableFrom(CommandSender::class.java) && sender is CommandSender -> { params.add(sender) }
					param.type.isAssignableFrom(String::class.java) -> {
						params.add(arguments.getOrNull(dynamicArgIdx))
						dynamicArgIdx++
					}
					// Sim, é necessário usar os nomes assim, já que podem ser tipos primitivos ou objetos
					typeName == "int" || typeName == "integer" -> {
						params.add(arguments.getOrNull(dynamicArgIdx)?.toIntOrNull())
						dynamicArgIdx++
					}
					typeName == "double" -> {
						params.add(arguments.getOrNull(dynamicArgIdx)?.toDoubleOrNull())
						dynamicArgIdx++
					}
					typeName == "float" -> {
						params.add(arguments.getOrNull(dynamicArgIdx)?.toFloatOrNull())
						dynamicArgIdx++
					}
					typeName == "long" -> {
						params.add(arguments.getOrNull(dynamicArgIdx)?.toLongOrNull())
						dynamicArgIdx++
					}
					param.type.isAssignableFrom(Array<String>::class.java) -> {
						params.add(arguments.subList(dynamicArgIdx, arguments.size).toTypedArray())
					}
					param.type.isAssignableFrom(Array<Int?>::class.java) -> {
						params.add(arguments.subList(dynamicArgIdx, arguments.size).map { it.toIntOrNull() }.toTypedArray())
					}
					param.type.isAssignableFrom(Array<Double?>::class.java) -> {
						params.add(arguments.subList(dynamicArgIdx, arguments.size).map { it.toDoubleOrNull() }.toTypedArray())
					}
					param.type.isAssignableFrom(Array<Float?>::class.java) -> {
						params.add(arguments.subList(dynamicArgIdx, arguments.size).map { it.toFloatOrNull() }.toTypedArray())
					}
					param.type.isAssignableFrom(Array<Long?>::class.java) -> {
						params.add(arguments.subList(dynamicArgIdx, arguments.size).map { it.toLongOrNull() }.toTypedArray())
					}
					else -> params.add(null)
				}
			}
			return params
		}
	}
}
