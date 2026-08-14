# Persistent Item Frames — Keep Item Frames visible from miles away

Welcome to **Persistent Item Frames**! A simple, incredibly lightweight client-side mod that stops your item frames from despawning when you walk away from them. 

Persistent Frames was originally created by **Chino_47**. This is an updated, maintained port for modern Minecraft 26.1.2+!

## The Pitch
You've just installed [Bobby](https://modrinth.com/mod/bobby), [Voxy](https://modrinth.com/mod/voxy), or [Distant Horizons](https://modrinth.com/mod/distanthorizons). Your render distance is cranked up to 64+ chunks, and your base looks beautiful from the mountain miles away... except all your Item Frames have vanished into thin air.

*Stop me if you've heard this one:*

### The server says "Delete this", and your client says "No."
Minecraft servers have a hard limit on how far they track entities. When you walk out of range, the server stops tracking the item frames to save performance, and sends a "Delete Entity" network packet to your client. This mod simply intercepts and ignores that packet for Item Frames.

## How It Works
By intercepting the `ClientboundRemoveEntitiesPacket` on the client side, this mod forces your client to keep the Item Frame in its active memory. 

- **The Server** still gets its performance gain because it stops tracking the frame. 
- **Your Client** keeps the item frame in memory and hands it off to the vanilla rendering engine.

> [!TIP]
> **Render Distance Limit**: Even though the frames are kept in memory, Minecraft's rendering engine still has a hard cap to save your GPU. Your item frames will render precisely up to the limit set by your **Entity Distance** slider in your Video Settings (up to 500%). Beyond that radius, they will temporarily cull to save FPS, but will instantly reappear when you get closer!

## Installation & Requirements
To run this mod and keep your frames persistent, you'll need:
1. **[Fabric Loader](https://fabricmc.net/)** (version **0.15.0 or newer**)
2. **Minecraft 26.1 or newer** (Tested on 26.1.2 and 26.2).
3. Drop the `.jar` into your `mods` folder!

*(This mod is entirely client-side. You do not need it on the server!)*

## Recommended Companion Mods
To get the most out of Persistent Item Frames, we highly recommend running it alongside extended rendering and LOD mods:
- **[Bobby](https://modrinth.com/mod/bobby)** — Caches chunks on your client so you can see terrain far beyond the server's view distance.
- **[Voxy](https://modrinth.com/mod/voxy)** — LOD-based extended render distances.
- **[Distant Horizons](https://modrinth.com/mod/distanthorizons)** — Simplifies distant terrain to drastically increase view distance.

## License
This updated port continues to be licensed under the **Academic Free License (AFL) v. 3.0**, in accordance with the original creator's license. 
You can find the full terms in the [LICENSE](LICENSE) file.

## Credits
- **Chino_47**: For the genius and incredibly lightweight original concept and implementation of Persistent Frames. 
- **Mojang Studios**: For Minecraft.
