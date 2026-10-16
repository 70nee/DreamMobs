# DreamMobs — Paper 1.21.11

A **server-side Paper plugin**, NOT a Fabric mod. Turns about half of naturally spawned standard zombies into stronger Dream mobs. Maintains roughly 1:1 Dream vs *remaining normal zombies* wherever natural zombies spawn; **does not increase the global monster spawn cap**. It does not add an independent spawn entry or change biome rules. Other zombie variants are unaffected.

## Features
- 150 HP, 30 attack damage, 12 armor, movement speed .30, follow range 40 (configurable)
- Standard zombie chase/combat AI; normal despawning; no boss bar
- Does not burn in daylight; vanilla zombie base entity
- Dream nametag and permanent data marker, survives chunk unloads
- `/dreammobs spawn`, `/dreammobs reload`, `/dreammobs status` (OP-only; run `spawn` as a player)
- No client mod required; all supported vanilla clients can see the player disguise **if their client/launcher supports standard player skin packets**

## IMPORTANT — making the *exact supplied* skin show
**Minecraft clients cannot load an arbitrary embedded PNG as a player skin from a Paper plugin alone.** The PNG is included in `skin/dream.png`. LibsDisguises (and its PacketEvents dependency) handles the player-shape and skin data. It may need to upload/cache the skin with its service. This step needs internet connectivity, and a working compatible version of LibsDisguises/PacketEvents. DreamMobs calls the LibsDisguises player-disguise API at runtime, using the `skin-name` setting.

1. Install a **Paper 1.21.11-compatible** build of **PacketEvents** and **LibsDisguises 11.0.14 or newer** to the Paper server's `plugins` folder. Check their release notes for exact compatible versions and any premium-feature limitations.
2. Start/stop the server once to create `plugins/LibsDisguises/skins/` (if missing, create that directory).
3. Copy the supplied `skin/dream.png` to `plugins/LibsDisguises/skins/dream.png`.
4. Start your server, then run **in-game as OP** (or console if supported): `/grabskin DreamSkin dream.png`. Syntax from LibsDisguises wiki: `/grabskin <Name to Save As> <SkinSource>`.
5. Wait for the skin import to finish, and check LibsDisguises logs/messages for success. `skin-name: DreamSkin` is already set in `plugins/DreamMobs/config.yml`. If your installed release uses another skin name, change it and `/dreammobs reload`.
6. Join and use `/dreammobs spawn` to test. Restart if the player disguise remains a zombie, and check for exceptions in the Paper console.

**Exact-skin visibility is dependent on the external disguise plugin and skin lookup; it is not verifiable from this source archive alone.** If the skin import or lookup fails, the strong Dream zombie will still spawn but may appear as a zombie or with the default player skin. TLauncher or other unofficial client skin/cape injectors can affect what they display; test with the actual players.

## Build on Windows
- Install **JDK 21** and **Apache Maven** (or use IntelliJ IDEA's Maven integration).
- Extract this project and open its directory in IntelliJ IDEA.
- In a terminal in the project directory: `mvn -U clean package`
- If the build succeeds, copy `target/dreammobs-1.1.1.jar` into Paper `plugins/`.
- Do not copy the ZIP or the `skin/dream.png` directly into `plugins/` (the skin goes into LibsDisguises skins as above).
- Restart Paper; check `/plugins` and `/dreammobs status`.

## Settings
`plugins/DreamMobs/config.yml` contains `zombie-replacement-chance` (default 0.5; range 0..1), `health`, `attack-damage`, `armor`, `movement-speed`, `follow-range`, and `skin-name`.

## Compatibility and caveats
- Designed for **Paper 1.21.11** with Java 21.
- This is **source code only**, not a verified working binary. The project could not be compiled or run against remote Maven dependencies in the authoring environment. Fixes may be needed for API or disguise integration.
- It converts 50% of eligible naturally spawned vanilla zombies, rather than adding an independently registered custom mob to vanilla spawn tables. This is the most straightforward way to be approximately as common as ordinary zombies without manipulating the spawn cap.
- Players do not need to install anything, but the server must install DreamMobs + PacketEvents + LibsDisguises for the skin to be visible.
- Default Dream attack value (30) is extremely high. Adjust carefully if the server should be more survivable.

## Version 1.1 — Nightmare Rush animation
- Dream charges with a rising purple **portal spiral** and soul-fire particles, then performs a sudden leap, a visible sweeping strike and a short-range follow-up hit.
- The hit can be dodged; set `nightmare-rush.enabled: false` to disable or adjust `cooldown-seconds` and `bonus-damage`.
- This is a **server-side particle and movement animation**, not a custom limb animation of the player model. True custom model animations require client resources or a more advanced packet/model solution.

## Prebuilt JAR status
`DreamMobs-1.1.0-UNTESTED.jar` was compiled with Java 21 against locally constructed Paper API signatures because the official Paper API artifact could not be downloaded from this environment. It is NOT verified against actual Paper 1.21.11. The preferred distribution is the included source project; for reliable deployment, rebuild it with real Paper dependencies using Maven (`mvn -U clean package`) in an internet-connected environment. Back up your server before trying the untested JAR, and inspect the Paper log for `NoSuchMethodError`/startup errors. A player-shaped skin requires working LibsDisguises and imported skin, as documented above.


## IMPORTANT: Prior 1.1.0 binary is BROKEN
If the server reports `NoSuchMethodError: NamespacedKey.<init>(java.lang.Object,java.lang.String)` at `DreamMobsPlugin.onEnable`, remove `DreamMobs-1.1.0-UNTESTED.jar`. That JAR was built against incomplete local API stubs. **Do not use it**. Build this source against the real Paper API.

### Easiest rebuild without installing Maven locally (GitHub Actions)
1. Create a new GitHub repository. Upload the **contents** of this ZIP to the repository root (including `.github/workflows/build.yml`), and commit.
2. Visit the repository's **Actions** tab; choose **Build DreamMobs Paper Plugin**, then **Run workflow**.
3. When the build is green, download the `DreamMobs-Paper-Plugin` artifact, unzip it and upload **only the resulting .jar** to Pterodactyl `plugins/`.
4. Delete the old broken JAR, restart, and check the console.
5. If the build fails, send the GitHub Actions failure lines; the source has not been compiled against live Paper API here.

This is a rebuildable source package, **not a verified compiled JAR**.
