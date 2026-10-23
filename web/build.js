// Gera o app a partir dos fontes:
//   - ../roteiro_presidente.html             (versão para abrir no navegador)
// Uso: node web/build.js
const fs = require('fs'), path = require('path');
const d = __dirname;
let h = fs.readFileSync(path.join(d, 'app_src.html'), 'utf8');
const sem = JSON.parse(fs.readFileSync(path.join(d, 'semanas.json'), 'utf8'));
const safe = s => s.replace(/<\/(script)/gi, '<\\/$1');
h = h.replace('/*__PARSER__*/', () => safe(fs.readFileSync(path.join(d, 'parser.js'), 'utf8')));
h = h.replace('/*__LICOES__*/', () => safe(fs.readFileSync(path.join(d, 'licoes.js'), 'utf8')));
h = h.replace('/*__SEMANAS__*/[]', () => safe(JSON.stringify(sem)));
fs.writeFileSync(path.join(d, '..', 'roteiro_presidente.html'), h);
console.log('ok', (h.length / 1024).toFixed(0) + ' KB,', sem.length, 'semanas');
