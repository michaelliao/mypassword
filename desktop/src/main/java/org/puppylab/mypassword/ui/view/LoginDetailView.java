package org.puppylab.mypassword.ui.view;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.puppylab.mypassword.core.data.LoginItemData;
import org.puppylab.mypassword.util.StringUtils;

public class LoginDetailView extends AbstractDetailView<LoginItemData> {

    private Label      titleValue;
    private Label      usernameValue;
    private Label      passwordValue;
    private MultiLabel websitesContainer;
    private Label      memoValue;

    public LoginDetailView(Composite parent) {
        super(parent);
    }

    @Override
    protected void createFields() {
        titleValue = createField("Title:");
        usernameValue = createField("Username:");
        passwordValue = createField("Password:");
        websitesContainer = createMultiValueField("Websites:");
        memoValue = createField("Memo:");
    }

    @Override
    protected void setData(LoginItemData item) {
        titleValue.setText(StringUtils.normalize(item.data.title));
        usernameValue.setText(StringUtils.normalize(item.data.username));
        passwordValue.setText(item.data.password != null && !item.data.password.isEmpty()
                ? "\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022"
                : "");
        websitesContainer.setValues(item.data.websites);
        memoValue.setText(StringUtils.normalize(item.data.memo));
    }
}
