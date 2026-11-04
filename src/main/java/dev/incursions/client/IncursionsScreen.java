package dev.incursions.client;

import dev.incursions.SettingsNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** One world/server toggle. Integrated server tasks also work while the game is paused. */
final class IncursionsScreen extends Screen {
    private static int nextRequest;
    private final Screen parent;
    private Button toggle;
    private int pending, elapsed, poll;
    private SettingsNetwork.State state;
    private String message="incursions.settings.loading";
    IncursionsScreen(Screen parent) {super(Component.translatable("incursions.settings.title"));this.parent=parent;}
    protected void init() {
        toggle=addRenderableWidget(Button.builder(Component.translatable("incursions.settings.loading"),b->request(true,!state.enabled()))
            .bounds(width/2-100,height/2-25,200,20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE,b->onClose()).bounds(width/2-100,height/2+65,200,20).build());
        refresh();request(false,false);
    }
    private void request(boolean change, boolean enabled) {
        var connection=minecraft.getConnection();
        if(connection==null) {message="incursions.settings.unavailable";state=null;refresh();return;}
        pending=++nextRequest;elapsed=0;toggle.active=false;
        int id=pending;
        var server=minecraft.getSingleplayerServer();
        if(server!=null) {
            var uuid=minecraft.player.getUUID();
            server.execute(()->{
                var player=server.getPlayerList().getPlayer(uuid);
                if(player==null)return;
                var result=SettingsNetwork.apply(player,change,enabled);
                minecraft.execute(()->accept(id,result));
            });
        } else if(connection.hasChannel(SettingsNetwork.Control.TYPE)) {
            PacketDistributor.sendToServer(new SettingsNetwork.Control(id,change,enabled,false,false));
        } else {pending=0;state=null;message="incursions.settings.unavailable";refresh();}
    }
    private void accept(int id,SettingsNetwork.State result) {
        if(id!=pending || minecraft.screen!=this) return;
        pending=0;poll=0;state=result;
        message=result.failed()?"incursions.settings.failed":result.editable()?"incursions.settings.scope":"incursions.settings.readonly";
        refresh();
    }
    private void refresh() {
        if(toggle==null)return;
        toggle.setMessage(Component.translatable(state==null?"incursions.settings.loading":state.enabled()?"incursions.settings.on":"incursions.settings.off"));
        toggle.active=pending==0 && state!=null && state.editable();
    }
    public void tick() {
        var reply=SettingsNetwork.latest;
        if(pending!=0 && reply!=null && reply.request()==pending)
            accept(reply.request(),new SettingsNetwork.State(reply.enabled(),reply.editable(),reply.failed()));
        if(pending!=0 && ++elapsed>=100) {pending=0;state=null;message="incursions.settings.timeout";refresh();}
        if(pending==0 && ++poll>=40) {poll=0;request(false,false);}
    }
    public void onClose() {minecraft.setScreen(parent);}
    public void render(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) {
        super.render(graphics,mouseX,mouseY,partialTick);
        graphics.drawCenteredString(font,title,width/2,Math.max(12,height/2-65),0xFFFFFF);
        graphics.drawWordWrap(font,Component.translatable(message),width/2-140,height/2+5,280,0xCCCCCC);
        graphics.drawWordWrap(font,Component.translatable("incursions.settings.warning"),width/2-140,height/2+32,280,0xAAAAAA);
    }
}
