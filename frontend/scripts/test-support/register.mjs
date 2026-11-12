// Lets `node --test` run the app's own TypeScript modules without a bundler:
// Node strips the types itself; these hooks add the extension-less imports Vite allows and swap the i18n setup
// (which needs a browser) for a tiny stub. Used by `npm test`.
import { register } from "node:module";

register("./hooks.mjs", import.meta.url);
