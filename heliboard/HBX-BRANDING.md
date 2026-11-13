# HBX Keyboard — identidade visual no HeliBoard

Este overlay conserva **a base HeliBoard**, o identificador do aplicativo
`br.com.tecladobr.heli.debug`, as configurações e os componentes de IA
e atualização atuais. O experimento CleverKeys foi descontinuado.

## O que muda

- Nome do aplicativo e serviço de teclado no Android: **HBX Keyboard**,
  usando o recurso `english_ime_name` em todos os conjuntos de tradução
  e nas variantes de depuração.
- Nome do corretor e das configurações nos recursos de depuração:
  `HBX Keyboard - Corretor ortográfico` e
  `HBX Keyboard - Configurações`.
- Arte original HBX em `overlay/hbx_brand.webp`, preparada para
  `res/drawable-nodpi/hbx_brand.webp`.
- Ícones adaptativos normal e redondo apontando para essa mesma arte,
  com fundo preto e sem herdar o ícone monocromático HeliBoard.
- O pacote, o versionCode e a versão 0.9.2-beta.18 da branch pública
  **não foram alterados por esta mudança**.

## Publicação e segurança

**Alterar o nome/ícone no GitHub não atualiza o aplicativo instalado.**
O novo APK ainda precisa passar pelos testes Android e ser assinado com
**o mesmo certificado** da versão instalada. O atualizador integrado
só pode oferecer a versão após a publicação de um APK assinado válido e
de um manifesto atualizado. Nunca instalar por cima um APK com assinatura
de debug descartável, pois isso falha e pode confundir o usuário.

Não substituir o HeliBoard nem ativar o atualizador até a assinatura
original e os testes reais serem confirmados.
