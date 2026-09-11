# Village Builder

Village Builder is a server-and-client QuarkMod feature module for planning shared village buildings and staffing them with Builder and Miner villagers.

## Blueprint files

On first server start, the module creates this world-specific folder:

```text
<world>/quarkmod/village-builder/templates/
```

It also writes two tiny working examples there:

- `sample_nbt.nbt`: a 3×1×3 oak-plank platform in Minecraft structure-template format.
- `sample_litematic.litematic`: a 2×2×2 cobblestone cube in Litematica format.

Copy additional `.nbt` (Structure Block export) or `.litematic` files into that folder, then run `/villagebuilder reload` as an operator. Subdirectories become part of the template name.

Templates are read on the server; clients do not upload files. The loader ignores entities and block-entity data, rejects command/structure/jigsaw blocks, and limits each template to 48×48×48 blocks, 8,000 non-air blocks, and 512 KiB compressed. A maximum of 64 templates is synchronized at once.

## Planning

Craft a Planner's Wand with a compass in the bottom-left, a stick in the center, and paper in the top-right of a crafting grid. Crouch-right-click anywhere to open the blueprint picker, then choose a design. Hold the wand to see that design's ghost blocks at the targeted block face; when targeting air, the preview is five blocks in front of the player. Right-click while standing to save a shared plan at that preview, whether it is attached to a block or floating in air. Plans persist with the world and show in blue while the wand is held.

## City jobs

`quarkmod:builder_desk` and `quarkmod:miner_station` are Point-of-Interest workstations. Nearby unemployed villagers can acquire the corresponding Builder or Miner profession, with starter construction/material trades.

This first version is planning and staffing only: villagers do not yet gather items or construct blueprints.
