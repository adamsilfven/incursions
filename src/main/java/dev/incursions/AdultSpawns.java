package dev.incursions;

import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;

/** Normalize known age models before and after native spawn preparation, before final collision checks. */
final class AdultSpawns {
    private AdultSpawns() {}
    static void normalize(Mob mob) {
        if(mob instanceof AgeableMob ageable) ageable.setAge(0);
        if(mob instanceof Zombie zombie) zombie.setBaby(false);
        if(mob instanceof AbstractPiglin piglin) piglin.setBaby(false);
    }
}
