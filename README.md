# Roteiro do Presidente — aplicativo Android (Kotlin + Jetpack Compose)

Aplicativo nativo para tablet Android que conduz a reunião Vida e Ministério em português e espanhol:
roteiro do presidente com falas sugeridas, designações, cronômetro (modo Presidir), elogios e conselhos
com os pontos das lições (th e lmd), visita do superintendente de circuito e salas adicionais. Funciona sem internet.

## Gerar o APK
Cada envio para a branch `main` roda os testes, compila o APK assinado e publica em **Releases**
(workflow `.github/workflows/gerar-apk.yml`). Para compilar de novo: **Actions → Gerar APK → Run workflow**.
No Android Studio: abra a pasta e use **Build → Build APK(s)**.

## Arquivos que o app importa (Configurações → Arquivos)
| Arquivo | Formato | O que o app usa |
|---|---|---|
| Apostila Vida e Ministério (PT/ES) | PDF | semanas, partes, tempos, lições, cânticos |
| Melhore Sua Leitura e Seu Ensino (th) | PDF | títulos oficiais das lições e dos pontos |
| Ame as Pessoas — Faça Discípulos (lmd) | PDF | títulos oficiais das lições e dos pontos |
| Cante de Coração para Jeová (sjj) | .jwpub | número e título dos cânticos (índice aberto do arquivo) |

O app não lê o texto protegido dos arquivos .jwpub, apenas o índice.

## Código
| Pasta | Conteúdo |
|---|---|
| `app/src/main/java/.../nucleo` | Kotlin puro: leitor da apostila e das brochuras, montagem do roteiro, textos PT/ES |
| `app/src/main/java/.../dados` | armazenamento (JSON), importação (PDFBox-Android, .jwpub), impressão, migração da versão 1 |
| `app/src/main/java/.../ui` | telas em Jetpack Compose |
| `app/src/main/assets` | semanas incluídas (`semanas.json`) e resumo das lições (`licoes.json`) |
| `web/` | versão HTML (navegador); `node web/build.js` gera `roteiro_presidente.html` |

## Chave de assinatura — IMPORTANTE
O APK é assinado com `keystore/roteiro-presidente.jks` (senha em `keystore/senha.txt`). Atualizações só
instalam por cima com a mesma chave. Guarde uma cópia e mantenha o repositório **privado**.

## Dados
Ficam no aparelho (`dados.json`). Cópia de segurança: Configurações → Exportar/Importar dados
(formato compatível com a versão HTML). Ao atualizar da versão 1.0, os dados são recuperados automaticamente.
