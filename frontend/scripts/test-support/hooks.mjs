import { existsSync, statSync } from "node:fs";
import { fileURLToPath, pathToFileURL } from "node:url";

const stubI18n = new URL("./i18n-stub.ts", import.meta.url).href;

export async function resolve(specifier, context, next) {
  const parent = context.parentURL ?? "";
  if (parent.includes("/src/") && /(^|\/)i18n(\/index)?$/.test(specifier) && specifier.startsWith(".")) {
    return { url: stubI18n, shortCircuit: true };
  }
  if (specifier.startsWith(".") && parent.startsWith("file:")) {
    const base = new URL(specifier, parent);
    const path = fileURLToPath(base);
    if (!existsSync(path) || statSync(path).isDirectory()) {
      for (const candidate of [`${path}.ts`, `${path}.tsx`, `${path}/index.ts`]) {
        if (existsSync(candidate)) return { url: pathToFileURL(candidate).href, shortCircuit: true };
      }
    }
  }
  return next(specifier, context);
}
