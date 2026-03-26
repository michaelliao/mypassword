package org.puppylab.mypassword.ui.view;

import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;

public record MultiLabel(Composite container, List<Label> fields) {

    public void setValues(List<String> values) {
        for (Control c : container().getChildren()) {
            c.dispose();
        }
        List<String> items = (values != null && !values.isEmpty()) ? values : List.of("");
        for (String v : items) {
            Label lbl = new Label(container(), SWT.WRAP);
            lbl.setText(v);
            lbl.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        }
    }
}
