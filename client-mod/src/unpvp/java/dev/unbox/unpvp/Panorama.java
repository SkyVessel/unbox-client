package dev.unbox.unpvp;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.*;
import org.lwjgl.util.glu.GLU;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.io.*;
import javax.imageio.ImageIO;
/** Native 1.8.9 title cubemap from the running game resources, with a separate Gaussian background pass. */
final class Panorama {
    private static int cube,overlay,program;private static float spin;private static long last;
    static void draw(int width,int height){
        long now=System.nanoTime();if(last!=0&&!UiState.flag("ui.reducedMotion",false))spin=(spin+Math.min(.1f,(now-last)/1e9f)*2)%360;last=now;
        int old=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D),oldProgram=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glPushMatrix();GL11.glLoadIdentity();GLU.gluPerspective(85,(float)width/height,.05f,10);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPushMatrix();GL11.glLoadIdentity();GL11.glRotatef(190,1,0,0);GL11.glRotatef(-spin,0,1,0);
        try{
            if(cube==0)load();GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDepthMask(false);GL11.glDisable(GL11.GL_ALPHA_TEST);GL11.glDisable(GL11.GL_BLEND);GL11.glDisable(GL11.GL_CULL_FACE);GL11.glDisable(GL11.GL_LIGHTING);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL13.GL_TEXTURE_CUBE_MAP,cube);GL20.glUseProgram(program);GL20.glUniform1i(GL20.glGetUniformLocation(program,"image"),0);
            float[][] vertices={{-1,-1,1,-1,1,1,1,1,1,1,-1,1},{1,-1,1,1,1,1,1,1,-1,1,-1,-1},{1,-1,-1,1,1,-1,-1,1,-1,-1,-1,-1},{-1,-1,-1,-1,1,-1,-1,1,1,-1,-1,1},{-1,-1,-1,-1,-1,1,1,-1,1,1,-1,-1},{-1,1,1,-1,1,-1,1,1,-1,1,1,1}};
            GL11.glBegin(GL11.GL_QUADS);for(float[] face:vertices)for(int i=0;i<12;i+=3)GL11.glVertex3f(face[i],face[i+1],face[i+2]);GL11.glEnd();
        }finally{GL20.glUseProgram(oldProgram);GL11.glPopMatrix();GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glPopMatrix();GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPopAttrib();GlStateManager.bindTexture(0);GlStateManager.bindTexture(old);}
        Backdrop.blur();
    }
    private static void load(){try{
        cube=GL11.glGenTextures();GL11.glBindTexture(GL13.GL_TEXTURE_CUBE_MAP,cube);filter(GL13.GL_TEXTURE_CUBE_MAP);int[] order={1,3,5,4,0,2};for(int i=0;i<6;i++)upload(GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X+i,"panorama_"+order[i]+".png");
        
        int v=shader(GL20.GL_VERTEX_SHADER,"#version 120\nvarying vec3 direction;void main(){gl_Position=ftransform();direction=gl_Vertex.xyz;}");int f=shader(GL20.GL_FRAGMENT_SHADER,"#version 120\nuniform samplerCube image;varying vec3 direction;void main(){gl_FragColor=textureCube(image,direction);}");program=GL20.glCreateProgram();GL20.glAttachShader(program,v);GL20.glAttachShader(program,f);GL20.glLinkProgram(program);GL20.glDeleteShader(v);GL20.glDeleteShader(f);if(GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)==0)throw new IllegalStateException(GL20.glGetProgramInfoLog(program,4096));
    }catch(IOException e){throw new IllegalStateException("Minecraft 1.8.9 panorama is unavailable",e);}}
    private static void filter(int target){GL11.glTexParameteri(target,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR);GL11.glTexParameteri(target,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);GL11.glTexParameteri(target,GL11.GL_TEXTURE_WRAP_S,33071);GL11.glTexParameteri(target,GL11.GL_TEXTURE_WRAP_T,33071);if(target==GL13.GL_TEXTURE_CUBE_MAP)GL11.glTexParameteri(target,GL12.GL_TEXTURE_WRAP_R,33071);}
    private static void upload(int target,String name)throws IOException{BufferedImage image=ImageIO.read(Panorama.class.getResourceAsStream("/assets/minecraft/textures/gui/title/background/"+name));int w=image.getWidth(),h=image.getHeight();ByteBuffer bytes=TexturePixels.rgba(image,target!=GL11.GL_TEXTURE_2D,false);GL11.glTexImage2D(target,0,GL11.GL_RGBA8,w,h,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,bytes);}
    private static int shader(int type,String text){int s=GL20.glCreateShader(type);GL20.glShaderSource(s,text);GL20.glCompileShader(s);if(GL20.glGetShaderi(s,GL20.GL_COMPILE_STATUS)==0)throw new IllegalStateException(GL20.glGetShaderInfoLog(s,4096));return s;}
}
