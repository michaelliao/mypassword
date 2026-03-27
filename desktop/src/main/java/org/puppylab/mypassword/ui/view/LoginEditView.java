package org.puppylab.mypassword.ui.view;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;
import org.puppylab.mypassword.rpc.data.LoginFieldsData;
import org.puppylab.mypassword.rpc.data.LoginItemData;
import org.puppylab.mypassword.rpc.util.StringUtils;

public class LoginEditView extends AbstractEditView<LoginItemData> {

    private Text titleField;
    private Text usernameField;
    private Text passwordField;
    private Text memoField;

    private MultiText websitesMultiFields;

    private long editingId = 0;

    public LoginEditView(Composite parent) {
        super(parent);
    }

    @Override
    protected void createFields() {
        titleField = createField("Title:", SWT.BORDER);
        usernameField = createField("Username:", SWT.BORDER);
        passwordField = createField("Password:", SWT.BORDER | SWT.PASSWORD);
        websitesMultiFields = createMultiTextFields("Websites:");
        memoField = createAreaField("Memo:");
    }

    @Override
    protected void setData(LoginItemData item) {
        // Dispose all existing website rows
        websitesMultiFields.disposeFields();

        if (item == null) {
            editingId = 0;
            titleField.setText("");
            usernameField.setText("");
            passwordField.setText("");
            addMultiTextRow(websitesMultiFields, "", false);
            memoField.setText("");
        } else {
            editingId = item.id;
            titleField.setText(StringUtils.normalize(item.data.title));
            usernameField.setText(StringUtils.normalize(item.data.username));
            passwordField.setText(StringUtils.normalize(item.data.password));
            setMultiTextValues(websitesMultiFields, item.data.websites);
            memoField.setText(StringUtils.normalize(item.data.memo));
        }
        updateMultiTextAddButton(websitesMultiFields);
    }

    @Override
    protected LoginItemData collectData() {
        LoginItemData data = new LoginItemData();
        data.id = editingId;
        data.data = new LoginFieldsData();
        data.data.title = titleField.getText().strip();
        data.data.username = usernameField.getText().strip();
        data.data.password = passwordField.getText();
        data.data.websites = websitesMultiFields.collectData();
        data.data.memo = memoField.getText();
        return data;
    }
}
