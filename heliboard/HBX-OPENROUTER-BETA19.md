# HBX Keyboard beta19 — OpenRouter e preservação das IAs

## O que foi implementado
- Mantém HeliBoard 3.6, logo e nome **HBX Keyboard** e o identificador Android `br.com.tecladobr.heli.debug`.
- Adiciona chave do **OpenRouter** cifrada no Android Keystore, armazenada separadamente da chave Gemini e Groq.
- Adiciona modelos permitidos `openrouter/free` e `qwen/qwen3-32b:free`, podendo ficar indisponíveis conforme cotas, disponibilidade e regras do provedor.
- Configurações próprias para salvar e remover a chave, escolher um dos modelos e **testar com uma frase fictícia**, sem tocar nos textos pessoais.
- Conserva a cascata **Gemini 3.8 → Gemini 3.6 → OpenRouter → Groq** para tarefas explícitas de escrita; no modo Automático o GGUF já importado é o último recurso. O modo Offline nunca envia texto.
- Não remove nem substitui as chaves Groq/Gemini existentes, os modelos offline, dicionário, prancheta, configurações ou o atualizador.
- Conserva a mesma assinatura exigida para instalações sobre versões anteriores.

## Validação e atualização
A beta19 só pode ser publicada após compilar os testes Android, gerar APK assinado com o certificado original, verificar pacote `br.com.tecladobr.heli.debug` e `versionCode=9020`, validar integridade e atualizar `main/update.json`. O simples merge do código não disponibiliza atualização no aplicativo.

Nenhuma chave real de OpenRouter/Gemini/Groq está no APK ou no repositório. O usuário precisa cadastrar a chave OpenRouter pela interface do teclado. O modo Online pode ter limites, coleta de dados por terceiros e eventuais cobranças conforme a conta; somente modos gratuitos estão permitidos no seletor, mas a disponibilidade não é garantida.
