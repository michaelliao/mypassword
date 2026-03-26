package org.puppylab.mypassword.ui.view;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.puppylab.mypassword.rpc.data.LoginItemData;
import org.puppylab.mypassword.ui.util.StringUtils;

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
        websitesMultiFields = createMultiTextFields();
        memoField = createAreaField("Memo:");
    }

    private MultiText createMultiTextFields() {
        // outer row matches the visual style of createField() rows
        Composite row = new Composite(content, SWT.NONE);
        row.setLayout(new GridLayout(2, false));
        row.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        Label lbl = new Label(row, SWT.NONE);
        lbl.setText("Websites:");
        GridData ld = new GridData(SWT.LEFT, SWT.TOP, false, false);
        ld.widthHint = 80;
        lbl.setLayoutData(ld);

        Composite right = new Composite(row, SWT.NONE);
        GridLayout rl = new GridLayout(1, false);
        rl.marginHeight = 0;
        rl.marginWidth = 0;
        rl.verticalSpacing = 4;
        right.setLayout(rl);
        right.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        List<Text> websiteFields = new ArrayList<>();
        Composite websitesContainer = new Composite(right, SWT.NONE);
        GridLayout wl = new GridLayout(1, false);
        wl.marginHeight = 0;
        wl.marginWidth = 0;
        wl.verticalSpacing = 3;
        websitesContainer.setLayout(wl);
        websitesContainer.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        Button addWebsiteBtn = new Button(right, SWT.PUSH);
        MultiText multiText = new MultiText(websitesContainer, websiteFields, addWebsiteBtn);

        addWebsiteBtn.setText("+ Add more");
        addWebsiteBtn.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
        addWebsiteBtn.addListener(SWT.Selection, _ -> addMultiTextRow(multiText, "", true));
        return multiText;
    }

    /** Add one text row. Pass doRelayout=false when bulk-populating. */
    private void addMultiTextRow(MultiText multiText, String value, boolean doRelayout) {
        Composite row = new Composite(multiText.container(), SWT.NONE);
        GridLayout rl = new GridLayout(2, false);
        rl.marginHeight = 0;
        rl.marginWidth = 0;
        row.setLayout(rl);
        row.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Text t = new Text(row, SWT.BORDER);
        t.setText(value);
        t.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        multiText.fields().add(t);

        Button removeBtn = new Button(row, SWT.PUSH);
        removeBtn.setText(" × ");
        removeBtn.addListener(SWT.Selection, _ -> {
            multiText.fields().remove(t);
            row.dispose();
            updateMultiTextAddButton(multiText);
            relayoutMultiText(multiText);
        });

        updateMultiTextAddButton(multiText);
        if (doRelayout) {
            relayoutMultiText(multiText);
        }
    }

    private void updateMultiTextAddButton(MultiText multiText) {
        if (multiText.addBtn() != null && !multiText.addBtn().isDisposed()) {
            multiText.addBtn().setEnabled(multiText.fields().size() < 10);
        }
    }

    private void relayoutMultiText(MultiText multiText) {
        multiText.container().layout(true, true);
        content.layout(true, true);
        sc.setMinSize(content.computeSize(sc.getClientArea().width, SWT.DEFAULT));
    }

    @Override
    protected void setData(LoginItemData item) {
        // Dispose all existing website rows
        for (Text t : websitesMultiFields.fields()) {
            if (!t.isDisposed()) {
                t.getParent().dispose();
            }
        }
        websitesMultiFields.fields().clear();

        if (item == null) {
            editingId = 0;
            titleField.setText("");
            usernameField.setText("");
            passwordField.setText("");
            addMultiTextRow(websitesMultiFields, "", false);
            memoField.setText("");
        } else {
            editingId = item.id;
            titleField.setText(StringUtils.normalize(item.title));
            usernameField.setText(StringUtils.normalize(item.username));
            passwordField.setText(StringUtils.normalize(item.password));
            if (item.websites != null && !item.websites.isEmpty()) {
                for (String url : item.websites)
                    addMultiTextRow(websitesMultiFields, url, false);
            } else {
                addMultiTextRow(websitesMultiFields, "", false);
            }
            memoField.setText(StringUtils.normalize(item.memo));
        }
        updateMultiTextAddButton(websitesMultiFields);
    }

    @Override
    protected LoginItemData collectData() {
        LoginItemData data = new LoginItemData();
        data.id = editingId;
        data.title = titleField.getText().strip();
        data.username = usernameField.getText().strip();
        data.password = passwordField.getText();
        data.websites = websitesMultiFields.fields().stream().map(t -> t.getText().strip()).filter(s -> !s.isEmpty())
                .toList();
        data.memo = memoField.getText();
        return data;
    }
}
