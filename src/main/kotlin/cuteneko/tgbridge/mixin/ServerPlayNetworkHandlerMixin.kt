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

@Mixin(ServerPlayNetworkHandler::class)
class ServerPlayNetworkHandlerMixin {

    // aliases указывает промежуточное имя поля для сборки без refMap
    @Shadow(aliases = ["field_14115", "player"])
    lateinit var player: ServerPlayerEntity

    @Inject(
        // method содержит и читаемое имя, и обфусцированное method_31286
        method = ["onChatGameMessage", "method_31286"], 
        at = [At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/PlayerManager;broadcast(Lnet/minecraft/text/Text;Ljava/util/function/Function;Lnet/minecraft/network/MessageType;Ljava/util/UUID;)V"
        )]
    )
    private fun onPlayerChatMessage(packet: ChatMessageC2SPacket, ci: CallbackInfo) {
        val username = this.player.name.string
        val message = packet.chatMessage
        
        // Передаем сообщение в наш Котлин-мост
        Bridge.onPlayerChat(username, message)
    }
}