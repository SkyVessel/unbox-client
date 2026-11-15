package dev.unbox.unpvp;

import java.awt.Shape;
import java.awt.geom.*;

/** Layout in UI units. Frames fill the viewport; controls retain their own metrics. */
final class SolidLayout {
    final String page;
    final double pixels,width,height,extraWidth,extraHeight;
    SolidLayout(String page,int pixelWidth,int pixelHeight,double density){
        this.page=page.replaceFirst("^(red|yellow)-","").replace("-background","");
        pixels=Math.min(pixelWidth/1440.,pixelHeight/810.)*density;
        width=pixelWidth/pixels;height=pixelHeight/pixels;extraWidth=width-1440;extraHeight=height-810;
    }
    double friendGrowth(){return Math.min(170,extraWidth*.08);}
    double friendLeft(){return width-406-friendGrowth();}
    double previewHeight(){return Math.min(620,375+extraHeight*.22);}
    double previewCenterX(){return 250+extraWidth*.22;}
    double previewCenterY(){return height/2-22.5;}
    int friendRows(){return Math.max(4,(int)((height-(263+extraHeight*.24)-220)/80));}
    double dx(double x,double y){
        if(x<280&&y>=750)return 0; // global footer
        switch(page){
            case "title": return  x<106?0:x>1250?extraWidth:extraWidth/2;
            case "quick": case "friend": return  x>=1330?extraWidth:x>=984?extraWidth-friendGrowth():x<500?extraWidth*.22:extraWidth/2;
            case "mods": case "pvp": return  x>=650?extraWidth:0;
            case "appearance": return  x>=1190?extraWidth:x<240?0:x<600?extraWidth*.22:extraWidth*.65;
            default: return  x<240?0:x>1250?extraWidth:extraWidth/2;
        }
    }
    double dy(double x,double y){
        if(x<280&&y>=750)return extraHeight;
        switch(page){
            case "title": return  x<106?(y<120?0:y>700?extraHeight:extraHeight/2):x>1250?extraHeight:extraHeight/2;
            case "quick": case "friend": return  x>=1330&&y<100?0:x>=984?((x<1090&&y>=625)||y==709?extraHeight:extraHeight*.24):x<500?extraHeight/2+(previewHeight()-375)/2:extraHeight/2;
            case "mods": case "pvp": return  y>=640?extraHeight:0;
            case "appearance": return  y<150?0:extraHeight/2;
            default: return  y<170?0:extraHeight/2;
        }
    }
    int x(double x,double y){return (int)Math.round(x+dx(x,y));}
    int y(double x,double y){return (int)Math.round(y+dy(x,y));}
    int gridWidth(){return (int)Math.round(width-482);}
    int gridBottom(){return (int)Math.round(height-126);}
    int gridHeight(){return gridBottom()-258;}
    int columns(){return Math.max(3,(gridWidth()+17)/319);}
    Shape panel(double x,double y,double w,double h,double radius){return new RoundRectangle2D.Double(x,y,w+extraWidth,h+extraHeight,radius*2,radius*2);}
    Shape friends(){
        double x=width-208-friendGrowth(),bottom=height;
        Path2D p=new Path2D.Double();p.moveTo(x,0);p.curveTo(x,83,x-18,116,x-113,143);p.curveTo(x-180,162,x-198,187,x-198,250);
        p.lineTo(x-198,bottom-262);p.curveTo(x-198,bottom-199,x-180,bottom-169,x-113,bottom-150);p.curveTo(x-18,bottom-123,x,bottom-84,x,bottom);p.lineTo(width,bottom);p.lineTo(width,0);p.closePath();return p;
    }
}
