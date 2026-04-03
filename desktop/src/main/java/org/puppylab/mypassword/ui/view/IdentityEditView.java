package org.puppylab.mypassword.ui.view;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;
import org.puppylab.mypassword.core.data.IdentityFieldsData;
import org.puppylab.mypassword.core.data.IdentityItemData;
import org.puppylab.mypassword.core.data.ItemType;
import org.puppylab.mypassword.util.StringUtils;

public class IdentityEditView extends AbstractEditView<IdentityItemData> {

    private Text nameField;
    private Text taxNumberField;
    private Text passportField;
    private Text identityNumberField;

    private MultiText telephonesMultiFields;
    private MultiText mobilesMultiFields;

    private IdentityItemData editingItem = null;

    public IdentityEditView(Composite parent) {
        super(parent);
    }

    @Override
    protected void createFields() {
        nameField = createField("Name:", SWT.BORDER);
        passportField = createField("Passport:", SWT.BORDER);
        identityNumberField = createField("ID Number:", SWT.BORDER);
        taxNumberField = createField("Tax Number:", SWT.BORDER);
        mobilesMultiFields = createMultiTextFields("Mobiles:");
        telephonesMultiFields = createMultiTextFields("Telephones:");
    }

    /**
     * Populate form for editing an existing identity, or pass null for a new one.
     */
    @Override
    protected void setData(IdentityItemData item) {
        // Dispose all existing telephones/mobiles rows
        telephonesMultiFields.disposeFields();
        mobilesMultiFields.disposeFields();

        editingItem = item;
        if (item == null) {
            nameField.setText("");
            passportField.setText("");
            identityNumberField.setText("");
            taxNumberField.setText("");
            addMultiTextRow(telephonesMultiFields, "", false);
            addMultiTextRow(mobilesMultiFields, "", false);
        } else {
            nameField.setText(StringUtils.normalize(item.data.name));
            passportField.setText(StringUtils.normalize(item.data.passport_number));
            identityNumberField.setText(StringUtils.normalize(item.data.identity_number));
            taxNumberField.setText(StringUtils.normalize(item.data.tax_number));
            setMultiTextValues(mobilesMultiFields, item.data.mobiles);
            setMultiTextValues(telephonesMultiFields, item.data.telephones);
        }
        updateMultiTextAddButton(telephonesMultiFields);
        updateMultiTextAddButton(mobilesMultiFields);
    }

    @Override
    protected IdentityItemData collectData() {
        IdentityItemData data = editingItem != null ? editingItem : new IdentityItemData();
        data.item_type = ItemType.IDENTITY;
        data.data = new IdentityFieldsData();
        data.data.name = nameField.getText().strip();
        data.data.passport_number = passportField.getText().strip();
        data.data.identity_number = identityNumberField.getText().strip();
        data.data.tax_number = taxNumberField.getText().strip();
        data.data.mobiles = mobilesMultiFields.collectData();
        data.data.telephones = telephonesMultiFields.collectData();
        return data;
    }
}
