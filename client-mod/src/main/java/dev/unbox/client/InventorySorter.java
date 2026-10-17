package dev.unbox.client;

/** Compatibility entry for existing callers; all sorting is performed by Inventory Profiles Next. */
public final class InventorySorter {
    private InventorySorter(){}
    public static boolean sort(){return SurvivalClientMods.sortInventory();}
}
