package dev.incursions;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Optional control channel: clients without the UI can still join the server. */
public final class SettingsNetwork {
    public record State(boolean enabled, boolean editable, boolean failed) {}
    public record Control(int request, boolean change, boolean enabled, boolean editable, boolean failed)
            implements CustomPacketPayload {
        public static final Type<Control> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Incursions.ID,"settings"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Control> CODEC = StreamCodec.of(
            (b,p)->{b.writeVarInt(p.request);b.writeBoolean(p.change);b.writeBoolean(p.enabled);b.writeBoolean(p.editable);b.writeBoolean(p.failed);},
            b->new Control(b.readVarInt(),b.readBoolean(),b.readBoolean(),b.readBoolean(),b.readBoolean()));
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    // Received only on the client main thread; contains no client classes on dedicated servers.
    public static Control latest;
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playBidirectional(Control.TYPE,Control.CODEC,(p,c)->{
            if(c.flow()==PacketFlow.CLIENTBOUND) {latest=p;return;}
            var state=apply((ServerPlayer)c.player(),p.change,p.enabled);
            c.reply(new Control(p.request,false,state.enabled,state.editable,state.failed));
        });
    }
    public static boolean mayEdit(boolean owner, boolean operator) {return owner || operator;}
    /** Called on the server thread. Never trusts permission fields supplied by a client. */
    public static State apply(ServerPlayer player, boolean change, boolean enabled) {
        var server=player.getServer();
        boolean editable=server!=null && mayEdit(server.isSingleplayerOwner(player.getGameProfile()),player.hasPermissions(2));
        boolean previous=Settings.ENABLED.get();
        if(change && editable && enabled!=previous) {
            try {
                Settings.ENABLED.set(enabled);
                Settings.SPEC.save();
                Incursions.LOG.info("Incursions enabled={} by {}",enabled,player.getGameProfile().getName());
            } catch(RuntimeException failure) {
                Settings.ENABLED.set(previous);
                Incursions.LOG.error("Could not save Incursions setting",failure);
                return new State(previous,editable,true);
            }
        }
        return new State(Settings.ENABLED.get(),editable,false);
    }
}
