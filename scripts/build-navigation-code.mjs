import {build} from 'vite';
await build({configFile:'vite.config.ts',publicDir:false,build:{outDir:'tmp/navigation-ui',emptyOutDir:true}});
