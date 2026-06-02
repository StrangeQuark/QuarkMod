# QuarkMod

QuarkMod is a Fabric mod bundle for Minecraft 1.21.8.

The main download is still `quarkmod`, which bundles all feature modules into one jar. The same repository also builds individual feature jars for users who only want part of the mod.

## Modules

- `mods/quarkmod`: the all-in-one QuarkMod bundle jar.
- `mods/grappling`: grappling hooks and the grappling crossbow.
- `mods/excavator`: the Excavator pickaxe enchantment.
- `mods/loot-tweaks`: spawner drops and spawn-egg chest loot.
- `mods/large-villages`: large village world generation.

The feature modules use their own Fabric mod IDs, such as `quarkmod_grappling` and `quarkmod_excavator`, but game registry IDs stay under the `quarkmod` namespace for compatibility. For example, the Excavator enchantment is still `quarkmod:excavator`.

## Development

Build every artifact:

```sh
./gradlew build
```

Build one feature module:

```sh
./gradlew :grappling:build
./gradlew :excavator:build
./gradlew :loot-tweaks:build
./gradlew :large-villages:build
```

Build the all-in-one bundle:

```sh
./gradlew :quarkmod:build
```

The compiled jars are written under each module's `build/libs` directory.

Run the all-in-one QuarkMod client from source:

```sh
./gradlew runClient
```

Run one feature module by itself:

```sh
./gradlew runGrapplingClient
./gradlew runExcavatorClient
./gradlew runLootTweaksClient
./gradlew runLargeVillagesClient
```

## License

QuarkMod is licensed under CC0-1.0. See [LICENSE](LICENSE).
