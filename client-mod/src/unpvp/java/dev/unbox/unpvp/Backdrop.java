package dev.unbox.unpvp;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.*;
import java.lang.reflect.Method;

/** Separable Gaussian title blur, normalized weights and no noise/dither. */
final class Backdrop {
    private static Framebuffer a;private static int program,width,height;
    static boolean failed;static int rendered;
    static void panorama(int w,int h,float partial){Panorama.draw(w,h);}
    static void tick(){}
    static void blur(){
        if(failed)return;
        int radius=12;
        Minecraft mc=Minecraft.getMinecraft();int previousProgram=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),previousTexture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try{
            if(program==0)program=makeProgram();
            // Blur has no useful high-frequency detail. Keep intermediate targets in window
            // points, then composite into the full-resolution world target before drawing UI.
            int blurWidth=Math.max(1,RetinaDisplay.points(mc.displayWidth)/2),blurHeight=Math.max(1,RetinaDisplay.points(mc.displayHeight)/2);
            if(a==null||width!=blurWidth||height!=blurHeight){releaseBuffers();width=blurWidth;height=blurHeight;a=new Framebuffer(width,height,false);a.setFramebufferFilter(GL11.GL_LINEAR);}
            GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDepthMask(false);GL11.glDisable(GL11.GL_BLEND);GL11.glDisable(GL11.GL_ALPHA_TEST);GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glDisable(GL11.GL_CULL_FACE);
            GL20.glUseProgram(program);GL20.glUniform1i(GL20.glGetUniformLocation(program,"source"),0);GL20.glUniform1f(GL20.glGetUniformLocation(program,"radius"),radius);
            int source=mc.getFramebuffer().framebufferTexture;
            for(int pass=0;pass<2;pass++){
                Framebuffer target=pass==1?mc.getFramebuffer():a;target.bindFramebuffer(true);
                GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glEnable(GL11.GL_TEXTURE_2D);GL11.glBindTexture(GL11.GL_TEXTURE_2D,source);
                int wrapS=GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S),wrapT=GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,33071);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,33071);
                int min=GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER),mag=GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);
                GL20.glUniform2f(GL20.glGetUniformLocation(program,"stepSize"),pass%2==0?1f/width:0,pass%2==1?1f/height:0);
                GL11.glBegin(GL11.GL_QUADS);GL11.glTexCoord2f(0,0);GL11.glVertex2f(-1,-1);GL11.glTexCoord2f(1,0);GL11.glVertex2f(1,-1);GL11.glTexCoord2f(1,1);GL11.glVertex2f(1,1);GL11.glTexCoord2f(0,1);GL11.glVertex2f(-1,1);GL11.glEnd();
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,min);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,mag);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,wrapS);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,wrapT);source=target.framebufferTexture;
            }
            rendered++;
        }catch(RuntimeException e){failed=true;System.err.println("[unpvp] Menu blur unavailable: "+e);}
        finally{GL20.glUseProgram(previousProgram);mc.getFramebuffer().bindFramebuffer(true);GL11.glPopAttrib();net.minecraft.client.renderer.GlStateManager.bindTexture(0);net.minecraft.client.renderer.GlStateManager.bindTexture(previousTexture);}
    }
    private static int makeProgram(){
        int v=shader(GL20.GL_VERTEX_SHADER,"#version 120\nvarying vec2 uv;void main(){gl_Position=gl_Vertex;uv=gl_MultiTexCoord0.xy;}");
        int f=shader(GL20.GL_FRAGMENT_SHADER,"#version 120\nuniform sampler2D source;uniform vec2 stepSize;uniform float radius;varying vec2 uv;void main(){vec4 sum=vec4(0.0);float weight=0.0;for(int i=-12;i<=12;i++){float x=float(i);float k=exp(-x*x/32.0);sum+=texture2D(source,uv+stepSize*x)*k;weight+=k;}gl_FragColor=sum/weight;}");
        int p=GL20.glCreateProgram();GL20.glAttachShader(p,v);GL20.glAttachShader(p,f);GL20.glLinkProgram(p);GL20.glDeleteShader(v);GL20.glDeleteShader(f);if(GL20.glGetProgrami(p,GL20.GL_LINK_STATUS)==0)throw new IllegalStateException(GL20.glGetProgramInfoLog(p,4096));return p;
    }
    private static int shader(int type,String source){int s=GL20.glCreateShader(type);GL20.glShaderSource(s,source);GL20.glCompileShader(s);if(GL20.glGetShaderi(s,GL20.GL_COMPILE_STATUS)==0)throw new IllegalStateException(GL20.glGetShaderInfoLog(s,4096));return s;}
    private static void releaseBuffers(){if(a!=null)a.deleteFramebuffer();a=null;}
    static void release(){releaseBuffers();if(program!=0)GL20.glDeleteProgram(program);program=0;}
    static boolean active(){return a!=null;}
}
