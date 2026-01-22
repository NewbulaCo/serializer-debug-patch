package com.dairymoose.sdpatch.mixins;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.dairymoose.sd.SerializerDebugCommon;
import com.dairymoose.sd.sync.ServerSerializerInfo;

import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.util.CrudeIncrementalIntIdentityHashBiMap;

/**
 * Patch mixin with higher priority to fix the clientId != 0 bug in SerializerDebug.
 * This mixin loads after SerializerDebug's mixin and overrides its methods with fixed versions.
 */
@Mixin(value = EntityDataSerializers.class, priority = 1001)
public abstract class EntityDataSerializersPatchMixin {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final String LOG_PREFIX = "[SerializerDebugPatch] ";

    @Accessor
    private static CrudeIncrementalIntIdentityHashBiMap<EntityDataSerializer<?>> getSERIALIZERS() {
        return null;
    }

    /**
     * Find a client serializer ID by class name, skipping already matched IDs.
     */
    private static int getSerializedIdFromClassname(String className, java.util.Set<Integer> alreadyMatchedClientIds) {
        if (className == null) {
            return -1;
        }

        int maxSerializer = SerializerDebugCommon.maxSerializer;

        for (int serializerId = 0; serializerId < maxSerializer; ++serializerId) {
            if (alreadyMatchedClientIds != null && alreadyMatchedClientIds.contains(serializerId)) {
                continue;
            }

            EntityDataSerializer<?> eds = net.minecraftforge.common.ForgeHooks.getSerializer(serializerId, getSERIALIZERS());
            if (eds != null) {
                if (className.equals(eds.getClass().getName())) {
                    return serializerId;
                }
            }
        }

        return -1;
    }

    /**
     * @author SerializerDebugPatch
     * @reason Override SerializerDebug to fix clientId != 0 bug
     */
    @Overwrite
    public static void registerSerializer(EntityDataSerializer<?> p_135051_) {
        if (getSERIALIZERS().add(p_135051_) >= 256) {
            throw new RuntimeException("Vanilla DataSerializer ID limit exceeded");
        }
    }

    /**
     * @author SerializerDebugPatch
     * @reason Override SerializerDebug to fix clientId != 0 bug that prevented proper remapping of EntityDataSerializer$1
     */
    @Overwrite
    public static EntityDataSerializer<?> getSerializer(int serializerId) {
        if (SerializerDebugCommon.reorderClientIds) {

            if (SerializerDebugCommon.serverSerializerList != null) {
                LOGGER.info(LOG_PREFIX + "Init remapping process using server data, size=" + SerializerDebugCommon.serverSerializerList.size());

                // Track which client IDs have already been matched to handle duplicate class names
                java.util.Set<Integer> alreadyMatchedClientIds = new java.util.HashSet<>();

                for (ServerSerializerInfo ssi : SerializerDebugCommon.serverSerializerList) {
                    int clientId = getSerializedIdFromClassname(ssi.className, alreadyMatchedClientIds);

                    // FIX: Removed "&& clientId != 0" condition that was preventing client ID 0
                    // from being added to the matched set, causing all EntityDataSerializer$1
                    // serializers to incorrectly match to client 0
                    if (clientId != -1) {
                        alreadyMatchedClientIds.add(clientId);

                        if (clientId != ssi.serializerId) {
                            LOGGER.info(LOG_PREFIX + "Init remap: serverId=" + ssi.serializerId + " -> clientId=" + clientId + " for className=" + ssi.className);
                            SerializerDebugCommon.serializerRemapper.put(ssi.serializerId, clientId);
                        } else {
                            LOGGER.debug(LOG_PREFIX + "No remap needed: serverId=" + ssi.serializerId + " matches clientId=" + clientId + " for className=" + ssi.className);
                        }
                    } else {
                        LOGGER.warn(LOG_PREFIX + "Could not find client serializer for server className=" + ssi.className + " (serverId=" + ssi.serializerId + ")");
                    }
                }

                SerializerDebugCommon.serverSerializerList = null;
            }

            Integer lookupResult = SerializerDebugCommon.serializerRemapper.get(serializerId);
            if (lookupResult != null) {
                int oldId = serializerId;
                serializerId = lookupResult;

                EntityDataSerializer<?> oldSerializer = net.minecraftforge.common.ForgeHooks.getSerializer(oldId, getSERIALIZERS());
                EntityDataSerializer<?> newSerializer = net.minecraftforge.common.ForgeHooks.getSerializer(serializerId, getSERIALIZERS());

                if (!SerializerDebugCommon.suppressReorderLogging) {
                    LOGGER.info(LOG_PREFIX + "Reordering from " + oldId + " to " + serializerId + ", old=" + oldSerializer + ", new=" + newSerializer);
                }
                SerializerDebugCommon.remappedSerializerObjects.put(newSerializer, serializerId);
            }
        }

        if (SerializerDebugCommon.serverSerializerList != null && !SerializerDebugCommon.reorderClientIds) {
            LOGGER.warn(LOG_PREFIX + "WARNING: Got serializer list from server but config reorderClientIds is set to FALSE");
        }

        return net.minecraftforge.common.ForgeHooks.getSerializer(serializerId, getSERIALIZERS());
    }

    /**
     * @author SerializerDebugPatch
     * @reason Override SerializerDebug for consistency
     */
    @Overwrite
    public static int getSerializedId(EntityDataSerializer<?> serializerObject) {
        if (SerializerDebugCommon.reorderClientIds) {
            Integer newSerializerId = SerializerDebugCommon.remappedSerializerObjects.get(serializerObject);
            if (newSerializerId != null) {
                Integer oldId = net.minecraftforge.common.ForgeHooks.getSerializerId(serializerObject, getSERIALIZERS());

                if (!SerializerDebugCommon.suppressReorderLogging) {
                    LOGGER.info(LOG_PREFIX + "Returning new serializer ID " + newSerializerId + " instead of " + oldId + " for object=" + serializerObject);
                }
                return newSerializerId;
            }
        }

        return net.minecraftforge.common.ForgeHooks.getSerializerId(serializerObject, getSERIALIZERS());
    }
}
