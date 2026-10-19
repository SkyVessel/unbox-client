# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres
to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [v26.1.9-mc26.1.x] - 2026-10-16

### Fixed

- Fix container item contents being saved when the storage item is no longer present in the interacted slot, which could cause item loss or duplication

## [v26.1.8-mc26.1.x] - 2026-10-15

### Changed

- The selected item contents preview can now span multiple rows instead of always being limited to a single row

## [v26.1.7-mc26.1.x] - 2026-10-15

### Added

- Storage item tooltips now show a rich preview for the currently selected item, configurable to display the item name only, the first row of its contents, or all stacks of the same item type combined into a single slot
- The total number of items contained in a storage item on item tooltips (disabled by default)

### Changed

- Play a sound when navigating into and out of nested storage item tooltips

### Fixed

- Fix item interactions with an item held by the cursor not falling through to the hovered container item

## [v26.1.6-mc26.1.x] - 2026-09-23

### Fixed

- Fix item duplication with backpacks from some mods again (NeoForge only)

## [v26.1.5-mc26.1.x] - 2026-08-12

### Fixed

- Fix item duplication issues when interacting with storage item contents in backpacks from some mods

## [v26.1.4-mc26.1.x] - 2026-08-10

### Added

- Add support for viewing nested storage item contents, meaning e.g., shulker box contents in an ender chest can now be
  viewed by highlighting the shulker box and pressing any selection key (return, enter or space bar)
- Note that nested contents can only be viewed but not interacted with
- Going back to the previous storage item tooltip is possible by pressing the backspace key

### Changed

- Vanilla clients can no longer trigger any of the newly added item interactions
- The vanilla container contents tooltip (most commonly used for shulker boxes) is now only disabled when the mod is
  installed on both the client and server
- The control scheme (i.e., which mouse buttons to use for inserting and removing contents from storage items) is now
  configurable
- The previous scheme (from versions for Minecraft 1.21.11 and before) which only relied on the right mouse button is
  set as the default again

## [v26.1.3-mc26.1.x] - 2026-06-20

### Changed

- The mod once again no longer blocks vanilla clients from connecting to a multiplayer server (at least on Fabric)
- This is achieved by the removal of the `iteminteractions:selected_item` data component
- Storing that data is now handled by an attachment on the player once again (which does not use a synced registry)

### Fixed

- Fix unable to use single item mode via scrolling while the cursor holds the item with contents
- Fix the dragging feature breaking copying item stacks via dragging the middle mouse button in creative mode

## [v26.1.2-mc26.1.x] - 2026-05-17

### Changed

- Colorful container backgrounds now are a bit brighter

### Fixed

- Fix item interactions not working correctly in the creative inventory menu

## [v26.1.1-mc26.1.x] - 2026-05-17

### Fixed

- Fix bundle capacity multiplier not being respected sometimes
- Fix items with contents blocking scrolling in screens when hovered under some circumstances

## [v26.1.0-mc26.1.x] - 2026-05-16

### Added

- Scrolling on tooltips is now supported vertically while holding any `Shift` key (configurable)
- The selected item on tooltips can now be changed using the arrow keys on your keyboard
- All items may now feature a bar representing the fill level just like vanilla bundles (disabled by default)

### Changed

- Update to Minecraft 26.1.x
- This release features a large overhaul of the mod to be more inline with the updated bundle mechanics introduced back
  in Minecraft 1.21.2
- Most notably, this changes the mouse buttons used for interactions: the right button now only inserts items, while the
  left button removes items
- The selected item tooltip now only shows the item name to be inline with the vanilla bundle ui
- The selected item slot on tooltips is no longer stored globally, but instead per item like vanilla bundles
- Bundle tooltips now mimic the vanilla tooltip design, while still featuring a glance at all items and an improved fill
  bar
- Reworked and simplified all config options
- Key-based config options now feature a `NEVER` setting for disabling them completely
- Modifier keys can now once again use `Command` on a Mac
- Greatly improved performance when rendering item contents on tooltips
- Move the data pack directory from `iteminteractions/item_container_providers` to `iteminteractions/item_storage`

### Removed

- Remove the `allow_slot_cycling` & `selected_item_tooltips` config options, as it's now always enabled to be inline
  with vanilla
- The `iteminteractions:bundle` type no longer supports defining a `background_color` property

### Fixed

- Fix some quirks with the single item movement, especially when combined with the mouse dragging feature
