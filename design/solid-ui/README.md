# 26.3 solid vector UI

Source: the approved scenes from `client-mod/ui-preview/resources/assets/unbox-preview/scenes` in the original checkout, handed over in `docs/87-游戏内矢量UI实验交接.md` on 2026-11-11.

`source/` preserves those SVG exports. Run `python3 scripts/prepare-solid-ui.py` only when changing the exports or component extraction. It writes the version-specific production resources under `client-mod/src/26.3/resources/assets/unbox/solid/`.

Production layers remove example users, server illustrations, skin/player images, fake friend acknowledgements, and static module switches. Native widgets and live business state supply those contents. Red and Yellow Friend / Appearance layouts use the same approved geometry with their respective palettes.

The module catalogue comes from the existing `ModOptions.MODS`; prototype example labels do not introduce additional gameplay modules. Dynamic Light and Coordinates remain their actual existing features. Every existing module remains reachable through pagination/search and its full settings screen.

Outfit Regular is distributed under the included OFL. Exact upstream URLs and hashes are in `font-source.json`. The runtime includes no third-party example server or skin images from the prototype.

See `docs/88-26.3纯色矢量UI正式接入.md` for implementation and verification boundaries.
