package dev.unbox.client;

/** Checks structural relationships across GUI densities and aspect ratios. No game/world. */
public final class SolidLayoutTest {
    private static int checks;
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
    public static void main(String[] args)throws Exception{
        int[][] windows={{3840,2160},{2560,1600},{1686,958},{1280,1024},{854,480},{3440,1440}};
        for(int[] size:windows){int max=Math.max(1,Math.min(size[0]/320,size[1]/240));
            for(int gui=1;gui<=max;gui++){
                double density=Math.sqrt(gui/(double)max);
                var title=new SolidLayout("title",size[0],size[1],density);
                var panel=title.panel(106,22,1312,766,56).getBounds2D();
                check(Math.abs(title.width-panel.getMaxX()-22)<.001,"Title panel right inset");
                check(Math.abs(title.height-panel.getMaxY()-22)<.001,"Title panel bottom inset");
                check(title.x(26,32)==26&&title.y(26,32)==32,"Account rail remains top left");
                check(Math.abs(title.height-title.y(24,715)-95)<1,"Quit remains bottom left");
                var quick=new SolidLayout("quick",size[0],size[1],density);
                var friends=quick.friends().getBounds2D();
                check(Math.abs(friends.getMaxX()-quick.width)<.001&&friends.getMinY()==0&&friends.getHeight()==quick.height,"Friends stay attached to three viewport edges");
                check(quick.previewCenterX()+quick.previewHeight()*.4<quick.x(598,372)-40,"Preview and central actions do not overlap");
                check(quick.x(598,372)+294<friends.getMinX()-30,"Actions and friends remain separated");
                double lastRow=263+(quick.friendRows()-1)*80+quick.extraHeight*.24;
                check(lastRow+64<quick.height-100,"Friend rows fit the rail");
                var mods=new SolidLayout("mods",size[0],size[1],density);
                check(mods.gridBottom()<mods.height-90&&mods.gridBottom()>258,"Grid remains inside panel");
                check(mods.columns()>=3&&mods.gridWidth()/mods.columns()>=300,"Cards keep usable widths");
            }
        }
        for(String theme:new String[]{"red","yellow"})for(double density:new double[]{1,.7071,.5}){
            var front=new VectorScene(theme+"-quick").render(1440,810,density,false);
            check((front.getRGB(1438,2)>>>24)==255,"Friend top reaches viewport edge");
            check((front.getRGB(1438,807)>>>24)==255,"Friend bottom reaches viewport edge");
            var title=new VectorScene(theme+"-title-background").render(1440,810,density,true);
            check(title.getRGB(1390,405)!=title.getRGB(2,405),"Title panel extends across screen at every density");
        }
        System.out.println(checks+" responsive layout and rendered edge checks passed");
    }
}
