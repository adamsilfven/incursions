package dev.incursions.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

@EventBusSubscriber(modid="incursions",value=Dist.CLIENT)
public final class PauseMenu {
    @SubscribeEvent
    public static void init(ScreenEvent.Init.Post event) {
        if(!(event.getScreen() instanceof PauseScreen screen) || !screen.showsPauseMenu()) return;
        // A compact side button, next to the menu rather than rearranging vanilla controls.
        int left=screen.width/2+106, top=screen.height/4+8;
        int bestY=-1;
        for(int y=Math.max(4,top);y<=screen.height-24;y+=24) {
            boolean free=true;
            for(var child:event.getListenersList()) if(child instanceof AbstractWidget w
                && left<w.getX()+w.getWidth()+2 && left+20>w.getX()-2
                && y<w.getY()+w.getHeight()+2 && y+20>w.getY()-2) {free=false;break;}
            if(free && left+20<=screen.width-4) {bestY=y;break;}
        }
        if(bestY<0) {left=screen.width-24;bestY=4;}
        var button=Button.builder(Component.literal("I"),b->Minecraft.getInstance().setScreen(new IncursionsScreen(screen)))
            .bounds(left,bestY,20,20).build();
        button.setTooltip(Tooltip.create(Component.translatable("incursions.settings.title")));
        event.addListener(button);
    }
}
