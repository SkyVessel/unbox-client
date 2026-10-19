package dev.unbox.client;

import java.io.*;
import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;

/** Only branded identity assets outrank selected packs; world textures and fonts stay customizable. */
public final class LogoResources implements PackResources {
    public static final String ID="unbox-identity-assets";
    public static final Set<String> FILES=Set.of(
        "font/name_badge.json", "textures/ui/badge.png", "textures/ui/badge.png.mcmeta",
        "textures/font/unbox-creator-crown.png", "textures/font/unbox-fluttering-cash.png",
        "textures/ui/unbox-creator-crown.png", "textures/ui/unbox-fluttering-cash.png");
    private static final PackLocationInfo LOCATION=new PackLocationInfo(ID,Component.literal("Unbox identity assets"),PackSource.BUILT_IN,Optional.empty());
    public static List<PackResources> prioritize(PackType type,List<PackResources> packs){
        if(type!=PackType.CLIENT_RESOURCES)return packs;
        var result=new ArrayList<>(packs);result.removeIf(p->p instanceof LogoResources);result.add(new LogoResources());return result;
    }
    public IoSupplier<InputStream> getRootResource(String... path){return null;}
    public IoSupplier<InputStream> getResource(PackType type,Identifier id){
        if(type!=PackType.CLIENT_RESOURCES||!id.getNamespace().equals("unbox")||!FILES.contains(id.getPath()))return null;
        return ()->{var stream=LogoResources.class.getResourceAsStream("/assets/unbox/"+id.getPath());if(stream==null)throw new FileNotFoundException(id.toString());return stream;};
    }
    public void listResources(PackType type,String namespace,String path,ResourceOutput output){
        if(type!=PackType.CLIENT_RESOURCES||!namespace.equals("unbox"))return;
        for(String file:FILES)if(path.isEmpty()||file.startsWith(path+"/")){var id=Identifier.fromNamespaceAndPath(namespace,file);output.accept(id,getResource(type,id));}
    }
    public Set<String> getNamespaces(PackType type){return type==PackType.CLIENT_RESOURCES?Set.of("unbox"):Set.of();}
    public <T> T getMetadataSection(MetadataSectionType<T> section){return null;}
    public PackLocationInfo location(){return LOCATION;}
    public void close(){}
}
