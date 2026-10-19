# Item Interactions

A Minecraft mod. Downloads can be found on [CurseForge](https://www.curseforge.com/members/fuzs_/projects)
and [Modrinth](https://modrinth.com/user/Fuzs).

![](https://raw.githubusercontent.com/Fuzss/modresources/main/pages/data/iteminteractions/banner.png)

## Outline

Item Interactions is a library mod that offers a lot of great features to supercharge using items storing other items
directly from your very own inventory without ever having to open the item's dedicated menu.

Some key features include:

- Item tooltips with a rich preview of all inventory contents.
- Bundle-like behavior for inserting and extracting individual item stacks.
- Scroll through all items on the item's tooltip to choose which item to extract next.
- Drag above other items in your inventory while holding a container item to add all those items to its inventory, drag
  above empty slots to extract items from the container.
- Move single items while scrolling for maximum precision (requires holding `Control` or `Command`).

Support for individual items is fully data-driven via data packs using so-called item storage definitions. This way
items from mods (mainly backpacks and larger shulker boxes) can work, too, when corresponding definitions are
configured.

While this library does not include any such definitions on its own,
the [Easy Shulker Boxes](https://github.com/Fuzss/easyshulkerboxes) mod offers them out-of-the-box.

## Configuration

All item storage definitions are found at the following locations:
> `data/<namespace>/iteminteractions/item_storage/<path>.json`

The placeholder `<namespace>:<path>` represents the arbitrary definition id.

A single item storage definition can enable capabilities for one or many items, meaning there does not have to be one
file per item, nor do the file names necessarily have to correspond to the item names.

Depending on the kind of item that the definition is intended to be used for, different types are available.

---

### Common fields

| Field             | Mandatory | Allowed Values                                                  | Explanation                                                                                                      |
|-------------------|-----------|-----------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------|
| `type`            | ✅         | `Identifier`                                                    | The provide type id for this definition. Available item storage definition types are listed and explained below. |
| `supported_items` | ❌         | `Identifier` or `TagKey` or a list of `Identifier` and `TagKey` | The container items this item storage definition will apply to.                                                  |

---

### Storage options fields

| Field                    | Mandatory | Allowed Values                    | Explanation                                                                               |
|--------------------------|-----------|-----------------------------------|-------------------------------------------------------------------------------------------|
| `disallow`               | ❌         | `true` or `false`                 | Turn the defined item list from including allowed items to excluding disallowed items.    |
| `filter_container_items` | ❌         | `true` or `false`                 | Prevent shulker boxes from being put into this item container.                            |
| `items`                  | ❌         | List of `Identifier` and `TagKey` | A list of item ids or tag keys allowed or not allowed to be put into this container item. |

---

### Provider type: `iteminteractions:empty`

This type, when added to an item, does nothing and adds no new capabilities. It exists solely to allow data packs to
override existing item storage definitions to remove them again.

#### Available fields

There are no settings available for this type.

#### Example

> `data/minecraft/iteminteractions/item_storage/empty.json`

```json
{
  "type": "iteminteractions:empty"
}
```

---

### Provider type: `iteminteractions:container`

This type is used for most items that store an inventory. It can be used all the way from shulker boxes to even
backpacks from other mods.

#### Available fields

| Field                     | Mandatory | Allowed Values                                                                                                                                                                                                                        | Explanation                                                                                                                                                                                          |
|---------------------------|-----------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `background_color`        | ❌         | [ARGB](https://en.wikipedia.org/wiki/RGBA_color_model) as `Integer` or any of: `white`, `orange`, `magenta`, `light_blue`, `yellow`, `lime`, `pink`, `gray`, `light_gray`, `cyan`, `purple`, `blue`, `brown`, `green`, `red`, `black` | The background color used on the item tooltip, defaults to vanilla's gray container background color.                                                                                                |
| `equipment_slots`         | ❌         | Any of `any`, `mainhand`, `offhand`, `hand`, `feet`, `legs`, `chest`, `head`, `armor`, `body`, `saddle`                                                                                                                               | An inventory equipment slot the item needs to be placed in to allow for inventory interactions in survival mode. Useful for items that must be worn in some inventory slot to become usable.         |
| `interaction_permissions` | ❌         | `always`, `creative_only` or `never`                                                                                                                                                                                                  | Controls the game mode the player must be in for using any of the inventory interactions. Purely visual capabilities may still be usable. Also can prevent any interactions at all in any game mode. |
| `inventory_height`        | ✅         | `0 >`                                                                                                                                                                                                                                 | The number of vertical container slots; meaning the amount of rows in the item container screen.                                                                                                     |
| `inventory_width`         | ✅         | `0 >`                                                                                                                                                                                                                                 | The number of horizontal container slots; meaning the amount of columns in the item container screen.                                                                                                |
| `item_storage`            | ❌         | [Item Storage Definition](#storage-options-fields)                                                                                                                                                                                    | An object for filtering items that can or cannot be put into this container item.                                                                                                                    |

#### Example

> `data/minecraft/iteminteractions/item_storage/shulker_box.json`

```json
{
  "type": "iteminteractions:container",
  "equipment_slots": "any",
  "interaction_permissions": "always",
  "inventory_height": 3,
  "inventory_width": 9,
  "item_storage": {
    "disallow": true,
    "filter_container_items": true
  },
  "supported_items": "minecraft:shulker_box"
}
```

---

### Provider type: `iteminteractions:ender_chest`

This type is used for blocks and items providing access to the ender chest of a player.

#### Available fields

There are no settings available for this type.

#### Example

> `data/minecraft/iteminteractions/item_storage/ender_chest.json`

```json
{
  "type": "iteminteractions:ender_chest",
  "supported_items": "minecraft:ender_chest"
}
```

---

### Provider type: `iteminteractions:bundle`

This type is used for bundle items. Instead of specifying inventory dimensions, the capacity multiplier must be set.

#### Available fields

| Field                 | Mandatory | Allowed Values                                     | Explanation                                                                                                                                |
|-----------------------|-----------|----------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------|
| `capacity_multiplier` | ✅         | `0 >`                                              | Scale for [capacity](https://minecraft.wiki/w/Bundle#Usage) of the bundle (the available weight); multiplied by base capacity which is 64. |
| `item_storage`        | ❌         | [Item Storage Definition](#storage-options-fields) | An object for filtering items that can or cannot be put into this container item.                                                          |

#### Example

> `data/minecraft/iteminteractions/item_storage/bundles.json`

```json
{
  "type": "iteminteractions:bundle",
  "capacity_multiplier": 1,
  "item_storage": {
    "disallow": true,
    "filter_container_items": true
  },
  "supported_items": "#minecraft:bundles"
}
```
