package dev.unbox.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import java.util.*;

/** World-anchored textures: camera movement only changes their transform, never clears terrain. */
final class MapTileRenderer {
    static final int SIZE=128,LIMIT=192;
    private static final class TerrainTexture extends DynamicTexture {
        TerrainTexture(NativeImage image){
            super(()->"Unbox terrain region",image);
            sampler=com.mojang.blaze3d.systems.RenderSystem.getSamplerCache().getSampler(
                com.mojang.blaze3d.textures.AddressMode.CLAMP_TO_EDGE,com.mojang.blaze3d.textures.AddressMode.CLAMP_TO_EDGE,
                com.mojang.blaze3d.textures.FilterMode.LINEAR,com.mojang.blaze3d.textures.FilterMode.NEAREST,false);
        }
    }
    private static final class Region {
        final Identifier id;
        final NativeImage image=new NativeImage(SIZE,SIZE,false);
        final DynamicTexture texture;
        final long[] revisions=new long[64];
        Region(int x,int z){
            id=Identifier.fromNamespaceAndPath("unbox","map/"+x+"/"+z);
            image.fillRect(0,0,SIZE,SIZE,WorldMap.EMPTY);
            texture=new TerrainTexture(image);
            Minecraft.getInstance().getTextureManager().register(id,texture);
        }
    }
    private final LinkedHashMap<Long,Region> regions=new LinkedHashMap<>(32,.75f,true);
    void clear(){for(Region region:regions.values())Minecraft.getInstance().getTextureManager().release(region.id);regions.clear();}
    void update(MapTerrainStore store,double x,double z,int width,int height){
        int left=Math.floorDiv((int)Math.floor(x),SIZE),top=Math.floorDiv((int)Math.floor(z),SIZE);
        int right=Math.floorDiv((int)Math.ceil(x+width),SIZE),bottom=Math.floorDiv((int)Math.ceil(z+height),SIZE);
        var visible=new ArrayList<Long>();
        for(int rz=top;rz<=bottom;rz++)for(int rx=left;rx<=right;rx++)visible.add(MapTerrainStore.key(rx,rz));
        double centerX=(x+width*.5)/SIZE,centerZ=(z+height*.5)/SIZE;
        visible.sort(Comparator.comparingDouble(k->Math.abs((int)(k>>32)+.5-centerX)+Math.abs((int)(long)k+.5-centerZ)));
        int created=0,uploads=0;
        for(long key:visible){
            int rx=(int)(key>>32),rz=(int)key;Region region=regions.get(key);
            if(region==null){if(created>=4)continue;region=new Region(rx,rz);regions.put(key,region);created++;}
            boolean dirty=false;
            for(int tz=0;tz<8;tz++)for(int tx=0;tx<8;tx++){
                var tile=store.chunk(rx*8+tx,rz*8+tz);int index=tz*8+tx;
                if(tile.revision==0||tile.revision==region.revisions[index])continue;
                region.revisions[index]=tile.revision;dirty=true;
                for(int p=0;p<256;p++)region.image.setPixel(tx*16+(p&15),tz*16+(p>>4),tile.pixels[p]==0?WorldMap.EMPTY:tile.pixels[p]);
            }
            if(dirty){region.texture.upload();if(++uploads>=8)break;}
        }
        while(regions.size()>LIMIT){var it=regions.entrySet().iterator();var entry=it.next();Minecraft.getInstance().getTextureManager().release(entry.getValue().id);it.remove();}
    }
    void draw(GuiGraphicsExtractor g,int x,int y,int width,int height,double originX,double originZ,int spanX,int spanZ){
        int left=Math.floorDiv((int)Math.floor(originX),SIZE),top=Math.floorDiv((int)Math.floor(originZ),SIZE);
        int right=Math.floorDiv((int)Math.ceil(originX+spanX),SIZE),bottom=Math.floorDiv((int)Math.ceil(originZ+spanZ),SIZE);
        g.pose().pushMatrix();g.pose().translate(x,y);g.pose().scale(width/(float)spanX,height/(float)spanZ);
        for(int rz=top;rz<=bottom;rz++)for(int rx=left;rx<=right;rx++){
            Region region=regions.get(MapTerrainStore.key(rx,rz));if(region==null)continue;
            g.pose().pushMatrix();g.pose().translate((float)(rx*SIZE-originX),(float)(rz*SIZE-originZ));
            g.blit(region.id,0,0,SIZE,SIZE,0,1,0,1);g.pose().popMatrix();
        }
        g.pose().popMatrix();
    }
}
