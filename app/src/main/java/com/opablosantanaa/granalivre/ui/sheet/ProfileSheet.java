package com.opablosantanaa.granalivre.ui.sheet;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.opablosantanaa.granalivre.R;
import com.opablosantanaa.granalivre.entity.AccountType;
import com.opablosantanaa.granalivre.entity.Card;
import com.opablosantanaa.granalivre.entity.Category;
import com.opablosantanaa.granalivre.entity.TransactionType;
import com.opablosantanaa.granalivre.relation.AccountWithBalance;
import com.opablosantanaa.granalivre.repository.BackupMerger;
import com.opablosantanaa.granalivre.repository.BackupRepository;
import com.opablosantanaa.granalivre.repository.BackupStorage;
import com.opablosantanaa.granalivre.repository.DemoDataSeeder;
import com.opablosantanaa.granalivre.repository.RepositoryCallback;
import com.opablosantanaa.granalivre.ui.BackupImport;
import com.opablosantanaa.granalivre.ui.Dates;
import com.opablosantanaa.granalivre.ui.MainViewModel;
import com.opablosantanaa.granalivre.ui.Money;
import com.opablosantanaa.granalivre.ui.PasswordDialog;
import com.opablosantanaa.granalivre.ui.Session;
import com.opablosantanaa.granalivre.ui.widget.SegmentedControl;
import com.opablosantanaa.granalivre.worker.BackupWorker;

import java.time.LocalDate;
import java.util.List;

public class ProfileSheet extends BaseSheet {
    public static final String[] BACKUP_TYPES = {BackupStorage.MIME, "text/plain", "application/octet-stream"};

    private final ActivityResultLauncher<Uri> pickFolder =
            registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(), this::onFolderPicked);
    private final ActivityResultLauncher<String> createBackup =
            registerForActivityResult(new ActivityResultContracts.CreateDocument(BackupStorage.MIME), this::onBackupCreated);
    private final ActivityResultLauncher<String[]> openBackup =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onBackupPicked);

    private BackupRepository backups;

    public ProfileSheet() {
        super(R.layout.sheet_profile);
    }

    private BackupRepository backups() {
        if (backups == null) backups = new BackupRepository(requireContext());
        return backups;
    }

    private void toast(String text) {
        if (isAdded()) Toast.makeText(requireContext(), text, Toast.LENGTH_LONG).show();
    }

    private void showBackupFolder(View view) {
        LinearLayout container = view.findViewById(R.id.backup);
        container.removeAllViews();
        BackupStorage storage = backups().storage();
        String caption;
        if (storage.folder() == null) {
            caption = getString(R.string.backup_automatic_off);
        } else {
            String name = storage.folderName();
            if (name == null) name = getString(R.string.backup_folder_unknown);
            LocalDate last = storage.lastAutomatic();
            caption = last == null ? getString(R.string.backup_automatic_on, name)
                    : getString(R.string.backup_automatic_on_last, name, Dates.shortDate(last));
        }
        View row = row(container, getString(R.string.backup_automatic), caption, "");
        row.setBackgroundResource(R.drawable.bg_row_ripple);
        row.setClickable(true);
        row.setOnClickListener(v -> pickFolder.launch(null));

        boolean protectedBackup = storage.hasPassword();
        View password = row(container, getString(R.string.backup_password),
                getString(protectedBackup ? R.string.backup_password_on : R.string.backup_password_off), "");
        password.setBackgroundResource(R.drawable.bg_row_ripple);
        password.setClickable(true);
        password.setOnClickListener(v -> {
            if (protectedBackup) managePassword();
            else PasswordDialog.create(requireContext(), this::savePassword);
        });
    }

    private void refreshBackup() {
        View view = getView();
        if (view != null) showBackupFolder(view);
    }

    private void managePassword() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.backup_password)
                .setMessage(R.string.backup_password_manage_message)
                .setNegativeButton(R.string.back, null)
                .setNeutralButton(R.string.backup_password_remove, (d, w) -> confirmRemovePassword())
                .setPositiveButton(R.string.backup_password_change, (d, w) ->
                        PasswordDialog.create(requireContext(), this::savePassword))
                .show();
    }

    private void confirmRemovePassword() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.backup_password_remove)
                .setMessage(R.string.backup_password_remove_message)
                .setNegativeButton(R.string.back, null)
                .setPositiveButton(R.string.backup_password_remove, (d, w) -> {
                    backups().storage().clearPassword();
                    backupAgain();
                    toast(getString(R.string.backup_password_removed));
                    refreshBackup();
                })
                .show();
    }

    private void savePassword(String password) {
        backups().setPassword(password, new RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                if (!isAdded()) return;
                backupAgain();
                toast(getString(R.string.backup_password_saved));
                refreshBackup();
            }

            @Override
            public void onError(Exception e) {
                toast(e.getMessage());
            }
        });
    }

    private void backupAgain() {
        if (backups().storage().folder() != null) BackupWorker.runNow(requireContext());
    }

    private void onFolderPicked(@Nullable Uri tree) {
        if (tree == null || !isAdded()) return;
        try {
            backups().storage().setFolder(tree);
        } catch (SecurityException e) {
            toast(e.getMessage());
            return;
        }
        BackupWorker.runNow(requireContext());
        toast(getString(R.string.backup_automatic_enabled));
        refreshBackup();
    }

    private void onBackupCreated(@Nullable Uri document) {
        if (document == null || !isAdded()) return;
        backups().exportTo(vm().getUserId(), document, new RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                if (isAdded()) toast(getString(R.string.backup_saved));
            }

            @Override
            public void onError(Exception e) {
                toast(e.getMessage());
            }
        });
    }

    private void onBackupPicked(@Nullable Uri document) {
        if (document == null || !isAdded()) return;
        BackupImport.confirm(this, backups(), document, R.string.backup_import, R.string.backup_import_message,
                R.string.backup_import_confirm, password -> merge(document, password));
    }

    private void merge(Uri document, @Nullable String password) {
        Context context = requireContext().getApplicationContext();
        backups().mergeInto(vm().getUserId(), document, password, new RepositoryCallback<BackupMerger.Result>() {
            @Override
            public void onSuccess(BackupMerger.Result result) {
                Toast.makeText(context, context.getString(R.string.backup_imported, result.added, result.updated,
                        result.kept), Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(context, e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private static int accountKind(AccountType type) {
        switch (type) {
            case POUPANCA: return R.string.account_poupanca;
            case INVESTIMENTO: return R.string.account_investimento;
            case CARTEIRA: return R.string.account_carteira;
            default: return R.string.account_corrente;
        }
    }

    private View row(LinearLayout parent, String title, String caption, CharSequence value) {
        View row = LayoutInflater.from(requireContext()).inflate(R.layout.item_simple_row, parent, false);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        ((TextView) row.findViewById(R.id.caption)).setText(caption);
        ((TextView) row.findViewById(R.id.value)).setText(value);
        parent.addView(row);
        return row;
    }

    /** Linha que abre a edição ao toque. */
    private void editable(View row, String title, BaseSheet sheet, String tag) {
        row.setBackgroundResource(R.drawable.bg_row_ripple);
        row.setClickable(true);
        row.setContentDescription(getString(R.string.profile_edit_item, title));
        row.setOnClickListener(v -> open(sheet, tag));
    }

    private void none(LinearLayout parent, int text) {
        TextView view = new TextView(requireContext(), null, 0, R.style.Text_Caption);
        view.setText(text);
        int unit = Math.round(getResources().getDisplayMetrics().density);
        view.setPadding(0, 12 * unit, 0, 4 * unit);
        parent.addView(view);
    }

    private void open(BaseSheet sheet, String tag) {
        FragmentManager manager = getParentFragmentManager();
        dismiss();
        sheet.show(manager, tag);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        MainViewModel vm = vm();
        header(view, "");
        vm.user.observe(getViewLifecycleOwner(), user -> {
            if (user == null) return;
            ((TextView) view.findViewById(R.id.sheet_title)).setText(user.getUser_name());
            ((TextView) view.findViewById(R.id.email)).setText(
                    getString(R.string.profile_born, Dates.shortDate(user.getUser_birth_date())));
        });

        SegmentedControl theme = view.findViewById(R.id.theme);
        theme.setOptions(getString(R.string.theme_system), getString(R.string.theme_light), getString(R.string.theme_dark));
        theme.setPalette(color(R.color.gl_line), color(R.color.gl_text_secondary),
                color(R.color.gl_text), color(R.color.gl_bg));
        theme.select(Session.theme(requireContext()), false);
        theme.setOnChange(index -> {
            dismiss();
            Session.setTheme(requireContext(), index);
        });

        int secondary = color(R.color.gl_text_secondary);
        LinearLayout accounts = view.findViewById(R.id.accounts);
        List<AccountWithBalance> accountList = vm.accountList.getValue();
        if (accountList == null || accountList.isEmpty()) none(accounts, R.string.profile_no_accounts);
        else for (AccountWithBalance a : accountList) {
            View row = row(accounts, a.account.getAccount_bank_name(), getString(accountKind(a.account.getAccount_type())),
                    Money.styled(a.balanceCents, secondary));
            editable(row, a.account.getAccount_bank_name(), AccountSheet.edit(a.account.getAccount_id()), "account");
        }

        LinearLayout cards = view.findViewById(R.id.cards);
        List<Card> cardList = vm.cardList.getValue();
        if (cardList == null || cardList.isEmpty()) none(cards, R.string.profile_no_cards);
        else for (Card c : cardList) {
            View row = row(cards, c.getCard_nickname(), getString(R.string.invoice_line, c.getCard_closing_day(),
                    c.getCard_due_day()), "\u2022\u2022\u2022\u2022 " + c.getCard_last_digits());
            editable(row, c.getCard_nickname(), CardSheet.edit(c.getCard_id()), "card");
        }

        vm.categoryList.observe(getViewLifecycleOwner(), list -> {
            int expense = 0;
            int income = 0;
            if (list != null) {
                for (Category c : list) {
                    if (c.getCategory_type() == TransactionType.RECEITA) income++;
                    else expense++;
                }
            }
            ((TextView) view.findViewById(R.id.categories_count)).setText(
                    getString(R.string.profile_categories_count, expense, income));
        });

        view.findViewById(R.id.add_account).setOnClickListener(v -> open(new AccountSheet(), "account"));
        view.findViewById(R.id.add_card).setOnClickListener(v -> open(new CardSheet(), "card"));
        view.findViewById(R.id.add_category).setOnClickListener(v ->
                open(CategorySheet.create(TransactionType.DESPESA), "category"));

        showBackupFolder(view);
        view.findViewById(R.id.backup_save).setOnClickListener(v ->
                createBackup.launch(BackupStorage.fileName(LocalDate.now())));
        view.findViewById(R.id.backup_import).setOnClickListener(v -> openBackup.launch(BACKUP_TYPES));
        view.findViewById(R.id.privacy).setOnClickListener(v ->
                LegalSheet.create(LegalSheet.PRIVACY).show(getChildFragmentManager(), "legal"));
        view.findViewById(R.id.terms).setOnClickListener(v ->
                LegalSheet.create(LegalSheet.TERMS).show(getChildFragmentManager(), "legal"));

        boolean debuggable =(requireContext().getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        View demo = view.findViewById(R.id.demo);
        demo.setVisibility(debuggable && (accountList == null || accountList.isEmpty()) ? View.VISIBLE : View.GONE);
        demo.setOnClickListener(v -> {
            demo.setEnabled(false);
            new DemoDataSeeder(requireContext()).seed(vm.getUserId(), new RepositoryCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    done(R.string.profile_demo_done);
                }

                @Override
                public void onError(Exception e) {
                    demo.setEnabled(true);
                    if (isAdded()) Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        });

    }
}
