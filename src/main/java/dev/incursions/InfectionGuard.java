package dev.incursions;

import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/** Policy scoped to the attacking entity; never clears a victim's existing infection. */
public final class InfectionGuard {
    public static final String TAG="incursions_block_utheric_infection";
    private InfectionGuard() {}
    public static boolean suppress(LivingIncomingDamageEvent event) {
        Entity source=event.getSource().getEntity();
        return source!=null && Protection.tagged(source) && source.getPersistentData().getBoolean(TAG);
    }
}
