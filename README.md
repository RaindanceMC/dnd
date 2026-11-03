# RaindanceMC/dnd

- **`dnd-module`** — Rain's Flexible Modules — Adventurers: levels 1–100 from defeating monsters, a skill point every level for a skill tree of five branches (Vitality, Might, Marksman, Shadow, Arcana — 75 nodes, a choice and a capstone each, feats like Dual Wielder, Second Wind and Undying), and magic items that monsters drop. Until 0.10.0 it was tabletop classes and spells; heroes from then keep their level and experience.
- **`dnd-standalone`** — Rain's Adventurers (`RainsAdventurers`): the dnd module and the standard wrapper, shaded together.

Part of [RaindanceMC](https://github.com/RaindanceMC); built on [RainsCore](https://github.com/RaindanceMC/RainsCore) and the shared parent in [framework](https://github.com/RaindanceMC/framework).

## How it looks

| | |
|---|---|
| ![The skill tree's five branches (art/tree/preview.py)](docs/screenshots/22-skill-tree-preview.png) | ![Magic items](docs/screenshots/10-items.png) |
| ![Magic items, in Blockbench](docs/screenshots/11-items-blockbench.png) | ![Potions on a wall](docs/screenshots/14-wall-close.png) |
| ![Gear on a wall](docs/screenshots/15-wall-gear.png) | ![Potions](docs/screenshots/13-wall-potions.png) |

The art is made in `dnd-module/art/`: `items/` (Blockbench models, built through Blockbench's MCP plugin by
`build.py`), `weapons/` (`runes.py`), `gui/` (`parchment.py`), `tree/` (the skill tree's frames, chain and branch
paintings on Core's `art/menus/tree.py`; `preview.py` lays the pages out), then `pack.py` writes the index the plugin
ships from.

## Playing

- `/adventurer` (`/dnd`) — the skill tree, one branch a page. A skill point every level; click a node to learn its next rank, shift-click the middle button to unlearn everything (once a day by default).
- **Levels** come from defeating monsters: 50 experience for a zombie up to 20,000 for the ender dragon; levels 1–20 on the old table, then 25,000 a level to 100.
- **Branches**: Vitality (hearts, armour, healing, Second Wind or Stoneskin, Undying), Might (melee, attack speed, lifesteal, **Dual Wielder** — the off hand strikes too, for half — or Great Weapon Master, Warlord), Marksman (arrows, Long Shot, arrows saved, Volley or Sharpshooter, Deadeye), Shadow (speed, dodging, jumps, backstabs, Acrobat or Assassin, Phantom), Arcana (experience, loot, luck, mining, stronger scrolls, wands and potions, Sage or Treasure Hunter, Archmage).
- **Magic items** drop from monsters by rarity: healing potions, Heroism, Speed, spell scrolls (Magic Missile, Fireball, Lightning, Healing, Misty Step, Shield; tier 1–5), +1/+2/+3 weapons and bows, a Wand of Magic Missiles, Ring of Protection, Amulet of Health, Boots of Striding and Springing, a Bag of Holding (your ender chest, anywhere). The Potion of Mach Fuck is brewed from a dragon head.

Staff (`rainsadventurers.admin`):

- `/adventurer admin xp <player> <amount>` — adventure experience, levels follow
- `/adventurer admin level <player> <level>` · `unlearn <player>` · `reset <player>`
- `/adventurer admin give <player> <item> [scroll] [tier]`

Everything an owner can tune (worlds, experience, loot rate, how much of the tree counts between players, how often it may be unlearnt, the Mach potion) is under `/settings` → Adventurers.

## Build

```
mvn install
```

Needs Java 25 and read access to `packages.tstieh.de` (server ids `packages-releases` / `packages-snapshots` in `~/.m2/settings.xml`). The end-to-end scenarios are `AdventurersScenarioTest` in [e2e](https://github.com/RaindanceMC/e2e) — written for the classes before 0.11.0 and not yet rewritten for the skill tree.

## Versions

The module's version is `dnd-module.version` in the root `pom.xml`, the plugin's `plugin.version` in `dnd-standalone/pom.xml`. CI publishes every version not yet in the registry on the next push to master.

## Licence notice

This work includes material taken from the System Reference Document 5.1 ("SRD 5.1") by Wizards of the Coast LLC, available at https://dnd.wizards.com/resources/systems-reference-document. The SRD 5.1 is licensed under the Creative Commons Attribution 4.0 International License, available at https://creativecommons.org/licenses/by/4.0/legalcode. Magic item names and some spell names are kept from it; this plugin is not affiliated with or endorsed by Wizards of the Coast.
