package org.puppylab.mypassword.ui.view;

import static org.puppylab.mypassword.util.I18nUtils.i18n;

import java.util.List;
import java.util.function.Consumer;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Text;
import org.puppylab.mypassword.core.Daemon;
import org.puppylab.mypassword.core.VaultManager;
import org.puppylab.mypassword.core.entity.RecoveryConfig;

public class UnlockView {

    private static final int MIN_PASSWORD_LEN = 8;
    private static final int MAX_PASSWORD_LEN = 50;

    public final Composite composite;

    private final VaultManager vaultManager;
    private final Composite    card;
    private final Text         passwordText;
    private final Button       unlockBtn;
    private final Label        errorLabel;
    private Composite          oauthContainer;

    private Consumer<String> onSubmit;

    public UnlockView(Composite parent, VaultManager vaultManager) {
        this.vaultManager = vaultManager;

        composite = new Composite(parent, SWT.NONE);
        composite.setLayout(new GridLayout(1, false));

        // vertically centre the card inside the view
        card = new Composite(composite, SWT.NONE);
        GridData cardGd = new GridData(SWT.CENTER, SWT.CENTER, true, true);
        cardGd.minimumWidth = 360;
        card.setLayoutData(cardGd);
        GridLayout cardGl = new GridLayout(1, false);
        cardGl.verticalSpacing = 12;
        card.setLayout(cardGl);

        // ── icon ──────────────────────────────────────────────────────
        Label icon = new Label(card, SWT.CENTER);
        icon.setText("\uD83D\uDD10"); // 🔐
        icon.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false));

        // ── app title ─────────────────────────────────────────────────
        Label title = new Label(card, SWT.CENTER);
        title.setText(i18n("app.name"));
        title.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false));
        Font boldFont = deriveBoldFont(title, 4);
        title.setFont(boldFont);
        title.addListener(SWT.Dispose, _ -> boldFont.dispose());

        // ── hint ──────────────────────────────────────────────────────
        Label hint = new Label(card, SWT.CENTER | SWT.WRAP);
        hint.setText(i18n("unlock.hint"));
        hint.setForeground(parent.getDisplay().getSystemColor(SWT.COLOR_DARK_GRAY));
        GridData hintGd = new GridData(SWT.CENTER, SWT.CENTER, true, false);
        hintGd.widthHint = 320;
        hint.setLayoutData(hintGd);

        // ── spacer ────────────────────────────────────────────────────
        new Label(card, SWT.NONE);

        // ── password row: input + Unlock button side-by-side ─────────
        Composite pwRow = new Composite(card, SWT.NONE);
        pwRow.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        GridLayout pwRowGl = new GridLayout(2, false);
        pwRowGl.marginWidth = 0;
        pwRowGl.marginHeight = 0;
        pwRowGl.horizontalSpacing = 6;
        pwRow.setLayout(pwRowGl);

        passwordText = new Text(pwRow, SWT.BORDER | SWT.PASSWORD | SWT.SINGLE);
        passwordText.setMessage(i18n("unlock.password.placeholder"));
        passwordText.setTextLimit(MAX_PASSWORD_LEN);
        GridData pwGd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        pwGd.heightHint = 28;
        passwordText.setLayoutData(pwGd);

        unlockBtn = new Button(pwRow, SWT.PUSH);
        unlockBtn.setText(i18n("unlock.btn"));
        GridData btnGd = new GridData(SWT.RIGHT, SWT.CENTER, false, false);
        btnGd.heightHint = 30;
        unlockBtn.setLayoutData(btnGd);
        unlockBtn.setEnabled(false);

        passwordText.addListener(SWT.Modify, _ -> {
            int len = passwordText.getText().length();
            unlockBtn.setEnabled(len >= MIN_PASSWORD_LEN);
        });
        passwordText.addTraverseListener(e -> {
            if (e.detail == SWT.TRAVERSE_RETURN && unlockBtn.isEnabled())
                submit();
        });
        unlockBtn.addListener(SWT.Selection, _ -> submit());

        // ── error label ───────────────────────────────────────────────
        errorLabel = new Label(card, SWT.CENTER);
        errorLabel.setForeground(parent.getDisplay().getSystemColor(SWT.COLOR_RED));
        errorLabel.setText("");
        errorLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false));

        // ── OAuth recovery list ───────────────────────────────────────
        oauthContainer = new Composite(card, SWT.NONE);
        GridLayout containerGl = new GridLayout(1, false);
        containerGl.marginWidth = 0;
        containerGl.marginHeight = 0;
        oauthContainer.setLayout(containerGl);
        oauthContainer.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false));

        buildOAuthRows();

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

    public void refreshOAuth() {
        for (var child : oauthContainer.getChildren()) {
            child.dispose();
        }
        buildOAuthRows();
        card.layout(true, true);
    }

    // ── helpers ───────────────────────────────────────────────────────

    private void buildOAuthRows() {
        List<RecoveryConfig> configured = vaultManager.getRecoveryConfigs().stream()
                .filter(rc -> rc.b64_uid_hash != null && !rc.b64_uid_hash.isEmpty()).toList();
        if (configured.isEmpty()) {
            return;
        }

        Label oauthHint = new Label(oauthContainer, SWT.WRAP);
        oauthHint.setText(i18n("unlock.oauth.hint"));
        oauthHint.setForeground(oauthContainer.getDisplay().getSystemColor(SWT.COLOR_DARK_GRAY));
        GridData oauthHintGd = new GridData(SWT.CENTER, SWT.CENTER, true, false);
        oauthHintGd.widthHint = 320;
        oauthHint.setLayoutData(oauthHintGd);

        Composite table = new Composite(oauthContainer, SWT.NONE);
        GridLayout tableGl = new GridLayout(3, false);
        tableGl.marginWidth = 0;
        tableGl.marginHeight = 0;
        tableGl.horizontalSpacing = 8;
        tableGl.verticalSpacing = 6;
        table.setLayout(tableGl);
        table.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false));

        for (RecoveryConfig rc : configured) {
            String provider = rc.oauth_provider;
            String displayProvider = Character.toUpperCase(provider.charAt(0)) + provider.substring(1);
            String name = rc.oauth_name != null && !rc.oauth_name.isEmpty() ? rc.oauth_name : "";
            String email = rc.oauth_email != null && !rc.oauth_email.isEmpty() ? rc.oauth_email : "";
            String userInfo = "";
            if (!name.isEmpty() && !email.isEmpty()) {
                userInfo = name + " <" + email + ">";
            } else if (!email.isEmpty()) {
                userInfo = email;
            } else if (!name.isEmpty()) {
                userInfo = name;
            }
            String url = "http://127.0.0.1:" + Daemon.PORT + "/oauth/" + provider + "/start?recover=true";

            Label providerLabel = new Label(table, SWT.NONE);
            providerLabel.setText(displayProvider);

            Label userLabel = new Label(table, SWT.NONE);
            userLabel.setText(userInfo);

            Link loginLink = new Link(table, SWT.NONE);
            loginLink.setText("<a>Login</a>");
            loginLink.addListener(SWT.Selection, _ -> Program.launch(url));
        }
    }

    private void submit() {
        if (onSubmit != null)
            onSubmit.accept(passwordText.getText());
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
