package org.puppylab.mypassword.ui.view;

import static org.puppylab.mypassword.util.I18nUtils.i18n;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.puppylab.mypassword.core.data.IdentityItemData;
import org.puppylab.mypassword.util.StringUtils;

public class IdentityDetailView extends AbstractDetailView<IdentityItemData> {

    private Label      nameValue;
    private Label      passportValue;
    private Label      identityNumberValue;
    private Label      taxNumberValue;
    private MultiLabel telephonesValue;
    private MultiLabel mobilesValue;

    public IdentityDetailView(Composite parent) {
        super(parent);
    }

    @Override
    protected void createFields() {
        nameValue = createField(i18n("field.name"));
        passportValue = createField(i18n("field.passport"));
        identityNumberValue = createField(i18n("field.id_number"));
        taxNumberValue = createField(i18n("field.tax_number"));
        telephonesValue = createMultiValueField(i18n("field.telephones"));
        mobilesValue = createMultiValueField(i18n("field.mobiles"));
    }

    @Override
    protected void setData(IdentityItemData item) {
        nameValue.setText(StringUtils.normalize(item.data.name));
        passportValue.setText(StringUtils.normalize(item.data.passport_number));
        identityNumberValue.setText(StringUtils.normalize(item.data.identity_number));
        taxNumberValue.setText(StringUtils.normalize(item.data.tax_number));
        telephonesValue.setValues(item.data.telephones);
        mobilesValue.setValues(item.data.mobiles);
    }
}
