package org.puppylab.mypassword.ui.view;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;
import org.puppylab.mypassword.rpc.data.IdentityItemData;
import org.puppylab.mypassword.ui.util.StringUtils;

public class IdentityEditView extends AbstractEditView<IdentityItemData> {

    private Text titleField;
    private Text nameField;
    private Text taxNumberField;
    private Text passportField;
    private Text identityNumberField;
    private Text telephonesField;
    private Text mobilesField;

    private long editingId = 0;

    public IdentityEditView(Composite parent) {
        super(parent);
    }

    @Override
    protected void createFields() {
        titleField = createField("Title:", SWT.BORDER);
        nameField = createField("Name:", SWT.BORDER);
        passportField = createField("Passport:", SWT.BORDER);
        identityNumberField = createField("ID Number:", SWT.BORDER);
        taxNumberField = createField("Tax Number:", SWT.BORDER);
        telephonesField = createField("Telephones:", SWT.BORDER);
        mobilesField = createField("Mobiles:", SWT.BORDER);
    }

    /**
     * Populate form for editing an existing identity, or pass null for a new one.
     */
    @Override
    protected void setData(IdentityItemData item) {
        if (item == null) {
            editingId = 0;
            titleField.setText("");
            nameField.setText("");
            passportField.setText("");
            identityNumberField.setText("");
            taxNumberField.setText("");
            telephonesField.setText("");
            mobilesField.setText("");
        } else {
            editingId = item.id;
            titleField.setText(StringUtils.normalize(item.title));
            nameField.setText(StringUtils.normalize(item.name));
            passportField.setText(StringUtils.normalize(item.passport_number));
            identityNumberField.setText(StringUtils.normalize(item.identity_number));
            taxNumberField.setText(StringUtils.normalize(item.tax_number));
            telephonesField.setText(item.telephones != null ? String.join(", ", item.telephones) : "");
            mobilesField.setText(item.mobiles != null ? String.join(", ", item.mobiles) : "");
        }
    }

    @Override
    protected IdentityItemData collectData() {
        IdentityItemData data = new IdentityItemData();
        data.id = editingId;
        data.title = titleField.getText().strip();
        data.name = nameField.getText().strip();
        data.passport_number = passportField.getText().strip();
        data.identity_number = identityNumberField.getText().strip();
        data.tax_number = taxNumberField.getText().strip();
        data.telephones = splitCSV(telephonesField.getText());
        data.mobiles = splitCSV(mobilesField.getText());
        return data;
    }
}
