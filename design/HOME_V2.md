# Home v0.2 — historical revision

Superseded by `UI_V3.md`. This file preserves the earlier homepage decisions, including controls removed in v0.3.

2026-10-06. Editable Pen visual prototype. Interface and layer names are English. Only the homepage was redesigned; earlier secondary pages remain historical drafts.

## Review files

- `Minecraft 联机客户端 UI.pen`: frame `01 · Home — News`.
- `exports/home-glass/CO9oe.png`: current homepage, 1280 × 800 at 2× export.
- Frame `09 · Home content states` and `exports/home-v2/W9Saw.png`: alternate contents of the same homepage and signed-out account state. The three narrow panels compare states; they are not three simultaneous columns in the product.

## Layout and typography

The shell follows the supplied sketch: an outer dark surface with an inset home panel, an icon rail, an empty central header, and the account at top right. Launch/version is the dominant upper action. A current-profile selector sits above it. Appearance controls occupy the upper right. The bottom area contains content tabs on the left and friends on the right.

Barlow Condensed 700–800 is used for headings and the launch action; Barlow 500–700 is used for IDs, profile names, labels, and status. This is a visual approximation of Lunar's compact, heavy style, not an identification of its exact font. Homepage values are scoped in `system/tokens.css` under `home-v2`.

The supplied FORM logo is reused without changing its geometry. Full-window scenery and greeting/marketing paragraphs have been removed. At the user's subsequent request, the Launch area alone now uses the selected W4 shader landscape beneath a 12 px frosted-glass layer with a dark translucent gradient. Controls and the player model remain above the glass; the outer shell and lower content area remain solid. News and shader images remain meaningful covers. News titles, account identities, profile contents and online states are illustrative.

## Intended interactions

| Control | Behavior |
| --- | --- |
| Launch 1.21.1 | Starts the selected profile. The primary action changes to the preparation stage/progress when necessary and to Running after successful launch. Failure retains the error and a specific retry action in this area. |
| Version chevron | Opens the selected profile's version choices; it does not accidentally launch. A change must validate loader/mod compatibility before it is saved. The chevron has a separate hit target. |
| Survival profile selector | Chooses a saved profile. Launch/version and the Mods, Packs and Shaders contents update together. Default first-open tab is News; subsequent profile changes retain the current content tab. |
| Account | Shows skin face + status dot + FORM badge + ID. Opens account switching. Signed-out state displays Sign in. Green means online and gray means offline; accessible names also expose the state. |
| Player preview | Shows the current account's selected skin. The prototype uses a sample skin, not an authenticated account. Future rendering should stop continuous animation when hidden or while the game runs. |
| Classic / Slim | Chooses four-pixel or three-pixel arms for the selected skin and updates the preview. Actual account upload or cosmetics persistence is outside this prototype. |
| Skin thumbnails | Select a skin, update the preview and selection marker. The plus control opens import/management. If saved to the account, account and friend-facing avatars update from that same identity. |
| Cape thumbnails | Select an available cape and preview it; plus opens cape management. The eventual list comes from actual entitlements/local allowed cosmetics, not the sample assets. |
| News | Default tab, two image-led entries with concise titles. Opens the selected article or guide. |
| Mods | Shows the current profile's mod icons and names. Toggles edit the next-launch configuration; ordinary JAR changes are not hot-loaded. Dependencies and shared-profile authority must be checked before applying a change. |
| Packs | Shows installed resource packs with art/icons and enabled state for the current profile. Selecting an item opens its enable/order controls. |
| Shaders | Shows installed shader packs with landscape previews. Selecting an item sets the current profile's shader selection; unsupported or failed loading must produce an explicit result. |
| Friends | Online players sort first. A joinable friend exposes Join; an online friend without a joinable room exposes Invite. Offline players retain avatar, badge and ID with a gray status dot and no misleading join action. Add friend is in the section header. |
| Join | Checks room access, prepares the host's required environment, then launches and joins. Preparation/failure states belong to the subsequent flow, not a promise of immediate connection. |
| Left rail | Home, Client mods, News, Skins; Settings anchored at the bottom. News selects the homepage News tab. Other entries are navigation affordances only in this revision. |

Controls require keyboard focus, descriptive accessible names and tooltips for icon-only actions. Hit targets must be at least 44 px during implementation, even where the visible glyph is smaller. Content changes preserve the account, launch region, friends list and focus context. No long introductory copy is added to explain routine controls.

## Verification and boundary

The homepage and content-state board were visually inspected in Pen and in exported PNGs. Pen bounds inspection reported no clipped nodes; revised frame names and copy are English. Supplied logo, skin renders, cape thumbnails, project icons and landscape covers render successfully. Asset provenance is in `assets/home-v2/SOURCES.md`.

This is a static, editable visual prototype. Clicking tabs, launching Minecraft, authenticating, joining friends, applying packs, animations and accessibility behavior are not implemented or functionally tested. Skin/cape samples and listed mods do not establish account ownership, licensing clearance, dependency compatibility or performance claims. The legacy export PDF does not represent this revision.
