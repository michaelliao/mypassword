package org.puppylab.mypassword.ui.view;

import java.util.List;

import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;

public record MultiText(Composite container, List<Text> fields, Button addBtn) {

    public List<String> collectData() {
        return fields().stream().map(t -> t.getText().strip()).filter(s -> !s.isEmpty()).toList();
    }

    public void disposeFields() {
        for (Text t : fields()) {
            if (!t.isDisposed()) {
                t.getParent().dispose();
            }
        }
        fields().clear();
    }
}
