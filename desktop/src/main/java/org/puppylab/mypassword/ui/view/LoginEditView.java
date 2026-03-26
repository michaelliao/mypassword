package org.puppylab.mypassword.ui.view;

import java.util.Arrays;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;
import org.puppylab.mypassword.rpc.data.LoginItemData;
import org.puppylab.mypassword.ui.util.StringUtils;

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
        titleField = createField("Title:", SWT.BORDER);
        usernameField = createField("Username:", SWT.BORDER);
        passwordField = createField("Password:", SWT.BORDER | SWT.PASSWORD);
        websitesField = createField("Websites:", SWT.BORDER);
        memoField = createAreaField("Memo:");
    }

    /** Populate form for editing an existing item, or pass null for a new item. */
    @Override
    protected void setData(LoginItemData item) {
        if (item == null) {
            editingId = 0;
            titleField.setText("");
            usernameField.setText("");
            passwordField.setText("");
            websitesField.setText("");
            memoField.setText("");
        } else {
            editingId = item.id;
            titleField.setText(StringUtils.normalize(item.title));
            usernameField.setText(StringUtils.normalize(item.username));
            passwordField.setText(StringUtils.normalize(item.password));
            websitesField.setText(item.websites != null ? String.join(", ", item.websites) : "");
            memoField.setText(StringUtils.normalize(item.memo));
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
}
