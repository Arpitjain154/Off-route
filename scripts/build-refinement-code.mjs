import {build} from 'vite';
// Rebundle code after a full build; retain the already verified, unchanged offline map assets.
await build({configFile:'vite.config.ts',publicDir:false,build:{emptyOutDir:false}});
