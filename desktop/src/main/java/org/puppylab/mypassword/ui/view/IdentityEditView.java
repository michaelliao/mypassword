package org.puppylab.mypassword.ui.view;

import static org.puppylab.mypassword.util.I18nUtils.i18n;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;
import org.puppylab.mypassword.core.data.IdentityFieldsData;
import org.puppylab.mypassword.core.data.IdentityItemData;
import org.puppylab.mypassword.core.data.ItemType;
import org.puppylab.mypassword.util.StringUtils;

public class IdentityEditView extends AbstractEditView<IdentityItemData> {

    private Text nameField;
    private Text emailField;
    private Text taxNumberField;
    private Text passportField;
    private Text identityNumberField;
    private Text addressField;
    private Text zipCodeField;
    private Text memoField;

    private MultiText telephonesMultiFields;
    private MultiText mobilesMultiFields;

    private IdentityItemData editingItem = null;

    public IdentityEditView(Composite parent) {
        super(parent);
    }

    @Override
    protected void createFields() {
        nameField = createField(i18n("field.name"), SWT.BORDER);
        emailField = createField(i18n("field.email"), SWT.BORDER);
        passportField = createField(i18n("field.passport"), SWT.BORDER);
        identityNumberField = createField(i18n("field.id_number"), SWT.BORDER);
        taxNumberField = createField(i18n("field.tax_number"), SWT.BORDER);
        mobilesMultiFields = createMultiTextFields(i18n("field.mobiles"));
        telephonesMultiFields = createMultiTextFields(i18n("field.telephones"));
        addressField = createAreaField(i18n("field.address"));
        zipCodeField = createField(i18n("field.zip_code"), SWT.BORDER);
        memoField = createAreaField(i18n("field.memo"));
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
            emailField.setText("");
            passportField.setText("");
            identityNumberField.setText("");
            taxNumberField.setText("");
            addressField.setText("");
            zipCodeField.setText("");
            memoField.setText("");
            addMultiTextRow(telephonesMultiFields, "", false);
            addMultiTextRow(mobilesMultiFields, "", false);
        } else {
            nameField.setText(StringUtils.normalize(item.data.name));
            emailField.setText(StringUtils.normalize(item.data.email));
            passportField.setText(StringUtils.normalize(item.data.passport_number));
            identityNumberField.setText(StringUtils.normalize(item.data.identity_number));
            taxNumberField.setText(StringUtils.normalize(item.data.tax_number));
            addressField.setText(StringUtils.normalize(item.data.address));
            zipCodeField.setText(StringUtils.normalize(item.data.zip_code));
            memoField.setText(StringUtils.normalize(item.data.memo));
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
        data.data.email = emailField.getText().strip();
        data.data.passport_number = passportField.getText().strip();
        data.data.identity_number = identityNumberField.getText().strip();
        data.data.tax_number = taxNumberField.getText().strip();
        data.data.mobiles = mobilesMultiFields.collectData();
        data.data.telephones = telephonesMultiFields.collectData();
        data.data.address = addressField.getText().strip();
        data.data.zip_code = zipCodeField.getText().strip();
        data.data.memo = memoField.getText().strip();
        return data;
    }
}
