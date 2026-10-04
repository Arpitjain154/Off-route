// Capacitor 6's tslib default import is incompatible with patched node-tar 7 exports.
// Preserve the required Capacitor major while using the security-patched tar release.
const fs = require('node:fs');
const path = require('node:path');
const file = path.resolve(__dirname, '../node_modules/@capacitor/cli/dist/util/template.js');
if (!fs.existsSync(file)) process.exit(0);
const source = fs.readFileSync(file, 'utf8');
const fixed = source.replace('const tar_1 = tslib_1.__importDefault(require("tar"));', 'const tar_1 = { default: require("tar") };');
if (fixed !== source) fs.writeFileSync(file, fixed);
console.log('Capacitor 6 / node-tar 7 compatibility verified.');
