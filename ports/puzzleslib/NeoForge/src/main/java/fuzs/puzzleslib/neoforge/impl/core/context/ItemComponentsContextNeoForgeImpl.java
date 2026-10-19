package fuzs.puzzleslib.neoforge.impl.core.context;

import fuzs.puzzleslib.common.api.core.v1.context.ItemComponentsContext;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;

import java.util.Objects;
import java.util.function.Predicate;

public record ItemComponentsContextNeoForgeImpl(ModifyDefaultComponentsEvent event) implements ItemComponentsContext {

    @Override
    public void registerItemComponentsPatch(Item item, InitializerV2 initializer) {
        Objects.requireNonNull(item, "item is null");
        Objects.requireNonNull(initializer, "initializer is null");
        this.event.modify(item, components -> initializer.run(components, components,
                Objects.requireNonNull(NativeComponentContext.LOOKUP.get()), item));
    }

    @Override
    public void registerItemComponentsPatch(Predicate<Item> itemPredicate, InitializerV2 initializer) {
        Objects.requireNonNull(itemPredicate, "item predicate is null");
        Objects.requireNonNull(initializer, "initializer is null");
        // Bind each item explicitly so the old Consumer API retains its item identity.
        for(Item item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
            if(itemPredicate.test(item)) this.registerItemComponentsPatch(item, initializer);
        }
    }
}
