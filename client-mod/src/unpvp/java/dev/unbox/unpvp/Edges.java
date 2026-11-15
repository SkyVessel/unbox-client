package dev.unbox.unpvp;
import org.lwjgl.opengl.*;
/** Analytic pixel coverage: edges stay smooth at fractional scales without enlarging the scene. */
final class Edges {
    private static int program,size,radius,bottom,mode;
    static void round(float x,float y,float w,float h,float r,int color,boolean bottomOnly){
        if(program==0)init();int c=Painter.tint(color);
        GL11.glDisable(GL11.GL_TEXTURE_2D);GL20.glUseProgram(program);
        GL20.glUniform1f(mode,0);GL20.glUniform2f(size,w,h);GL20.glUniform1f(radius,r);GL20.glUniform1f(bottom,bottomOnly?1:0);
        GL11.glColor4f((c>>16&255)/255f,(c>>8&255)/255f,(c&255)/255f,(c>>>24)/255f);
        float pad=1;GL11.glBegin(GL11.GL_QUADS);vertex(x-pad,y-pad,-pad,-pad);vertex(x-pad,y+h+pad,-pad,h+pad);vertex(x+w+pad,y+h+pad,w+pad,h+pad);vertex(x+w+pad,y-pad,w+pad,-pad);GL11.glEnd();GL20.glUseProgram(0);
    }
    static void colorPlane(float x,float y,float w,float h,int hue){if(program==0)init();GL20.glUseProgram(program);GL20.glUniform1f(mode,1);GL20.glUniform2f(size,w,h);GL11.glColor4f((hue>>16&255)/255f,(hue>>8&255)/255f,(hue&255)/255f,Painter.opacity);GL11.glBegin(GL11.GL_QUADS);vertex(x,y,0,0);vertex(x,y+h,0,h);vertex(x+w,y+h,w,h);vertex(x+w,y,w,0);GL11.glEnd();GL20.glUseProgram(0);}
    private static void vertex(float x,float y,float u,float v){GL11.glTexCoord2f(u,v);GL11.glVertex2f(x,y);}
    private static void init(){
        int v=shader(GL20.GL_VERTEX_SHADER,"#version 120\nvarying vec2 point;varying vec4 tint;void main(){gl_Position=ftransform();point=gl_MultiTexCoord0.xy;tint=gl_Color;}");
        int f=shader(GL20.GL_FRAGMENT_SHADER,"#version 120\nuniform vec2 size;uniform float radius;uniform float bottomOnly;uniform float mode;varying vec2 point;varying vec4 tint;void main(){if(mode>0.5){gl_FragColor=vec4(mix(vec3(1.0),tint.rgb,clamp(point.x/size.x,0.0,1.0))*(1.0-clamp(point.y/size.y,0.0,1.0)),tint.a);return;}float r=bottomOnly>0.5&&point.y<size.y*0.5?0.0:radius;vec2 q=abs(point-size*0.5)-size*0.5+r;float d=length(max(q,0.0))+min(max(q.x,q.y),0.0)-r;float coverage=clamp(0.5-d/max(fwidth(d),0.0001),0.0,1.0);gl_FragColor=vec4(tint.rgb,tint.a*coverage);}");
        program=GL20.glCreateProgram();GL20.glAttachShader(program,v);GL20.glAttachShader(program,f);GL20.glLinkProgram(program);GL20.glDeleteShader(v);GL20.glDeleteShader(f);if(GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)==0)throw new IllegalStateException(GL20.glGetProgramInfoLog(program,4096));size=GL20.glGetUniformLocation(program,"size");radius=GL20.glGetUniformLocation(program,"radius");bottom=GL20.glGetUniformLocation(program,"bottomOnly");mode=GL20.glGetUniformLocation(program,"mode");
    }
    private static int shader(int type,String text){int s=GL20.glCreateShader(type);GL20.glShaderSource(s,text);GL20.glCompileShader(s);if(GL20.glGetShaderi(s,GL20.GL_COMPILE_STATUS)==0)throw new IllegalStateException(GL20.glGetShaderInfoLog(s,4096));return s;}
}
