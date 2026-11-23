# DPI Reborn

Aplicativo Android (Kotlin + Jetpack Compose) para organizar PKG de PS4 e enviá-los ao console pela rede local.

## O que ele faz

- **Biblioteca**: escolhe pastas (Storage Access Framework, sem permissão ampla de armazenamento), acha arquivos `.pkg`, lê título, ID, versão e capa (`icon0.png`) direto do cabeçalho do PKG.
- **Console**: cadastra PS4 por IP/porta, testa a conexão e salva.
- **Envios**: sobe um servidor HTTP no celular (com suporte a `HEAD` e `Range`) e pede ao PS4 para baixar o PKG dele. Mostra progresso real do que o PS4 já leu, velocidade, tempo restante e histórico.
- **Arquivos**: pesquisa, filtra, ordena, move e exclui (com confirmação) os arquivos das pastas da biblioteca.
- **Ajustes**: cor de destaque, grade/lista, porta do servidor, armazenamento, cache e informações do app.

## Compatibilidade com o PS4 (o que foi confirmado)

O envio usa a API HTTP do instalador remoto (Remote Package Installer / DPI v2 / etaHEN DPIv2), que escuta na porta **12800** do PS4:

- `POST /api/install` com `{"type":"direct","packages":["http://IP:PORTA/arquivo.pkg"]}`
- `POST /api/get_task_progress` e `/api/stop_task` com `{"task_id":N}`

O PS4 precisa ter homebrew habilitado e o app instalador aberto na tela. O formato exato das respostas não é documentado publicamente, então o app mostra o texto bruto devolvido pelo PS4 e só interpreta o campo `task_id`.

O app **não** instala PKG sozinho, **não** baixa jogos e **não** inventa APIs do GoldHEN. A porta 9090 (servidor de payloads do GoldHEN) só é verificada no teste de conexão, para informação.

## Como compilar (sem computador)

1. Envie `dpi-reborn.zip` para a raiz do repositório (site do GitHub → Add file → Upload files).
2. Crie o arquivo `.github/workflows/build-apk.yml` (Add file → Create new file) com o conteúdo do workflow.
3. A aba **Actions** roda o build. Ele extrai o zip, compila e publica o APK.
4. Baixe em **Releases → DPI Reborn (build de teste mais recente)** ou em **Actions → run → Artifacts**.

O APK é de debug (assinado com a chave de debug). Para instalar, permita "instalar apps desconhecidos" para o navegador.

## Requisitos de build

JDK 17, Gradle 8.10.2, Android Gradle Plugin 8.7.3, Kotlin 2.0.21, compileSdk 35, minSdk 26.
