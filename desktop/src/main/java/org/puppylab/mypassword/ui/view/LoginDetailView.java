package org.puppylab.mypassword.ui.view;

import static org.puppylab.mypassword.util.I18nUtils.i18n;

import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
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
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.ToolTip;
import org.puppylab.mypassword.core.ClearPasswordThread;
import org.puppylab.mypassword.core.data.LoginItemData;
import org.puppylab.mypassword.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoginDetailView extends AbstractDetailView<LoginItemData> {

    private static final String MASKED = "\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private Label      titleValue;
    private Label      usernameValue;
    private Label      passwordValue;
    private MultiLabel websitesContainer;
    private Label      memoValue;

    private String  plainPassword   = "";
    private boolean passwordVisible = false;

    private MenuItem showHideItem;
    private Button   copyBtn;

    public LoginDetailView(Composite parent) {
        super(parent);
    }

    @Override
    protected void createFields() {
        titleValue = createField(i18n("field.title"));
        usernameValue = createField(i18n("field.username"));
        createPasswordField();
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
        Composite btnGroup = new Composite(valueCell, SWT.NONE);
        GridLayout btnGl = new GridLayout(2, false);
        btnGl.marginWidth = 0;
        btnGl.marginHeight = 0;
        btnGl.horizontalSpacing = 0;
        btnGroup.setLayout(btnGl);

        copyBtn = new Button(btnGroup, SWT.PUSH);
        copyBtn.setText(i18n("password.btn.copy"));
        copyBtn.addListener(SWT.Selection, _ -> copyPassword());

        Button arrowBtn = new Button(btnGroup, SWT.PUSH);
        arrowBtn.setText("\u25BE");

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

    @Override
    protected void setData(LoginItemData item) {
        titleValue.setText(StringUtils.normalize(item.data.title));
        usernameValue.setText(StringUtils.normalize(item.data.username));
        plainPassword = item.data.password != null ? item.data.password : "";
        passwordVisible = false;
        passwordValue.setText(plainPassword.isEmpty() ? "" : MASKED);
        showHideItem.setText(i18n("password.menu.show"));
        websitesContainer.setValues(item.data.websites);
        memoValue.setText(StringUtils.normalize(item.data.memo));
    }

    private void copyPassword() {
        if (plainPassword.isEmpty())
            return;
        Display display = Display.getCurrent();
        Clipboard cb = new Clipboard(display);
        cb.setContents(new Object[] { plainPassword }, new Transfer[] { TextTransfer.getInstance() });
        cb.dispose();
        logger.info("password copied.");
        ClearPasswordThread.scheduleClear(plainPassword);
        // show tooltip below copy button:
        ToolTip tip = new ToolTip(composite.getShell(), SWT.ICON_INFORMATION);
        tip.setMessage(i18n("password.tip.copied"));
        Rectangle rect = copyBtn.getBounds();
        Point loc = copyBtn.getParent().toDisplay(rect.x, rect.y + rect.height);
        tip.setLocation(loc);
        tip.setVisible(true);
        display.timerExec(3000, () -> {
            if (!tip.isDisposed())
                tip.dispose();
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
