/* Leitor da apostila Vida e Ministério (PDF -> semanas). Funciona no navegador (pdf.js) e no Node. */
(function (root) {
  'use strict';

  const ACENTOS = { 'ˆ': '̂', '˜': '̃', '´': '́', '`': '̀', '¨': '̈', '¸': '̧', '˚': '̊', '^': '̂', '~': '̃' };

  /* ---------- 1. Itens de texto -> linhas -> segmentos ---------- */
  async function lerPaginas(pdf) {
    const paginas = [];
    for (let n = 1; n <= pdf.numPages; n++) {
      const p = await pdf.getPage(n);
      const vp = p.getViewport({ scale: 1 });
      const tc = await p.getTextContent();
      const itens = [];
      for (const it of tc.items) {
        if (!it.str || !it.str.trim()) continue;
        const h = Math.abs(it.transform[3]) || Math.abs(it.transform[0]);
        itens.push({ s: it.str, x: it.transform[4], y: vp.height - it.transform[5], w: it.width, h });
      }
      paginas.push({ n, W: vp.width, H: vp.height, itens });
    }
    return paginas;
  }

  // Junta acentos soltos (ˆ ´ ˜ ¸) à letra que está embaixo deles
  const COMPAT = { '\u0301': /[aeiouıAEIOUy]/, '\u0300': /[aeiouıAEIOU]/, '\u0302': /[aeiouAEIOU]/, '\u0308': /[aeiouıAEIOU]/, '\u0303': /[aonAON]/, '\u0327': /[cC]/, '\u030A': /[aA]/ };
  function comporAcentos(itens) {
    const base = [], acentos = [];
    for (const it of itens) (ACENTOS[it.s.trim()] && it.s.trim().length === 1 ? acentos : base).push(it);
    for (const a of acentos) {
      const comb = ACENTOS[a.s.trim()];
      const cx = a.x + a.w / 2;
      let melhor = null, idxM = -1, dist = 1e9;
      for (const b of base) {
        if (Math.abs(b.y - a.y) > b.h * 0.9) continue;
        if (comb !== '\u0327' && a.y > b.y + 0.5) continue;   // acento fica acima da letra
        if (comb === '\u0327' && a.y < b.y - 2) continue;     // cedilha fica embaixo
        if (b.x > cx + 8 || b.x + b.w < cx - 8) continue;
        const L = b.s.length, cw = b.w / Math.max(1, L);
        for (let k = 0; k < L; k++) {
          if (!COMPAT[comb].test(b.s[k])) continue;
          const d = Math.abs(b.x + (k + 0.5) * cw - cx) + Math.abs(b.y - a.y) * 0.3;
          if (d < dist) { dist = d; melhor = b; idxM = k; }
        }
      }
      if (!melhor || dist > 6) continue;
      let ch = melhor.s[idxM];
      if (ch === 'ı') ch = 'i';
      melhor.s = melhor.s.slice(0, idxM) + (ch + comb).normalize('NFC') + melhor.s.slice(idxM + 1);
    }
    for (const b of base) b.s = b.s.replace(/ı/g, 'i');
    return base;
  }

  function linhasDaPagina(pg) {
    const itens = comporAcentos(pg.itens.map(i => Object.assign({}, i)));
    itens.sort((a, b) => a.y - b.y || a.x - b.x);
    const linhas = [];
    for (const it of itens) {
      let l = linhas.find(L => Math.abs(L.y - it.y) <= Math.max(2, it.h * 0.3));
      if (!l) { l = { y: it.y, itens: [] }; linhas.push(l); }
      l.itens.push(it);
    }
    const segs = [];
    for (const l of linhas) {
      l.itens.sort((a, b) => a.x - b.x);
      let seg = null, fim = -1e9;
      for (const it of l.itens) {
        const gap = it.x - fim;
        if (!seg || gap > Math.max(14, it.h * 1.6)) {
          seg = { y: l.y, x: it.x, h: it.h, partes: [], fim: 0 };
          segs.push(seg);
        } else if (gap > it.h * 0.15) seg.partes.push(' ');
        seg.partes.push(it.s);
        seg.h = Math.max(seg.h, it.h);
        fim = it.x + it.w;
        seg.fim = fim;
      }
    }
    for (const s of segs) s.txt = s.partes.join('').replace(/[\uE000-\uF8FF\u0000-\u001F\u0002\u0003]/g, '').replace(/\s+/g, ' ').trim();
    return segs;
  }

  // Ordem de leitura: cabeçalho, coluna esquerda, coluna direita
  function textoDaPagina(pg) {
    const segs = linhasDaPagina(pg).filter(s => s.h >= 7.6 && s.y < pg.H - 30);
    const meio = pg.W * 0.47;
    const cab = segs.filter(s => s.y < 22);
    const esq = segs.filter(s => s.y >= 22 && s.x < meio).sort((a, b) => a.y - b.y || a.x - b.x);
    const dir = segs.filter(s => s.y >= 22 && s.x >= meio).sort((a, b) => a.y - b.y || a.x - b.x);
    return { cab, linhas: [...esq, ...dir] };
  }

  /* ---------- 2. Texto -> semanas ---------- */
  const MESES = {
    JANEIRO: 1, FEVEREIRO: 2, 'MARÇO': 3, MARCO: 3, ABRIL: 4, MAIO: 5, JUNHO: 6, JULHO: 7, AGOSTO: 8, SETEMBRO: 9, OUTUBRO: 10, NOVEMBRO: 11, DEZEMBRO: 12,
    ENERO: 1, FEBRERO: 2, MARZO: 3, MAYO: 5, JUNIO: 6, JULIO: 7, SEPTIEMBRE: 9, SETIEMBRE: 9, OCTUBRE: 10, NOVIEMBRE: 11, DICIEMBRE: 12
  };
  const NOMES_MES = {
    pt: ['', 'janeiro', 'fevereiro', 'março', 'abril', 'maio', 'junho', 'julho', 'agosto', 'setembro', 'outubro', 'novembro', 'dezembro'],
    es: ['', 'enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre']
  };
  const RX_MES = Object.keys(MESES).sort((a, b) => b.length - a.length).join('|');
  const RX_CAB = new RegExp('^(\\d{1,2})(?:DE(' + RX_MES + '))?(?:-|–|—|A)(\\d{1,2})\\D{0,3}?DE(' + RX_MES + ')(.*)$');

  const compacto = s => s.normalize('NFC').toUpperCase().replace(/\s+/g, '');

  function tituloLivro(resto) {
    // "JEREMIAS45-46" -> "Jeremias 45-46" ; "JEREMÍAS49,50" -> "Jeremías 49, 50"
    resto = (resto.split('|').map(x => x.trim()).find(x => /\p{Lu}/u.test(x)) || '').replace(/^[^0-9\p{Lu}]+/u, '');
    const m = resto.match(/^(\d?)(\p{Lu}+)(\d.*)?$/u);
    if (!m) return resto;
    const livro = m[2].charAt(0) + m[2].slice(1).toLowerCase();
    const caps = (m[3] || '').replace(/,(?=\d)/g, ', ');
    return ((m[1] ? m[1] + ' ' : '') + livro + ' ' + caps).trim();
  }

  function detectarIdioma(txt) {
    const t = txt.toUpperCase();
    const pt = (t.match(/CÂNTICO|ORAÇÃO|LIÇÃO|COMENTÁRIOS/g) || []).length;
    const es = (t.match(/CANCIÓN|ORACIÓN|LECCIÓN|PALABRAS DE/g) || []).length;
    return es > pt ? 'es' : 'pt';
  }

  const TIT_MINISTERIO = /^(Iniciando conversas|Cultivando o interesse|Fazendo disc[ií]pulos|Explicando suas cren[çc]as|Discurso|O que voc[êe] diria\?|Empiece conversaciones|Empecemos conversaciones|Haga revisitas|Hagamos revisitas|Haga disc[ií]pulos|Hagamos disc[ií]pulos|Explique sus creencias|Expliquemos (?:nuestras|sus) creencias|¿Qué (?:diría usted|dirías|diría)\?)/i;
  const TIT_SEM_ESTUDANTE = /^(O que voc[êe] diria\?|¿Qué (?:diría usted|dirías|diría)\?)/i;

  function analisarSemana(linhas, idioma) {
    const txt = linhas.join('\n');
    const sem = { partes: [] };
    const cantE = [...txt.matchAll(/(?:C[âa]ntico|Canci[óo]n)\s+(\d+)\s+(?:e oração|e ora[çc][ãa]o|y oraci[óo]n)/gi)].map(m => +m[1]);
    sem.cantico_inicial = cantE[0] || null;
    sem.cantico_final = cantE.length > 1 ? cantE[cantE.length - 1] : null;
    const meio = linhas.map(l => l.match(/^(?:C[âa]ntico|Canci[óo]n)\s+(\d+)\s*$/i)).find(Boolean);
    sem.cantico_meio = meio ? +meio[1] : null;

    // Cabeçalhos de partes: "N. Título (X min)" — o título pode quebrar em várias linhas
    const RX_MIN = /\((\d+)\s*mins?\.?\)/i;
    const inicios = [];
    for (let i = 0; i < linhas.length; i++) {
      const m = linhas[i].match(/^(\d{1,2})\.\s*(\S.*)$/);
      if (!m) continue;
      let tit = m[2], j = i, min = null;
      for (let k = 0; k < 3 && j < linhas.length; k++) {
        const mm = (k === 0 ? tit : linhas[j]).match(RX_MIN);
        if (mm) { min = +mm[1]; break; }
        j++;
        if (j < linhas.length) tit += '\n' + linhas[j];
      }
      if (min === null) continue;
      const corte = tit.search(RX_MIN);
      let titulo = tit.slice(0, corte).replace(/-\n/g, '').replace(/\n/g, ' ').trim();
      const resto = tit.slice(corte).replace(RX_MIN, '').trim();
      inicios.push({ i, fimCab: j, n: +m[1], titulo, min, resto });
    }
    // remove duplicados (mesmo número) mantendo o primeiro
    const vistos = new Set();
    const cabs = inicios.filter(c => !vistos.has(c.n) && vistos.add(c.n)).sort((a, b) => a.n - b.n);

    const PARADAS_MAI = /^(TESOUROS|TESOROS|FAÇA SEU|SEAMOS|NOSSA$|NUESTRA$|NOSSA VIDA|NUESTRA VIDA|VIDA CRIST|MEJORES MAESTROS|NO MINIST|DE LA BIBLIA$|PALAVRA DE DEUS)/;
    const PARADAS_MIN = /^(Coment[áa]rios finais|Palabras de conclusi[óo]n|Que joias|¿Qué perlas)/i;
    const PARADAS = { test: l => PARADAS_MAI.test(l) || PARADAS_MIN.test(l) };
    for (const c of cabs) {
      // detalhe: linhas depois do cabeçalho até o próximo cabeçalho ou título de seção
      const prox = inicios.filter(o => o.i > c.fimCab).map(o => o.i);
      const limite = prox.length ? Math.min(...prox) : linhas.length;
      const det = [];
      if (c.resto) det.push(c.resto);
      for (let k = c.fimCab + 1; k < limite; k++) {
        const l = linhas[k];
        if (PARADAS.test(l) || /^(?:C[âa]ntico|Canci[óo]n)\s+\d+/i.test(l)) break;
        det.push(l);
      }
      let detalhe = det.join(' ').replace(/(\p{L})- (\p{Ll})/gu, '$1$2').replace(/\s+/g, ' ').trim();
      const p = { n: c.n, titulo: c.titulo, min: c.min };
      const lic = detalhe.match(/\b(th|lmd)\s+(?:lição|lección|lecci[óo]n)\s+(\d+)(?:\s+(?:ponto|punto)\s+(\d+))?/i);
      if (lic) p.licao = { pub: lic[1].toLowerCase(), n: +lic[2], ponto: lic[3] ? +lic[3] : null, txt: lic[0] };
      const ap = detalhe.match(/lmd\s+ap[êe]ndice\s+A\s+(?:ponto|punto)\s+(\d+)/i);
      if (ap) p.apendiceA = +ap[1];
      if (/Considera[çc][ãa]o|An[áa]lisis con el auditorio/i.test(detalhe)) p.consideracao = true;
      p.detalhe = detalhe;
      sem.partes.push(p);
    }

    // Classificação por seção e tipo
    let secao = 'tesouros';
    for (const p of sem.partes) {
      if (p.n === 1) p.tipo = 'discurso';
      else if (p.n === 2) p.tipo = 'joias';
      else if (p.n === 3) { p.tipo = 'leitura'; const m = p.detalhe.match(/^([^(]+?)\s*\(/); p.passagem = m ? m[1].trim() : p.detalhe; }
      else {
        if (secao !== 'vida' && TIT_MINISTERIO.test(p.titulo)) secao = 'ministerio'; else secao = 'vida';
        if (secao === 'ministerio') {
          if (TIT_SEM_ESTUDANTE.test(p.titulo)) p.tipo = 'consideracao_min';
          else if (/^Discurso/i.test(p.titulo)) p.tipo = 'discurso_estudante';
          else if (/Explicando|Explique|Expliquemos/i.test(p.titulo) && /Discurso/i.test(p.detalhe) && !/Demonstra/i.test(p.detalhe)) p.tipo = 'discurso_estudante';
          else p.tipo = 'estudante';
        } else {
          if (/Estudo b[íi]blico de congrega|Estudio b[íi]blico de la congregaci/i.test(p.titulo)) { p.tipo = 'ebc'; const m = p.detalhe.match(/^[^.]*?\b(\w+ cap\.\s*\d+)/); p.materia = m ? m[1] : p.detalhe; }
          else if (/Necessidades locais|Necesidades de la congregaci/i.test(p.titulo)) p.tipo = 'necessidades';
          else p.tipo = 'consideracao';
        }
      }
      if (p.tipo === 'leitura') p.detalhe = (p.passagem || '') + (p.licao ? ' (' + p.licao.txt + ')' : '');
      if (p.sec !== 'vida') {
        const cena = p.detalhe.match(/\b(DE CASA EM CASA|TESTEMUNHO INFORMAL|TESTEMUNHO PÚBLICO|DE CASA EN CASA|PREDICACIÓN INFORMAL|PREDICACIÓN PÚBLICA)\.?/);
        if (cena) p.cena = cena[1];
        p.texto = p.detalhe
          .replace(cena ? cena[0] : '###', '')
          .replace(/\((?:lmd|th)\s[^)]*\)\.?/gi, '')
          .replace(/^\s*(Considera[çc][ãa]o|An[áa]lisis con el auditorio[^.]*)\.\s*/i, '')
          .replace(/\s+([.,;])/g, '$1').replace(/\s+/g, ' ').trim();
      }
      p.sec = p.n <= 3 ? 'tesouros' : (['estudante', 'discurso_estudante', 'consideracao_min'].includes(p.tipo) ? 'ministerio' : 'vida');
    }
    return sem;
  }

  async function analisarPDF(pdf, nomeArquivo) {
    const paginas = await lerPaginas(pdf);
    let todas = [];
    let ano = null;
    const blocos = []; // {cab, linhas}
    for (const pg of paginas) {
      const { cab, linhas } = textoDaPagina(pg);
      const txtPg = linhas.map(s => s.txt).join(' ');
      const cmp = compacto(txtPg);
      const ma = cmp.match(new RegExp('(?:' + RX_MES + ')(?:-|–|Y|E)(?:' + RX_MES + ')DE(20\\d\\d)'));
      if (!ano && ma) ano = +ma[1];
      blocos.push({ cab: cab.map(c => c.txt), linhas: linhas.map(s => s.txt), pagina: pg.n });
    }
    if (!ano) { const m = (nomeArquivo || '').match(/(20\d\d)(\d\d)/); ano = m ? +m[1] : new Date().getFullYear(); }
    const idioma = detectarIdioma(blocos.map(b => b.linhas.join(' ')).join(' '));

    // Fatiar por cabeçalho de semana
    const semanas = [];
    let atual = null;
    for (const b of blocos) {
      let cabSem = null;
      const cabTxt = compacto(b.cab.join('|'));
      const ic = cabTxt.search(/\d{1,2}(?:DE|-|–|—|A)/);
      if (ic >= 0) cabSem = cabTxt.slice(ic).match(RX_CAB);
      if (cabSem) {
        atual = { m: cabSem, linhas: [], paginas: [b.pagina] };
        semanas.push(atual);
      } else if (atual) atual.paginas.push(b.pagina);
      if (atual) atual.linhas.push(...b.linhas);
    }

    const out = [];
    let mesAnterior = null, anoCorr = ano;
    for (const s of semanas) {
      const [, d1, mes1, d2, mes2, resto] = s.m;
      const m2 = MESES[mes2], m1 = mes1 ? MESES[mes1] : m2;
      if (mesAnterior && m1 < mesAnterior) anoCorr++;
      mesAnterior = m2;
      const ini = new Date(Date.UTC(anoCorr, m1 - 1, +d1));
      const sem = analisarSemana(s.linhas, idioma);
      if (!sem.partes.some(p => p.n === 1) || sem.partes.length < 5) continue;
      sem.idioma = idioma;
      sem.inicio = ini.toISOString().slice(0, 10);
      sem.id = idioma + '-' + sem.inicio;
      const nm = NOMES_MES[idioma];
      sem.periodo = mes1 && m1 !== m2
        ? `${+d1} de ${nm[m1]} ${idioma === 'es' ? 'a' : '–'} ${+d2} de ${nm[m2]}`
        : `${+d1}–${+d2} de ${nm[m2]}`;
      sem.leitura = tituloLivro(resto);
      sem.origem = nomeArquivo || '';
      out.push(sem);
    }
    // "próxima semana" para os comentários finais
    for (let i = 0; i < out.length; i++) {
      const p = out[i + 1];
      if (p) out[i].proxima = resumoProxima(p);
    }
    return { idioma, ano, semanas: out };
  }

  function resumoProxima(s) {
    const t1 = s.partes.find(p => p.n === 1);
    const vida = s.partes.filter(p => p.sec === 'vida' && p.tipo !== 'ebc').map(p => p.titulo);
    const ebc = s.partes.find(p => p.tipo === 'ebc');
    return { periodo: s.periodo, leitura: s.leitura, tema: t1 ? t1.titulo : '', vida, ebc: ebc ? ebc.materia : '' };
  }

  const api = { analisarPDF, lerPaginas, textoDaPagina, analisarSemana, comporAcentos };
  if (typeof module !== 'undefined' && module.exports) module.exports = api; else root.LeitorApostila = api;
})(typeof window !== 'undefined' ? window : globalThis);
