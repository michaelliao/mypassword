package org.puppylab.mypassword.ui.view;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.puppylab.mypassword.rpc.data.LoginItemData;

public class LoginDetailView {

    public final Composite composite;

    private final Label titleValue;
    private final Label usernameValue;
    private final Label passwordValue;
    private final Label websitesValue;
    private final Label memoValue;

    private Runnable onEdit;

    public LoginDetailView(Composite parent) {
        composite = new Composite(parent, SWT.NONE);
        composite.setLayout(new GridLayout(1, false));

        Composite actions = new Composite(composite, SWT.NONE);
        actions.setLayout(new RowLayout());
        Button btnEdit = new Button(actions, SWT.PUSH);
        btnEdit.setText(" Edit ");
        btnEdit.addListener(SWT.Selection, e -> { if (onEdit != null) onEdit.run(); });

        new Label(composite, SWT.SEPARATOR | SWT.HORIZONTAL)
                .setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        titleValue    = createField(composite, "Title:",    80);
        usernameValue = createField(composite, "Username:", 80);
        passwordValue = createField(composite, "Password:", 80);
        websitesValue = createField(composite, "Websites:", 80);
        memoValue     = createField(composite, "Memo:",     80);
    }

    public void show(LoginItemData item) {
        titleValue.setText(notNull(item.title));
        usernameValue.setText(notNull(item.username));
        passwordValue.setText(item.password != null && !item.password.isEmpty()
                ? "\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022" : "");
        websitesValue.setText(item.websites != null ? String.join(", ", item.websites) : "");
        memoValue.setText(notNull(item.memo));
        composite.layout(true, true);
    }

    public void setOnEdit(Runnable listener) { this.onEdit = listener; }

    private Label createField(Composite parent, String labelText, int labelWidth) {
        Composite row = new Composite(parent, SWT.NONE);
        row.setLayout(new GridLayout(2, false));
        row.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Label lbl = new Label(row, SWT.NONE);
        lbl.setText(labelText);
        GridData ld = new GridData();
        ld.widthHint = labelWidth;
        lbl.setLayoutData(ld);

        Label value = new Label(row, SWT.NONE);
        value.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        return value;
    }

    private String notNull(String s) { return s != null ? s : ""; }
}
