# HBX AnySoftKeyboard — teste PT-BR de digitação e pontuação

**Experimento independente.** Esta branch parte do repositório público
`teclado-br-publico/main`, **não** do código CleverKeys. O build não altera
nem o HBX/HeliBoard instalado nem o canal `main/update.json`.

## Por que trocar a base

O CleverKeys experimental tinha atalhos demais e dificultava o uso dos
caracteres de pontuação que o usuário já dominava no HBX. Em vez de continuar
remendando essa base, o primeiro teste com AnySoft se concentra em **digitar
português brasileiro normalmente**.

## Ajustes reais desta prévia

- Upstream oficial `AnySoftKeyboard/AnySoftKeyboard` em commit pinado;
  não copia código de apps proprietários da Samsung.
- Motor de entrada nativo AnySoftKeyboard, dicionários, gestos e sugestões.
- Pacote brasileiro oficial embutido no APK (antes era um add-on externo).
- Seleção inicial do **QWERTY brasileiro com Ç dedicada** (2ª fileira).
- Barra de teclas numéricas/navegação extra desabilitada por padrão.
- Fileira inferior padrão com **?, vírgula, ponto, apóstrofo e espaço**.
  Pontuação adicional: **! / @** ao segurar ?, e **; : -** ao segurar ponto.
- Ícone HBX fornecido pelo proprietário e nome HBX AnySoft (Teste).
- Identificador `br.com.hbx.anysoft.preview`, separado de HeliBoard e
  CleverKeys. Não há migração automática de dados, senhas ou modelos.
- Acesso à rede não é adicionado: esta **primeira prévia não tem integração
  Gemini, OpenRouter, Groq ou GGUF**. O foco é a usabilidade da digitação.

## O que não deve ser confundido com uma versão final

A proposta **não** afirma reproduzir pixel a pixel o teclado S25 Ultra.
Não inclui o assistente IA e nem o atualizador assinado do HBX original,
até que a nova distribuição de teclas seja aprovada. O aplicativo antigo
continua disponível para voltar em segundos.

## Critérios de avaliação no celular

Use o aplicativo de notas para testar:

- `São Paulo, 12 de novembro.` — Ç, acento, vírgula e ponto.
- `Você vem amanhã? Sim! Às 15h.` — ?, !, maiúsculas e acentos.
- `Não quero atalhos extras; quero escrever: rápido, fácil.`
- Digite nomes, links, e-mails e números para verificar que o AnySoft
  oferece a fileira específica adequada a cada campo.
- Confira a correção de português e a existência de tecla Ç visível.
- Confira se o teclado ocupa altura razoável e permite uso com uma mão.
- Confira prancheta e símbolos sem interferir no campo principal.

Não avalie sua substituição definitiva antes de revisar essas tarefas.
A versão HBX+AnySoft deve ser instalada como **aplicativo separado**.
Nunca desinstale o HeliBoard atual como forma de resolver falhas.

## Próxima etapa se for aprovado

1. Integrar IA acionada manualmente: Gemini + OpenRouter + GGUF local,
   preservando as chaves no Android Keystore e privacidade.
2. Criar barra discreta com IA, prancheta, emojis, tradutor e ajustes,
   sem substituir a fileira de pontuação.
3. Ajustar cores/raios/altura ao visual do Galaxy S25 Ultra com prints reais.
4. Portar atualizador integrado com os mesmos bloqueios de assinatura e SHA.
5. Testar em Android e avaliar uma versão assinada capaz de atualizar
   o HBX original, sem perda de dados.

## Licenças

AnySoftKeyboard é Apache-2.0, e sua licença e atribuições permanecem
preservadas no clone do upstream. Os componentes HBX e recursos auxiliares
mantêm suas respectivas licenças. Não publicar fontes sem atribuição.

## Como construir

O arquivo `anysoft-port/upstream.json` fixa a revisão a ser usada.
`anysoft-port/prepare.py` aplica um pequeno overlay de preferências,
identificador e imagem em um checkout dessa revisão, sem editar a branch
principal do projeto. O GitHub Actions da branch experimental compila e
assina um APK de **depuração**, verifica pacote/assinatura e publica
uma **pré-release de testes** somente após os passos passarem.

**Status inicial:** build e teste reais ainda precisam ser confirmados.
