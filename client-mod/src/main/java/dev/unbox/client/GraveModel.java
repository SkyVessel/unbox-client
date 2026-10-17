package dev.unbox.client;

import com.mojang.math.Transformation;
import dev.unbox.client.mixin.GraveBlockDisplayAccess;
import dev.unbox.client.mixin.GraveDisplayAccess;
import dev.unbox.client.mixin.GraveTextDisplayAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.ArrayList;

/** Vanilla display entities render a thin tablet; an invisible anchor owns mining and storage. */
public final class GraveModel {
    static final String PART = "unbox.grave_model";
    static final int PART_COUNT = 3;
    private static final net.minecraft.world.phys.shapes.VoxelShape SHAPE=net.minecraft.world.phys.shapes.Shapes.box(0,0,.375,1,1,.625);
    private GraveModel() {}
    public static net.minecraft.world.phys.shapes.VoxelShape shape(){return SHAPE;}

    static boolean isAnchor(BlockState state) { return state.is(Blocks.STONE) || state.is(Blocks.STONE_SLAB) || state.is(Blocks.STRUCTURE_VOID); }
    private static String marker(BlockPos pos) { return "unbox.grave:" + pos.asLong(); }

    /** The synchronized hidden custom name is cosmetic client metadata, never ownership authority. */
    public static boolean isGraveAnchor(BlockGetter getter, BlockPos pos) {
        if (getter instanceof ServerLevel level) return GraveKeeper.isGrave(level, pos);
        if (!(getter instanceof Level level)) return false;
        String marker=marker(pos);
        return !level.getEntitiesOfClass(Display.TextDisplay.class, new AABB(pos).inflate(.1, .5, .1),
            label -> label.getCustomName()!=null && marker.equals(label.getCustomName().getString())).isEmpty();
    }

    /** Called on the server thread, including when an old saved nameplate starts ticking. */
    static void ensure(ServerLevel level, Display label) {
        // Both old (1.3) and new (1.15) label positions map to the same saved anchor.
        BlockPos pos=BlockPos.containing(label.getX(),label.getY()-1.0,label.getZ());
        var storage=GraveStorage.get(level);
        var grave=storage.graves.get(pos);
        if(grave==null || !grave.label().equals(label.getUUID()) || grave.model().size()==PART_COUNT && level.getBlockState(pos).is(Blocks.STRUCTURE_VOID)
            || !isAnchor(level.getBlockState(pos))) return;
        var spawned=new ArrayList<Display>();
        // One flat stone rectangle: width 1, height 1, thickness 1/4 block. No plinth or cap.
        if(!part(level,pos,spawned,Blocks.STONE,0,0,.375f,1,1,.25f)
            || !inscription(level,pos,spawned,false) || !inscription(level,pos,spawned,true)) {
            spawned.forEach(Display::discard);return;
        }
        // Convert either earlier stone model only after all replacement visuals are accepted.
        boolean replaced;
        storage.removing.add(pos);
        try { replaced=level.setBlock(pos,Blocks.STRUCTURE_VOID.defaultBlockState(),Block.UPDATE_ALL); }
        finally { storage.removing.remove(pos); }
        if(!replaced && !level.getBlockState(pos).is(Blocks.STRUCTURE_VOID)) {
            spawned.forEach(Display::discard);return;
        }
        storage.graves.put(pos,new GraveStorage.Grave(pos,grave.owner(),grave.name(),grave.label(),grave.items(),
            spawned.stream().map(Display::getUUID).toList()));
        storage.setDirty();
        remove(level,grave);
        label.setPos(pos.getX()+.5,pos.getY()+1.15,pos.getZ()+.5);
        label.setCustomName(Component.literal(marker(pos)));
        label.setCustomNameVisible(false);
    }

    private static boolean part(ServerLevel level,BlockPos pos,ArrayList<Display> spawned,
                                Block block,float x,float y,float z,float width,float height,float depth) {
        var display=new Display.BlockDisplay(EntityType.BLOCK_DISPLAY,level);
        prepare(display,pos);
        ((GraveBlockDisplayAccess)display).unbox$setBlockState(block.defaultBlockState());
        var access=(GraveDisplayAccess)display;
        access.unbox$setTransformation(new Transformation(new Vector3f(x-.5f,y,z-.5f),new Quaternionf(),
            new Vector3f(width,height,depth),new Quaternionf()));
        return spawn(level,display,spawned);
    }

    private static boolean inscription(ServerLevel level,BlockPos pos,ArrayList<Display> spawned,boolean back) {
        var display=new Display.TextDisplay(EntityType.TEXT_DISPLAY,level);
        prepare(display,pos);
        var text=(GraveTextDisplayAccess)display;
        text.unbox$setText(Component.literal("R.I.P").withStyle(style->style.withColor(0x202020)));
        text.unbox$setBackgroundColor(0);
        text.unbox$setFlags((byte)0); // Opaque lettering, no background, no shadow or see-through.
        var access=(GraveDisplayAccess)display;
        access.unbox$setBillboard(Display.BillboardConstraints.FIXED);
        access.unbox$setTransformation(new Transformation(new Vector3f(0,.58f,back?-.126f:.126f),
            new Quaternionf().rotateY(back?(float)Math.PI:0),new Vector3f(1),new Quaternionf()));
        return spawn(level,display,spawned);
    }

    private static void prepare(Display display,BlockPos pos) {
        // All origins stay in the anchor's chunk; relative translations form the model.
        display.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);
        display.setNoGravity(true);display.setInvulnerable(true);
        display.addTag(PART);display.addTag(marker(pos));
        var access=(GraveDisplayAccess)display;
        access.unbox$setWidth(1);access.unbox$setHeight(1);access.unbox$setViewRange(1);
    }

    private static boolean spawn(ServerLevel level,Display display,ArrayList<Display> spawned) {
        if(!level.addFreshEntity(display))return false;
        spawned.add(display);return true;
    }

    static void remove(ServerLevel level,GraveStorage.Grave grave) {
        for(var id:grave.model()){var entity=level.getEntity(id);if(entity!=null)entity.discard();}
    }

    /** Handles a model entity loading after its anchor was already claimed; never spawns replacements. */
    public static void cleanup(Display display) {
        if(!(display.level() instanceof ServerLevel level) || !display.entityTags().contains(PART))return;
        BlockPos pos=display.blockPosition();
        var grave=GraveStorage.get(level).graves.get(pos);
        if(grave==null || !grave.model().contains(display.getUUID()))display.discard();
    }
}
