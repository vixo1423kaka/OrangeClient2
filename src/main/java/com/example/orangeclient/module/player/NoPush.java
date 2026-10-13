package com.example.orangeclient.module.player;

import com.example.orangeclient.module.Category;
import com.example.orangeclient.module.Module;
import com.example.orangeclient.setting.BoolSetting;

public class NoPush extends Module {
    private final BoolSetting entities = add(new BoolSetting("Entities", true));
    private final BoolSetting liquids = add(new BoolSetting("Liquids", true));
    private final BoolSetting blocks = add(new BoolSetting("Blocks", true));

    public NoPush() {
        super("NoPush", Category.PLAYER);
    }

    /** Odpychanie przez graczy / moby / lodki. */
    public boolean blocksEntityPush() { return isEnabled() && entities.get(); }

    /** Znoszenie przez plynacą wode i lawe. */
    public boolean blocksLiquidPush() { return isEnabled() && liquids.get(); }

    /** Wypychanie z bloku (np. po teleporcie perla w srodek bloku). */
    public boolean blocksBlockPush() { return isEnabled() && blocks.get(); }
}
