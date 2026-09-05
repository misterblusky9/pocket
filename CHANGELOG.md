# Changelog

## Alpha 0.18.0

Minecraft 1.21.1 · NeoForge 21.1.240 · Create 6.0.10

### New: Switch Bearing

A Mechanical Bearing that is also a lever. Assemble a structure onto it and the
whole thing becomes one clickable switch — right-click anywhere on the moved
contraption and the bearing emits redstone.

- **Impulse** — one click, one 2-tick pulse. Button semantics.
- **Toggle** — one click flips the output and it stays there. Lever semantics.
- **Analog** — click to step the signal up, sneak-click to step it down, 0–15.
  Neighbour updates are held 15 ticks after the last change, matching Create's
  Analog Lever, so a burst of clicks costs one redstone update instead of one
  per click.

Mode is set from the value-box slot with a wrench, the way every other Create
scroll option works. Crafted from a Mechanical Bearing, a button, a lever, an
Analog Lever and Railway Casing.

The bearing stays assembled at 0 RPM. RPM controls rotation only — the switch
remains present and clickable until you explicitly disassemble it. Emits weak
power on all sides and strong power through the shaft side, like a lever.
Advertises Analog Strength on the goggle tooltip in Analog mode. 4 SU impact,
Mechanical Bearing parity.

### New: Switch Piston

The same idea on a Sticky Mechanical Piston. Poles, heads, extension, collision,
wrenching and assembly are all Create's sticky piston behaviour, inherited
unchanged; the addition is the redstone output driven by clicking the moved
structure. Create identifies pistons through static block-identity helpers that
are hardcoded to its own blocks, so those now recognise the Switch Piston too —
pole attachment, contraption assembly, and sticky drag all work normally. 4 SU
impact. Same recipe as the Switch Bearing with a Sticky Mechanical Piston at its
centre.

### Contraptions survive pocketing

Assembled contraptions used to be deleted when you pocketed a sublevel. Their
blocks live inside the entity rather than in the world, and entity capture skips
contraption entities, so the blocks were serialized out of existence and the
entity was left stranded with a controller it could no longer reach.

Controller-driven contraptions are now disassembled back into the sublevel
before it is measured and serialized, driven from the controller wherever one is
reachable so the controller also leaves its running state. Their blocks now
count toward the compression limit and travel as ordinary sublevel blocks.

Switch contraptions that outlive their controller entirely — possible when a
sublevel is rebuilt, pocketed or split out from under one — are recovered after
a 200-tick grace period by disassembling rather than discarding, so the moved
blocks are never deleted.

### Scale interpolation is now tick-accurate

Scale snapshots carry the sublevel's interpolation tick, and the client keeps a
16-sample history and interpolates against Sable's own tick pointer instead of
snapping to whatever arrived last. Compression and expansion now move smoothly
at any tick rate instead of stepping.

Network protocol version bumped 8 → 10; 0.18.0 clients and servers will not
connect to older ones.

### Rendering

- **Flywheel sublevel embeddings** rebuilt: the scale is applied inside the
  rotation frame instead of after the translation, so a rotated shrunken
  sublevel no longer drifts away from its collision.
- **Contraption embeddings** now build from the contraption matrix directly and
  run the host's own local transforms, instead of post-multiplying a scale onto
  an already-composed pose. Normals come out correct rather than
  inverse-scaled.
- **Sublevel block entities** got a camera-precision fix for rendering far from
  the origin.
- **Primed TNT** now renders and collides at sublevel scale.

### Compression Cannon and Toy Shrinkray

- The shrink ray's beam is now colour-coded: amber growing, cyan shrinking,
  white when the target is already where you're pointing it. Firing at a settled
  target no longer silently does nothing — it tells you. Colours are matched to
  the beam by impact point and sent to everyone tracking the shooter, so nearby
  players see the same thing you do and two people firing at once don't swap
  colours.
- Moon compression direction is taken from the cannon's mode rather than from
  where the moon currently sits, so holding grow through the end of a growth no
  longer paints the field blue for a retraction.
- The compression field no longer flips direction mid-animation once it has left
  the acquiring phase.

### Static Subspace Compressor

- **Beam aiming is now conical.** The forgiveness pad widens with distance
  instead of staying a flat half-block, so a compressed sublevel a dozen blocks
  out is no longer a target barely wider than the beam itself. Base radius
  0.5 → 0.75.
- **Opposed emitters now deadlock instead of racing.** Two sealed compressors
  pulling a sublevel opposite ways hold it in place with both beams locked on,
  rather than fighting through a channel that only remembered whichever ticked
  last. The step delay restarts while held, so a stall cannot bank progress that
  fires the instant one emitter drops out. Emitters shrinking to *different*
  depths still cooperate — only a genuine shrink-against-grow holds.

### Physics

- **Merged mass tracking:** a Create kinematic contraption's moving centre of
  mass no longer steers the parent sublevel's physics pivot while shrunken. Its
  mass contribution is kept but its position sample is pinned to the first local
  position seen at that scale, so a shrunken craft no longer orbits and wobbles
  as an internal bearing rotates. Normal size is untouched.
- **Contraption collision** moved into a dedicated scaled collider rather than
  a wrapped call with a thread-local scale context, so the broad phase, step
  separation and response epsilon all see the scale directly.

### Compatibility

**Simulated / Sable**

- Honey and super glue lookups now include sublevel-inclusive results on both
  client and assembly paths.
- Physics Staff: pickup targeting, hold distance and scroll step all scale
  with the sublevel.
- Guarded the heat map against removed sublevels.

**Simulated Coasters**

- Cart-to-cart train link constraints, link placement, and the coupling
  renderer now scale.
- Path trace endpoints and open-end disengage matching scale.

### Fixes

- The Static Subspace Compressor's icon sheet was widened to 64px for the new
  switch-mode icons; the atlas width constant is now correct, which also fixes
  the UVs of the existing Grow/Shrink icons.
- The Portable Subspace Compressor no longer restarts its field animation when
  the commanded stage already matches the current one.

### Renames

- Creative Shrinkray → **Toy Shrinkray**
- Collider Wand → **Debug Wand**
- Tweezers → **Tweezers (WIP!)**

### Build

CI now runs only on `main` and pull requests, cancels superseded runs, has a
20-minute timeout and a read-only token, and uploads a named, retained jar
artifact with `if-no-files-found: error`.
