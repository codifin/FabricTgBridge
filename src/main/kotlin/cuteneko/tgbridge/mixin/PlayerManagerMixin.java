package cuteneko.tgbridge.mixin;

import cuteneko.tgbridge.Bridge;
import net.minecraft.network.MessageType;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(PlayerManager.class)
public abstract class PlayerManagerMixin {

    @Inject(
        method = "broadcast(Lnet/minecraft/text/Text;Lnet/minecraft/network/MessageType;Ljava/util/UUID;)V",
        at = @At("HEAD")
    )
    private void tgbridge$onBroadcast(Text message, MessageType type, UUID sender, CallbackInfo ci) {
        if (type != MessageType.CHAT) return;
        if (!(message instanceof TranslatableText t) || !"chat.type.text".equals(t.getKey())) return;

        Object[] args = t.getArgs();
        if (args.length < 2) return;

        ServerPlayerEntity player = ((PlayerManager) (Object) this).getPlayer(sender);
        String name = player != null
                ? player.getName().getString()
                : (args[0] instanceof Text n ? n.getString() : String.valueOf(args[0]));
        String content = args[1] instanceof Text c ? c.getString() : String.valueOf(args[1]);

        Bridge.Companion.onPlayerChat(name, content);
    }
}