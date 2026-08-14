# Persistent Item Frames

**Persistent Item Frames** is a lightweight, client-side mod that prevents item frames from disappearing prematurely when you walk away from them.

Originally created by **Chino_47**, this is an updated and maintained port for modern Minecraft 26+ (Fabric).

---

## The Problem

To save server TPS, multiplayer servers often aggressively clamp down entity tracking distances—sometimes hiding item frames just 16 to 32 blocks away. 

If you're running extended view distance or LOD mods like [Bobby](https://modrinth.com/mod/bobby), [Voxy](https://modrinth.com/mod/voxy), or [Distant Horizons](https://modrinth.com/mod/distanthorizons), your world renders for miles while your chest labels, storage halls, and map art vanish into thin air as soon as you take a few steps back.

---

## How It Works

1. **The Server Saves Performance**: When you walk out of tracking range, the server stops tracking the frame and sends a "Remove Entity" packet to your client.
2. **The Client Keeps the Frame**: This mod intercepts that packet and keeps the item frame in client memory, letting vanilla Minecraft continue rendering it seamlessly.

### Smart In-World Cleanup
To prevent "ghost" or phantom frames from lingering when things actually break:
* **Audio & Destruction Detection**: Listens for genuine destruction events (break sounds, item drops, explosions) so frames broken by other players, creepers, or water are removed in real-time.
* **Supporting Wall Checks**: Automatically removes the frame if you break or target the supporting block behind it.
* **Server-Verified Interactions**: Clicking or punching a frame validates it with the server—phantom frames vanish cleanly, while claim protections (like spawn/WorldGuard) are fully respected.
* **GPU Friendly**: Minecraft's built-in **Entity Distance** slider in Video Settings still manages GPU culling normally.

---

## Works Great With

* **[Bobby](https://modrinth.com/mod/bobby)** — Client-side chunk caching
* **[Voxy](https://modrinth.com/mod/voxy)** — Extended LOD rendering
* **[Distant Horizons](https://modrinth.com/mod/distanthorizons)** — Simplified LOD terrain
* Any multiplayer server with low entity tracking distances

---

## Requirements & Installation

1. **[Fabric Loader](https://fabricmc.net/)** (version `0.15.0+`)
2. **Minecraft 26.1+** (Tested on `26.1.2` and `26.2`)
3. Drop the `.jar` into your `.minecraft/mods` folder.

*(100% client-side. No server installation required.)*

---

## License & Credits

* **Original Creator**: [Chino_47](https://curseforge.com/members/chino_47) for the original concept and implementation.
* **License**: Licensed under the **Academic Free License (AFL) v. 3.0** (see [LICENSE](LICENSE)).
