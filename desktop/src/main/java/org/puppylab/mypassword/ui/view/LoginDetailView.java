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
import org.puppylab.mypassword.rpc.data.LoginItemData;

public class LoginDetailView {

    public final Composite composite;

    private final ScrolledComposite sc;
    private final Composite         content;
    private final Label             titleValue;
    private final Label             usernameValue;
    private final Label             passwordValue;
    private final Label             websitesValue;
    private final Label             memoValue;

    private Runnable onEdit;

    public LoginDetailView(Composite parent) {
        composite = new Composite(parent, SWT.NONE);
        composite.setLayout(new GridLayout(1, false));

        // ── fixed action bar ──────────────────────────────────────────
        Composite actions = new Composite(composite, SWT.NONE);
        actions.setLayout(new RowLayout());
        Button btnEdit = new Button(actions, SWT.PUSH);
        btnEdit.setText(" Edit ");
        btnEdit.addListener(SWT.Selection, e -> {
            if (onEdit != null)
                onEdit.run();
        });

        new Label(composite, SWT.SEPARATOR | SWT.HORIZONTAL)
                .setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        // ── scrollable field area ─────────────────────────────────────
        sc = new ScrolledComposite(composite, SWT.V_SCROLL);
        sc.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        sc.setExpandHorizontal(true);
        sc.setExpandVertical(true);
        sc.setLayout(new FillLayout(SWT.VERTICAL));

        content = new Composite(sc, SWT.NONE);
        content.setLayout(new FillLayout(SWT.VERTICAL));

        titleValue = createField(content, "Title:", 80);
        usernameValue = createField(content, "Username:", 80);
        passwordValue = createField(content, "Password:", 80);
        websitesValue = createField(content, "Websites:", 80);
        memoValue = createField(content, "Memo:", 80);

        sc.setContent(content);
        sc.setMinSize(content.computeSize(SWT.DEFAULT, SWT.DEFAULT));
        sc.addControlListener(ControlListener.controlResizedAdapter(e -> {
            int w = sc.getClientArea().width;
            sc.setMinSize(content.computeSize(w, SWT.DEFAULT));
        }));
    }

    public void show(LoginItemData item) {
        titleValue.setText(notNull(item.title));
        usernameValue.setText(notNull(item.username));
        passwordValue.setText(
                item.password != null && !item.password.isEmpty() ? "\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022"
                        : "");
        websitesValue.setText(item.websites != null ? String.join(", ", item.websites) : "");
        memoValue.setText(notNull(item.memo));
        content.layout(true, true);
        sc.setMinSize(content.computeSize(sc.getClientArea().width, SWT.DEFAULT));
    }

    public void setOnEdit(Runnable listener) {
        this.onEdit = listener;
    }

    private Label createField(Composite parent, String labelText, int labelWidth) {
        Composite row = new Composite(parent, SWT.NONE);
        row.setLayout(new GridLayout(2, false));
        row.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Label lbl = new Label(row, SWT.NONE);
        lbl.setText(labelText);
        GridData ld = new GridData();
        ld.widthHint = labelWidth;
        lbl.setLayoutData(ld);

        Label value = new Label(row, SWT.WRAP);
        value.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        return value;
    }

    private String notNull(String s) {
        return s != null ? s : "";
    }
}
