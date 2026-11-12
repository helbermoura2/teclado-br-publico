# HBX · Helber Board X AI — beta 19 de testes

## O que muda

- Ícone do aplicativo substituído pela imagem **HBX** enviada pelo usuário, na
  área de launcher do Android. O layout das teclas e a identificação dos botões
  internos do HeliBoard não mudam.
- **Online, ordem fixa:** Gemini 3.6 Flash → OpenRouter. O Groq **não é
  chamado**; chave Groq anterior continua no Android Keystore, porém inativa.
- OpenRouter: `openrouter/free` (roteamento variável entre modelos gratuitos)
  ou `qwen/qwen3-32b:free` quando disponível. O modelo efetivamente usado
  aparece no resultado. Cada solicitação explícita pode contar na cota diária
  gratuita, mesmo com erro.
- **Offline:** três posições separadas para Gemma atual (sem migração de arquivo),
  Qwen 3.5 2B e Gemma 4 E4B. É possível importar e selecionar os modelos,
  comparar duração, memória aproximada e resultado da frase sintética.
- Importar outro modelo não remove automaticamente o antigo. GGUFs, dicionário
  e chaves ficam fora do APK e não são enviados para a nuvem.
- Atualizador, modo Off-line e seleção de tarefas continuam com a UI existente.

## Como testar no telefone

1. Instale a beta somente após o GitHub Actions aprovar a compilação e o
   certificado da versão anterior. Não desinstale o teclado existente.
2. Em Assistente → Configurações, salve sua chave de
   https://openrouter.ai/settings/keys. Nunca forneça chaves pelo chat,
   GitHub ou em screenshots. Escolha o modelo `openrouter/free` para começar.
3. Toque em **Testar conexão OpenRouter** (frase fictícia). Se a chave for
   válida, escolha **Automático** e salve o modo.
4. Escolha a posição **Qwen 3.5 2B**, baixe o arquivo GGUF Q4_K_M
   (~1,4 GB), importe na posição selecionada e use
   **Medir somente GGUF ativo** para avaliar a mesma frase.
5. Opcionalmente, faça o mesmo para **Gemma 4 E4B Q4_0** (~4,6 GB).
   Teste experimental: pode falhar por memória, ficar lento ou esquentar
   no Nothing Phone (1). Não é recomendado substituir o Gemma atual antes
   de confirmar a execução no aparelho.
6. Volte para **Gemma 4 E2B · modelo atual** com **Ativar modelo selecionado**
   quando desejar. A posição anterior é preservada.

## Limites

Testes JVM sintéticos validam rotas, bloqueio de endpoints arbitrários e nomes
de posições; **não** certificam API OpenRouter com chave real nem velocidade
ou qualidade dos modelos GGUF no Nothing Phone (1). Esses testes devem ser
executados no celular.

A IA não envia texto ao servidor durante digitação normal; apenas tarefas
acionadas pelo usuário. O uso de `openrouter/free` pode transferir texto a
provedores variáveis, de acordo com as políticas do OpenRouter e do provedor
selecionado. A configuração não ativa pagamentos e não permite um modelo pago.

## Links dos modelos

- Qwen3.5 2B Q4_K_M:
  https://huggingface.co/bartowski/Qwen_Qwen3.5-2B-GGUF
- Gemma 4 E4B Q4_0:
  https://huggingface.co/ggml-org/gemma-4-E4B-it-GGUF
- Gemma 4 E2B Q4_0:
  https://huggingface.co/ggml-org/gemma-4-E2B-it-GGUF

## Segurança

O aplicativo verifica SHA-256, pacote e assinatura do APK antes de instalar
uma atualização. As chaves Gemini, OpenRouter e Groq usam arquivos distintos
cifrados pelo Android Keystore. Nenhuma senha entra no repositório nem em
testes CI. Proteções criptográficas locais não substituem proxy backend em
produto público; esta beta destina-se a um uso pessoal de teste.
