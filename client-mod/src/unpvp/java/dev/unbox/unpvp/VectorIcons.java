package dev.unbox.unpvp;
import java.awt.*;
import java.awt.geom.*;
import java.io.*;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
/** Approved SVG icon outlines, resolved once and rasterized at each surface's physical resolution. */
final class VectorIcons {
 static Shape read(InputStream in)throws Exception{DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);f.setFeature("http://xml.org/sax/features/external-general-entities",false);f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);return node(f.newDocumentBuilder().parse(in).getDocumentElement(),new HashMap<String,String>());}
 private static Area node(Element e,Map<String,String> inherited){Map<String,String> s=new HashMap<>(inherited);for(String key:new String[]{"fill","stroke","stroke-width","stroke-linecap","stroke-linejoin"})if(e.hasAttribute(key))s.put(key,e.getAttribute(key));String tag=e.getTagName();Shape shape=null;
  if(tag.equals("path"))shape=VectorScene.path(e.getAttribute("d"));else if(tag.equals("circle")){double r=n(e,"r",0);shape=new Ellipse2D.Double(n(e,"cx",0)-r,n(e,"cy",0)-r,2*r,2*r);}else if(tag.equals("rect"))shape=new RoundRectangle2D.Double(n(e,"x",0),n(e,"y",0),n(e,"width",0),n(e,"height",0),2*n(e,"rx",0),2*n(e,"rx",0));
  Area out=new Area();if(shape!=null){if(!"none".equals(s.get("fill")))out.add(new Area(shape));if(s.containsKey("stroke")&&!s.get("stroke").equals("none"))out.add(new Area(new BasicStroke(Float.parseFloat(s.getOrDefault("stroke-width","1")),"round".equals(s.get("stroke-linecap"))?BasicStroke.CAP_ROUND:BasicStroke.CAP_BUTT,"round".equals(s.get("stroke-linejoin"))?BasicStroke.JOIN_ROUND:BasicStroke.JOIN_MITER).createStrokedShape(shape)));}
  for(Node child=e.getFirstChild();child!=null;child=child.getNextSibling())if(child instanceof Element)out.add(node((Element)child,s));return out;
 }
 private static double n(Element e,String key,double fallback){return e.hasAttribute(key)?Double.parseDouble(e.getAttribute(key)):fallback;}
}
