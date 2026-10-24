# Website icon rules

User revision: 2026-10-24.

- No decorative arrows in the current website. The navigation Download button is text only. The hero has no Download / Explore action row.
- Future arrows must come from the existing Lucide library, not Unicode glyphs or custom path drawings. Selected family: `ArrowUpRight` for an external destination, `ArrowRight` for forward navigation, `ArrowDown` only when a downward direction is necessary. Use the standard 24 × 24 viewBox, 2px stroke, rounded caps and joins, with currentColor. Do not add these arrows back without a functional reason.
- Source inspected: installed `lucide-react` v0.468.0, `node_modules/lucide-react/dist/esm/icons/arrow-up-right.js`, `arrow-right.js`, `arrow-down.js`, and `hand.js`. License copy: `assets/LUCIDE-LICENSE`.
- The HUD illustration uses Lucide Hand instead of a four-way arrow.
- Brand assets remain the original six FORM paths from `app/src/BrandMark.tsx`. The theme-specific SVGs change only the stroke and surface colors. Do not darken the entire logo image with CSS filters.
