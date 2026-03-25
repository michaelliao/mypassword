package org.puppylab.mypassword.ui.view;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.puppylab.mypassword.rpc.data.LoginItemData;

public class EditView {

    public final Composite composite;

    private final Text titleField;
    private final Text usernameField;
    private final Text passwordField;
    private final Text websitesField;
    private final Text memoField;

    private Consumer<LoginItemData> onSave;
    private Runnable                onCancel;

    private long editingId = 0; // 0 = new item

    public EditView(Composite parent) {
        composite = new Composite(parent, SWT.NONE);
        composite.setLayout(new GridLayout(1, false));

        Composite actions = new Composite(composite, SWT.NONE);
        RowLayout rl = new RowLayout();
        rl.spacing = 10;
        actions.setLayout(rl);

        Button btnSave = new Button(actions, SWT.PUSH);
        btnSave.setText(" Save ");
        btnSave.addListener(SWT.Selection, e -> { if (onSave != null) onSave.accept(collectData()); });

        Button btnCancel = new Button(actions, SWT.PUSH);
        btnCancel.setText(" Cancel ");
        btnCancel.addListener(SWT.Selection, e -> { if (onCancel != null) onCancel.run(); });

        new Label(composite, SWT.SEPARATOR | SWT.HORIZONTAL)
                .setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        titleField    = createField(composite, "Title:", SWT.BORDER);
        usernameField = createField(composite, "Username:", SWT.BORDER);
        passwordField = createField(composite, "Password:", SWT.BORDER | SWT.PASSWORD);
        websitesField = createField(composite, "Websites:", SWT.BORDER);
        memoField     = createMemoField(composite, "Memo:");
    }

    /** Populate form for editing an existing item, or pass null for a new item. */
    public void edit(LoginItemData item) {
        if (item == null) {
            editingId = 0;
            titleField.setText("");
            usernameField.setText("");
            passwordField.setText("");
            websitesField.setText("");
            memoField.setText("");
        } else {
            editingId = item.id;
            titleField.setText(notNull(item.title));
            usernameField.setText(notNull(item.username));
            passwordField.setText(notNull(item.password));
            websitesField.setText(item.websites != null ? String.join(", ", item.websites) : "");
            memoField.setText(notNull(item.memo));
        }
    }

    public void setOnSave(Consumer<LoginItemData> listener) {
        this.onSave = listener;
    }

    public void setOnCancel(Runnable listener) {
        this.onCancel = listener;
    }

    private LoginItemData collectData() {
        LoginItemData data = new LoginItemData();
        data.id       = editingId;
        data.title    = titleField.getText().strip();
        data.username = usernameField.getText().strip();
        data.password = passwordField.getText();
        String ws = websitesField.getText().strip();
        data.websites = ws.isEmpty() ? List.of()
                : Arrays.stream(ws.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
        data.memo = memoField.getText();
        return data;
    }

    private Text createField(Composite parent, String labelText, int textStyle) {
        Composite row = new Composite(parent, SWT.NONE);
        row.setLayout(new GridLayout(2, false));
        row.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Label lbl = new Label(row, SWT.NONE);
        lbl.setText(labelText);
        GridData ld = new GridData();
        ld.widthHint = 80;
        lbl.setLayoutData(ld);

        Text t = new Text(row, textStyle);
        t.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        return t;
    }

    private Text createMemoField(Composite parent, String labelText) {
        Composite row = new Composite(parent, SWT.NONE);
        row.setLayout(new GridLayout(2, false));
        row.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        Label lbl = new Label(row, SWT.NONE);
        lbl.setText(labelText);
        GridData ld = new GridData(SWT.LEFT, SWT.TOP, false, false);
        ld.widthHint = 80;
        lbl.setLayoutData(ld);

        Text t = new Text(row, SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL);
        GridData td = new GridData(SWT.FILL, SWT.FILL, true, false);
        td.heightHint = 80;
        t.setLayoutData(td);
        return t;
    }

    private String notNull(String s) {
        return s != null ? s : "";
    }
}
