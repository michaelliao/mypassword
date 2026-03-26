package org.puppylab.mypassword.ui.view;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.ControlListener;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.puppylab.mypassword.rpc.data.IdentityItemData;

public class IdentityDetailView {

    public final Composite composite;

    private final ScrolledComposite sc;
    private final Composite         content;
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

        // ── fixed action bar ──────────────────────────────────────────
        Composite actions = new Composite(composite, SWT.NONE);
        actions.setLayout(new RowLayout());
        Button btnEdit = new Button(actions, SWT.PUSH);
        btnEdit.setText(" Edit ");
        btnEdit.addListener(SWT.Selection, e -> { if (onEdit != null) onEdit.run(); });

        new Label(composite, SWT.SEPARATOR | SWT.HORIZONTAL)
                .setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        // ── scrollable field area ─────────────────────────────────────
        sc = new ScrolledComposite(composite, SWT.V_SCROLL);
        sc.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        sc.setLayout(new FillLayout(SWT.VERTICAL));
        sc.setExpandHorizontal(true);
        sc.setExpandVertical(true);

        content = new Composite(sc, SWT.NONE);
        content.setLayout(new FillLayout(SWT.VERTICAL));

        titleValue          = createField(content, "Title:");
        nameValue           = createField(content, "Name:");
        passportValue       = createField(content, "Passport:");
        identityNumberValue = createField(content, "ID Number:");
        taxNumberValue      = createField(content, "Tax Number:");
        telephonesValue     = createField(content, "Telephones:");
        mobilesValue        = createField(content, "Mobiles:");

        sc.setContent(content);
        sc.setMinSize(content.computeSize(SWT.DEFAULT, SWT.DEFAULT));
        sc.addControlListener(ControlListener.controlResizedAdapter(e -> {
            int w = sc.getClientArea().width;
            content.layout(true, true);
            sc.setMinSize(content.computeSize(w, SWT.DEFAULT));
        }));
    }

    public void show(IdentityItemData item) {
        titleValue.setText(notNull(item.title));
        nameValue.setText(notNull(item.name));
        passportValue.setText(notNull(item.passport_number));
        identityNumberValue.setText(notNull(item.identity_number));
        taxNumberValue.setText(notNull(item.tax_number));
        telephonesValue.setText(item.telephones != null ? String.join(", ", item.telephones) : "");
        mobilesValue.setText(item.mobiles != null ? String.join(", ", item.mobiles) : "");
        content.layout(true, true);
        sc.setMinSize(content.computeSize(sc.getClientArea().width, SWT.DEFAULT));
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

        Label value = new Label(row, SWT.WRAP);
        value.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        return value;
    }

    private String notNull(String s) { return s != null ? s : ""; }
}
