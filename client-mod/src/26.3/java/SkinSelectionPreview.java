package dev.unbox.client;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.*;
import net.minecraft.core.ClientAsset;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import java.nio.file.*;
/** Launcher-published, bounded PNG previews. Selection never uploads or equips a skin. */
final class SkinSelectionPreview implements AutoCloseable {
    private final Identifier id=Identifier.fromNamespaceAndPath("unbox","skin_selection/"+java.util.UUID.randomUUID());
    private String key="";private PlayerSkin skin;private long next;
    PlayerSkin get(String selected,String model){String request=selected+model;if(request.equals(key))return skin;if(System.currentTimeMillis()<next)return skin;next=System.currentTimeMillis()+500;
        if(!selected.matches("[a-f0-9]{64}")){close();return null;}
        Path file=Platform.gameDir().resolve("unbox-skin-previews").resolve(selected+".png");
        try{if(!Files.isRegularFile(file)||Files.size(file)>1048576){close();return null;}byte[] bytes=Files.readAllBytes(file);NativeImage image=NativeImage.read(bytes);if(image.getWidth()!=64||image.getHeight()!=64){image.close();close();return null;}Minecraft.getInstance().getTextureManager().register(id,new DynamicTexture(()->"Selected skin",image));skin=new PlayerSkin(new ClientAsset.ResourceTexture(id,id),null,null,model.equals("slim")?PlayerModelType.SLIM:PlayerModelType.WIDE,false);key=request;return skin;}catch(Exception e){close();return null;}
    }
    @Override public void close(){if(skin!=null)Minecraft.getInstance().getTextureManager().release(id);skin=null;key="";}
}
