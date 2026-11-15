package dev.unbox.pvp.mixin;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.scoreboard.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
/** Server lobby resets may remove entries already absent locally. Preserve valid removals without exception/log bursts. */
@Mixin(NetHandlerPlayClient.class)
public class ServerScoreboardMixin {
 @Redirect(method="handleTeams",at=@At(value="INVOKE",target="Lnet/minecraft/scoreboard/Scoreboard;removeTeam(Lnet/minecraft/scoreboard/ScorePlayerTeam;)V"))
 private void unboxRemoveExistingTeam(Scoreboard board,ScorePlayerTeam team){if(team!=null)board.removeTeam(team);}
 @Redirect(method="handleScoreboardObjective",at=@At(value="INVOKE",target="Lnet/minecraft/scoreboard/Scoreboard;removeObjective(Lnet/minecraft/scoreboard/ScoreObjective;)V"))
 private void unboxRemoveExistingObjective(Scoreboard board,ScoreObjective objective){if(objective!=null)board.removeObjective(objective);}
}
