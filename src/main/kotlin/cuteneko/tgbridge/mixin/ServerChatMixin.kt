package cuteneko.tgbridge.mixin

import cuteneko.tgbridge.Bridge
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket
import net.minecraft.server.network.ServerPlayNetworkHandler
import net.minecraft.server.network.ServerPlayerEntity
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

// Используем abstract class, это обязательно для правильной работы @Shadow в Kotlin
@Mixin(ServerPlayNetworkHandler::class)
abstract class ServerChatMixin {

    // lateinit var позволяет Mixin'у "подставить" значение переменной без инициализации
    @Shadow
    lateinit var player: ServerPlayerEntity

    @Inject(method = ["onChatMessage"], at = [At("HEAD")])
    private fun onChatMessage(packet: ChatMessageC2SPacket, ci: CallbackInfo) {
        val message = packet.chatMessage
        
        // Отправляем в Telegram только если это обычное сообщение, а не команда (например, /spawn)
        if (!message.startsWith("/")) {
            val username = player.name.string
            
            // Вызываем отправку. Она работает в фоне (bridgeScope) и не вызывает лагов.
            Bridge.onPlayerChat(username, message)
        }
    }
}