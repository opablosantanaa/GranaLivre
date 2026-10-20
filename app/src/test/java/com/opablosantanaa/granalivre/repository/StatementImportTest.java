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

    @Test
    public void unknownExpensesStayWithoutCategory() throws OfxException {
        String text = "<OFX><STMTTRN><DTPOSTED>20261001<TRNAMT>-10.00<FITID>Z<MEMO>PIX ENVIADO JOAO</STMTTRN></OFX>";
        long accountId = createAccount(userId, 0);
        OfxStatement other = OfxParser.parse(text.getBytes(StandardCharsets.UTF_8));
        StatementImportRepository.Preview preview = success(cb -> repository.preview(userId, accountId, other, cb));
        assertNull(preview.rows.get(0).categoryId);
    }
}
