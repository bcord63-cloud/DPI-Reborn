# Preparação para uma versão iOS

Hoje só existe o app Android. A tela inicial mostra o aviso "Em breve" (texto em `strings.xml`, `ios_notice`). Nada aqui converte o APK em app de iPhone: a versão iOS será um projeto independente (Swift/SwiftUI).

O que já está separado para facilitar isso:

- **Protocolos de envio** documentados no README (RPI na porta 12800, payload GoldHEN, manifesto JSON e servidor HTTP com Range) podem ser reimplementados em qualquer linguagem.
- **Camadas do Android**: `data/`, `net/` e `transfer/` (rede e envio) não dependem de Compose; só `ui/` é específico.

Limites a avaliar antes de começar: a Apple restringe HTTP sem TLS (App Transport Security) e servidores em segundo plano, e distribuição fora da App Store exige condições específicas. Isso não foi analisado a fundo aqui.
