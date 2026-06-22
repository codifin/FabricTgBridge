package cuteneko.tgbridge.mixin

import cuteneko.tgbridge.Bridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket
import net.minecraft.server.network.ServerPlayNetworkHandler
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(ServerPlayNetworkHandler::class)
class ServerPlayNetworkHandlerMixin {

    private val mixinScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Inject(method = ["onChatMessage"], at = [At("TAIL")])
    fun onChatMessageInject(packet: ChatMessageC2SPacket, ci: CallbackInfo) {
        if (Bridge.CONFIG.sendChatMessage) {
            val text = packet.chatMessage
            if (!text.startsWith("/")) {
                // ИСПРАВЛЕНО: Вместо капризного @Shadow поля, мы безопасно приводим текущий контекст (this)
                // к целевому обработчику сети и берём игрока через стандартный метод getPlayer() / player.
                val handler = this as? ServerPlayNetworkHandler ?: return
                val username = handler.player?.name?.string ?: "Unknown Player"
                
                mixinScope.launch {
                    try {
                        val bot = Bridge.BOT
                        bot.sendMessageToTelegram(text, username)
                    } catch (e: UninitializedPropertyAccessException) {
                        // Бот еще не успел инициализироваться, игнорируем
                    }
                }
            }
        }
    }
}