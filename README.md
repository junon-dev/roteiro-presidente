# Roteiro do Presidente — aplicativo Android

Aplicativo para tablet Android que conduz a reunião Vida e Ministério em português e espanhol.
Ele mostra o roteiro do presidente, as designações, o cronômetro e os pontos das lições (th e lmd)
nos elogios e conselhos. Funciona sem internet.

## Como gerar o APK

**Opção 1 — GitHub (automático):** cada envio para a branch `main` compila o APK
(workflow `.github/workflows/gerar-apk.yml`) e publica o arquivo na página **Releases** do repositório.
Abra essa página no tablet, baixe o `.apk` e toque nele para instalar.
Para compilar de novo sem alterar nada: aba **Actions → Gerar APK → Run workflow**.

**Opção 2 — Android Studio:** abra esta pasta no Android Studio e use
**Build → Build App Bundle(s) / APK(s) → Build APK(s)**. O arquivo fica em `app/build/outputs/apk/`.

**Opção 3 — linha de comando** (com JDK 17 e Android SDK instalados): `./gradlew assembleRelease`.

## Instalar no tablet

1. Copie ou baixe o arquivo `.apk` no tablet.
2. Toque nele. Na primeira vez, o Android pede para **permitir instalar apps desta fonte** (navegador ou gerenciador de arquivos): permita.
3. Para atualizar, instale o APK novo por cima. Os nomes, anotações e apostilas importadas continuam salvos.

## Chave de assinatura — IMPORTANTE

O APK é assinado com `keystore/roteiro-presidente.jks`, e a senha está em `keystore/senha.txt`.
O Android só instala uma atualização por cima se ela tiver **a mesma assinatura**. Se a chave se perder,
será preciso desinstalar o app, e os dados do tablet se perdem (faça antes **Configurações → Exportar dados**).
Guarde uma cópia da pasta `keystore` e mantenha este repositório **privado**.

## Atualizar o conteúdo do app

O app em HTML fica em `web/`:

| Arquivo | Conteúdo |
|---|---|
| `web/app_src.html` | telas, textos PT/ES, roteiro, cronômetro |
| `web/parser.js` | leitor da apostila em PDF |
| `web/licoes.js` | resumo dos pontos das lições th e lmd |
| `web/semanas.json` | semanas que já vêm incluídas no app |

Depois de alterar, rode `node web/build.js`. Ele gera `app/src/main/assets/www/index.html`
(usado pelo app) e `roteiro_presidente.html` (versão para abrir no navegador).
Aumente `versionCode` e `versionName` em `app/build.gradle` antes de gerar uma nova versão.

## Componentes de terceiros

- pdf.js 3.11.174 (Mozilla, licença Apache 2.0) em `app/src/main/assets/www/pdfjs/`.
- AndroidX WebKit (licença Apache 2.0).
