// Parche de Agathia OS para @capgo/capacitor-llm 8.2.1-agathia.7 + LiteRT-LM 0.11.0
// 1) Motor LiteRT-LM solo texto: no cargar el Vision Encoder ni el de audio (visionBackend/audioBackend = null).
// 2) Renombrar el paquete para publicarlo bajo el scope propio.
// Si el código original no coincide, el script falla a propósito para no publicar nada roto.
import { readFileSync, writeFileSync } from 'node:fs';

const javaPath = 'android/src/main/java/ee/forgr/capgo_llm/LLM.java';
const java = readFileSync(javaPath, 'utf8');

const pattern = /(new EngineConfig\(\s*actualPath,\s*primaryBackend,\s*)primaryBackend,(\s*)primaryBackend,/;
const alreadyPatchedPattern = /new EngineConfig\(\s*actualPath,\s*primaryBackend,\s*null,(\s*)null,/;
if (pattern.test(java)) {
  const patched = java.replace(pattern, (_m, start, gap) => `${start}null,${gap}null,`);
  writeFileSync(javaPath, patched);
  console.log('LLM.java parcheado: visionBackend y audioBackend = null');
} else if (alreadyPatchedPattern.test(java)) {
  console.log('LLM.java ya contiene el parche de backends de texto');
} else {
  console.error('ERROR: no se encontró la llamada a EngineConfig esperada en LLM.java. No se aplica ningún cambio.');
  process.exit(1);
}

const pluginPath = 'android/src/main/java/ee/forgr/capgo_llm/LLMPlugin.java';
const plugin = readFileSync(pluginPath, 'utf8');
const versionPattern = /private final String pluginVersion = "[^"]+";/;
if (!versionPattern.test(plugin)) {
  console.error('ERROR: no se encontró pluginVersion en LLMPlugin.java. No se aplica ningún cambio.');
  process.exit(1);
}
if (!plugin.includes("public void closeChat(PluginCall call)")) { console.error("ERROR: falta closeChat en LLMPlugin.java."); process.exit(1); }
const expectedPluginVersion = 'private final String pluginVersion = "8.2.1-agathia.7";';
const pluginPatched = plugin.replace(versionPattern, expectedPluginVersion);
if (pluginPatched !== plugin) {
  writeFileSync(pluginPath, pluginPatched);
}
console.log('LLMPlugin.java: pluginVersion = 8.2.1-agathia.7');

const pkg = JSON.parse(readFileSync('package.json', 'utf8'));
pkg.name = '@uniksolana/capacitor-llm';
pkg.version = '8.2.1-agathia.7';
writeFileSync('package.json', JSON.stringify(pkg, null, 2) + '\n');
console.log(`package.json: ${pkg.name}@${pkg.version}`);
