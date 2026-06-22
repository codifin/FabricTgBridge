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

    // ИСПРАВЛЕНО: Добавлен Intermediary-маппинг метода (method_14364) и его дескриптор,
    // чтобы сервер понимал цель инжекта в обфусцированной среде без refMap.
    @Inject(
        method = ["method_14364(Lnet/minecraft/network/packet/c2s/play/ChatMessageC2SPacket;)V", "onChatMessage"], 
        at = [At("TAIL")],
        remap = false
    )
    fun onChatMessageInject(packet: ChatMessageC2SPacket, ci: CallbackInfo) {
        if (Bridge.CONFIG.sendChatMessage) {
            val text = packet.chatMessage
            if (!text.startsWith("/")) {
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