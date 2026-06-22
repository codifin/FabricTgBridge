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
        // Проверяем, включена ли отправка сообщений чата в конфиге
        if (Bridge.CONFIG.sendChatMessage) {
            val text = packet.chatMessage
            // Игнорируем команды внутри игрового чата (например, /me или кастомные команды)
            if (!text.startsWith("/")) {
                val username = player.name.string
                mixinScope.launch {
                    if (::Bridge.BOT.isInitialized) {
                        Bridge.BOT.sendMessageToTelegram(text, username)
                    }
                }
            }
        }
    }
}