package fuzs.puzzleslib.neoforge.mixin;
import fuzs.puzzleslib.neoforge.impl.core.context.NativeComponentContext;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentInitializers;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import java.util.List;
@Mixin(DataComponentInitializers.class)
abstract class DataComponentInitializersNeoForgeMixin {
 @WrapMethod(method="build")
 private List<DataComponentInitializers.PendingComponents<?>> context(HolderLookup.Provider provider,Operation<List<DataComponentInitializers.PendingComponents<?>>> original){
  var previous=NativeComponentContext.LOOKUP.get();NativeComponentContext.LOOKUP.set(provider);
  try{return original.call(provider);}finally{if(previous==null)NativeComponentContext.LOOKUP.remove();else NativeComponentContext.LOOKUP.set(previous);}
 }
}
