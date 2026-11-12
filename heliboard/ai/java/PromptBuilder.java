// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;
/** Small explicit action allowlist; the keyboard never generates on normal keystrokes. */
public final class PromptBuilder {
    public static String build(String task,String style,String prompt,String source) {
        String command;
        switch(task) {
            case "Compositor":
                String tone="Padrão";
                if("Profissional".equals(style)||"Casual".equals(style)||"Educado".equals(style)||
                    "Direto".equals(style)||"Amigável".equals(style))tone=style;
                command="Redija somente o texto solicitado. Não invente dados. Tom: "+tone+
                    ". Pedido: "+prompt;
                break;
            case "Estilo de escrita":
                command="Reescreva no estilo "+style+", preservando fatos, nomes, números e negações.";
                break;
            case "Resumir":
                command="Resuma em português, preservando os fatos principais. Não acrescente informações.";
                break;
            case "Traduzir para português":
                command="Traduza para português brasileiro, preservando nomes, valores, links e sentido.";
                break;
            case "Traduzir para inglês":
                command="Translate into natural English, preserving names, numbers, links and meaning.";
                break;
            case "Traduzir para espanhol":
                command="Traduce al español natural, conservando nombres, cifras, enlaces y significado.";
                break;
            case "Traduzir para francês":
                command="Traduisez en français naturel, en conservant noms, chiffres, liens et sens.";
                break;
            case "Traduzir para italiano":
                command="Traduci in italiano naturale, mantenendo nomi, numeri, link e significato.";
                break;
            case "Sugerir resposta":
                command="Com base SOMENTE no texto informado, sugira duas respostas curtas em português, "+
                    "numeradas 1 e 2, com tons respectivamente amigável e objetivo. "+
                    "Não afirme conhecer mensagens anteriores ou o contexto de conversas privadas.";
                break;
            case "Marcador":
                command="Organize o texto em uma lista com marcadores, sem inventar informações.";
                break;
            case "Tabela":
                command="Organize os dados em tabela de texto simples, sem acrescentar informações.";
                break;
            default:
                command="Corrija somente ortografia, pontuação e gramática do português brasileiro. "+
                    "Preserve rigorosamente o sentido, nomes próprios, números, datas, siglas, links, "+
                    "negações e informações factuais. Não reescreva por estilo, não acrescente frases, "+
                    "não complete informação ausente e não troque palavras válidas por sinônimos. "+
                    "Preserve gírias e informalidade quando forem intencionais. "+
                    "Se não houver erro, devolva exatamente o texto original.";
        }
        return command+"\n\n<texto_para_editar>\n"+source+"\n</texto_para_editar>\nDevolva somente o texto final.";
    }
}

