# Mobile interface

React 18, TypeScript, Capacitor 6, Axios, React Map GL + MapLibre, Lucide, Recharts. `App.tsx` owns application state and local recording metadata; `engine.ts` is the typed adapter for Java and the explicitly labeled browser simulation. `api.ts` configures Axios from the connection settings. `MapView.tsx` interpolates pose at animation-frame cadence and loads bundled vector tiles.

Commands from the repository root: `npm run dev:web`, `npm run build`, `npm run android:sync`.

Browser behavior: **Test drive** runs a synthetic pose on a real OSM road polyline. Recording creates a synthetic DRLOG file with `source=simulation`. Hardware tracking is only available in the Android APK. Simulated confidence, speed, and sensor health are UI demonstrations and not sensor readings. The demo limits exported samples to ten minutes; use physical Android capture for longer recordings.

Settings and local metadata persist in localStorage. Native binary logs remain in app-private storage. A failed upload keeps the pending recording; retry from My drives. Successful records are loaded from `GET /api/drives`. The API schema and UI metadata fields are identical, including mount, route, notes, duration, distance, session ID, source and start time.

Only fused pose, log metadata and model metadata cross the custom bridge. Debugging raw sensors uses offline DRLOG parsing; the brief’s conflicting request for a raw sensor JS diagnostics screen is resolved in favor of its “raw data never crosses the bridge” rule.

Google Fonts are optional visual enhancement; system fonts work offline. The dark map stays muted in both appearance modes. Map and history screens adapt to phone widths with bottom navigation; desktop preview has the same components with a sidebar.
