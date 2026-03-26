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

    private MultiText telephonesMultiFields;
    private MultiText mobilesMultiFields;

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
        telephonesMultiFields = createMultiTextFields("Telephones:");
        mobilesMultiFields = createMultiTextFields("Mobiles:");
    }

    /**
     * Populate form for editing an existing identity, or pass null for a new one.
     */
    @Override
    protected void setData(IdentityItemData item) {
        // Dispose all existing telephones/mobiles rows
        telephonesMultiFields.disposeFields();
        mobilesMultiFields.disposeFields();

        if (item == null) {
            editingId = 0;
            titleField.setText("");
            nameField.setText("");
            passportField.setText("");
            identityNumberField.setText("");
            taxNumberField.setText("");
            addMultiTextRow(telephonesMultiFields, "", false);
            addMultiTextRow(mobilesMultiFields, "", false);
        } else {
            editingId = item.id;
            titleField.setText(StringUtils.normalize(item.title));
            nameField.setText(StringUtils.normalize(item.name));
            passportField.setText(StringUtils.normalize(item.passport_number));
            identityNumberField.setText(StringUtils.normalize(item.identity_number));
            taxNumberField.setText(StringUtils.normalize(item.tax_number));
            setMultiTextValues(telephonesMultiFields, item.telephones);
            setMultiTextValues(mobilesMultiFields, item.mobiles);
        }
        updateMultiTextAddButton(telephonesMultiFields);
        updateMultiTextAddButton(mobilesMultiFields);
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
        data.telephones = telephonesMultiFields.collectData();
        data.mobiles = mobilesMultiFields.collectData();
        return data;
    }
}
