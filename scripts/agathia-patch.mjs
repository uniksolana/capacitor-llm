// Parche de Agathia OS para @capgo/capacitor-llm 8.2.0
// 1) Motor LiteRT-LM solo texto: no cargar el Vision Encoder ni el de audio (visionBackend/audioBackend = null).
// 2) Renombrar el paquete para publicarlo bajo el scope propio.
// Si el código original no coincide, el script falla a propósito para no publicar nada roto.
import { readFileSync, writeFileSync } from 'node:fs';

const javaPath = 'android/src/main/java/ee/forgr/capgo_llm/LLM.java';
const java = readFileSync(javaPath, 'utf8');

const pattern = /(new EngineConfig\(\s*actualPath,\s*primaryBackend,\s*)primaryBackend,(\s*)primaryBackend,/;
if (!pattern.test(java)) {
  console.error('ERROR: no se encontró la llamada a EngineConfig esperada en LLM.java. No se aplica ningún cambio.');
  process.exit(1);
}

const patched = java.replace(pattern, (_m, start, gap) => `${start}null,${gap}null,`);
if (patched === java) {
  console.error('ERROR: el parche no cambió nada.');
  process.exit(1);
}
writeFileSync(javaPath, patched);
console.log('LLM.java parcheado: visionBackend y audioBackend = null');

const pkg = JSON.parse(readFileSync('package.json', 'utf8'));
pkg.name = '@uniksolana/capacitor-llm';
pkg.version = '8.2.1-agathia.2';
writeFileSync('package.json', JSON.stringify(pkg, null, 2) + '\n');
console.log(`package.json: ${pkg.name}@${pkg.version}`);
