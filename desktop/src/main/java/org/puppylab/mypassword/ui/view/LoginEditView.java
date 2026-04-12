package org.puppylab.mypassword.ui.view;

import static org.puppylab.mypassword.util.I18nUtils.i18n;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Scale;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Text;
import org.puppylab.mypassword.core.data.ItemType;
import org.puppylab.mypassword.core.data.LoginFieldsData;
import org.puppylab.mypassword.core.data.LoginItemData;
import org.puppylab.mypassword.core.data.PasskeyData;
import org.puppylab.mypassword.ui.Icons;
import org.puppylab.mypassword.util.PasswordUtils;
import org.puppylab.mypassword.util.StringUtils;

public class LoginEditView extends AbstractEditView<LoginItemData> {

    private static final int MIN_PASSWORD_LENGTH     = 8;
    private static final int MAX_PASSWORD_LENGTH     = 32;
    private static final int DEFAULT_PASSWORD_LENGTH = 16;

    private Text      titleField;
    private Text      usernameField;
    private Text      passwordField;
    private Button    eyeBtn;
    private Button    toggleGenBtn;
    private Composite genArea;
    private boolean   passwordVisible = false;

    private Button  radioAlphaNum;
    private Button  radioAlpha;
    private Button  radioNum;
    private Button  radioSymbol;
    private Scale   lengthScale;
    private Spinner lengthSpinner;

    private Text memoField;

    private MultiText websitesMultiFields;

    private Label  passkeyValueLabel;
    private Button passkeyDeleteBtn;

    // Current passkey state while editing. Initialized from the loaded item in
    // setData; nulled out when the delete button is clicked. Copied back into
    // the collected LoginItemData so unchanged passkeys are preserved.
    private PasskeyData currentPasskey = null;

    private LoginItemData editingItem = null;

    public LoginEditView(Composite parent) {
        super(parent);
    }

    @Override
    protected void createFields() {
        titleField = createField(i18n("field.title"), SWT.BORDER);
        usernameField = createField(i18n("field.username"), SWT.BORDER);
        createPasswordRow();
        createGenerateRow();
        createPasskeyRow();
        websitesMultiFields = createMultiTextFields(i18n("field.websites"));
        memoField = createAreaField(i18n("field.memo"));
    }

    private void createPasskeyRow() {
        Composite row = new Composite(content, SWT.NONE);
        GridLayout gl = new GridLayout(3, false);
        row.setLayout(gl);
        row.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        org.eclipse.swt.widgets.Label lbl = new org.eclipse.swt.widgets.Label(row, SWT.NONE);
        lbl.setText(i18n("field.passkey"));
        GridData ld = new GridData();
        ld.widthHint = LABEL_WIDTH;
        lbl.setLayoutData(ld);

        passkeyValueLabel = new org.eclipse.swt.widgets.Label(row, SWT.NONE);
        passkeyValueLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        passkeyDeleteBtn = new Button(row, SWT.PUSH);
        passkeyDeleteBtn.setImage(Icons.get("delete"));
        passkeyDeleteBtn.setToolTipText(i18n("passkey.btn.delete"));
        GridData dgd = new GridData();
        dgd.widthHint = 32;
        passkeyDeleteBtn.setLayoutData(dgd);
        passkeyDeleteBtn.addListener(SWT.Selection, _ -> {
            if (currentPasskey == null) {
                return;
            }
            String label = formatPasskey(currentPasskey);
            if (label.isEmpty()) {
                label = StringUtils.normalize(currentPasskey.relyingPartyId);
            }
            MessageBox mb = new MessageBox(passkeyDeleteBtn.getShell(), SWT.ICON_QUESTION | SWT.OK | SWT.CANCEL);
            mb.setText(i18n("confirm.title"));
            mb.setMessage(i18n("passkey.confirm.delete", label));
            if (mb.open() != SWT.OK) {
                return;
            }
            currentPasskey = null;
            updatePasskeyRow();
        });
    }

    private void updatePasskeyRow() {
        if (currentPasskey != null) {
            passkeyValueLabel.setText(formatPasskey(currentPasskey));
            passkeyDeleteBtn.setVisible(true);
            ((GridData) passkeyDeleteBtn.getLayoutData()).exclude = false;
        } else {
            passkeyValueLabel.setText("N/A");
            passkeyDeleteBtn.setVisible(false);
            ((GridData) passkeyDeleteBtn.getLayoutData()).exclude = true;
        }
        passkeyValueLabel.getParent().layout(true, true);
    }

    private static String formatPasskey(PasskeyData p) {
        String user = StringUtils.normalize(p.username);
        String display = StringUtils.normalize(p.displayName);
        if (user.isEmpty() && display.isEmpty()) {
            return "";
        }
        if (user.isEmpty()) {
            return display;
        }
        if (display.isEmpty() || user.equals(display)) {
            return user;
        }
        return user + " / " + display;
    }

    private void createPasswordRow() {
        Composite row = new Composite(content, SWT.NONE);
        GridLayout gl = new GridLayout(4, false);
        row.setLayout(gl);
        row.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        org.eclipse.swt.widgets.Label lbl = new org.eclipse.swt.widgets.Label(row, SWT.NONE);
        lbl.setText(i18n("field.password"));
        GridData ld = new GridData();
        ld.widthHint = LABEL_WIDTH;
        lbl.setLayoutData(ld);

        passwordField = new Text(row, SWT.BORDER | SWT.PASSWORD);
        passwordField.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        eyeBtn = new Button(row, SWT.PUSH);
        eyeBtn.setImage(Icons.get("eye_open"));
        GridData eyeGd = new GridData();
        eyeGd.widthHint = 32;
        eyeBtn.setLayoutData(eyeGd);
        eyeBtn.addListener(SWT.Selection, _ -> {
            passwordVisible = !passwordVisible;
            passwordField.setEchoChar(passwordVisible ? '\0' : '\u2022');
            eyeBtn.setImage(Icons.get(passwordVisible ? "eye_closed" : "eye_open"));
        });

        toggleGenBtn = new Button(row, SWT.PUSH);
        toggleGenBtn.setImage(Icons.get("view_down"));
        GridData toggleGd = new GridData();
        toggleGd.widthHint = 32;
        toggleGenBtn.setLayoutData(toggleGd);
        toggleGenBtn.addListener(SWT.Selection, _ -> setGenAreaVisible(!genArea.getVisible()));
    }

    private void createGenerateRow() {
        genArea = new Composite(content, SWT.NONE);
        GridLayout genLayout = new GridLayout(1, false);
        genLayout.marginHeight = 0;
        genLayout.marginWidth = 0;
        genArea.setLayout(genLayout);
        genArea.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        // Row 1: radio buttons
        Composite radioRow = new Composite(genArea, SWT.NONE);
        radioRow.setLayout(new GridLayout(2, false));
        radioRow.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        org.eclipse.swt.widgets.Label spacer1 = new org.eclipse.swt.widgets.Label(radioRow, SWT.NONE);
        GridData spacer1Gd = new GridData();
        spacer1Gd.widthHint = LABEL_WIDTH;
        spacer1.setLayoutData(spacer1Gd);

        Composite radios = new Composite(radioRow, SWT.NONE);
        GridLayout rl = new GridLayout(4, false);
        rl.marginHeight = 0;
        rl.marginWidth = 0;
        radios.setLayout(rl);
        radios.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        radioAlphaNum = new Button(radios, SWT.RADIO);
        radioAlphaNum.setText(i18n("password.gen.alpha_num"));
        radioAlphaNum.setSelection(true);

        radioAlpha = new Button(radios, SWT.RADIO);
        radioAlpha.setText(i18n("password.gen.alpha"));

        radioNum = new Button(radios, SWT.RADIO);
        radioNum.setText(i18n("password.gen.num"));

        radioSymbol = new Button(radios, SWT.RADIO);
        radioSymbol.setText(i18n("password.gen.symbol"));

        // Row 2: slider + spinner + generate button
        Composite sliderRow = new Composite(genArea, SWT.NONE);
        sliderRow.setLayout(new GridLayout(2, false));
        sliderRow.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        org.eclipse.swt.widgets.Label spacer2 = new org.eclipse.swt.widgets.Label(sliderRow, SWT.NONE);
        GridData spacer2Gd = new GridData();
        spacer2Gd.widthHint = LABEL_WIDTH;
        spacer2.setLayoutData(spacer2Gd);

        Composite controls = new Composite(sliderRow, SWT.NONE);
        GridLayout cl = new GridLayout(3, false);
        cl.marginHeight = 0;
        cl.marginWidth = 0;
        controls.setLayout(cl);
        controls.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        lengthScale = new Scale(controls, SWT.HORIZONTAL);
        lengthScale.setMinimum(MIN_PASSWORD_LENGTH);
        lengthScale.setMaximum(MAX_PASSWORD_LENGTH);
        lengthScale.setSelection(DEFAULT_PASSWORD_LENGTH);
        lengthScale.setIncrement(1);
        lengthScale.setPageIncrement(4);
        lengthScale.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        lengthScale.addListener(SWT.Selection, _ -> lengthSpinner.setSelection(lengthScale.getSelection()));

        lengthSpinner = new Spinner(controls, SWT.BORDER);
        lengthSpinner.setMinimum(MIN_PASSWORD_LENGTH);
        lengthSpinner.setMaximum(MAX_PASSWORD_LENGTH);
        lengthSpinner.setSelection(DEFAULT_PASSWORD_LENGTH);
        lengthSpinner.setIncrement(1);
        lengthSpinner.addListener(SWT.Selection, _ -> lengthScale.setSelection(lengthSpinner.getSelection()));

        Button genBtn = new Button(controls, SWT.PUSH);
        genBtn.setText(i18n("password.gen.btn"));
        genBtn.addListener(SWT.Selection, _ -> {
            int style = getSelectedStyle();
            int len = lengthSpinner.getSelection();
            String password = PasswordUtils.generatePassword(len, style);
            passwordField.setText(password);
            if (!passwordVisible) {
                passwordVisible = true;
                passwordField.setEchoChar('\0');
                eyeBtn.setImage(Icons.get("eye_closed"));
            }
        });
    }

    private void setGenAreaVisible(boolean visible) {
        genArea.setVisible(visible);
        ((GridData) genArea.getLayoutData()).exclude = !visible;
        toggleGenBtn.setImage(Icons.get(visible ? "view_up" : "view_down"));
        content.layout(true, true);
        sc.setMinSize(content.computeSize(sc.getClientArea().width, SWT.DEFAULT));
    }

    private int getSelectedStyle() {
        if (radioAlpha.getSelection())
            return PasswordUtils.STYLE_ALPHABET;
        if (radioNum.getSelection())
            return PasswordUtils.STYLE_NUMBER;
        if (radioSymbol.getSelection())
            return PasswordUtils.STYLE_ALPHABET_NUMBER_SYMBOL;
        return PasswordUtils.STYLE_ALPHABET_NUMBER;
    }

    @Override
    protected void setData(LoginItemData item) {
        // Dispose all existing website rows
        websitesMultiFields.disposeFields();

        editingItem = item;
        passwordVisible = false;
        passwordField.setEchoChar('\u2022');
        eyeBtn.setImage(Icons.get("eye_open"));
        radioAlphaNum.setSelection(true);
        radioAlpha.setSelection(false);
        radioNum.setSelection(false);
        radioSymbol.setSelection(false);
        lengthScale.setSelection(DEFAULT_PASSWORD_LENGTH);
        lengthSpinner.setSelection(DEFAULT_PASSWORD_LENGTH);
        if (item == null) {
            titleField.setText("");
            usernameField.setText("");
            passwordField.setText("");
            addMultiTextRow(websitesMultiFields, "", false);
            memoField.setText("");
            currentPasskey = null;
        } else {
            titleField.setText(StringUtils.normalize(item.data.title));
            usernameField.setText(StringUtils.normalize(item.data.username));
            passwordField.setText(StringUtils.normalize(item.data.password));
            setMultiTextValues(websitesMultiFields, item.data.websites);
            memoField.setText(StringUtils.normalize(item.data.memo));
            currentPasskey = item.data.passkey;
        }
        updatePasskeyRow();
        // show generate area if password is empty, hide if not
        boolean hasPassword = !passwordField.getText().isEmpty();
        setGenAreaVisible(!hasPassword);
        updateMultiTextAddButton(websitesMultiFields);
    }

    @Override
    protected LoginItemData collectData() {
        LoginItemData data = editingItem != null ? editingItem : new LoginItemData();
        data.item_type = ItemType.LOGIN;
        data.data = new LoginFieldsData();
        data.data.title = titleField.getText().strip();
        data.data.username = usernameField.getText().strip();
        data.data.password = passwordField.getText();
        data.data.websites = websitesMultiFields.collectData();
        data.data.memo = memoField.getText();
        // Passkeys cannot be edited from this view — only deleted via the × button.
        // currentPasskey holds the original PasskeyData reference (or null if the
        // user clicked delete), so carrying it over preserves unchanged passkeys.
        data.data.passkey = currentPasskey;
        return data;
    }
}
