package dev.unbox.pvp.mixin;
import dev.unbox.unpvp.PvpSettings;
import net.minecraft.client.particle.*;
import net.minecraft.util.*;
import net.minecraft.block.state.IBlockState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
/** OverflowParticles adaptation: filter on spawn and tint/scale once, retaining batched rendering. */
@Mixin(EffectRenderer.class)
public class ParticleMixin {
 @Inject(method="spawnEffectParticle",at=@At("HEAD"),cancellable=true)
 private void filter(int id,double x,double y,double z,double vx,double vy,double vz,int[] args,CallbackInfoReturnable<EntityFX> ci){if(PvpSettings.on("particles")&&((id==9&&!PvpSettings.flag("particles.crit",true))||(id==10&&!PvpSettings.flag("particles.magic",true))||(id==0||id==1||id==2)&&!PvpSettings.flag("particles.explosions",true)||(id==11||id==12||id==13)&&!PvpSettings.flag("particles.smoke",true)||(id>=14&&id<=19)&&!PvpSettings.flag("particles.potions",true)||PvpSettings.number("particles.density",100,0,100)<java.util.concurrent.ThreadLocalRandom.current().nextInt(100)+1))ci.setReturnValue(null);}
 @Inject(method="spawnEffectParticle",at=@At("RETURN"))
 private void style(int id,double x,double y,double z,double vx,double vy,double vz,int[] args,CallbackInfoReturnable<EntityFX> ci){EntityFX fx=ci.getReturnValue();if(fx!=null&&PvpSettings.on("particles")){fx.multipleParticleScaleBy(PvpSettings.number("particles.size",100,25,200)/100f);if(PvpSettings.flag("particles.tint",false)){int c=PvpSettings.color("particles.color","#FFFFFF");fx.setRBGColorF((c>>16&255)/255f,(c>>8&255)/255f,(c&255)/255f);}}}
 @ModifyConstant(method="addEffect",constant=@Constant(intValue=4000)) private int limit(int vanilla){return PvpSettings.on("particles")?PvpSettings.number("particles.limit",2000,100,4000):vanilla;}
 @Inject(method="addBlockDestroyEffects",at=@At("HEAD"),cancellable=true) private void breaking(BlockPos pos,IBlockState state,CallbackInfo ci){if(PvpSettings.on("particles")&&!PvpSettings.flag("particles.blocks",true))ci.cancel();}
 @Inject(method="addBlockHitEffects(Lnet/minecraft/util/BlockPos;Lnet/minecraft/util/EnumFacing;)V",at=@At("HEAD"),cancellable=true) private void hitting(BlockPos pos,EnumFacing side,CallbackInfo ci){if(PvpSettings.on("particles")&&!PvpSettings.flag("particles.blocks",true))ci.cancel();}
}
