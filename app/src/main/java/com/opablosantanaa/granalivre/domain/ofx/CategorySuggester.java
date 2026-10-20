package com.opablosantanaa.granalivre.domain.ofx;

import java.text.Normalizer;
import java.util.Locale;

public final class CategorySuggester {
    private static final String[][] EXPENSE_RULES = {
            {"Transporte", "UBER", "99APP", "99 APP", "99POP", "CABIFY", "POSTO", "SHELL", "IPIRANGA",
                    "PETROBRAS", "COMBUST", "ESTACION", "PEDAGIO", "SEM PARAR", "CONECTCAR", "METRO", "ONIBUS"},
            {"Alimentação", "IFOOD", "RAPPI", "RESTAUR", "LANCHON", "PADARIA", "PANIFIC", "MERCADO", "SUPERMERC",
                    "CARREFOUR", "ASSAI", "ATACADAO", "PAO DE ACUCAR", "HORTIFRUTI", "ACOUGUE", "BURGER",
                    "MCDONALD", "PIZZA"},
            {"Saúde", "FARMACIA", "DROGARIA", "DROGASIL", "RAIA", "PAGUE MENOS", "PANVEL", "HOSPITAL",
                    "CLINICA", "LABORAT", "ODONTO", "UNIMED", "AMIL", "ACADEMIA", "SMARTFIT"},
            {"Lazer", "NETFLIX", "SPOTIFY", "DISNEY", "PRIME VIDEO", "HBO", "MAX.COM", "YOUTUBE", "CINEMA",
                    "INGRESSO", "STEAM", "PLAYSTATION", "XBOX", "BAR "},
            {"Moradia", "ALUGUEL", "CONDOMINIO", "IMOBILIARIA", "IPTU"},
            {"Contas e serviços", "ENERGIA", "ENEL", "CEMIG", "COPEL", "LIGHT", "CPFL", "SABESP", "COPASA",
                    "SANEPAR", "CEDAE", "AGUA", "VIVO", "CLARO", "TIM ", "OI ", "NET ", "INTERNET", "TARIFA",
                    "ANUIDADE", "IOF", "SEGURO"},
            {"Educação", "ESCOLA", "COLEGIO", "CURSO", "FACULDADE", "UNIVERSIDADE", "LIVRARIA", "UDEMY", "ALURA"},
            {"Compras", "AMAZON", "MAGALU", "MAGAZINE", "SHOPEE", "MERCADOLIVRE", "MERCADO LIVRE", "ALIEXPRESS",
                    "RENNER", "RIACHUELO", "C&A", "CENTAURO", "AMERICANAS", "SHEIN", "LOJA"},
    };

    private static final String[][] INCOME_RULES = {
            {"Salário", "SALARIO", "PROVENTO", "FOLHA", "PAGTO SAL", "REMUNERACAO", "VENCIMENTOS"},
            {"Investimentos", "RENDIMENTO", "REND PAGO", "JUROS", "DIVIDENDO", "CDB", "TESOURO", "RESGATE"},
            {"Presentes", "PRESENTE"},
    };

    private CategorySuggester() {}

    public static String normalize(String text) {
        String plain = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFD);
        StringBuilder out = new StringBuilder();
        for (char c : plain.toCharArray()) {
            if (Character.getType(c) != Character.NON_SPACING_MARK) out.append(c);
        }
        return " " + out.toString().toUpperCase(Locale.ROOT) + " ";
    }

    public static String suggest(String description, boolean income) {
        String text = normalize(description);
        for (String[] rule : income ? INCOME_RULES : EXPENSE_RULES) {
            for (int i = 1; i < rule.length; i++) {
                if (text.contains(rule[i])) return rule[0];
            }
        }
        return income ? "Outras receitas" : null;
    }
}
