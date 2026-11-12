# HBX sobre CleverKeys — migração experimental

**Status:** implementação inicial em branch isolada. **Não é um APK pronto.**
Nenhuma compilação, instalação, teste em aparelho ou assinatura foi feita.
Não publicar como release nem substituir o aplicativo HeliBoard em uso.

## Base

- CleverKeys: `tribixbite/CleverKeys`, revisão
  `fdd949486fb38bd73991c8b8da850be06da9b95b`.
- Código HBX reaproveitado de `heliboard/ai/java` do mesmo repositório.
- Modelo nativo GGUF: llama.cpp `0ec4027a2a0183de87c007069adddc06e665f7de`.
- Licenças: GPL-3.0 para CleverKeys/HeliBoard e avisos de dependências
  preservados. Não copiar marca ou arte proprietária Samsung.
- **Identificador do teste:** `br.com.hbx.clever.preview`, separado da
  instalação atual `br.com.tecladobr.heli.debug` (não atualiza nem apaga
  a versão antiga).
- Android 8+, ARM64; variante x86_64 para emulador.

## O que já está preparado no código

- Novo motor de digitação CleverKeys com autocorreção e gestos locais, seu
  dicionário português e sua prancheta/emoji nativos.
- Layout PT-BR com tecla **ç** e tema familiar `look_samsung`, sem tocar no
  algoritmo ONNX de digitação. Visual aproximado One UI / S25 Ultra, ainda
  dependente de comparação de capturas em celular.
- Barra acima das sugestões: **IA → Prancheta → Emoji → Traduzir →
  Configurações**. O teclado oculta a barra em campos de senha.
- Tela de IA acionada explicitamente, com ortografia, tradução, resumo,
  reescrita e compositor; modo Automático: **Gemini 3.6 → OpenRouter → GGUF**.
  O Groq não participa do encaminhamento online.
- Segredos Gemini/OpenRouter guardados em arquivos independentes protegidos
  pelo Android Keystore. Testes individuais usam frases fictícias.
- Posições GGUF: **Gemma E2B, Qwen 3.5 2B e Gemma E4B**, com importação e
  medição offline; o APK não inclui os pesos.
- Mecanismo seguro de substituição: quando iniciado via
  `android.intent.action.PROCESS_TEXT`, o resultado volta ao CleverKeys e
  pode ser aplicado somente se o editor ainda corresponde à seleção original.
  **Quando iniciado diretamente da barra**, só oferece cópia (não força
  substituição em outro aplicativo).
- A voz offline do próprio CleverKeys continua independente do HBX.

## Ainda faltam validar/adaptar

1. Compilar Kotlin/Java e o JNI `libtecladoai.so` contra o upstream pinado;
   resolver erros de dependência e testes de regressão.
2. Comparar o layout **de verdade** no Nothing Phone (1) e no S25 Ultra;
   ajustar alturas, espaçamento, símbolos, cores, rolagem e acessibilidade.
3. Certificar a correção neural em português com frases e gestos reais,
   sem degradar as sugestões do CleverKeys.
4. Validar 3.6, OpenRouter e 3 GGUFs com chaves e pesos **somente no celular**.
5. Migrar dicionário/atalhos/backup com ferramentas oficiais de exportação.
   **Chaves Android Keystore não podem ser copiadas de um pacote para outro**:
   devem ser recadastradas na versão final de testes.
6. O **atualizador integrado do preview** já foi portado em código.
   Ele verifica o SHA-256, pacote Android, versionCode, certificado e domínio
   antes de abrir o instalador Android. A tela de configurações contém o botão
   **Verificar e instalar atualização**. O canal permanece propositalmente
   DESATIVADO até a publicação de um APK inicialmente compilado, assinado e
   validado. O atualizador do HeliBoard NÃO pode instalar este pacote distinto.
   Na migração final, conferir assinatura legada e implementar o canal estável.
7. Assinar a primeira versão de testes e verificar o APK antes de divulgar
   link de instalação. Não basta publicar código.

## Atualização dentro do próprio teclado

Existem **dois aplicativos diferentes** neste estágio:

1. `br.com.tecladobr.heli.debug`: HBX com motor HeliBoard. O atualizador
   oficial permanece apontando para `main/update.json`, hoje com a
   beta 17 assinada. Ele não pode instalar o novo pacote CleverKeys.
2. `br.com.hbx.clever.preview`: HBX experimental com motor CleverKeys,
   que poderá usar `main/cleverkeys/update.json` após a primeira versão
   assinada. O arquivo inicialmente é `enabled: false`; portanto
   **não há uma atualização de CleverKeys disponível para instalar agora**.

A atualização pelo próprio app não substitui o primeiro processo de
instalação: o Android não permite atualizar um pacote para outro diferente.
Durante o piloto, os dois coexistem. O usuário escolhe manualmente qual
teclado está ativo no Android e pode voltar ao HeliBoard sem perder dados.

A promoção futura para atualização **por cima do HBX atual** exige:
- pacote `br.com.tecladobr.heli.debug` exatamente igual ao instalado;
- certificado SHA-256 **igual ao da beta17 publicada**;
- versionCode superior ao instalado (sem reverter versionCode);
- verificação por instalação de teste e backup de dicionário/configurações;
- preservação do diretório privado `offline-ai`, das preferências e das
  chaves Android Keystore; sem forçar cópia entre aplicativos.

O preview possui `HbxUpdateActivity`, `HbxUpdatePolicy` e os testes
`HbxUpdatePolicyTest`. O pacote aceita apenas releases com padrão
`hbx-clever-vX.Y.Z` hospedadas no GitHub oficial; nenhuma URL fornecida
remotamente pode mudar o destino de instalação ou ignorar o certificado.
O script `cleverkeys-port/make-update-manifest.py` exige um APK real já
assinado, verifica o certificado por `apksigner`, e gera os metadados a
serem publicados **somente após testes reais no aparelho**. Nunca inclua
keystores privadas, senhas ou chaves de IA no repositório.

## Compilação sem GitHub Actions

É possível usar ambiente Android/Termux **equipado com JDK 17, Android SDK 34,
NDK 28, CMake e Ninja**. O CleverKeys inclui suporte de build no Termux,
mas a integração do llama.cpp aumenta memória, disco e tempo de compilação
e pode exigir ajustes de ambiente. Não foi executada aqui.

```bash
# Clone do projeto com os arquivos do HBX.
git clone -b feat/hbx-cleverkeys-migration-20261112 \
  https://github.com/helbermoura2/teclado-br-publico.git hbx
git clone https://github.com/tribixbite/CleverKeys.git cleverkeys-src
git -C cleverkeys-src checkout fdd949486fb38bd73991c8b8da850be06da9b95b
python3 hbx/cleverkeys-port/prepare.py cleverkeys-src
git clone https://github.com/ggml-org/llama.cpp.git cleverkeys-src/hbx-native/llama.cpp
git -C cleverkeys-src/hbx-native/llama.cpp checkout 0ec4027a2a0183de87c007069adddc06e665f7de
bash hbx/cleverkeys-port/build-native.sh cleverkeys-src
cd cleverkeys-src
./scripts/gradle-guard.sh assembleDebug
```

A saída é um **APK de depuração de pacote separado**, não uma atualização
instalável por cima do HBX atual. Não instalar APKs com origem ou assinatura
desconhecida. Não publicar chaves, dados de digitação ou textos pessoais.

## Privacidade

O CleverKeys original não solicita Internet. **Este derivado HBX solicita
INTERNET e ACCESS_NETWORK_STATE apenas para ações de IA online iniciadas
pelo usuário**. O motor de gestos, a autocorreção, o ditado local e o modelo
GGUF não usam a rede. O modo Online pode enviar somente o texto selecionado/
fornecido ao Google ou ao OpenRouter e seus provedores, sujeitos a políticas,
cotas e eventuais custos. Nenhum segredo é incluído em APK, GitHub ou logs.

## Critérios de aceite para chamar de migração concluída

- Teclado normal abre sem crash, sem rede e com PT-BR/ç e swipe.
- Cinco ações da barra funcionam e ficam ocultas em campos de senha.
- IA offline opera sem Internet; online é opt-in com fallback testado.
- Importação Qwen/E4B não sobrescreve o arquivo Gemma E2B.
- Reproduzir 20 frases em PT-BR, números, negações e links sem alterações
  indevidas, e comparar desempenho e RAM no aparelho.
- Atualização validada com assinatura compatível, sem perder dicionários.
- Sem degradar recursos de CleverKeys (prancheta, voz, símbolos e temas).
- APK assinado e confirmado com resultado de build + testes reais.

A versão antiga permanece disponível e é a opção segura até cumprir esses
critérios.
