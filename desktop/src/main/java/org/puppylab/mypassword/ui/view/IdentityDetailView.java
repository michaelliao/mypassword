package org.puppylab.mypassword.ui.view;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.puppylab.mypassword.rpc.data.IdentityItemData;
import org.puppylab.mypassword.ui.util.StringUtils;

public class IdentityDetailView extends AbstractDetailView<IdentityItemData> {

    private Label      titleValue;
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
        titleValue = createField("Title:");
        nameValue = createField("Name:");
        passportValue = createField("Passport:");
        identityNumberValue = createField("ID Number:");
        taxNumberValue = createField("Tax Number:");
        telephonesValue = createMultiValueField("Telephones:");
        mobilesValue = createMultiValueField("Mobiles:");
    }

    @Override
    protected void setData(IdentityItemData item) {
        titleValue.setText(StringUtils.normalize(item.title));
        nameValue.setText(StringUtils.normalize(item.name));
        passportValue.setText(StringUtils.normalize(item.passport_number));
        identityNumberValue.setText(StringUtils.normalize(item.identity_number));
        taxNumberValue.setText(StringUtils.normalize(item.tax_number));
        telephonesValue.setValues(item.telephones);
        mobilesValue.setValues(item.mobiles);
    }
}
