# SerializerDebug Patch

A Mixin patch for [SerializerDebug](https://github.com/dairymoose/Serializer_Debug) that fixes EntityDataSerializer remapping issues. Temporary fix until PR gets accepted by original mod author.

## The Problem

SerializerDebug v1.0.1 for Forge 1.20.1 fails to remap serializers from mods like doapi and FallingTrees, causing client crashes when entities using those serializers are encountered.

### Bugs in Original

1. **Vanilla class filter** - `getSerializedIdFromClassname` skips any class starting with `net.minecraft.network.syncher.EntityDataSerializer`, which includes doapi's `EntityDataSerializer$1`

2. **No duplicate tracking** - Multiple serializers with the same class name (e.g., `EntityDataSerializer$1`) all match to the first client ID found, causing incorrect remappings

3. **clientId != 0 condition** - The condition `if (clientId != -1 && clientId != 0 && clientId != ssi.serializerId)` prevents client ID 0 from being added to the matched set, causing all subsequent `EntityDataSerializer$1` lookups to match ID 0

## The Fix

This patch uses a higher-priority Mixin (1001 vs default 1000) to override SerializerDebug's `EntityDataSerializers` methods with corrected versions:

- Removed the vanilla class name filter
- Added `alreadyMatchedClientIds` Set to track matched client IDs and prevent duplicate matching
- Changed condition to `if (clientId != -1)` with separate remap check

