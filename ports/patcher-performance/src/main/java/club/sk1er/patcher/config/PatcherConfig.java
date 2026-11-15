package club.sk1er.patcher.config;
/** Unbox subset: batch model compilation; configured before loading any models. */
public final class PatcherConfig { public static boolean batchModelRendering=true; public static boolean optimizedWorldSwapping=!Boolean.getBoolean("unpvp.vanillaWorldGc"); }
