package org.puppylab.mypassword.ui.view;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.puppylab.mypassword.rpc.data.IdentityItemData;

public class IdentityDetailView {

    public final Composite composite;

    private final Label titleValue;
    private final Label nameValue;
    private final Label passportValue;
    private final Label identityNumberValue;
    private final Label taxNumberValue;
    private final Label telephonesValue;
    private final Label mobilesValue;

    private Runnable onEdit;

    public IdentityDetailView(Composite parent) {
        composite = new Composite(parent, SWT.NONE);
        composite.setLayout(new GridLayout(1, false));

        Composite actions = new Composite(composite, SWT.NONE);
        actions.setLayout(new RowLayout());
        Button btnEdit = new Button(actions, SWT.PUSH);
        btnEdit.setText(" Edit ");
        btnEdit.addListener(SWT.Selection, e -> { if (onEdit != null) onEdit.run(); });

        new Label(composite, SWT.SEPARATOR | SWT.HORIZONTAL)
                .setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        titleValue          = createField(composite, "Title:");
        nameValue           = createField(composite, "Name:");
        passportValue       = createField(composite, "Passport:");
        identityNumberValue = createField(composite, "ID Number:");
        taxNumberValue      = createField(composite, "Tax Number:");
        telephonesValue     = createField(composite, "Telephones:");
        mobilesValue        = createField(composite, "Mobiles:");
    }

    public void show(IdentityItemData item) {
        titleValue.setText(notNull(item.title));
        nameValue.setText(notNull(item.name));
        passportValue.setText(notNull(item.passport_number));
        identityNumberValue.setText(notNull(item.identity_number));
        taxNumberValue.setText(notNull(item.tax_number));
        telephonesValue.setText(item.telephones != null ? String.join(", ", item.telephones) : "");
        mobilesValue.setText(item.mobiles != null ? String.join(", ", item.mobiles) : "");
        composite.layout(true, true);
    }

    public void setOnEdit(Runnable listener) { this.onEdit = listener; }

    private Label createField(Composite parent, String labelText) {
        Composite row = new Composite(parent, SWT.NONE);
        row.setLayout(new GridLayout(2, false));
        row.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Label lbl = new Label(row, SWT.NONE);
        lbl.setText(labelText);
        GridData ld = new GridData();
        ld.widthHint = 90;
        lbl.setLayoutData(ld);

        Label value = new Label(row, SWT.NONE);
        value.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        return value;
    }

    private String notNull(String s) { return s != null ? s : ""; }
}
