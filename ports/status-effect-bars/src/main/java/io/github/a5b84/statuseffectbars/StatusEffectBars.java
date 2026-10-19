package io.github.a5b84.statuseffectbars;

import io.github.a5b84.statuseffectbars.config.StatusEffectBarsConfig;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.neoforged.fml.common.Mod;
import net.neoforged.api.distmarker.Dist;

// Native NeoForge entry point, adapted by Unbox on 2026-10-18.
@Mod(value = "status_effect_bars", dist = Dist.CLIENT)
public class StatusEffectBars {

  public static final String MOD_ID = "status-effect-bars";

  public static StatusEffectBarsConfig config;

  public StatusEffectBars() {
    AutoConfig.register(StatusEffectBarsConfig.class, GsonConfigSerializer::new);
    config = AutoConfig.getConfigHolder(StatusEffectBarsConfig.class).getConfig();
  }
}
