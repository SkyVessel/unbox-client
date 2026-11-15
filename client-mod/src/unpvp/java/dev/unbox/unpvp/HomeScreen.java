package dev.unbox.unpvp;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/** Current title / quick-menu design, adapted to native Forge 1.8.9. */
class HomeScreen extends SolidScreen {
    private static ModelPlayer model,slimModel;
    final SocialPanel social=new SocialPanel(this);
    final boolean inWorld;int centerX,logoY,skinX,skinY;private float rotation,lastX;private boolean rotating;
    HomeScreen(boolean inWorld){this(inWorld,null);}
    HomeScreen(boolean inWorld,GuiScreen parent){super(inWorld?"quick":"title",parent);this.inWorld=inWorld;}
    @Override void build(){
        solidButton(104,30,128,40,"Feedback",SolidTheme.card(0),()->navigate(new FeedbackScreen(this)));
        if(page.equals("title")){
            hit(576,314,380,85,"Singleplayer",()->navigate(new GuiSelectWorld(this))).geometry("title-576-314");
            hit(576,403,380,85,"Multiplayer",()->navigate(new MultiplayerScreen(this))).geometry("title-576-403");
            hit(576,504,124,78,"Game settings",()->navigate(new GuiOptions(this,mc.gameSettings))).geometry("title-576-504");
            hit(704,504,124,78,"Appearance",()->navigate(new SkinsScreen(this))).shape(0,0);
            hit(832,504,124,78,"Client Mods",()->navigate(new VectorModsScreen(this))).geometry("title-832-504");
            hit(26,32,62,62,"Account and friends",()->visit("quick"));hit(24,715,64,46,"Quit game",()->navigate(new GuiYesNo((yes,id)->{if(yes)mc.shutdown();else navigate(this);},"Quit Unbox?","Close Minecraft",0)));
            solidButton(21,278,64,64,"+",SolidTheme.card(0),()->navigate(new MultiplayerScreen(this)));controls.add(new ThemeControl(SolidGeometry.TITLE));
        }else{
            hit(598,372,144,70,"Edit HUD",()->{ModsScreen s=new ModsScreen(this);navigate(s);s.editHud();}).geometry("quick-598-372").active=mc.theWorld!=null;
            icon("move",657,394,26,SolidTheme.text());hit(746,372,146,70,"Game settings",()->navigate(new GuiOptions(this,mc.gameSettings))).geometry("quick-746-372");
            hit(598,446,294,82,"Client Mods",()->navigate(new VectorModsScreen(this))).geometry("quick-598-446");
            hit(144,596,212,62,"Change skin",()->navigate(new SkinsScreen(this)));hit(1368,28,48,50,"Return to game",this::close);
            hit(984,250,50,138,"Worlds",()->{social.worlds=!social.worlds;visit("quick");}).geometry("quick-984-250");hit(984,396,50,138,"Add friend",()->navigate(new FriendsScreen(this))).geometry("quick-984-396");
            social.build(page.equals("friend"));controls.add(new OpacityControl(SolidGeometry.QUICK));
        }
    }
    @Override public void updateScreen(){super.updateScreen();if(!page.equals("title"))social.tick();}
    @Override void live(float mx,float my,float partial){if(!page.equals("title"))social.draw();if(page.equals("title")){net.minecraft.client.network.NetworkPlayerInfo info=mc.getNetHandler()==null?null:mc.getNetHandler().getPlayerInfo(mc.getSession().getProfile().getId());mc.getTextureManager().bindTexture(LocalSkin.texture()!=null?LocalSkin.texture():info==null?new ResourceLocation("textures/entity/steve.png"):info.getLocationSkin());GlStateManager.color(1,1,1,1);Gui.drawScaledCustomSizeModalRect(32,32,8,8,8,8,50,50,64,64);return;}double h=viewport.previewHeight(),cx=viewport.previewCenterX(),cy=viewport.previewCenterY();skinX=(int)(cx-h*.4);skinY=(int)(cy-h/2);player(skinX,skinY,(int)(h*.8),(int)h,rotation,mx,my,rotating);}
    static void player(int x,int y,int w,int h,float angle,float mx,float my,boolean dragging){
        net.minecraft.client.Minecraft mc=net.minecraft.client.Minecraft.getMinecraft();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);GL11.glPushMatrix();
        try{
            GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthMask(true);GL11.glDepthFunc(GL11.GL_LEQUAL);GL11.glEnable(GL11.GL_ALPHA_TEST);GL11.glAlphaFunc(GL11.GL_GREATER,.1f);
            float yaw=dragging?0:(float)Math.atan((x+w/2f-mx)/40f)*20,pitch=dragging?0:(float)Math.atan((y+h/2f-my)/40f)*20;
            GlStateManager.enableRescaleNormal();GlStateManager.enableColorMaterial();RenderHelper.enableGUIStandardItemLighting();GlStateManager.color(1,1,1,1);
            float size=h*.43f;GL11.glTranslatef(x+w/2f,y+h/2f,100);GL11.glScalef(size,size,size);GL11.glRotatef(-pitch,1,0,0);GL11.glRotatef(180+angle+yaw,0,1,0);GL11.glTranslatef(0,-.5f,0);
            GlStateManager.bindTexture(0);mc.getTextureManager().bindTexture(LocalSkin.texture()!=null?LocalSkin.texture():mc.thePlayer==null?new ResourceLocation("textures/entity/steve.png"):mc.thePlayer.getLocationSkin());
            if(model==null){model=new ModelPlayer(0,false);slimModel=new ModelPlayer(0,true);}ModelPlayer avatar=(LocalSkin.texture()!=null?LocalSkin.model().equals("slim"):mc.thePlayer!=null&&mc.thePlayer.getSkinType().equals("slim"))?slimModel:model;
            avatar.bipedHead.rotateAngleY=yaw*(float)Math.PI/180;avatar.bipedHead.rotateAngleX=-pitch*(float)Math.PI/180;avatar.bipedHeadwear.rotateAngleY=avatar.bipedHead.rotateAngleY;avatar.bipedHeadwear.rotateAngleX=avatar.bipedHead.rotateAngleX;
            for(net.minecraft.client.model.ModelRenderer part:new net.minecraft.client.model.ModelRenderer[]{avatar.bipedHead,avatar.bipedBody,avatar.bipedLeftArm,avatar.bipedRightArm,avatar.bipedLeftLeg,avatar.bipedRightLeg,avatar.bipedHeadwear,avatar.bipedLeftArmwear,avatar.bipedRightArmwear,avatar.bipedLeftLegwear,avatar.bipedRightLegwear,avatar.bipedBodyWear})part.render(.0625f);
            RenderHelper.disableStandardItemLighting();GlStateManager.disableRescaleNormal();
        }finally{GL11.glPopMatrix();GL11.glPopAttrib();GlStateManager.disableLighting();GlStateManager.disableDepth();GlStateManager.disableAlpha();GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);GlStateManager.color(1,1,1,1);}
    }

    @Override boolean interceptClick(float x,float y,int b){if(!page.equals("title")&&b==0&&Math.abs(x-viewport.previewCenterX())<viewport.previewHeight()*.4&&Math.abs(y-viewport.previewCenterY())<viewport.previewHeight()/2){rotating=true;lastX=x;return true;}return false;}
    @Override protected void mouseClickMove(int x,int y,int b,long held){if(rotating){float next=x/scale;rotation-=next-lastX;lastX=next;}else super.mouseClickMove(x,y,b,held);}
    @Override protected void mouseReleased(int x,int y,int b){rotating=false;super.mouseReleased(x,y,b);}
    @Override void close(){if(inWorld)super.close();else if(!page.equals("title"))visit("title");}
}
