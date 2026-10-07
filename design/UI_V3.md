# UI v0.3 — Home, Client Mods, Settings, Create Profile

## Implementation revision — 2026-10-06

The current Pen boards `12 · Create Profile — Name` (E5bKcQ) and `15 · Profile creation — Four steps` (LWzSi) supersede the profile flow below. The sequence is Name → Minecraft version (26.1) → Loader → Block icon → Create. Back retains the draft. Downloads start on first Launch in the implementation. Current exports for these two boards are in `exports/ui-v4/`; the v0.3 PDF is historical for profile creation. Home launch glass now uses a neutral translucent fill. See `../IMPLEMENTATION.md` for functional and test status.

2026-10-06. All interface text and layer names are English. This revision supersedes conflicting v0.1/v0.2 layouts. This is an editable Pen visual prototype with state illustrations, not a running launcher.

## Screens and exports

Open `Minecraft 联机客户端 UI.pen`. All current exports are under `exports/ui-v3/`; `export.pdf` contains the eleven review screens/state boards.

| Screen | PNG | Scope |
| --- | --- | --- |
| Home — News | CO9oe.png | Revised launch controls, block profile rail, looping appearance strips |
| Home — Mods | PZmyY.png | Two columns, four items, same content footprint as News |
| Home — Packs | OFTdR.png | Two large icon-led items using the News footprint |
| Home — Shaders | amUWC.png | Two landscape-led items using the News footprint |
| Client Mods | swIvw.png | All twelve first-version modules, search and category filters |
| Settings — Game | MNzcL.png | Memory, Java, resolution, fullscreen and launcher behavior |
| Settings — Appearance | Bz9nw.png | Scene selection, glass, blur and reduced effects |
| Create Profile | E5bKcQ.png | Version, loader, block icon and editable generated name |
| Profile creation states | LWzSi.png | Version/block pickers, advanced options, preparing, retry and ready |
| Client Mods — Chat settings | ithc2.png | Detailed chat controls and live-preview layout |
| Client module configuration states | x1DfWc.png | Performance, crosshair, particles and HUD configuration |

The four-part boards illustrate separate interaction states, not simultaneous product panels. `Archive · Home content states v0.2` and the older secondary screens are historical drafts.

## Home changes

- The Launch button is more rounded. Survival and Ready to play are removed from above/below it. The frosted image background remains.
- The bottom-left plus creates a profile. Saved profiles appear beside it as block icons; selection is marked. Hover/focus exposes the profile name, game version and loader. Selecting an icon updates the Launch version and profile-bound content together.
- Launch starts the selected profile. Its chevron opens version/profile management without launching. Unprepared profiles show preparation progress within the primary action; failure exposes retry. No extra permanent status paragraph is restored.
- Mods, Packs and Shaders retain the exact News content-area footprint, with two items per row. Additional rows scroll inside that region, leaving the launch area and friends fixed. Empty states stay in the same region and expose a relevant Add action; no fictitious filler items are added.
- The skin strip scrolls horizontally and the cape strip vertically. Each displays three complete items and half the next, with a blurred/faded edge. There is no trailing plus. The preview shows wraparound; implementation uses cyclic selection without duplicating underlying cosmetic records. With fewer items, show only actual items. Wheel/trackpad and arrow keys advance the focused list, and selection updates the main preview. Screen-reader names expose selection and position; the partly hidden edge item is a continuation cue, not the selected item.
- Cosmetic import/management remains in Skins. The profile plus only creates game profiles.

## Client Mods interaction model

These are personal client features, independent of the shared gameplay environment. Friends do not need identical HUD, input, chat or cosmetic settings. This does not waive server feature restrictions or real loader/version compatibility.

Click a toggle to enable/disable a module; click its title or settings icon to configure it. The HUD tile is an action, not an enable/disable switch. Search matches names and synonyms; filters preserve the query. Empty search shows Clear search. Unavailable modules have an explicit reason and disabled control rather than a switch that pretends to work.

Settings apply locally and show Saved after persistence succeeds. Changes requiring a game restart instead show On next launch. Do not describe bundled JAR modifications as hot-loading. The performance group chooses only compatible, non-conflicting optimization providers for the selected runtime; its visible list is a sample, not a fixed list for every loader.

| Module | First-version controls and behavior |
| --- | --- |
| Performance | Automatic compatible optimizations; inspect individual providers and their purpose. Sodium, Lithium, Entity Culling and ImmediatelyFast appear as illustrative choices. Advanced overrides require dependency validation; changes requiring restart are marked. No FPS uplift is promised by the UI. |
| OptiFine Cape | Show/hide available OptiFine capes only; does not install OptiFine, enable its renderer, or grant cape ownership. Provider failure leaves the skin usable. |
| FPS | Show/hide widget, display style, update interval, text/background appearance, scale and HUD position. Preview values are examples. |
| Crosshair | Shape presets, color, size, gap, thickness, outline and preview. Reset returns to the default; disabling restores the normal game crosshair. |
| Particles | Density and per-type visibility (critical hits, potion effects, explosions, weather). This adjusts rendering, not gameplay effects. |
| HUD Editor | Drag widgets, resize, snap to grid, keyboard nudging, scale, show/hide widgets, reset layout and Done. Escape restores the pre-edit layout; reopening preserves the last saved layout. |
| Armor & Coordinates | Separate toggles for armor/item durability, numeric/percentage display, low-durability indication, XYZ and compass/heading. All widgets can be positioned through the HUD editor. |
| Auto Sprint | Enable, activation key, hold/toggle mode and sprint-state indicator. Does not bypass gameplay conditions. |
| Chat | Background color and opacity, maximum visible lines, text size, timestamps, shadow and width/height via HUD layout. Values are bounded and previewed before saving; normal chat transport is unchanged. |
| CPS | Left/right counters, counting interval, display style, appearance, scale and position. It only displays real clicks. |
| Freelook | Keybind, hold/toggle mode, sensitivity and reset-view behavior. Disable with an explanation where disallowed. |
| Zoom | Keybind, zoom strength, scroll adjustment, smooth transition and sensitivity. |

Keybinding capture accepts one combination, Escape cancels, conflicts are shown beside the field. Reset controls are scoped to the current module rather than resetting the entire client. Color controls support a swatch picker and typed value; invalid input retains the last valid setting.

## Settings

The two drawn settings pages are Game and Appearance. General, Accounts, Multiplayer, AI & MCP, Storage and Advanced are navigation affordances whose full page designs are outside this revision. No language-switch UI is included.

- Game: automatic memory by default; manual allocation is unavailable while Auto is on. Capacity labels come from the real machine in implementation. Java defaults to a compatible managed runtime; custom paths must validate before saving. Resolution requires valid positive bounds, fullscreen is independent, and after-launch behavior can minimize or keep the launcher open.
- Appearance: choose Forest, Cherry Grove or Solid. Frosted glass has an adjustable blur level. Reduced transparency disables glass controls and selects an opaque reading surface; reduced motion disables decorative transitions. Pause effects while playing is enabled by default.
- Values save after successful validation/persistence; edits made while Minecraft runs identify next-launch settings. Global defaults can be overridden by a profile's Advanced settings without rewriting unrelated profiles.
- Planned categories: General (startup/update preferences), Accounts (add/switch/remove identity), Multiplayer (invitations/room access/connection diagnostics), AI & MCP (connection status, per-connection scopes and revocation), Storage (folders/cache), Advanced (diagnostics). These are navigation contracts, not implemented pages or services.

## Create Profile — complete intended flow

1. Click the homepage plus. Open Create Profile with a suggested name, a supported release, a default compatible loader and Grass Block already selected. The illustration uses Fabric 1.21.1; it is not labeled the latest Minecraft release. Changing version/loader updates the generated name until the user edits it manually.
2. Select a game version. Searchable Releases is the default; Snapshots is explicit. A choice returns to the form. If metadata is loading, show loading in place; if offline, offer cached versions and Retry instead of an empty fake list.
3. Choose Vanilla, Fabric, Forge or NeoForge using the labeled icon cards. Incompatible loaders stay visible but disabled with a reason. Switching version revalidates the existing loader choice; never silently substitutes another loader. Vanilla means no mod loader. Advanced can pin a compatible loader build.
4. Choose one of the quick block icons or open All blocks. Search/category selection uses the chosen Minecraft version's block catalog. Clicking a block returns to the form and updates the right-hand preview. Selected states include a checkmark, not color alone.
5. Optionally edit the name or expand Advanced for loader build, Java, memory and a separate game directory. Folder naming uses a stable internal ID, not an unchecked user name. Empty names and invalid paths show inline errors; duplicate display names are disambiguated rather than overwriting another profile.
6. Create profile validates selections and available storage, disables duplicate submission, and starts preparation. Download the selected version/loader/runtime, show the actual current stage and progress, and verify required files before marking Ready. Existing content may be reused after integrity checks; never copy arbitrary personal settings into a shared profile.
7. Cancel preparation stops work safely, keeps the editable draft, and does not leave a ready-looking broken profile. Retry resumes/rechecks interrupted work with selections retained. Failure gives a specific retry/change-folder/free-space action as applicable. Cached/offline availability must be based on verified local files.
8. Successful preparation returns home with the new block icon selected and the correct Launch version. It does not launch without that next action. Profile editing/renaming later uses the same fields; deletion is separate from creation and is not an implicit side effect.

The profile-state board visualizes version search, block selection, advanced options, inline validation and the progress/error/success transition. Illustrative download percentages are not live results.

## Verification

All eleven current frames were inspected using Pen screenshots and bounds checks, with additional full-size PNG review of the revised home and primary new pages. No unintended clipping or non-English copy/layer names was found. The only clipped children are the deliberate half-visible fourth cosmetic tiles. Assets rendered in the exported previews. Source records are in `assets/ui-v3/SOURCES.md` and `assets/home-v2/SOURCES.md`.

Pen presents static, editable states. Search, toggles, infinite scrolling, downloads, account connections, game launch and in-game editing have not been wired or functionally tested. Exact focus order, hit targets, screen-reader behavior, dynamic reflow, and runtime performance remain implementation checks. Prototype frames do not establish ownership of the sample skins/capes or permission to distribute third-party artwork.
