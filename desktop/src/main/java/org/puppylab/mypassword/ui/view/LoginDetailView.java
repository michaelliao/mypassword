package org.puppylab.mypassword.ui.view;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.puppylab.mypassword.rpc.data.LoginItemData;
import org.puppylab.mypassword.ui.util.StringUtils;

public class LoginDetailView extends AbstractDetailView<LoginItemData> {

    private Label titleValue;
    private Label usernameValue;
    private Label passwordValue;
    private Label websitesValue;
    private Label memoValue;

    public LoginDetailView(Composite parent) {
        super(parent);
    }

    @Override
    protected void createFields() {
        titleValue = createField("Title:");
        usernameValue = createField("Username:");
        passwordValue = createField("Password:");
        websitesValue = createField("Websites:");
        memoValue = createField("Memo:");
    }

    protected void setData(LoginItemData item) {
        titleValue.setText(StringUtils.normalize(item.title));
        usernameValue.setText(StringUtils.normalize(item.username));
        passwordValue.setText(
                item.password != null && !item.password.isEmpty() ? "\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022"
                        : "");
        websitesValue.setText(item.websites != null ? String.join(", ", item.websites) : "");
        memoValue.setText(StringUtils.normalize(item.memo));
    }
}
