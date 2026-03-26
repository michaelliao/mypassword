package org.puppylab.mypassword.ui.view;

import java.util.Arrays;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;
import org.puppylab.mypassword.rpc.data.LoginItemData;

public class LoginEditView extends AbstractEditView<LoginItemData> {

    private Text titleField;
    private Text usernameField;
    private Text passwordField;
    private Text websitesField;
    private Text memoField;

    private long editingId = 0;

    public LoginEditView(Composite parent) {
        super(parent);
    }

    @Override
    protected void createFields() {
        titleField = createField(content, "Title:", SWT.BORDER);
        usernameField = createField(content, "Username:", SWT.BORDER);
        passwordField = createField(content, "Password:", SWT.BORDER | SWT.PASSWORD);
        websitesField = createField(content, "Websites:", SWT.BORDER);
        memoField = createAreaField(content, "Memo:");
    }

    /** Populate form for editing an existing item, or pass null for a new item. */
    @Override
    protected void doEdit(LoginItemData item) {
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

    @Override
    protected LoginItemData collectData() {
        LoginItemData data = new LoginItemData();
        data.id = editingId;
        data.title = titleField.getText().strip();
        data.username = usernameField.getText().strip();
        data.password = passwordField.getText();
        String ws = websitesField.getText().strip();
        data.websites = ws.isEmpty() ? List.of()
                : Arrays.stream(ws.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
        data.memo = memoField.getText();
        return data;
    }

    private String notNull(String s) {
        return s != null ? s : "";
    }
}
