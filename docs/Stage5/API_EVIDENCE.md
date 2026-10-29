# Stage 5 API evidence and limits

Primary documentation consulted for pinned Yarn `1.20.1+build.10`:

- https://maven.fabricmc.net/docs/yarn-1.20.1%2Bbuild.10/net/minecraft/nbt/NbtIo.html — compressed File/InputStream/OutputStream APIs.
- https://maven.fabricmc.net/docs/yarn-1.20.1%2Bbuild.10/net/minecraft/world/PersistentStateManager.html — existing R2 getOrCreate semantics; new debris WAL uses a separate explicit durable layer.
- https://maven.fabricmc.net/docs/yarn-1.20.1%2Bbuild.10/net/minecraft/util/WorldSavePath.html — ROOT world-save location.
- https://maven.fabricmc.net/docs/yarn-1.20.1%2Bbuild.10/net/minecraft/server/world/ServerWorld.html — server world/height/collision interfaces.
- https://maven.fabricmc.net/docs/yarn-1.20.1%2Bbuild.10/net/minecraft/world/border/WorldBorder.html — border bounds.
- https://maven.fabricmc.net/docs/yarn-1.20.1%2Bbuild.10/net/minecraft/test/TestContext.html — getAbsolutePos and runAtTick(long,Runnable).
- https://maven.fabricmc.net/docs/fabric-api-0.92.1%2B1.20.1/net/fabricmc/fabric/api/event/lifecycle/v1/ServerTickEvents.html — START_WORLD_TICK/END_WORLD_TICK callback shape. Exact 0.92.2 documentation was not accessible; the project dependency remains 0.92.2.

The existing WorldChunk mixin selector is reused unchanged from R2. Existing PacketByteBuf/ClientPlayNetworking/ServerPlayNetworking and world-render registration patterns are retained. These documentation/source checks are not a Fabric symbol-resolution pass, Java bytecode Mixin application, or executed GameTest. Full Gradle/bootstrap logs are supplied separately.
