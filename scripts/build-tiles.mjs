import geojsonvt from 'geojson-vt';
import vtpbf from 'vt-pbf';
import { readFile, mkdir, writeFile } from 'node:fs/promises';
const geo = JSON.parse(await readFile('data/maps/bhopal.geojson', 'utf8'));
const index = geojsonvt(geo, {
  maxZoom: 16,
  indexMaxZoom: 16,
  indexMaxPoints: 0,
  tolerance: 3,
  extent: 4096,
});
const tx = (lon, z) => Math.floor(((lon + 180) / 360) * 2 ** z);
const ty = (lat, z) =>
  Math.floor(((1 - Math.asinh(Math.tan((lat * Math.PI) / 180)) / Math.PI) / 2) * 2 ** z);
let count = 0;
for (let z = 11; z <= 16; z++)
  for (let x = tx(77.38, z); x <= tx(77.43, z); x++) {
    const directory = `public/maps/tiles/${z}/${x}`;
    await mkdir(directory, { recursive: true });
    for (let y = ty(23.27, z); y <= ty(23.22, z); y++) {
      const tile = index.getTile(z, x, y);
      if (!tile) continue;
      // geojson-vt 4 produces the nested XY arrays expected by vt-pbf.
      // v5's packed typed arrays silently encode as zero coordinates with vt-pbf 3.
      if (tile.features.some((f) => !Array.isArray(f.geometry?.[0]?.[0])))
        throw new Error('Unsupported tile geometry format; use the pinned geojson-vt version.');
      await writeFile(`${directory}/${y}.pbf`, vtpbf.fromGeojsonVt({ bhopal: tile }, {version:2}));
      count++;
    }
  }
console.log(`Built ${count} offline MVT tiles for central Bhopal.`);
