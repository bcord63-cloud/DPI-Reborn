# Catálogo de apps do DPI Reborn

## Como funciona

O app **não** guarda versões, tamanhos nem links de arquivos no código Kotlin. Ele lê um `catalog.json` e confere cada fonte ao vivo.

1. **Lista de itens** (`catalog.json`): ordem de busca: cópia baixada de
   `https://raw.githubusercontent.com/bcord63-cloud/DPI-Reborn/main/catalog/catalog.json` → última cópia salva no aparelho → cópia embutida (`app/src/main/assets/catalog.json`).
   Para cadastrar, remover ou recategorizar um app basta editar `catalog/catalog.json` no GitHub. Não precisa gerar outro APK.
2. **Verificação ao vivo** (ao abrir a aba Apps, no máximo a cada 6 h, ou no botão atualizar):
   - Item com `"github": "dono/repo"`: o app chama `https://api.github.com/repos/dono/repo/releases/latest` e lê versão, data, quem publicou, arquivo (primeiro asset que casa com `assetRegex`), tamanho, link e `digest` SHA-256 (quando o GitHub informa).
   - Item sem `github`: é uma página oficial; o app faz um pedido HTTPS e mostra "Fonte online" ou "Fonte indisponível".
3. **Download**: só para itens com arquivo verificado. Usa o `DownloadManager` do Android (HTTPS), mostra progresso, velocidade e bytes recebidos, permite cancelar e, se a fonte publicou SHA-256, confere o arquivo no fim (se não bater, apaga).

## Campos de um item

| campo | obrigatório | exemplo |
|---|---|---|
| `id` | sim | `"newpipe"` (único) |
| `name`, `description`, `developer` | sim | |
| `category` | sim | Streaming, Emuladores, Homebrew, Utilitários, Multimídia, Navegadores, Ferramentas para PS4, Aplicativos diversos |
| `platforms` | sim | `["Android"]`, `["PS4"]`, `["PS4","PC"]` |
| `sourceUrl` | sim | página oficial (HTTPS obrigatório) |
| `github` | não | `"TeamNewPipe/NewPipe"` |
| `assetRegex` | não | `"(?i)\\.apk$"` |
| `license`, `added` | não | `"GPL-3.0"`, `"2026-11-24"` |
| `removed` | não | `true` tira o item do app |

## Regras de segurança

- Só HTTPS, só com nome de domínio (sem IP, `localhost` ou usuário/senha na URL). Item com `sourceUrl` inválida é ignorado.
- Links de download do GitHub só são aceitos se o host for `github.com` ou `*.githubusercontent.com`.
- Nada é aberto ou instalado automaticamente; o app só entrega o arquivo na pasta de downloads do app. PKG baixado pode ser enviado ao PS4 pelo mesmo mecanismo da Biblioteca.
- Estar no GitHub não prova que o arquivo é confiável. A tela mostra quem publicou, a data e o hash (se houver) para você conferir.
- Jogos comerciais, cracks e apps pagos pirateados não entram no catálogo.

## O que NÃO foi verificado

- Os itens do catálogo inicial foram escolhidos por serem projetos/páginas oficiais conhecidos; o app confirma a existência na hora. Se um repositório não tiver release com arquivo compatível, o app mostra só a página oficial (e diz isso). Não afirmo que cada um terá arquivo baixável.
- **PKG-Zone**: o site `pkg-zone.com` aparece em buscas como loja comunitária de homebrew/emuladores (HB-Store) e o app só linka a página (sem API pública de catálogo conhecida). Ele não é espelhado nem baixado pelo app.
- A API do GitHub sem login permite 60 consultas por hora por IP. Com ~10 repositórios, isso basta; se estourar, o app mostra a última verificação salva.
- Streaming (YouTube, Netflix, Kick): só links oficiais/Google Play. O app não baixa APK deles.
