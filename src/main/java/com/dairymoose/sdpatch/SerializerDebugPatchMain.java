package com.dairymoose.sdpatch;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraftforge.fml.common.Mod;

@Mod(SerializerDebugPatchMain.MODID)
public class SerializerDebugPatchMain {
    public static final String MODID = "serializer_debug_patch";
    public static final String LOG_PREFIX = "[SerializerDebugPatch] ";
    private static final Logger LOGGER = LogUtils.getLogger();

    public SerializerDebugPatchMain() {
        LOGGER.info(LOG_PREFIX + "SerializerDebug Patch loaded - fixes EntityDataSerializer$1 remapping");
    }
}
