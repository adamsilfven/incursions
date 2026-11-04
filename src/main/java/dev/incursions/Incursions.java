package dev.incursions;

import com.mojang.logging.LogUtils;
import com.mojang.brigadier.Command;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

@Mod(Incursions.ID)
public final class Incursions {
    public static final String ID="incursions";
    public static final Logger LOG=LogUtils.getLogger();
    private Director director;
    public Incursions(IEventBus bus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER,Settings.SPEC);
        bus.addListener(SettingsNetwork::register);
        NeoForge.EVENT_BUS.addListener(this::reload);
        NeoForge.EVENT_BUS.addListener(this::tick);
        NeoForge.EVENT_BUS.addListener(this::started);
        NeoForge.EVENT_BUS.addListener(this::stopping);
        NeoForge.EVENT_BUS.addListener(this::commands);
        Protection.register();
    }
    private void reload(AddReloadListenerEvent e) { e.addListener(new Definitions()); }
    private void started(ServerStartedEvent e) { director=new Director(e.getServer()); }
    private void stopping(ServerStoppingEvent e) { if(director!=null) director.stop(); director=null; }
    private void tick(ServerTickEvent.Post e) { if(director!=null) director.tick(); }
    private int trigger(net.minecraft.commands.CommandSourceStack source,String dimension,boolean elite) {
        source.sendSuccess(()->Component.literal(director==null?"Server not ready":director.trigger(dimension,elite)),false);
        return Command.SINGLE_SUCCESS;
    }
    private void commands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("incursions").requires(s->s.hasPermission(2))
            .then(Commands.literal("trigger")
                .then(Commands.literal("nether").executes(c->trigger(c.getSource(),"nether",false))
                    .then(Commands.literal("elite").executes(c->trigger(c.getSource(),"nether",true))))
                .then(Commands.literal("aether").executes(c->trigger(c.getSource(),"aether",false))
                    .then(Commands.literal("elite").executes(c->trigger(c.getSource(),"aether",true))))
                .then(Commands.literal("undergarden").executes(c->trigger(c.getSource(),"undergarden",false))
                    .then(Commands.literal("elite").executes(c->trigger(c.getSource(),"undergarden",true))))
                .then(Commands.literal("twilight").executes(c->trigger(c.getSource(),"twilight",false))
                    .then(Commands.literal("elite").executes(c->trigger(c.getSource(),"twilight",true)))))
            .then(Commands.literal("stop").executes(c->{
                c.getSource().sendSuccess(()->Component.literal(director==null?"Server not ready":director.cancel()),false);
                return Command.SINGLE_SUCCESS;
            }))
            .then(Commands.literal("status").executes(c->{ c.getSource().sendSuccess(()->Component.literal(director==null?"Server not ready":director.status()),false);return Command.SINGLE_SUCCESS; }))
            .then(Commands.literal("origins").executes(c->{if(director==null)c.getSource().sendSuccess(()->Component.literal("Server not ready"),false); else director.originReport().forEach(line->c.getSource().sendSuccess(()->Component.literal(line),false));return 1;}))
            .then(Commands.literal("validate").executes(c->{
                c.getSource().sendSuccess(()->Component.literal("Definitions generation "+Definitions.current.generation()+": "+Definitions.current.profiles().size()+" enabled profiles"),false);
                Definitions.current.diagnostics().forEach(x->c.getSource().sendSuccess(()->Component.literal(x),false)); return 1;
            })));
    }
}
