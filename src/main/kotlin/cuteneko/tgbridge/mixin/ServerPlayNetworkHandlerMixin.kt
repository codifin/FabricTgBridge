package cuteneko.tgbridge.mixin

import cuteneko.tgbridge.Bridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket
import net.minecraft.server.network.ServerPlayNetworkHandler
import net.minecraft.server.network.ServerPlayerEntity
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(ServerPlayNetworkHandler::class)
class ServerPlayNetworkHandlerMixin {

    @Shadow
    lateinit var player: ServerPlayerEntity

    private val mixinScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Inject(method = ["onChatMessage"], at = [At("TAIL")])
    fun onChatMessageInject(packet: ChatMessageC2SPacket, ci: CallbackInfo) {
        if (Bridge.CONFIG.sendChatMessage) {
            val text = packet.chatMessage
            if (!text.startsWith("/")) {
                val username = player.name.string
                mixinScope.launch {
                    // ИСПРАВЛЕНО: Безопасное обращение к внешнему lateinit свойству BOT через try-catch
                    try {
                        val bot = Bridge.BOT
                        bot.sendMessageToTelegram(text, username)
                    } catch (e: UninitializedPropertyAccessException) {
                        // Бот еще не успел инициализироваться, либо выключен — игнорируем отправку пакета
                    }
                }
            }
        }
    }
}