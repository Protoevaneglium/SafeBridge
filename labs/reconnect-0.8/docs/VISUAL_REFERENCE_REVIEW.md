# Safe Bridge 0.8 visual-reference review

Reviewed November 16, 2026 in `safe-bridge-reconnect-lab`. This document compares design intent and source structure. It does not claim a pixel match, rendered visual parity or completed usability/accessibility testing.

## Reference evidence retained from the earlier review

Both owner-supplied Library images were previously materialized, their local files and Library identity attributes checked, and their **actual pixels inspected** with the image viewer. The first is a 1500×1100 PNG. The second is a 1600×1600 PNG despite its `.jpg` filename. That historical inspection was not OCR- or filename-only inference. The 0.8 documentation update did not re-open the images, capture the app or create a screenshot.

The first reference uses mint/cyan/blush accents, dark typography, large rounded panels and paired pill choices. The second uses translucent blue/cyan glass-like surfaces, white edge highlights and rounded sheets/controls. These are broad visual cues, not a specification to reproduce reference artwork or unrelated application features.

## Current 0.8 interpretation

The Android source retains the original mint/cyan/lilac background gradients, white glass edges and rounded native controls. Home is more compact and action-oriented: paired pastel choices put Support now, Find Me, quiet/staying options, warning signs, peers, tools and support for someone else before deeper menus. The pinned shell separates the red U.S. 911 control, Find Me and Calculator. An active Stop/recovery strip sits above content pagination, including in Calculator. Blue primary actions and distinct emergency coloring retain priority.

Find Me separates snapshots from gated live links. Sharing states distinguish awaiting/acknowledged updates, unverified connectivity, local Stop and pending remote revocation. Until-stopped and chosen-end choices are explicit. The source provides the visual hierarchy and state language; the delivered lab has a blank live endpoint and no deployed public service.

The recipient uses compatible pastel/glass cues with readable dark text, rounded panels and visible focus outlines. Position and Privacy are explicit measured pages rather than a stacked scrolling document. Refresh, Clear and the optional Map action sit outside the text body. The last-known state is explicitly unverified, with UTC fix time, age and accuracy; a nonterminal paused connection is visually and semantically separate from stopped/expired. Until-stopped does not display a fabricated requested end. No street address is invented.

The optional Google Maps flow uses separate measured disclosure pages before the final external action. It explains the transfer of the selected last-known coordinates and device/network data to Google under its own policy. No third-party map, tile, geocoding request or visual content loads automatically, and the external URL excludes the viewing capability. This is an optional external snapshot view, not an embedded or continuously updating map.

No reference artwork, screenshots, photos, avatars, logos, Apple branding, third-party fonts or unrelated weather/music/profile/mood content are embedded. Native gradients, code-drawn shapes and CSS surfaces are original. No decorative live-blur engine or animation was added.

## Pagination, text and focus

The owner’s pages/popups requirement is represented in Android measured pages and disclosure sheets and in recipient Position/Privacy/map pages. Long text continues at readable size rather than being dynamically shrunk to fit. Action labels remain complete. Source minimum touch targets and pressed/focus styling are present; they do not prove device geometry or accessibility.

At very constrained sizes, the deck can show Expand window instead of rendering clipped disclosure text. Persistent controls can still consume too much space at extreme font/viewport combinations. This fallback must not be presented as universal narrow-screen, landscape or 200% text support.

The recipient skips unchanged reflows and retains semantic element/text-node identity during age updates. Android refresh uses reading anchors and best-effort focus restoration. These are source-level improvements supported by host/simulated tests, not verified TalkBack, browser virtual-cursor or keyboard behavior.

## What has not been visually verified

There is **no successful real-browser or native Android render, no application screenshot and no pixel measurement for 0.8** in this review. Historical Chromium startup attempts failed before rendering because of an environment socket restriction; the separate cloud-browser attempt could not reach the local test surface. Neither produced visual evidence of the recipient UI.

Native appearance, glass compositing, visual density, Home first-screen fit, popup/Stop geometry, focus restoration, landscape/split-screen and 200% or extreme text/display scaling remain untested. No assistive-technology session was performed. Prior solid-color contrast calculations belong to earlier audits; they are not measurements of current translucent rendered pixels or an accessibility certification.

Actual device/browser rendering and survivor-led accessibility review are still required to assess polish and practical use. No public deployment or release was performed, and the blank endpoint means the source’s live-sharing design cannot be treated as a working public service.
