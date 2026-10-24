# RaindanceMC/dnd

- **`dnd-module`** — Rain's Flexible Modules — Adventurers: tabletop-style classes on a survival server. Six classes (fighter, barbarian, rogue, wizard, sorcerer, cleric), levels 1–20 from defeating monsters, ability scores and improvements, spell slots, sorcery points with Font of Magic and metamagic, rage, Second Wind, Action Surge, Sneak Attack, Cunning Action, Channel Divinity, concentration, d20 attack rolls and saving throws, short and long rests.
- **`dnd-standalone`** — Rain's Adventurers (`RainsAdventurers`): the dnd module and the standard wrapper, shaded together.

Part of [RaindanceMC](https://github.com/RaindanceMC); built on [RainsCore](https://github.com/RaindanceMC/RainsCore) and the shared parent in [framework](https://github.com/RaindanceMC/framework).

## Playing

- `/adventurer` (`/dnd`) — choose a class, then the character sheet: abilities, slots, features, spellbook.
- **F** (swap hands) — the casting stance: hotbar slots 1–8 become your spells and features, slot 9 your sheet (a sorcerer's metamagic). Click to cast; sneak and click to cast from your highest slot. F again and your hotbar is back.
- `/adventurer rest` — a short rest: stand still, unhurt, for 30 seconds. Sleeping through the night is a long rest.
- `/adventurer admin level|xp|rest|reset <player> …` — staff (`rainsadventurers.admin`).

Everything an owner can tune (worlds, experience, health per level, spell damage, damage against players, waits, rest lengths) is under `/settings` → Adventurers.

## Build

```
mvn install
```

Needs Java 25 and read access to `packages.tstieh.de` (server ids `packages-releases` / `packages-snapshots` in `~/.m2/settings.xml`). The end-to-end scenario is `AdventurersScenarioTest` in [e2e](https://github.com/RaindanceMC/e2e).

## Versions

The module's version is `dnd-module.version` in the root `pom.xml`, the plugin's `plugin.version` in `dnd-standalone/pom.xml`. CI publishes every version not yet in the registry on the next push to master.

## Licence notice

This work includes material taken from the System Reference Document 5.1 ("SRD 5.1") by Wizards of the Coast LLC, available at https://dnd.wizards.com/resources/systems-reference-document. The SRD 5.1 is licensed under the Creative Commons Attribution 4.0 International License, available at https://creativecommons.org/licenses/by/4.0/legalcode. Rule names, spell names and numbers are adapted to real-time play; this plugin is not affiliated with or endorsed by Wizards of the Coast.
