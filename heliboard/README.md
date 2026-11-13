# HBX Keyboard

**HBX Keyboard** é uma variante do HeliBoard 3.6 para Android 8 ou superior, com teclado brasileiro, dicionário pessoal, prancheta e assistente de escrita online e offline. Licença GPL-3.0-only; autores e avisos originais preservados nas fontes correspondentes.

## Compilar

Clone o HeliBoard no commit de `heliboard/upstream.json` e llama.cpp no commit indicado em `ai_runtime`, dentro de `heli-source/ai-native/llama.cpp`. Use JDK 17, Android SDK 35, build-tools 35 e NDK 28.0.13004108. Execute `TECLADO_UNSIGNED_BUILD=1 python3 heliboard/prepare.py heli-source`, `bash heliboard/ai/build-native.sh heli-source` e, dentro de heli-source, `./gradlew testRunTestsUnitTest assembleDebugNoMinify`. O workflow público contém os comandos completos.

## Atualizações

O runner público compila sem a chave privada. A assinatura de atualização ocorre separadamente; o APK só é publicado depois de conferir a assinatura com apksigner. Os modelos GGUF são importados pelo usuário; não acompanham o APK. A IA online depende de credenciais e disponibilidade do provedor.

## Barra

No modo compacto, palavras aparecem durante a digitação e os atalhos voltam depois do ponto. A chegada de resultados de dicionário não troca o modo da barra. Ferramentas manuais e seleção continuam disponíveis.
