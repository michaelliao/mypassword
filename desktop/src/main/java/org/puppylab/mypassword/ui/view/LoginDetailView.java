package org.puppylab.mypassword.ui.view;

import static org.puppylab.mypassword.util.I18nUtils.i18n;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.ToolTip;
import org.puppylab.mypassword.core.data.LoginItemData;
import org.puppylab.mypassword.core.data.PasskeyData;
import org.puppylab.mypassword.core.data.TotpData;
import org.puppylab.mypassword.ui.Icons;
import org.puppylab.mypassword.util.ClipboardUtils;
import org.puppylab.mypassword.util.StringUtils;
import org.puppylab.mypassword.util.TotpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoginDetailView extends AbstractDetailView<LoginItemData> {

    private static final String MASKED = "\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private Label      titleValue;
    private Label      usernameValue;
    private Label      passwordValue;
    private Label      passkeyValue;
    private MultiLabel websitesContainer;
    private Label      memoValue;

    private String  plainPassword   = "";
    private boolean passwordVisible = false;

    private MenuItem  showHideItem;
    private Button    copyBtn;
    private Button    arrowBtn;
    private Composite btnGroup;

    private Composite   totpRow;
    private Label       totpCodeLabel;
    private ProgressBar totpProgress;
    private Button      totpCopyBtn;
    private TotpData    currentTotp;
    private boolean     totpTimerRunning;

    public LoginDetailView(Composite parent) {
        super(parent);
    }

    @Override
    protected void createFields() {
        titleValue = createField(i18n("field.title"));
        usernameValue = createField(i18n("field.username"));
        createPasswordField();
        createTotpField();
        passkeyValue = createField(i18n("field.passkey"));
        websitesContainer = createMultiValueField(i18n("field.websites"));
        memoValue = createField(i18n("field.memo"));
    }

    private void createPasswordField() {
        // same 2-column layout as createField:
        Composite row = new Composite(content, SWT.NONE);
        row.setLayout(new GridLayout(2, false));
        row.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Label lbl = new Label(row, SWT.NONE);
        lbl.setText(i18n("field.password"));
        GridData ld = new GridData();
        ld.widthHint = 80;
        lbl.setLayoutData(ld);

        // value cell: password label + buttons in one row
        Composite valueCell = new Composite(row, SWT.NONE);
        GridLayout vcl = new GridLayout(3, false);
        vcl.marginWidth = 0;
        vcl.marginHeight = 0;
        valueCell.setLayout(vcl);
        valueCell.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        passwordValue = new Label(valueCell, SWT.WRAP);
        passwordValue.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        // Copy button with dropdown arrow:
        btnGroup = new Composite(valueCell, SWT.NONE);
        btnGroup.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
        GridLayout btnGl = new GridLayout(2, false);
        btnGl.marginWidth = 0;
        btnGl.marginHeight = 0;
        btnGl.horizontalSpacing = 0;
        btnGroup.setLayout(btnGl);

        copyBtn = new Button(btnGroup, SWT.PUSH);
        copyBtn.setText(i18n("password.btn.copy"));
        copyBtn.setImage(Icons.get("copy"));
        copyBtn.addListener(SWT.Selection, _ -> copyPassword());

        arrowBtn = new Button(btnGroup, SWT.PUSH);
        arrowBtn.setImage(Icons.get("view_down"));

        Menu menu = new Menu(arrowBtn);
        showHideItem = new MenuItem(menu, SWT.PUSH);
        showHideItem.setText(i18n("password.menu.show"));
        showHideItem.addListener(SWT.Selection, _ -> togglePasswordVisibility());

        MenuItem largeItem = new MenuItem(menu, SWT.PUSH);
        largeItem.setText(i18n("password.menu.show_large"));
        largeItem.addListener(SWT.Selection, _ -> showLargePassword());

        arrowBtn.addListener(SWT.Selection, _ -> {
            Rectangle rect = arrowBtn.getBounds();
            Point pt = arrowBtn.getParent().toDisplay(rect.x, rect.y + rect.height);
            menu.setLocation(pt);
            menu.setVisible(true);
        });
    }

    private void createTotpField() {
        totpRow = new Composite(content, SWT.NONE);
        totpRow.setLayout(new GridLayout(2, false));
        totpRow.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Label lbl = new Label(totpRow, SWT.NONE);
        lbl.setText(i18n("field.totp"));
        GridData ld = new GridData();
        ld.widthHint = 80;
        lbl.setLayoutData(ld);

        // value cell: code + progress | copy button (matches password row layout)
        Composite valueCell = new Composite(totpRow, SWT.NONE);
        GridLayout vcl = new GridLayout(3, false);
        vcl.marginWidth = 0;
        vcl.marginHeight = 0;
        valueCell.setLayout(vcl);
        valueCell.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        totpCodeLabel = new Label(valueCell, SWT.NONE);
        Font monoFont = new Font(valueCell.getDisplay(), new FontData("Courier New", 12, SWT.BOLD));
        totpCodeLabel.setFont(monoFont);
        totpCodeLabel.addListener(SWT.Dispose, _ -> monoFont.dispose());
        totpCodeLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));

        totpProgress = new ProgressBar(valueCell, SWT.HORIZONTAL | SWT.SMOOTH);
        GridData pgd = new GridData(SWT.LEFT, SWT.CENTER, true, false);
        pgd.widthHint = 60;
        pgd.heightHint = 14;
        totpProgress.setLayoutData(pgd);

        totpCopyBtn = new Button(valueCell, SWT.PUSH);
        totpCopyBtn.setText(i18n("password.btn.copy"));
        totpCopyBtn.setImage(Icons.get("copy"));
        totpCopyBtn.addListener(SWT.Selection, _ -> copyTotp());
    }

    private void updateTotpRow(TotpData totp) {
        currentTotp = totp;
        GridData rowGd = (GridData) totpRow.getLayoutData();
        if (totp == null) {
            totpRow.setVisible(false);
            rowGd.exclude = true;
            stopTotpTimer();
            return;
        }
        totpRow.setVisible(true);
        rowGd.exclude = false;
        totpProgress.setMaximum(totp.period);
        refreshTotp();
        startTotpTimer();
    }

    private void refreshTotp() {
        if (currentTotp == null || totpCodeLabel.isDisposed()) return;
        String code = TotpUtils.getTotp(currentTotp);
        totpCodeLabel.setText(code);
        int elapsed = (int) ((System.currentTimeMillis() / 1000) % currentTotp.period);
        int remaining = currentTotp.period - elapsed;
        totpProgress.setSelection(remaining);
        totpRow.layout(true, true);
    }

    private void startTotpTimer() {
        if (totpTimerRunning) return;
        totpTimerRunning = true;
        Display display = Display.getCurrent();
        Runnable tick = new Runnable() {
            @Override
            public void run() {
                if (!totpTimerRunning || totpCodeLabel.isDisposed()) return;
                refreshTotp();
                display.timerExec(1000, this);
            }
        };
        display.timerExec(1000, tick);
    }

    private void stopTotpTimer() {
        totpTimerRunning = false;
    }

    private void copyTotp() {
        if (currentTotp == null) return;
        String code = TotpUtils.getTotp(currentTotp);
        ClipboardUtils.copyPassword(code);
        Display display = Display.getCurrent();
        logger.info("TOTP code copied.");
        totpCopyBtn.setImage(Icons.get("copied"));
        ToolTip tip = new ToolTip(composite.getShell(), SWT.ICON_INFORMATION);
        tip.setMessage(i18n("password.tip.copied"));
        Rectangle rect = totpCopyBtn.getBounds();
        Point loc = totpCopyBtn.getParent().toDisplay(rect.x, rect.y + rect.height);
        tip.setLocation(loc);
        tip.setVisible(true);
        display.timerExec(2000, () -> {
            if (!tip.isDisposed()) tip.dispose();
            if (!totpCopyBtn.isDisposed()) totpCopyBtn.setImage(Icons.get("copy"));
        });
    }

    @Override
    protected void setData(LoginItemData item) {
        titleValue.setText(StringUtils.normalize(item.data.title));
        usernameValue.setText(StringUtils.normalize(item.data.username));
        plainPassword = item.data.password != null ? item.data.password : "";
        passwordVisible = false;
        passwordValue.setText(plainPassword.isEmpty() ? "" : MASKED);
        showHideItem.setText(i18n("password.menu.show"));
        boolean hasPassword = !plainPassword.isEmpty();
        btnGroup.setVisible(hasPassword);
        ((GridData) btnGroup.getLayoutData()).exclude = !hasPassword;
        btnGroup.getParent().layout(true, true);

        // TOTP row
        updateTotpRow(item.data.totp);

        // Passkey row — hidden entirely when the login has no passkey.
        PasskeyData passkey = item.data.passkey;
        Composite passkeyRow = passkeyValue.getParent();
        GridData passkeyRowData = (GridData) passkeyRow.getLayoutData();
        if (passkey != null) {
            passkeyValue.setText(formatPasskey(passkey));
            passkeyRow.setVisible(true);
            passkeyRowData.exclude = false;
        } else {
            passkeyValue.setText("");
            passkeyRow.setVisible(false);
            passkeyRowData.exclude = true;
        }

        websitesContainer.setValues(item.data.websites);
        memoValue.setText(StringUtils.normalize(item.data.memo));
    }

    private static String formatPasskey(PasskeyData p) {
        String user = StringUtils.normalize(p.username);
        String display = StringUtils.normalize(p.displayName);
        if (user.isEmpty() && display.isEmpty()) {
            return "";
        }
        if (user.isEmpty()) {
            return display;
        }
        if (display.isEmpty() || user.equals(display)) {
            return user;
        }
        return user + " / " + display;
    }

    private void copyPassword() {
        if (plainPassword.isEmpty())
            return;
        ClipboardUtils.copyPassword(plainPassword);
        Display display = Display.getCurrent();
        logger.info("password copied.");
        copyBtn.setImage(Icons.get("copied"));
        // show tooltip below copy button:
        ToolTip tip = new ToolTip(composite.getShell(), SWT.ICON_INFORMATION);
        tip.setMessage(i18n("password.tip.copied"));
        Rectangle rect = copyBtn.getBounds();
        Point loc = copyBtn.getParent().toDisplay(rect.x, rect.y + rect.height);
        tip.setLocation(loc);
        tip.setVisible(true);
        display.timerExec(2000, () -> {
            if (!tip.isDisposed())
                tip.dispose();
            if (!copyBtn.isDisposed())
                copyBtn.setImage(Icons.get("copy"));
        });
    }

    private void togglePasswordVisibility() {
        if (plainPassword.isEmpty())
            return;
        passwordVisible = !passwordVisible;
        passwordValue.setText(passwordVisible ? plainPassword : MASKED);
        showHideItem.setText(passwordVisible ? i18n("password.menu.hide") : i18n("password.menu.show"));
        content.layout(true, true);
        sc.setMinSize(content.computeSize(sc.getClientArea().width, SWT.DEFAULT));
    }

    private void showLargePassword() {
        if (plainPassword.isEmpty())
            return;
        Shell parent = composite.getShell();
        Shell popup = new Shell(parent, SWT.DIALOG_TRIM);
        popup.setText(i18n("password.dialog.title"));
        popup.setLayout(new GridLayout(1, false));

        Label label = new Label(popup, SWT.WRAP);
        label.setText(plainPassword);
        Font largeFont = new Font(popup.getDisplay(), new FontData("Courier New", 24, SWT.NORMAL));
        label.setFont(largeFont);
        GridData gd = new GridData(SWT.FILL, SWT.FILL, true, true);
        gd.widthHint = 400;
        label.setLayoutData(gd);

        popup.addListener(SWT.Dispose, _ -> largeFont.dispose());

        popup.pack();
        // center on parent:
        Rectangle pb = parent.getBounds();
        Point ps = popup.getSize();
        popup.setLocation(pb.x + (pb.width - ps.x) / 2, pb.y + (pb.height - ps.y) / 2);
        popup.open();
    }

}
