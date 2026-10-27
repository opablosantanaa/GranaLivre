package com.opablosantanaa.granalivre.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.opablosantanaa.granalivre.domain.DefaultCategories;
import com.opablosantanaa.granalivre.domain.ofx.OfxException;
import com.opablosantanaa.granalivre.domain.ofx.OfxParser;
import com.opablosantanaa.granalivre.domain.ofx.OfxStatement;
import com.opablosantanaa.granalivre.entity.Category;
import com.opablosantanaa.granalivre.entity.FinancialTransaction;
import com.opablosantanaa.granalivre.entity.Recurrence;
import com.opablosantanaa.granalivre.entity.TransactionType;

import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

public class StatementImportTest extends DatabaseTest {
    private static final String STATEMENT = "<OFX><BANKMSGSRSV1><STMTTRNRS><STMTRS><CURDEF>BRL"
            + "<BANKACCTFROM><BANKID>0341<ACCTID>12345-6</BANKACCTFROM><BANKTRANLIST>"
            + "<STMTTRN><TRNTYPE>DEBIT<DTPOSTED>20261005<TRNAMT>-150.00<FITID>A1<MEMO>CONTA AGUA SABESP</STMTTRN>"
            + "<STMTTRN><TRNTYPE>CREDIT<DTPOSTED>20261006<TRNAMT>3500.00<FITID>A2<MEMO>SALARIO EMPRESA X</STMTTRN>"
            + "</BANKTRANLIST><LEDGERBAL><BALAMT>4350.00<DTASOF>20261020</LEDGERBAL></STMTRS></STMTTRNRS>"
            + "</BANKMSGSRSV1></OFX>";

    private long userId;
    private StatementImportRepository repository;
    private OfxStatement statement;

    @Before
    public void createData() throws OfxException {
        userId = createUser("import@email.com");
        database.categoryDao().insertAll(DefaultCategories.create(userId));
        repository = new StatementImportRepository(database, DIRECT, DIRECT);
        statement = OfxParser.parse(STATEMENT.getBytes(StandardCharsets.UTF_8));
    }

    private long categoryId(String name) {
        for (Category c : database.categoryDao().findByUser(userId)) {
            if (c.getCategory_name().equals(name)) return c.getCategory_id();
        }
        throw new AssertionError(name);
    }

    @Test
    public void importsOnceAndMarksDuplicatesOnSecondImport() {
        long accountId = createAccount(userId, 100_000);
        StatementImportRepository.Preview preview = success(cb -> repository.preview(userId, accountId, statement, cb));
        assertEquals(2, preview.rows.size());
        assertFalse(preview.rows.get(0).duplicate);
        assertEquals(Long.valueOf(categoryId("Contas e serviços")), preview.rows.get(0).categoryId);
        assertEquals(Long.valueOf(categoryId("Salário")), preview.rows.get(1).categoryId);

        StatementImportRepository.Result result = success(cb -> repository.commit(userId, accountId, preview.rows,
                null, null, false, cb));
        assertEquals(2, result.imported);
        assertEquals(100_000 - 15_000 + 350_000, database.accountDao().findBalance(accountId));

        StatementImportRepository.Preview again = success(cb -> repository.preview(userId, accountId, statement, cb));
        assertTrue(again.rows.get(0).duplicate);
        assertTrue(again.rows.get(1).duplicate);
        assertFalse(again.rows.get(0).selected);
        DatabaseTest.<StatementImportRepository.Result>failure(cb -> repository.commit(userId, accountId, again.rows, null, null, false, cb));
        assertEquals(100_000 - 15_000 + 350_000, database.accountDao().findBalance(accountId));
    }

    @Test
    public void reconciliationKeepsCurrentBalanceAndCreatesAdjustmentOnlyOnce() {
        long accountId = createAccount(userId, 50_000);
        StatementImportRepository.Preview preview = success(cb -> repository.preview(userId, accountId, statement, cb));
        assertEquals(50_000, preview.balanceNowCents);
        assertEquals(-15_000 + 350_000, preview.selectedNet(null));

        StatementImportRepository.Result result = success(cb -> repository.commit(userId, accountId, preview.rows,
                statement.ledgerBalanceCents, statement.ledgerBalanceDate, true, cb));
        assertEquals(2, result.imported);
        assertEquals(-(350_000 - 15_000), result.adjustmentCents);
        assertEquals(50_000, database.accountDao().findBalance(accountId));

        StatementImportRepository.Preview again = success(cb -> repository.preview(userId, accountId, statement, cb));
        DatabaseTest.<StatementImportRepository.Result>failure(cb -> repository.commit(userId, accountId, again.rows,
                statement.ledgerBalanceCents, statement.ledgerBalanceDate, true, cb));
        assertEquals(50_000, database.accountDao().findBalance(accountId));
    }

    @Test
    public void reconciliationOffsetsOnlySelectedRows() {
        long accountId = createAccount(userId, 100_000);
        FinancialTransaction later = new FinancialTransaction();
        later.setUser_id(userId);
        later.setAccount_id(accountId);
        later.setTransaction_type(TransactionType.DESPESA);
        later.setTransaction_amount_cents(10_000);
        later.setTransaction_date(LocalDate.of(2026, 10, 25));
        later.setTransaction_paid(true);
        database.financialTransactionDao().insert(later);

        StatementImportRepository.Preview preview = success(cb -> repository.preview(userId, accountId, statement, cb));
        preview.rows.get(1).selected = false;
        StatementImportRepository.Result result = success(cb -> repository.commit(userId, accountId, preview.rows,
                statement.ledgerBalanceCents, statement.ledgerBalanceDate, true, cb));
        assertEquals(1, result.imported);
        assertEquals(15_000, result.adjustmentCents);
        assertEquals(100_000 - 10_000, database.accountDao().findBalance(accountId));
    }

    private static final String AESA = "<OFX><BANKTRANLIST>"
            + "<STMTTRN><TRNTYPE>DEBIT<DTPOSTED>20261027<TRNAMT>-278.71<FITID>B1"
            + "<MEMO>PAG BOLETO AUTARQUIA DE ENSINO SUPERIOR</STMTTRN>"
            + "<STMTTRN><TRNTYPE>DEBIT<DTPOSTED>20261026<TRNAMT>-10.00<FITID>B2<MEMO>PIX QRS MARIANNA</STMTTRN>"
            + "<STMTTRN><TRNTYPE>DEBIT<DTPOSTED>20261026<TRNAMT>-10.00<FITID>B3<MEMO>PIX QRS MARIANNA</STMTTRN>"
            + "</BANKTRANLIST><LEDGERBAL><BALAMT>1000.00<DTASOF>20261027</LEDGERBAL></OFX>";

    private long aesaRecurrence(long accountId) {
        Recurrence recurrence = new Recurrence();
        recurrence.setUser_id(userId);
        recurrence.setAccount_id(accountId);
        recurrence.setRecurrence_amount_cents(27_871);
        recurrence.setRecurrence_description("Mensalidade - AESA");
        recurrence.setRecurrence_start_date(LocalDate.of(2026, 10, 27));
        return database.recurrenceDao().insert(recurrence);
    }

    private long expense(long accountId, Long recurrenceId, long cents, LocalDate date, boolean paid) {
        FinancialTransaction t = new FinancialTransaction();
        t.setUser_id(userId);
        t.setAccount_id(accountId);
        t.setRecurrence_id(recurrenceId);
        t.setTransaction_type(TransactionType.DESPESA);
        t.setTransaction_amount_cents(cents);
        t.setTransaction_description(recurrenceId == null ? "Lanche" : "Mensalidade - AESA");
        t.setTransaction_date(date);
        t.setTransaction_paid(paid);
        return database.financialTransactionDao().insert(t);
    }

    @Test
    public void paidRecurrenceIsLinkedInsteadOfDuplicated() throws OfxException {
        long accountId = createAccount(userId, 100_000);
        long paid = expense(accountId, aesaRecurrence(accountId), 27_871, LocalDate.of(2026, 10, 27), true);
        OfxStatement aesa = OfxParser.parse(AESA.getBytes(StandardCharsets.UTF_8));

        StatementImportRepository.Preview preview = success(cb -> repository.preview(userId, accountId, aesa, cb));
        assertEquals(paid, preview.rows.get(0).match.getTransaction_id());
        assertFalse(preview.rows.get(0).selected);
        assertNull(preview.rows.get(1).match);
        assertEquals(1, preview.linkCount());
        assertEquals(-2_000, preview.selectedNet(null));

        StatementImportRepository.Result result = success(cb -> repository.commit(userId, accountId, preview.rows,
                null, null, false, cb));
        assertEquals(2, result.imported);
        assertEquals(1, result.linked);
        assertEquals(100_000 - 27_871 - 2_000, database.accountDao().findBalance(accountId));
        assertEquals("fit:B1", database.financialTransactionDao().findById(paid).getTransaction_import_id());

        StatementImportRepository.Preview again = success(cb -> repository.preview(userId, accountId, aesa, cb));
        for (StatementImportRepository.Row row : again.rows) assertTrue(row.duplicate);
    }

    @Test
    public void pendingOccurrenceIsSettledByStatementLine() throws OfxException {
        long accountId = createAccount(userId, 100_000);
        long pending = expense(accountId, aesaRecurrence(accountId), 27_871, LocalDate.of(2026, 10, 24), false);
        OfxStatement aesa = OfxParser.parse(AESA.getBytes(StandardCharsets.UTF_8));

        StatementImportRepository.Preview preview = success(cb -> repository.preview(userId, accountId, aesa, cb));
        assertEquals(pending, preview.rows.get(0).match.getTransaction_id());
        assertEquals(-27_871 - 2_000, preview.selectedNet(null));

        StatementImportRepository.Result result = success(cb -> repository.commit(userId, accountId, preview.rows,
                aesa.ledgerBalanceCents, aesa.ledgerBalanceDate, true, cb));
        assertEquals(27_871 + 2_000, result.adjustmentCents);
        assertEquals(100_000, database.accountDao().findBalance(accountId));
        FinancialTransaction settled = database.financialTransactionDao().findById(pending);
        assertTrue(settled.isTransaction_paid());
        assertEquals(LocalDate.of(2026, 10, 27), settled.getTransaction_date());
    }

    @Test
    public void eachExistingEntryMatchesOneLineAndCanBeOverridden() throws OfxException {
        long accountId = createAccount(userId, 100_000);
        long snack = expense(accountId, null, 1_000, LocalDate.of(2026, 10, 26), true);
        expense(accountId, null, 27_871, LocalDate.of(2026, 10, 20), true);
        OfxStatement aesa = OfxParser.parse(AESA.getBytes(StandardCharsets.UTF_8));

        StatementImportRepository.Preview preview = success(cb -> repository.preview(userId, accountId, aesa, cb));
        assertNull("Fora da janela de dias", preview.rows.get(0).match);
        assertEquals(snack, preview.rows.get(1).match.getTransaction_id());
        assertNull(preview.rows.get(2).match);

        preview.rows.get(1).selected = true;
        StatementImportRepository.Result result = success(cb -> repository.commit(userId, accountId, preview.rows,
                null, null, false, cb));
        assertEquals(3, result.imported);
        assertEquals(0, result.linked);
        assertNull(database.financialTransactionDao().findById(snack).getTransaction_import_id());
    }

    @Test
    public void unknownExpensesFallBackToOtherExpenses() throws OfxException {
        String text = "<OFX><STMTTRN><DTPOSTED>20261001<TRNAMT>-10.00<FITID>Z<MEMO>PIX ENVIADO JOAO</STMTTRN></OFX>";
        long accountId = createAccount(userId, 0);
        OfxStatement other = OfxParser.parse(text.getBytes(StandardCharsets.UTF_8));
        StatementImportRepository.Preview preview = success(cb -> repository.preview(userId, accountId, other, cb));
        assertEquals(Long.valueOf(categoryId(DefaultCategories.OTHER_EXPENSES)), preview.rows.get(0).categoryId);
    }
}
