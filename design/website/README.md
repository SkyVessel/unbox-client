# Unbox website

## Current deployment — 2026-10-24

The approved design is live at https://printoria.printoria-studio.workers.dev/unbox/. Windows and macOS download buttons serve signed 0.2.21 packages from Cloudflare. The public feedback board uses Cloudflare D1 and supports Microsoft sign-in, optional public names, posting and pagination in both themes. Each post retains the Microsoft contact email privately for studio replies; public API responses never include it. Production setup and verification are documented in `../../deploy/unbox/README.md` and `../../docs/72-Cloudflare网站与更新迁移.md`.

The notes below preserve the earlier prototype iterations; their placeholder/download status applies only to those iterations. The local Python preview renders the interface but does not serve the Cloudflare feedback API.

# Prototype history

Date: 2026-10-23 (America/New_York).

## Scope

This chat is named 上线管理. Its two launch-preparation goals are a download/features website and removing the current dependency on GitHub for client updates. This iteration delivers the website prototype. Update hosting, download endpoints, deployment, and publication are not implemented here.

## Review

- Pen document: `../Minecraft 联机客户端 UI.pen`.
- Dark artboard: `01 · Website — Dark`, node `zKwW2`, 1440px wide.
- Light artboard: `02 · Website — Light`, node `QH0ON`, 1440px wide.
- The website artboards are at x=6200 and x=7800. Existing client artboards are retained.
- Local preview: `http://127.0.0.1:4178/design/website/?theme=dark`, or `?theme=light`.
- Start preview from the repository root with `python3 -m http.server 4178 --bind 127.0.0.1`.
- Browser screenshots: `exports/dark-hero.png`, `exports/light-hero.png`, `exports/dark-desktop.png`, `exports/light-desktop.png`.
- Pen exports: `exports/zKwW2.png`, `exports/QH0ON.png`.

## Visual approach

Barlow / Barlow Condensed, original Unbox box mark, grayscale panels, blue actions, large rounded color fields, no section dividers. The hero uses an illustrative Client Mods panel. Other sections combine product UI captures with brief copy: invitations and environment preparation, HUD/maps/controls/survival tools, Discover, profiles, skins, performance dependencies, and platform downloads.

The browser preview has a static perspective transform and slowly evolving noise contours. Motion respects reduced-motion preferences, skips drawing in hidden tabs and offscreen sections, and is limited to roughly 12fps. Pen uses a flat angled panel and captured static contour textures because browser-to-canvas import does not preserve 3D transforms or live canvases.

## Sources and status

- Typography: existing `app/public/assets/barlow*.ttf` files.
- Original mark: `design/assets/home-v2/form-shell.png`.
- Landscape, sample skin and avatar: existing `app/public/assets` assets.
- Client Mods screenshot: `design/validation/version263-2026-10-16/cards.png`.
- Discover screenshot: `research/verification/2026-10-23-discover-details/discover-real-projects.png`.
- Capability basis: `IMPLEMENTATION.md`, docs 25, 60–61, 66–68; no FPS multiplier, guaranteed network success, or arbitrary-JAR hot-loading claim.
- Invitations and sync status in the landing page are illustrative, not a live service connection.
- Download buttons show prototype feedback; no installer is downloaded. Public download URLs and release metadata must be connected later.
- Existing sample character and third-party UI assets are used for internal prototype review. Production asset selection remains part of launch preparation.

## Checks

- Reviewed both themes at 1440px, including full-page screenshots.
- Checked 390px mobile rendering and corrected hero wrapping; no horizontal page overflow or missing images in that viewport.
- Checked theme switching, section anchors, reduced-motion preference, and placeholder download feedback.
- Inspected Pen section captures and exports, corrected imported labels and bounds, and replaced unsupported shader previews with static contour captures.
- Pen's direct Insert route produced blank rendering; the delivered artboards use browser import followed by Pen edits. The native Save menu confirmed that the document's Edited indicator cleared.

This is a first design proposal, not a published website or an approved production specification.

## Revision 02 — 2026-10-24

Reference composition reviewed at https://www.lunarclient.com/ and https://www.lunarclient.com/download. The centered brand/landscape hero and platform download tiles follow that reference direction; the Unbox mark, fonts, feature copy and grayscale UI are retained.

- Hero: centered original Unbox mark, short English introduction, full-width blurred Minecraft background. Three scenes crossfade every 12 seconds with a 3-second transition; background playback pauses offscreen, in hidden tabs, on user request, or with reduced motion.
- Navigation: solid charcoal/light surface, separate red Download area. Hover and keyboard focus reveal green from left to right over 480 ms. Reduced motion switches immediately.
- Client Mods: angled illustration moved from the hero to its feature section.
- Download section: Windows x64 and macOS Apple silicon landscape tiles. Links remain prototype placeholders.
- Pen: updated existing dark/light artboards (`zKwW2`, `QH0ON`), plus `03 · Download interaction` (`l7382o`) showing default, partial reveal, and hover states. Pen is static; use the browser preview for actual motion.
- Whole-page browser import timed out during this revision. Updated canvas sections directly through Pen MCP instead. Final exports were inspected; text bounds checks report no clipped text. Native Save confirmation was unavailable because macOS screen capture returned error -3811; canvas updates and exported PNG files are verified.

### Background source mapping

All three are copied unchanged from the user-provided folder `/Users/ty/Pictures/我的世界背景图/`. SHA-256 comparisons confirm the original bytes are preserved. The launcher and website reference the same new client assets; Pen uses copies within `website/assets/`.

| User file | Client asset | Use |
| --- | --- | --- |
| 染尽_6_original.png | app/public/assets/autumn-ravine.png | First hero scene, Windows tile |
| 天色_4_original.png | app/public/assets/alpine-evening.png | Second scene, shared-world section, macOS tile |
| 余温_5_original.png | app/public/assets/underwater-ruins.png | Third scene |

The actual launcher `app/src/main.tsx` now references these three files in its Launch scenery. Its existing timing, glass overlay, and animation pause behavior are unchanged. `npm run build` passed (TypeScript + Vite); Vite reports its existing large-chunk advisory. No installer was rebuilt or published in this revision.

### Revision checks

- Desktop dark/light, mobile 390px, all images loaded and no horizontal overflow.
- Actual background scene index advanced; pause control and reduced-motion state checked.
- Download hover reached the green end state; CSS transition and left-to-right start/end positions checked.
- Theme toggle and placeholder download feedback checked.
- Pen hero, platform tiles, full artboards and hover board exported for review. The exports `dark-hero.png` and `light-hero.png` now show the Pen hero; `dark-desktop.png` and `light-desktop.png` show the browser pages.

## Revision 03 — 2026-10-24

- Removed the hero Download / Explore row marked in the user's screenshot. Removed navigation, section-link and platform-tile arrow glyphs. The invitation now uses a check and the HUD illustration uses Lucide Hand.
- Corrected light navigation/footer logos: original six FORM paths with theme-specific stroke and surface colors, replacing the whole-image brightness filter. Browser uses SVG; Pen uses 192px PNG rasterizations of those same SVGs because its image-fill renderer does not display SVG files reliably.
- New icon policy is in `ICON_GUIDELINES.md`: future arrows use the installed Lucide family, 24px viewBox, 2px rounded strokes; no Unicode arrow glyphs.
- Text groups reveal with an 800ms opacity transition and 16px upward settling (10px on mobile). Image groups use 1000ms; group staggering is capped at 210ms. Each group reveals once on entry. Reduced motion, no-JavaScript, Pen export and keyboard focus retain visible content.
- Verified a section image changed from opacity 0 / pending to opacity 1 / settled after scrolling. Verified reduced motion produces no hidden pending content, desktop/mobile have no horizontal overflow, logos load and switch correctly, and rendered page text has no arrow glyphs.
- Updated both Pen artboards and the Download hover-state board. Pen text bounds checks report no clipping. Existing client source changes from revision 02 were preserved.
