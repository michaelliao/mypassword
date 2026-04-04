package org.puppylab.mypassword.ui.view;

import static org.puppylab.mypassword.util.I18nUtils.i18n;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

public class EmptyView {

    public final Composite composite;

    public EmptyView(Composite parent) {
        composite = new Composite(parent, SWT.NONE);
        GridLayout layout = new GridLayout(1, false);
        layout.marginTop = 100;
        composite.setLayout(layout);

        Label iconLabel = new Label(composite, SWT.CENTER);
        iconLabel.setText("\uD83D\uDD11");
        iconLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false));

        Label hint = new Label(composite, SWT.CENTER);
        hint.setText(i18n("empty.hint"));
        hint.setForeground(parent.getDisplay().getSystemColor(SWT.COLOR_DARK_GRAY));
        hint.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false));
    }
}
