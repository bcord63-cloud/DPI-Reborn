# Avisos de terceiros

## DirectPackageInstaller (payload de instalação)

O arquivo `app/src/main/assets/dpi_payload.bin` é o payload compilado do projeto
**DirectPackageInstaller** de marcussacana (https://github.com/marcussacana/DirectPackageInstaller),
licenciado sob a **GNU GPL v3** (texto completo em `LICENSE` e `licenses/DirectPackageInstaller-GPL-3.0.txt`).
O código fonte do payload está na pasta `Payload/` desse repositório.

O protocolo usado pelo app para falar com esse payload (injeção pela porta 9090/9021/9020 do
servidor de payloads do GoldHEN, conexão reversa, pacote de comando e manifesto JSON) foi
reimplementado em Kotlin a partir da leitura do código fonte do DirectPackageInstaller.

Por embutir esse payload, o DPI Reborn como um todo deve ser distribuído sob a GPL v3: quem
receber o APK tem direito ao código fonte completo deste repositório.
