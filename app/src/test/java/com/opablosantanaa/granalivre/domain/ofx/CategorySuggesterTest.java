package com.opablosantanaa.granalivre.domain.ofx;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class CategorySuggesterTest {
    @Test
    public void suggestsExpenseCategoriesIgnoringAccentsAndCase() {
        assertEquals("Transporte", CategorySuggester.suggest("Uber *Trip 1234", false));
        assertEquals("Alimentação", CategorySuggester.suggest("IFOOD *Pedido", false));
        assertEquals("Saúde", CategorySuggester.suggest("Farmácia São João", false));
        assertEquals("Contas e serviços", CategorySuggester.suggest("PAGTO CONTA ÁGUA SABESP", false));
        assertEquals("Moradia", CategorySuggester.suggest("aluguel outubro", false));
    }

    @Test
    public void suggestsIncomeCategories() {
        assertEquals("Salário", CategorySuggester.suggest("SALARIO EMPRESA X", true));
        assertEquals("Investimentos", CategorySuggester.suggest("REND PAGO APLIC AUT", true));
        assertEquals("Outras receitas", CategorySuggester.suggest("PIX RECEBIDO MARIA", true));
    }

    @Test
    public void leavesUnknownExpensesWithoutCategory() {
        assertNull(CategorySuggester.suggest("PIX ENVIADO JOAO", false));
    }
}
