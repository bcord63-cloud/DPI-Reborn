# DPI Reborn

Aplicativo Android (Kotlin + Jetpack Compose) para organizar PKG de PS4 e enviá-los ao console pela rede local.

## O que ele faz

- **Biblioteca**: escolhe pastas (Storage Access Framework) ou procura em **todo o celular**, acha arquivos `.pkg`, lê título, ID, versão e capa (`icon0.png`) direto do cabeçalho do PKG. O Android 11+ não deixa o seletor de pastas escolher a pasta Download nem a raiz do armazenamento (regra do sistema, vale para todos os apps); por isso a busca em todo o celular usa a permissão "Acesso a todos os arquivos" (`MANAGE_EXTERNAL_STORAGE`), que o usuário liga na tela de ajustes do Android. Com ela ativa, a Biblioteca e o Gerenciador mostram só os `.pkg`.
- **Console**: cadastra PS4 por IP, escolhe o método de envio, testa a conexão e salva.
- **Envios**: sobe um servidor HTTP no celular (com suporte a `HEAD` e `Range`) e pede ao PS4 para baixar o PKG dele. Mostra progresso real do que o PS4 já leu, velocidade, tempo restante e histórico.
- **Arquivos**: pesquisa, filtra, ordena, move e exclui (com confirmação) os arquivos das pastas da biblioteca.
- **Ajustes**: cor de destaque, grade/lista, porta do servidor, armazenamento, cache e informações do app.

## Métodos de envio (os mesmos do DirectPackageInstaller)

Escolhidos por console: **Auto**, **RPI** ou **GoldHEN**. No Auto, o app usa o RPI se ele responder e, se não, o payload.

### RPI: API do Remote Package Installer (porta 12800)

Precisa do app Remote Package Installer aberto na tela do PS4.

- Detecção: `GET http://IP:12800/api` responde "Unsupported method" e "fail".
- Instalação: `POST /api/install` com `{"type":"direct","packages":["http://IP:PORTA/arquivo.pkg"]}`; sucesso quando a resposta contém `"success"`.
- Acompanhamento: `/api/get_task_progress` e `/api/stop_task` com `{"task_id":N}`.

### GoldHEN: payload do DirectPackageInstaller (sem app no PS4)

Precisa do servidor de payloads do GoldHEN ativo (porta 9090). Protocolo lido do código fonte do DirectPackageInstaller:

1. Detecção: `GET http://IP:9090/status` contém `"status":"ready"`.
2. O app abre um servidor TCP no celular e envia ao PS4 (portas 9090, 9021 ou 9020) o `dpi_payload.bin`, com o IP e a porta do celular gravados no marcador de 6 bytes `0xB4`.
3. O payload roda no PS4, conecta de volta no celular e recebe: comando (1 = novo pacote, 0 = sair), URL do manifesto, nome, Content ID, tipo (`PS4` + categoria do PKG, ex. `PS4GD`), tamanho e ícone PNG.
4. O PS4 baixa o manifesto JSON `http://IP:PORTA/json/ID.json` (`originalFileSize`, `packageDigest`, `numberOfSplitFiles`, `pieces[]`) e depois o PKG, e registra a instalação no serviço de downloads (BGFT).
5. PKG maiores que 4 GiB são divididos em peças de até 4 GiB no manifesto.

Erros do payload aparecem como notificação "DPI: ..." na tela do PS4.

## Limites e coisas não verificadas

- **Nada disso foi testado em um PS4 real.** O app compila no GitHub Actions, mas os dois métodos dependem de comportamento do console que só o primeiro teste confirma.
- O `packageDigest` do manifesto vem dos 32 bytes em `0xFE0` do cabeçalho do PKG (campo `pkg_digest` na documentação do formato). Isso foi deduzido; o DirectPackageInstaller usa o `HeaderDigest` da biblioteca LibOrbisPkg.
- O `dpi_payload.bin` é o binário que acompanha o código do DirectPackageInstaller. O código C dele (`Payload/main.c`, `Payload/info.c`) foi lido e os textos do binário conferem, mas o código de máquina não foi auditado.
- O app não baixa jogos, e o envio só funciona com PKG que você já tem.

## Licença

Por embutir o payload do DirectPackageInstaller (GPL-3.0), este projeto é distribuído sob a **GNU GPL v3**. Veja `LICENSE` e `NOTICE.md`. Se você repassar o APK a outras pessoas, elas têm direito ao código fonte completo deste repositório.

## Como compilar (sem computador)

1. Envie `dpi-reborn.zip` para a **raiz** do repositório (site do GitHub → Add file → Upload files, sem extrair).
2. O arquivo `.github/workflows/build-apk.yml` precisa existir no repositório (Add file → Create new file).
3. A aba **Actions** roda o build. Ele extrai o zip, compila e publica o APK.
4. Baixe em **Releases → DPI Reborn (build de teste mais recente)** ou em **Actions → run → Artifacts**.

O APK é de debug (assinado com a chave de debug). Para instalar, permita "instalar apps desconhecidos" para o navegador.

## Requisitos de build

JDK 17, Gradle 8.10.2, Android Gradle Plugin 8.7.3, Kotlin 2.0.21, compileSdk 35, minSdk 26.
