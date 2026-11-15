package dev.unbox.client;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.regex.*;
import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

/** Pen paths (including outlined type), resolved once; no SVG/browser runtime dependency. */
public final class VectorScene {
    private interface Paint { void draw(Graphics2D g,SolidLayout layout); }
    private final Paint root;
    private final String name;
    private final Map<String,Element> ids=new HashMap<String,Element>();
    public final int background;
    public VectorScene(String name) throws Exception {
        this.name=name;
        DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities",false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
        Element svg;
        try(InputStream in=VectorScene.class.getResourceAsStream("/assets/unbox/solid/scenes/"+name+".svg")){
            if(in==null)throw new IOException("Missing vector scene "+name);
            svg=factory.newDocumentBuilder().parse(in).getDocumentElement();
        }
        NodeList all=svg.getElementsByTagName("*");
        for(int i=0;i<all.getLength();i++){Element e=(Element)all.item(i);if(e.hasAttribute("id"))ids.put(e.getAttribute("id"),e);}
        background=name.startsWith("yellow")?0xff323437:name.startsWith("red")?0xffe8e8ec:(name.contains("mods")||name.equals("pvp")||name.equals("appearance")||name.equals("crown"))?0xffe9e6df:0xfff5f3ed;
        root=compile(svg,new HashMap<String,String>(),false);
    }
    public BufferedImage render(int width,int height) {return render(width,height,1,false);}
    public BufferedImage render(int width,int height,double zoom,boolean opaque) {
        BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=image.createGraphics();
        try{
            if(opaque){g.setColor(new Color(background,true));g.fillRect(0,0,width,height);}
            // Foreground solids and backing are composited independently.
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,RenderingHints.VALUE_STROKE_PURE);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            SolidLayout layout=new SolidLayout(name,width,height,zoom);g.scale(layout.pixels,layout.pixels);root.draw(g,layout);
        }finally{g.dispose();}
        return image;
    }
    private Paint compile(final Element e,Map<String,String> inherited,boolean referenced)throws Exception{
        final Map<String,String> style=new HashMap<String,String>(inherited);
        for(String key:new String[]{"fill","fill-opacity","fill-rule","stroke","stroke-width","stroke-linecap","stroke-linejoin"})if(e.hasAttribute(key))style.put(key,e.getAttribute(key));
        final String tag=e.getTagName();final AffineTransform transform=transform(e.getAttribute("transform"));
        final Shape clip;
        if(e.hasAttribute("clip-path")){
            String ref=e.getAttribute("clip-path");Element ce=ids.get(ref.substring(5,ref.length()-1));
            Area a=new Area();for(Element child:children(ce))a.add(new Area(transform(child.getAttribute("transform")).createTransformedShape(shape(child))));clip=a;
        }else clip=null;
        final List<Paint> parts=new ArrayList<Paint>();
        if(tag.equals("defs")&&!referenced)return (g,layout)->{};
        if(tag.equals("use"))parts.add(compile(ids.get(e.getAttribute("href").substring(1)),style,true));
        else if(tag.equals("image")){
            final BufferedImage image=ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(e.getAttribute("href").split(",",2)[1])));
            final AffineTransform size=AffineTransform.getScaleInstance(number(e,"width",image.getWidth())/image.getWidth(),number(e,"height",image.getHeight())/image.getHeight());
            parts.add((g,layout)->drawImageClipped(g,image,size));
        }else if(tag.equals("path")||tag.equals("rect")||tag.equals("circle")){
            final Shape shape=shape(e);if(shape instanceof Path2D)((Path2D)shape).setWindingRule("evenodd".equals(style.get("fill-rule"))?Path2D.WIND_EVEN_ODD:Path2D.WIND_NON_ZERO);
            final Color fill=color(style.containsKey("fill")?style.get("fill"):"#000000",Float.parseFloat(style.containsKey("fill-opacity")?style.get("fill-opacity"):"1"));
            final Color stroke=color(style.get("stroke"),1);final BasicStroke pen=new BasicStroke(Float.parseFloat(style.containsKey("stroke-width")?style.get("stroke-width"):"1"),"round".equals(style.get("stroke-linecap"))?BasicStroke.CAP_ROUND:BasicStroke.CAP_BUTT,"round".equals(style.get("stroke-linejoin"))?BasicStroke.JOIN_ROUND:BasicStroke.JOIN_MITER);
            final boolean knockout="true".equals(e.getAttribute("data-knockout"));
            parts.add((g,layout)->{Shape drawn=switch(e.getAttribute("data-stretch")){case "viewport"->new Rectangle2D.Double(0,0,layout.width,layout.height);case "title"->layout.panel(106,22,1312,766,56);case "mods"->layout.panel(244,166,1012,554,56);case "appearance"->layout.panel(88,72,1264,666,32);case "friends"->layout.friends();default->shape;};if(fill!=null){Composite composite=g.getComposite();if(knockout)g.setComposite(AlphaComposite.Clear);g.setColor(fill);fillClipped(g,drawn);g.setComposite(composite);}if(stroke!=null){g.setColor(stroke);g.setStroke(pen);fillClipped(g,pen.createStrokedShape(shape));}});
        }else for(Element child:children(e))parts.add(compile(child,style,false));
        return (g,layout)->{Graphics2D local=(Graphics2D)g.create();try{if(e.getAttribute("data-stretch").isEmpty()){if(e.hasAttribute("data-layout-x")){double x=number(e,"data-layout-x",0),y=number(e,"data-layout-y",0);local.translate(layout.dx(x,y),layout.dy(x,y));}local.transform(transform);}if(e.hasAttribute("data-viewport"))local.clip(new Rectangle2D.Double(0,0,layout.width,layout.height));else if(clip!=null)local.clip(clip);for(Paint p:parts)p.draw(local,layout);}finally{local.dispose();}};
    }
    private static void drawImageClipped(Graphics2D g,BufferedImage image,AffineTransform size){
        Shape clip=g.getClip();Shape bounds=size.createTransformedShape(new Rectangle2D.Double(0,0,image.getWidth(),image.getHeight()));
        if(clip==null||clip.contains(bounds.getBounds2D())){g.drawImage(image,size,null);return;}
        AffineTransform device=g.getTransform();Area visible=new Area(device.createTransformedShape(clip));visible.intersect(new Area(device.createTransformedShape(bounds)));Rectangle box=visible.getBounds();if(box.isEmpty())return;
        BufferedImage patch=new BufferedImage(box.width,box.height,BufferedImage.TYPE_INT_ARGB),mask=new BufferedImage(box.width,box.height,BufferedImage.TYPE_INT_ARGB);
        Graphics2D a=patch.createGraphics(),b=mask.createGraphics();
        try{a.setRenderingHints(g.getRenderingHints());a.translate(-box.x,-box.y);a.transform(device);a.drawImage(image,size,null);
            b.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);b.translate(-box.x,-box.y);b.setColor(Color.WHITE);b.fill(visible);
            a.setTransform(new AffineTransform());a.setComposite(AlphaComposite.DstIn);a.drawImage(mask,0,0,null);
        }finally{a.dispose();b.dispose();}
        Graphics2D target=(Graphics2D)g.create();try{target.setTransform(new AffineTransform());target.setClip(null);target.drawImage(patch,box.x,box.y,null);}finally{target.dispose();}
    }
    private static void fillClipped(Graphics2D g,Shape shape){
        Shape clip=g.getClip();if(clip==null){g.fill(shape);return;}
        Area visible=new Area(shape);visible.intersect(new Area(clip));g.setClip(null);g.fill(visible);g.setClip(clip);
    }
    private static List<Element> children(Element e){List<Element> out=new ArrayList<Element>();for(Node n=e.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element)out.add((Element)n);return out;}
    private static Color color(String s,float opacity){if(s==null||s.equals("none"))return null;int rgb=Integer.parseInt(s.substring(1),16);return new Color((rgb>>16)&255,(rgb>>8)&255,rgb&255,Math.round(opacity*255));}
    private static double number(Element e,String k,double fallback){return e.hasAttribute(k)?Double.parseDouble(e.getAttribute(k)):fallback;}
    private static Shape shape(Element e){String t=e.getTagName();if(t.equals("path"))return path(e.getAttribute("d"));double x=number(e,"x",0),y=number(e,"y",0),w=number(e,"width",0),h=number(e,"height",0),r=number(e,"rx",0);if(t.equals("circle")){r=number(e,"r",0);return new Ellipse2D.Double(number(e,"cx",0)-r,number(e,"cy",0)-r,2*r,2*r);}return new RoundRectangle2D.Double(x,y,w,h,2*r,2*r);}
    private static final Pattern NUMBER=Pattern.compile("[-+]?(?:[0-9]*\\.[0-9]+|[0-9]+\\.?[0-9]*)(?:[eE][-+]?[0-9]+)?");
    private static AffineTransform transform(String s){AffineTransform t=new AffineTransform();Matcher m=Pattern.compile("(\\w+)\\(([^)]*)\\)").matcher(s);while(m.find()){List<Double> a=new ArrayList<Double>();Matcher n=NUMBER.matcher(m.group(2));while(n.find())a.add(Double.valueOf(n.group()));String op=m.group(1);if(op.equals("translate"))t.translate(a.get(0),a.size()>1?a.get(1):0);else if(op.equals("scale"))t.scale(a.get(0),a.size()>1?a.get(1):a.get(0));else if(op.equals("matrix"))t.concatenate(new AffineTransform(a.get(0),a.get(1),a.get(2),a.get(3),a.get(4),a.get(5)));else throw new IllegalArgumentException("Unsupported SVG transform "+op);}return t;}
    static Path2D path(String d){
        List<String> ts=new ArrayList<String>();Matcher m=Pattern.compile("[A-Za-z]|"+NUMBER.pattern()).matcher(d);while(m.find())ts.add(m.group());
        Path2D.Double p=new Path2D.Double();double x=0,y=0,sx=0,sy=0;char cmd=' ';int i=0;
        while(i<ts.size()){
            if(Character.isLetter(ts.get(i).charAt(0)))cmd=ts.get(i++).charAt(0);
            char u=Character.toUpperCase(cmd);boolean rel=Character.isLowerCase(cmd);if(u=='Z'){p.closePath();x=sx;y=sy;continue;}
            int count=u=='M'||u=='L'?2:u=='H'||u=='V'?1:u=='Q'?4:u=='C'?6:0;if(count==0)throw new IllegalArgumentException("Unsupported SVG command "+cmd);
            double[] v=new double[count];for(int k=0;k<count;k++)v[k]=Double.parseDouble(ts.get(i++));
            if(u=='H'){x=v[0]+(rel?x:0);p.lineTo(x,y);continue;}if(u=='V'){y=v[0]+(rel?y:0);p.lineTo(x,y);continue;}
            if(rel)for(int k=0;k<count;k+=2){v[k]+=x;v[k+1]+=y;}
            if(u=='M'){p.moveTo(v[0],v[1]);sx=v[0];sy=v[1];cmd=rel?'l':'L';}else if(u=='L')p.lineTo(v[0],v[1]);else if(u=='Q')p.quadTo(v[0],v[1],v[2],v[3]);else p.curveTo(v[0],v[1],v[2],v[3],v[4],v[5]);x=v[count-2];y=v[count-1];
        }return p;
    }
}
