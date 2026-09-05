# Changelog

## Alpha 0.18.0

Minecraft 1.21.1 · NeoForge 21.1.240 · Create 6.0.10

### New: Switch Bearing

A Mechanical Bearing that doubles as a redstone switch. Assemble a contraption onto it, then right-click anywhere on the moved structure to control its output.

* **Impulse** — sends a short pulse.
* **Toggle** — switches between on and off.
* **Analog** — click to increase signal strength, sneak-click to decrease it.

Modes are changed with a wrench through Create's normal value-box interface.

The contraption stays assembled even at 0 RPM, and rotation speed only controls its movement.

### New: Switch Piston

A Sticky Mechanical Piston with the same switch functionality.

Right-click the moved piston contraption to control redstone output using Impulse, Toggle, or Analog mode.

All normal Sticky Mechanical Piston behavior remains unchanged.

### Contraptions survive pocketing

Assembled Create contraptions are now safely disassembled before a sublevel is pocketed.

This prevents moved blocks from disappearing and ensures they are included normally when the sublevel is compressed.

Orphaned Switch contraptions are also recovered instead of being deleted.

### Smoother scaling

Sublevel scaling now interpolates using the correct simulation ticks.

Compression and expansion should remain smooth and consistent at different tick rates.

Network protocol updated from **8 → 10**. Older clients and servers are not compatible with 0.18.0.

### Rendering

* Fixed rotated shrunken sublevels visually drifting away from their collision.
* Improved Create contraption rendering while scaled.
* Improved block entity rendering far from the world origin.
* Primed TNT now renders and collides at the correct sublevel scale.

### Compression Cannon & Toy Shrinkray

* Beam colors now show the current action:

  * **Cyan** — shrinking
  * **Amber** — growing
  * **White** — target already at the selected size
* Beam colors now display correctly for nearby players.
* Fixed incorrect moon compression direction.
* Compression fields no longer reverse direction midway through an animation.

### Static Subspace Compressor

* Improved long-distance targeting with a wider, distance-based beam cone.
* Opposing Grow and Shrink compressors now hold a sublevel in place instead of fighting each other.
* Compressors targeting different shrink stages can still cooperate normally.

### Physics

* Fixed rotating internal Create contraptions causing shrunken sublevels to orbit or wobble.
* Improved scaled contraption collision handling.

### Compatibility

**Simulated / Sable**

* Improved Honey Glue and Super Glue support inside sublevels.
* Physics Staff targeting and movement now respect sublevel scale.
* Fixed a possible heat-map error involving removed sublevels.

**Simulated Coasters**

* Train links and coupling visuals now scale correctly.
* Improved track-end detection for scaled coaster carts.

### Fixes

* Fixed Static Subspace Compressor icon UVs.
* Portable Subspace Compressor no longer restarts its animation when already at the requested scale.

### Renamed

* Creative Shrinkray → **Toy Shrinkray**