# Live Navigation destination search — 1.3.3

The hero is replaced by a single search bar. Its dialog offers an editable current-location starting point, destination autocomplete, swap, cancel, and Get directions. The map card, GPS status/tracking panel, Your drive section, and bottom tabs retain their existing UI. Confirmed routes add a road line and A/B markers to the map; the search bar displays destination, road distance and estimated driving time. Recenter continues to use the existing live-follow control.

## Data and route behavior

- Search uses 45,010 named places extracted from the same checksum-verified OpenStreetMap/Geofabrik source used by the bundled Madhya Pradesh maps. Names, available address tags, categories and Hindi/alternative names are indexed locally. Nearby distance is shown when GPS is available. Coverage is limited to named places in that offline extract; this is not Google's place database.
- Android calculates car routes locally with the existing GraphHopper 7 road graph, through a separate worker and read-only graph handle. It does not modify the fusion filter or sensor service. The routing graph is reused after tracking initializes; manual endpoints can also work without location permission, using an independently staged graph cache.
- Current location is resolved from the latest real device pose when confirming, not from the Bhopal preview location. Selecting current location before GPS lock keeps confirmation disabled and explains how to choose a manual origin.
- Editing a field invalidates its prior selected coordinate. Swapping moves both labels and endpoint values. Cancel discards search edits and ignores any late route response, retaining an earlier confirmed route. Missing routes, invalid points and offline-load failures show an inline error.
- Browser preview supports offline autocomplete, endpoint editing, swap and cancel. Offline road computation runs in the Android app; the browser clearly reports that limitation on confirmation. No straight-line or fabricated road route is substituted.
- Route estimates are static car travel estimates from the offline graph, without live traffic. This change plots routes alongside the existing tracking flow; it does not add spoken turn instructions, automatic rerouting or change GNSS/INS behavior.

## Checks

- Browser interaction tests pass with the real offline index: hero removal, tap/open, both fields, swap, no-results handling, disabled confirmation for unselected text, close, Escape, preserved bottom tabs and no page errors. Screenshots: `destination-home-browser.png` and `destination-search-browser.png`.
- Native JVM routing tests pass: actual Bhopal–Indore roads in both directions, nontrivial road geometry and distances, finite coordinates, and invalid/out-of-area rejection.
- TypeScript, production web build and Android APK build pass.
- Final APK installed and tested on the connected Samsung SM-S711B, Android 16. Eight interaction checks pass: initial search/default origin, both autocomplete fields, coordinate/text swap, real Bhopal–Indore route with both endpoints inside the map viewport, cancellation retaining the route, latest real GPS origin, unchanged tracking service and panels, and three tab cycles plus a background return. The Bhopal–Indore route contains 509 road points and measures 185,952.8 m. Current-location routing also returned real road geometry. Evidence: `destination-android-tests.json` and `destination-android-tests.log`.
- Physical screen taps and Android keyboard input were checked separately. Typing shows matching results; selecting a destination dismisses the keyboard and reveals the centered Get directions action. Screenshots: `destination-keyboard-android.png`, `destination-keyboard-selection-android.png`, `destination-route-android.png` and `destination-current-route-android.png`.
- The final app process retained one GPS registration and one engine generation through search, routing, tab changes and background return. Its captured logcat contains no fatal exception, fatal signal, uncaught error or route-fit warning. This is a destination-search regression check on a stationary phone, not a new moving-vehicle accuracy validation; see `GPS-LOCK-FIX.md` for the earlier ten-minute GPS endurance checks.

## Camera issue found during device testing

MapLibre adds fit-bounds padding to the camera's persistent padding. The GPS-follow camera already reserved 270 vertical pixels, and the initial route overview added another 310 pixels. On the phone's map card, `cameraForBounds` returned undefined and left the camera at zoom 14.6. The implementation now clears persistent camera padding before fitting, scales route margins to the map dimensions, and does not wait for all tiles to load before adjusting the camera. Device checks now require both route endpoints to be inside the map viewport, and the blue road route was visually verified.

## Delivered build

`IDR-Navigator-1.3.3-destination-search.apk`, version 1.3.3 / code 7, 220,995,120 bytes. SHA-256: `cbbcbf40976967f5f0aeb7a6e1148bac5c790a9c2796a78c12bff1870be8a3e2`.

APK signature and 16 KB ZIP alignment checks pass. All 39,285 statewide tiles, seven graph files, three trained model hashes, 45,010 search places and current UI were verified inside this exact APK. It is signed with the existing development identity and installed as an update preserving app data; production signing remains as described in `MAIN-APP-STATUS.md`.

Implementation: `src/DestinationSearch.tsx`, `src/directions.ts`, `src/destination-search.css`, `src/MapView.tsx`, `DirectionsPlugin.java`, `OfflineDirections.java`. The auto-start effect, engine bridge and native sensor/fusion classes were not edited for this change.

Reproduce search data with `.venv-ml/Scripts/python.exe scripts/prepare-destination-places.py`. Source hash and index count are saved in `destination-places-build.log`. Browser tests: `node scripts/test-destination-ui.mjs`. Android device tests: `node scripts/test-destination-android.mjs <authorized-device-serial>`.
