# Persistent Item Frames — Architecture & Design Decisions

> **Target Audience**: Future developers, maintainers, and AI coding agents working on or extending this codebase.

---

## 1. Executive Summary & Core Objective

**Persistent Item Frames** is an ultra-lightweight client-side Fabric mod for Minecraft 26.x (and backward/forward compatible across modern versions). 

* **The Problem**: Minecraft servers aggressively cull entities outside their simulation / entity-tracking radius (often configured as low as 16–32 blocks on Spigot/Paper/Purpur to protect TPS when players place thousands of item frames on chests or map walls). When playing with extended client view distance mods ([Bobby](https://modrinth.com/mod/bobby), [Voxy](https://modrinth.com/mod/voxy), [Distant Horizons](https://modrinth.com/mod/distanthorizons)), terrain renders 64+ chunks away while all item frames prematurely vanish from view.
* **The Goal**: Keep item frames rendered seamlessly in client memory across any distance without introducing memory leaks, ghost mobs, ghost frames on broken walls, or breaking server claim protections.

---

## 2. Core Architectural Philosophy: Packet Interception vs. State Caching

| Approach | How It Works | Trade-offs & Evaluation |
| :--- | :--- | :--- |
| **Heavy Client-Side Caching** *(e.g. danncd's approach)* | Serializes entity NBT data to local disk/memory caches, hooks chunk load/unload cycles, and reconstructs synthetic entities. | ❌ Heavy CPU/disk overhead.<br>❌ Potential NBT desyncs, flickering on chunk reload.<br>❌ Complex state machine. |
| **Packet Interception** *(Chino_47 / Current Approach)* | Intercepts the server's `ClientboundRemoveEntitiesPacket` at network ingress and prevents the client from removing `ItemFrame` instances from `ClientLevel.entityStorage`. | ⭐ **Zero disk/memory overhead**.<br>⭐ Native Minecraft rendering pipeline continues drawing existing frames.<br>⭐ Video Settings **Entity Distance Slider** continues to cull GPU draw calls naturally. |

---

## 3. Detailed Scenarios, Decisions & Rationale

### Scenario 1: Multi-Entity Packet Bundling
* **Problem**: The vanilla server frequently packs multiple entity IDs into a single `ClientboundRemoveEntitiesPacket` (e.g. 1 Item Frame + 3 Zombies leaving simulation range).
* **Flawed Naive Solution**: Calling `ci.cancel()` on the entire packet prevented the Zombies from being removed, leaving static ghost mobs.
* **Our Solution ([`PersistentFramesMixin.java`](file:///c:/Users/fitch/Not-backed-up/dev/GitHub/persistent-item-frames/src/main/java/com/chino/persistentframes/mixin/PersistentFramesMixin.java))**:
  1. Perform a fast-path scan: `if (!hasFrame) return;` (0 overhead for normal mob traffic).
  2. If an `ItemFrame` is present, call `ci.cancel()` and iterate through each entity ID individually.
  3. Non-frame entities (`!(entity instanceof ItemFrame)`) are explicitly removed via `this.level.removeEntity(id, Entity.RemovalReason.DISCARDED)`.
  4. Only `ItemFrame` entities are conditionally preserved.

---

### Scenario 2: Distance Culling vs. Real In-World Destruction
* **Problem**: How does the client tell the difference between *"Player walked 16 blocks away (distance culling)"* and *"Another player punched the frame / Creeper blew it up / Water washed over it"*?
* **Rejected Alternative A (Distance Thresholds)**:
  * *Idea*: Assume $< 32$ blocks is destruction, $> 32$ blocks is distance culling.
  * *Why Rejected*: Many servers configure entity tracking to 16 blocks. A distance threshold breaks on those servers.
* **Rejected Alternative B (Solid Block Verification)**:
  * *Idea*: Check `level.getBlockState(supportPos).isSolid()`.
  * *Why Rejected*: Custom maps, spawn lobbies, and creative builds frequently use floating frames or frames on barrier blocks (`/summon item_frame ~ ~ ~ {Invisible:1b, Fixed:1b}`). Checking block solidity deletes legitimate floating lobby frames.
* **Our Chosen Solution (Break Audio & Item Drops)**:
  * In vanilla Minecraft, in-world destruction **always calls `dropItem()`**, which **always broadcasts `SoundEvents.ITEM_FRAME_BREAK` or `GLOW_ITEM_FRAME_BREAK`** and/or spawns an `EntityType.ITEM` entity at the coordinate.
  * Distance culling **never** broadcasts break sounds or item drops.
  * **Implementation**: We maintain a lightweight 1.5-second sliding window (`RECENT_BREAK_POSITIONS` and `RECENT_BREAK_ENTITY_IDS`). If a removal packet matches a recent break event, the frame is removed immediately.

---

### Scenario 3: Local Player Breaking the Wall Behind the Frame
* **Problem**: If the local player mines the block behind the frame, `client.hitResult` is `HitResult.Type.BLOCK`, not `ENTITY`.
* **Our Solution**:
  ```java
  if (client.hitResult instanceof BlockHitResult bhr) {
      BlockPos supportPos = itemFrame.getPos().relative(itemFrame.getDirection().getOpposite());
      if (bhr.getBlockPos().equals(supportPos)) {
          this.level.removeEntity(id, Entity.RemovalReason.DISCARDED);
      }
  }
  ```
  If the player's crosshair is aimed directly at the support block, removal is permitted immediately.

---

### Scenario 4: Phantom Frames & Claim Protections (WorldGuard, Spawn)
* **Problem**: If an item frame was destroyed while the player was away, it lingers as a visual phantom upon return.
* **Flawed Solution (Eager Deletion on Left-Click)**:
  * If the client immediately discards the frame on left-click:
    * In protected regions (WorldGuard, Towny, Spawn), the server cancels the punch. Eager deletion would make protected frames falsely vanish on the player's screen.
    * Punching a frame containing an item is supposed to pop the item out, not destroy the frame.
* **Our Chosen Solution: Unified Server Acknowledgment Pipeline**:
  * **On Left-Click (Attack) or Right-Click (Interact)**: Flag the entity ID in `PENDING_INTERACTIONS` via [`ItemFrameAttackMixin`](file:///c:/Users/fitch/Not-backed-up/dev/GitHub/persistent-item-frames/src/main/java/com/chino/persistentframes/mixin/ItemFrameAttackMixin.java).
  * **If Real Frame (Protected or Normal)**: The server recognizes `target != null` and replies with `ClientboundSetEntityDataPacket`, `ClientboundHurtAnimationPacket`, or sound $\rightarrow$ Pending flag is cleared; frame stays intact or rotates.
  * **If Phantom Frame**: The server sees `target == null` and sends nothing. After **500 ms** with zero server response, the client cleanly discards the phantom entity.

---

## 4. Performance & Resource Footprint

1. **CPU Overhead**:
   * **0** background threads.
   * **0** per-tick game loop hooks.
   * Execution occurs strictly inside packet listeners (`ClientPacketListener`), taking $< 1\ \mu\text{s}$ per packet.
2. **Network Footprint**:
   * **0** outgoing packets generated. 100% passive client-side inspection.
3. **Memory Footprint**:
   * Storing item frame entities in `ClientLevel.entityStorage` consumes standard vanilla entity heap memory (a few kilobytes per chunk).
   * Level transitions (reconnect, Nether portal) wipe and reconstruct `ClientLevel`, preventing memory leaks.

---

## 5. File & Package Structure

```text
persistent-item-frames/
├── src/main/java/com/chino/persistentframes/mixin/
│   ├── PersistentFramesMixin.java    # Packet interception, audio/drop tracking, removal logic
│   └── ItemFrameAttackMixin.java      # Left/Right click interaction registration
├── src/main/resources/
│   ├── fabric.mod.json               # Mod metadata, i18n descriptions, AFL-3.0 license
│   ├── persistentframes.mixins.json  # Mixin registration
│   └── assets/persistent-item-frames/lang/
│       ├── en_us.json                # English localization
│       ├── es_es.json                # Spain Spanish localization (marcos, despawneen)
│       └── es_mx.json                # Latin American Spanish localization (cuadros, render)
├── build.gradle                      # Loom build script
├── gradle.properties                 # Version (1.1.1+mc26), group (com.chino)
├── README.md                         # Project pitch, companion mods, installation guide
├── ARCHITECTURE.md                   # Complete architectural guide & decisions for future agents
└── LICENSE                           # Academic Free License (AFL 3.0) crediting Chino_47
```

---

## 6. Guidelines for Future Agent Maintenance

1. **Preserve AFL 3.0 Attribution**: Always maintain Chino_47 attribution in LICENSE and README.
2. **Never Add Per-Tick Entity Physics**: Do not iterate through all loaded entities on `ClientTickEvents`. Keep checks packet-driven.
3. **Respect Claim Protection**: Never eagerly discard frames on client interaction without server acknowledgment.
4. **Minecraft Version Ports**: When porting to newer Minecraft releases (e.g. 26.3, 1.22+), only verify Mojmap mappings for:
   * `ClientPacketListener` (`handleRemoveEntities`, `handleSoundEvent`, `handleSoundEntityEvent`, `handleAddEntity`, `handleSetEntityData`, `handleHurtAnimation`).
   * `MultiPlayerGameMode` (`attack`, `interact`).
