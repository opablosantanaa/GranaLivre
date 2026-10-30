package com.opablosantanaa.granalivre.ui.sheet;

import android.os.Bundle;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.style.BulletSpan;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.opablosantanaa.granalivre.R;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class LegalSheet extends BaseSheet {
    public static final String PRIVACY = "privacy_policy.html";
    public static final String TERMS = "terms.html";
    private static final String ARG_FILE = "file";

    public LegalSheet() {
        super(R.layout.sheet_legal);
    }

    public static LegalSheet create(String file) {
        LegalSheet sheet = new LegalSheet();
        Bundle args = new Bundle();
        args.putString(ARG_FILE, file);
        sheet.setArguments(args);
        return sheet;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        String file = requireArguments().getString(ARG_FILE, PRIVACY);
        header(view, PRIVACY.equals(file) ? R.string.legal_privacy : R.string.legal_terms);
        TextView body = view.findViewById(R.id.body);
        String html;
        try (InputStream in = requireContext().getAssets().open(file);
             Scanner scanner = new Scanner(in, StandardCharsets.UTF_8.name())) {
            html = scanner.useDelimiter("\\A").next();
        } catch (IOException e) {
            body.setText(R.string.legal_error);
            return;
        }
        int start = html.indexOf("</h1>");
        int end = html.indexOf("</body>");
        if (start >= 0 && end > start) html = html.substring(start + "</h1>".length(), end);
        SpannableStringBuilder text = new SpannableStringBuilder(Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY));
        int gap = Math.round(8 * getResources().getDisplayMetrics().density);
        for (BulletSpan bullet : text.getSpans(0, text.length(), BulletSpan.class)) {
            int spanStart = text.getSpanStart(bullet);
            int spanEnd = text.getSpanEnd(bullet);
            int flags = text.getSpanFlags(bullet);
            text.removeSpan(bullet);
            text.setSpan(new BulletSpan(gap, body.getCurrentTextColor()), spanStart, spanEnd, flags);
        }
        body.setText(text);
        body.setMovementMethod(LinkMovementMethod.getInstance());
    }
}
