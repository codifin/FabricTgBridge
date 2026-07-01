package cuteneko.tgbridge.mixin;

import cuteneko.tgbridge.Bridge;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public class ServerPlayNetworkHandlerMixin {

    @Shadow 
    public ServerPlayerEntity player;

    @Inject(
        method = "onGameMessage", 
        at = @At(
            value = "INVOKE", 
            target = "Lnet/minecraft/server/PlayerManager;broadcast(Lnet/minecraft/text/Text;Ljava/util/function/Function;Lnet/minecraft/network/MessageType;Ljava/util/UUID;)V"
        )
    )
    private void onPlayerChatMessage(ChatMessageC2SPacket packet, CallbackInfo ci) {
        // Получаем имя игрока и текст сообщения
        String username = this.player.getName().getString();
        String message = packet.getChatMessage();
        
        // Передаем сообщение в наш метод моста, который мы уже починили и русифицировали
        Bridge.Companion.onPlayerChat(username, message);
    }
}