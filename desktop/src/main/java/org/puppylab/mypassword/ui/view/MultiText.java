package org.puppylab.mypassword.ui.view;

import java.util.List;

import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;

public record MultiText(Composite container, List<Text> fields, Button addBtn) {

}
