package dev.unbox.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import java.nio.file.Files;

/** Opt-in diagnostics for isolated test runs waiting at a startup screen. */
final class TestStartupDiagnostics {
    private static long next;
    static void tick(Minecraft mc) {
        // Only copied benchmark worlds may automatically accept the backup step.
        if(Boolean.getBoolean("unbox.testUpgradeBackup")&&mc.gameDirectory.toPath().toAbsolutePath().normalize().toString().contains("/.cache/")
                &&(mc.screen instanceof net.minecraft.client.gui.screens.BackupConfirmScreen
                ||mc.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen&&mc.screen.getTitle().getString().equals("Upgrading World Completed"))) {
            String action=mc.screen instanceof net.minecraft.client.gui.screens.BackupConfirmScreen?"Create Backup and Load":"Yes";
            for(var child:mc.screen.children())if(child instanceof AbstractWidget w&&w.getMessage().getString().equals(action)) {
                mc.screen.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(w.getX()+w.getWidth()/2.,w.getY()+w.getHeight()/2.,new net.minecraft.client.input.MouseButtonInfo(0,0)),false);
                return;
            }
        }
        if(!Boolean.getBoolean("unbox.startupDiagnostics")||mc.player!=null||mc.screen==null||System.currentTimeMillis()<next)return;
        next=System.currentTimeMillis()+10000;
        try {
            String details=mc.screen.getClass().getName()+"\n"+mc.screen.getTitle().getString()+"\n";
            for(var child:mc.screen.children())if(child instanceof AbstractWidget w)details+=w.getMessage().getString()+"\n";
            Files.writeString(mc.gameDirectory.toPath().resolve("logs/startup-screen.txt"),details);
            net.minecraft.client.Screenshot.grab(mc.gameDirectory,"startup-screen.png",mc.getMainRenderTarget(),1,c->{});
        }catch(Exception e){System.err.println("Startup diagnostics: "+e);}
    }
}
