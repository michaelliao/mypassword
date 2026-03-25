package org.puppylab.mypassword.ui.view;

import java.util.function.Consumer;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

public class UnlockView {

    private static final int MIN_PASSWORD_LEN = 8;
    private static final int MAX_PASSWORD_LEN = 50;

    public final Composite composite;

    private final Text   passwordText;
    private final Button unlockBtn;
    private final Label  errorLabel;

    private Consumer<String> onSubmit;

    public UnlockView(Composite parent) {
        composite = new Composite(parent, SWT.NONE);
        composite.setLayout(new GridLayout(1, false));

        // vertically centre the card inside the view
        Composite card = new Composite(composite, SWT.NONE);
        GridData  cardGd = new GridData(SWT.CENTER, SWT.CENTER, true, true);
        cardGd.widthHint = 360;
        card.setLayoutData(cardGd);
        GridLayout cardGl      = new GridLayout(1, false);
        cardGl.verticalSpacing = 12;
        card.setLayout(cardGl);

        // ── icon ──────────────────────────────────────────────────────
        Label icon = new Label(card, SWT.CENTER);
        icon.setText("\uD83D\uDD10");   // 🔐
        icon.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false));

        // ── app title ─────────────────────────────────────────────────
        Label title = new Label(card, SWT.CENTER);
        title.setText("MyPassword");
        title.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false));
        Font boldFont = deriveBoldFont(title, 4);
        title.setFont(boldFont);
        title.addListener(SWT.Dispose, e -> boldFont.dispose());

        // ── hint ──────────────────────────────────────────────────────
        Label hint = new Label(card, SWT.CENTER | SWT.WRAP);
        hint.setText("Enter your master password to unlock the vault.");
        hint.setForeground(parent.getDisplay().getSystemColor(SWT.COLOR_DARK_GRAY));
        GridData hintGd   = new GridData(SWT.CENTER, SWT.CENTER, true, false);
        hintGd.widthHint  = 320;
        hint.setLayoutData(hintGd);

        // ── spacer ────────────────────────────────────────────────────
        new Label(card, SWT.NONE);

        // ── password row: input + Unlock button side-by-side ─────────
        Composite pwRow = new Composite(card, SWT.NONE);
        pwRow.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        GridLayout pwRowGl      = new GridLayout(2, false);
        pwRowGl.marginWidth     = 0;
        pwRowGl.marginHeight    = 0;
        pwRowGl.horizontalSpacing = 6;
        pwRow.setLayout(pwRowGl);

        passwordText = new Text(pwRow, SWT.BORDER | SWT.PASSWORD | SWT.SINGLE);
        passwordText.setMessage("Master password (8–50 chars)");
        passwordText.setTextLimit(MAX_PASSWORD_LEN);
        GridData pwGd   = new GridData(SWT.FILL, SWT.CENTER, true, false);
        pwGd.heightHint = 28;
        passwordText.setLayoutData(pwGd);

        unlockBtn = new Button(pwRow, SWT.PUSH);
        unlockBtn.setText("Unlock");
        GridData btnGd   = new GridData(SWT.RIGHT, SWT.CENTER, false, false);
        btnGd.heightHint = 30;
        unlockBtn.setLayoutData(btnGd);
        unlockBtn.setEnabled(false);

        passwordText.addListener(SWT.Modify, e -> {
            int len = passwordText.getText().length();
            unlockBtn.setEnabled(len >= MIN_PASSWORD_LEN);
        });
        passwordText.addTraverseListener(e -> {
            if (e.detail == SWT.TRAVERSE_RETURN && unlockBtn.isEnabled()) submit();
        });
        unlockBtn.addListener(SWT.Selection, e -> submit());

        // ── error label ───────────────────────────────────────────────
        errorLabel = new Label(card, SWT.CENTER);
        errorLabel.setForeground(parent.getDisplay().getSystemColor(SWT.COLOR_RED));
        errorLabel.setText("");
        errorLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false));

        // make Unlock the default button so Enter works anywhere in card
        composite.getShell().setDefaultButton(unlockBtn);
    }

    // ── public API ────────────────────────────────────────────────────

    public void setOnSubmit(Consumer<String> listener) {
        this.onSubmit = listener;
    }

    public void showError(String message) {
        errorLabel.setText(message);
        errorLabel.getParent().layout(true, true);
        passwordText.selectAll();
        passwordText.setFocus();
    }

    public void clearError() {
        errorLabel.setText("");
        passwordText.setText("");
        unlockBtn.setEnabled(false);
    }

    // ── helpers ───────────────────────────────────────────────────────

    private void submit() {
        if (onSubmit != null) onSubmit.accept(passwordText.getText());
    }

    private static Font deriveBoldFont(Label base, int extraPoints) {
        FontData[] fds = base.getFont().getFontData();
        for (FontData fd : fds) {
            fd.setHeight(fd.getHeight() + extraPoints);
            fd.setStyle(SWT.BOLD);
        }
        return new Font(base.getDisplay(), fds);
    }
}
