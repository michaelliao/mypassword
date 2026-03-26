package org.puppylab.mypassword.ui.view;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

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
import org.eclipse.swt.widgets.Text;
import org.puppylab.mypassword.rpc.data.IdentityItemData;

public class IdentityEditView {

    public final Composite composite;

    private final ScrolledComposite sc;
    private final Composite         content;
    private final Text titleField;
    private final Text nameField;
    private final Text taxNumberField;
    private final Text passportField;
    private final Text identityNumberField;
    private final Text telephonesField;
    private final Text mobilesField;

    private Consumer<IdentityItemData> onSave;
    private Runnable                   onCancel;

    private long editingId = 0;

    public IdentityEditView(Composite parent) {
        composite = new Composite(parent, SWT.NONE);
        composite.setLayout(new GridLayout(1, false));

        // ── fixed action bar ──────────────────────────────────────────
        Composite actions = new Composite(composite, SWT.NONE);
        RowLayout rl = new RowLayout();
        rl.spacing = 10;
        actions.setLayout(rl);

        Button btnSave = new Button(actions, SWT.PUSH);
        btnSave.setText(" Save ");
        btnSave.addListener(SWT.Selection, e -> { if (onSave != null) onSave.accept(collectData()); });

        Button btnCancel = new Button(actions, SWT.PUSH);
        btnCancel.setText(" Cancel ");
        btnCancel.addListener(SWT.Selection, e -> { if (onCancel != null) onCancel.run(); });

        new Label(composite, SWT.SEPARATOR | SWT.HORIZONTAL)
                .setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        // ── scrollable field area ─────────────────────────────────────
        sc = new ScrolledComposite(composite, SWT.V_SCROLL);
        sc.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        sc.setLayout(new FillLayout(SWT.VERTICAL));
        sc.setExpandHorizontal(true);
        sc.setExpandVertical(true);

        content = new Composite(sc, SWT.NONE);
        content.setLayout(new GridLayout(1, false));

        titleField          = createField(content, "Title:");
        nameField           = createField(content, "Name:");
        passportField       = createField(content, "Passport:");
        identityNumberField = createField(content, "ID Number:");
        taxNumberField      = createField(content, "Tax Number:");
        telephonesField     = createField(content, "Telephones:");
        mobilesField        = createField(content, "Mobiles:");

        Label hint = new Label(content, SWT.NONE);
        hint.setText("  * Separate multiple phone numbers with commas");
        hint.setForeground(content.getDisplay().getSystemColor(SWT.COLOR_DARK_GRAY));

        sc.setContent(content);
        sc.setMinSize(content.computeSize(SWT.DEFAULT, SWT.DEFAULT));
        sc.addControlListener(ControlListener.controlResizedAdapter(e -> {
            int w = sc.getClientArea().width;
            content.layout(true, true);
            sc.setMinSize(content.computeSize(w, SWT.DEFAULT));
        }));
    }

    /** Populate form for editing an existing identity, or pass null for a new one. */
    public void edit(IdentityItemData item) {
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
            titleField.setText(notNull(item.title));
            nameField.setText(notNull(item.name));
            passportField.setText(notNull(item.passport_number));
            identityNumberField.setText(notNull(item.identity_number));
            taxNumberField.setText(notNull(item.tax_number));
            telephonesField.setText(item.telephones != null ? String.join(", ", item.telephones) : "");
            mobilesField.setText(item.mobiles != null ? String.join(", ", item.mobiles) : "");
        }
        content.layout(true, true);
        sc.setMinSize(content.computeSize(sc.getClientArea().width, SWT.DEFAULT));
    }

    public void setOnSave(Consumer<IdentityItemData> listener) { this.onSave   = listener; }
    public void setOnCancel(Runnable listener)                  { this.onCancel = listener; }

    private IdentityItemData collectData() {
        IdentityItemData data = new IdentityItemData();
        data.id              = editingId;
        data.title           = titleField.getText().strip();
        data.name            = nameField.getText().strip();
        data.passport_number = passportField.getText().strip();
        data.identity_number = identityNumberField.getText().strip();
        data.tax_number      = taxNumberField.getText().strip();
        data.telephones      = splitCSV(telephonesField.getText());
        data.mobiles         = splitCSV(mobilesField.getText());
        return data;
    }

    private Text createField(Composite parent, String labelText) {
        Composite row = new Composite(parent, SWT.NONE);
        row.setLayout(new GridLayout(2, false));
        row.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Label lbl = new Label(row, SWT.NONE);
        lbl.setText(labelText);
        GridData ld = new GridData();
        ld.widthHint = 90;
        lbl.setLayoutData(ld);

        Text t = new Text(row, SWT.BORDER);
        t.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        return t;
    }

    private List<String> splitCSV(String value) {
        String v = value == null ? "" : value.strip();
        if (v.isEmpty()) return List.of();
        return Arrays.stream(v.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    private String notNull(String s) { return s != null ? s : ""; }
}
