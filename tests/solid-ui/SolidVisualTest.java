package dev.unbox.client;
import java.awt.*;
import java.awt.image.BufferedImage;
/** No Minecraft instance, world, camera, or GPU. Tests the actual packaged vector resources. */
public final class SolidVisualTest {
    static int checks;
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;}
    static BufferedImage composite(BufferedImage back,BufferedImage front,float alpha){
        var image=new BufferedImage(1440,810,BufferedImage.TYPE_INT_ARGB);var g=image.createGraphics();g.setColor(new Color(0xff258f67,true));g.fillRect(0,0,1440,810);g.setComposite(AlphaComposite.SrcOver.derive(alpha));g.drawImage(back,0,0,null);g.setComposite(AlphaComposite.SrcOver);g.drawImage(front,0,0,null);g.dispose();return image;
    }
    public static void main(String[] args)throws Exception{
        for(String theme:new String[]{"red","yellow"}){
            var front=new VectorScene(theme+"-quick").render(1440,810);
            var back=new VectorScene(theme+"-quick-background").render(1440,810,1,true);
            var low=composite(back,front,.1f);var high=composite(back,front,.9f);
            for(int[] p:new int[][]{{630,400},{780,400},{710,500},{1300,400},{200,620},{1010,310},{1010,460}}){
                check((front.getRGB(p[0],p[1])>>>24)==255,theme+" solid alpha at "+p[0]+","+p[1]);
                check(low.getRGB(p[0],p[1])==high.getRGB(p[0],p[1]),theme+" solid ignores background opacity");
            }
            var mods=new VectorScene(theme+"-mods").render(1440,810);
            var modsBack=new VectorScene(theme+"-mods-background").render(1440,810,1,true);
            var modsLow=composite(modsBack,mods,.1f);var modsHigh=composite(modsBack,mods,.9f);
            for(int[] p:new int[][]{{260,300},{700,190},{1200,700}}){check((mods.getRGB(p[0],p[1])>>>24)==255,"Mods frame is solid");check(modsLow.getRGB(p[0],p[1])==modsHigh.getRGB(p[0],p[1]),"Mods frame ignores opacity");}
            check(low.getRGB(500,500)!=high.getRGB(500,500),theme+" background opacity changes");
            if(args.length>0){var directory=java.nio.file.Path.of(args[0]);java.nio.file.Files.createDirectories(directory);javax.imageio.ImageIO.write(low,"png",directory.resolve(theme+"-opacity-10.png").toFile());javax.imageio.ImageIO.write(high,"png",directory.resolve(theme+"-opacity-90.png").toFile());}
            int matte=theme.equals("red")?0xe8e8ec:0x323437,mattePixels=0,transparent=0;
            for(int y=226;y<330;y++)for(int x=692;x<796;x++){int color=front.getRGB(x,y);if((color>>>24)>200&&(color&0xffffff)==matte)mattePixels++;if((color>>>24)==0)transparent++;}
            check(mattePixels==0,theme+" logo has no matte underlay");check(transparent>3000,theme+" logo holes are transparent");
            check(SolidVisualTest.class.getResource("/assets/unbox/solid/scenes/quick.svg")==null,"Light is not packaged");
            for(String page:new String[]{"title","quick","mods","pvp","friend","appearance","dialog"}){
                var rendered=new VectorScene(theme+"-"+page).render(1686,958,.5,false);check(rendered.getWidth()==1686,theme+" scaled "+page);
            }
        }
        for(var curve:new SolidGeometry.Curve[]{SolidGeometry.THEME,SolidGeometry.TITLE,SolidGeometry.QUICK})for(int i=0;i<=100;i++){
            double t=i/100.;var p=curve.point(t);check(Math.abs(curve.nearest(p.x,p.y)-t)<.00001,"Slider uses exact drawn curve");
        }
        check(!SolidGeometry.contains("tab",2,2,196,82),"Tab corner is outside its hit shape");
        check(SolidGeometry.contains("tab",98,35,196,82),"Tab center is interactive");
        System.out.println(checks+" vector alpha, logo, geometry and resource checks passed");
    }
}
