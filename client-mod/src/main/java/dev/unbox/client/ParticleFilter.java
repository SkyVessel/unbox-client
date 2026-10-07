package dev.unbox.client;

import net.minecraft.client.particle.Particle;
import java.util.concurrent.ThreadLocalRandom;

public final class ParticleFilter {
    // Classify once per particle class, not once per particle spawn.
    private static final ClassValue<String> FAMILY=new ClassValue<>(){
        @Override protected String computeValue(Class<?> type){
            String name=type.getSimpleName().toLowerCase(java.util.Locale.ROOT);
            for(String family:new String[]{"crit","smoke","flame","portal","explosion"})if(name.contains(family))return "particles."+family;
            if(name.contains("terrain")||name.contains("block")||name.contains("dust"))return "particles.block";
            if(name.contains("spell")||name.contains("effect"))return "particles.potion";
            if(name.contains("water")||name.contains("splash")||name.contains("bubble"))return "particles.water";
            return "";
        }
    };
    public static String classify(String id){
        if(id.equals("crit"))return "particles.crit";
        if(id.contains("enchanted")||id.equals("enchant"))return "particles.enchant";
        if(id.contains("heart"))return "particles.heart";
        if(id.contains("totem"))return "particles.totem";
        if(id.contains("lava"))return "particles.lava";
        if(id.contains("snow"))return "particles.snow";
        if(id.equals("cloud"))return "particles.cloud";
        for(String family:new String[]{"smoke","flame","portal","explosion"})if(id.contains(family))return "particles."+family;
        if(id.contains("block")||id.contains("dust"))return "particles.block";
        if(id.contains("effect")||id.equals("witch"))return "particles.potion";
        if(id.contains("water")||id.contains("bubble")||id.contains("splash")||id.equals("rain"))return "particles.water";
        return "";
    }
    static String family(Particle p){String tagged=((ParticleAccess)p).unboxFamily();return tagged==null?FAMILY.get(p.getClass()):tagged;}
    public static float opacity(Particle p){if(!UnboxClient.enabled("particles"))return 1;String family=family(p);return family.isEmpty()?1:UnboxClient.number(family+".opacity",100,0,100)/100f;}
    public static boolean allow(Particle particle){
        if(!UnboxClient.enabled("particles"))return true;
        String family=family(particle);
        if(!family.isEmpty()&&!ClientHud.flag(family,true))return false;
        int density=UnboxClient.number("particles.density",100,0,100);
        if(!family.isEmpty())density=density*UnboxClient.number(family+".density",100,0,100)/100;
        return density==100||density>0&&ThreadLocalRandom.current().nextInt(100)<density;
    }
    public static void customize(Particle particle){
        if(!UnboxClient.enabled("particles"))return;String family=family(particle);if(family.isEmpty())return;
        int size=UnboxClient.number(family+".size",100,25,200),life=UnboxClient.number(family+".lifetime",100,25,200);
        if(size!=100)particle.scale(size/100f);if(life!=100)particle.setLifetime(Math.max(1,particle.getLifetime()*life/100));
        if(ClientHud.flag(family+".tint",false)&&particle instanceof net.minecraft.client.particle.SingleQuadParticle quad){int c=ClientHud.color(family+".color","#FFFFFF");quad.setColor(((c>>16)&255)/255f,((c>>8)&255)/255f,(c&255)/255f);}
    }
}
