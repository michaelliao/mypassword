package org.puppylab.mypassword.ui.view;

import java.util.List;
import java.util.function.Consumer;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ControlListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.puppylab.mypassword.core.data.AbstractItemData;
import org.puppylab.mypassword.core.data.ItemType;
import org.puppylab.mypassword.ui.model.Category;

public class ItemListView {

    // ── row geometry ──────────────────────────────────────────────────
    private static final int ROW_HEIGHT  = 52;
    private static final int ICON_SIZE   = 34;
    private static final int ICON_LEFT   = 9;                         // left margin for icon
    private static final int TEXT_LEFT   = ICON_LEFT + ICON_SIZE + 9; // left margin for text
    private static final int TITLE_Y_OFF = 8;                         // y offset for title inside row
    private static final int SUB_Y_OFF   = 29;                        // y offset for subtitle inside row

    // ── category metadata ─────────────────────────────────────────────
    private static final Category[] CATEGORIES      = { Category.ALL, Category.FAVORITES, Category.LOGINS,
            Category.NOTES, Category.IDENTITIES, Category.TRASH };
    private static final String[]   CATEGORY_LABELS = { "All Items", "Favorites", "Logins", "Notes", "Identities",
            "Trash" };

    // ── widgets ───────────────────────────────────────────────────────
    private final Table categoryTable;
    private final Table itemTable;

    // ── state ─────────────────────────────────────────────────────────
    private List<AbstractItemData> allItems        = List.of();
    private Category               currentCategory = Category.ALL;

    // ── listeners ─────────────────────────────────────────────────────
    private Consumer<AbstractItemData> onSelectionChanged;
    private Consumer<Category>         onCategoryChanged;

    // ── icon colors (disposed with itemTable) ─────────────────────────
    private final Color loginColor;
    private final Color noteColor;
    private final Color identityColor;

    public ItemListView(Composite parent) {
        Display display = parent.getDisplay();
        loginColor = new Color(display, 70, 130, 180);
        noteColor = new Color(display, 230, 150, 50);
        identityColor = new Color(display, 72, 175, 110);

        Composite container = new Composite(parent, SWT.NONE);
        container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, true));
        GridLayout gl = new GridLayout(2, false);
        gl.marginWidth = 0;
        gl.marginHeight = 0;
        gl.horizontalSpacing = 1;
        container.setLayout(gl);

        categoryTable = buildCategoryTable(container);
        itemTable = buildItemTable(container);

        itemTable.addListener(SWT.Dispose, e -> {
            loginColor.dispose();
            noteColor.dispose();
            identityColor.dispose();
        });
    }

    // ── public API ────────────────────────────────────────────────────

    public void setAllItems(List<AbstractItemData> items) {
        this.allItems = items;
        refreshItemTable();
    }

    public void clearSelection() {
        itemTable.deselectAll();
    }

    public void selectItem(long id) {
        TableItem[] items = itemTable.getItems();
        for (int i = 0; i < items.length; i++) {
            AbstractItemData d = (AbstractItemData) items[i].getData();
            if (d != null && d.id == id) {
                itemTable.setSelection(i);
                return;
            }
        }
    }

    public void updateItem(AbstractItemData item) {
        for (TableItem ti : itemTable.getItems()) {
            AbstractItemData d = (AbstractItemData) ti.getData();
            if (d != null && d.id == item.id) {
                ti.setData(item);
                itemTable.redraw();
                return;
            }
        }
    }

    public void setOnSelectionChanged(Consumer<AbstractItemData> listener) {
        this.onSelectionChanged = listener;
    }

    public void setOnCategoryChanged(Consumer<Category> listener) {
        this.onCategoryChanged = listener;
    }

    // ── category table ────────────────────────────────────────────────

    private Table buildCategoryTable(Composite parent) {
        Table table = new Table(parent, SWT.SINGLE | SWT.FULL_SELECTION);
        GridData gd = new GridData(SWT.FILL, SWT.FILL, false, true);
        gd.widthHint = 130;
        table.setLayoutData(gd);
        table.setHeaderVisible(false);
        table.setLinesVisible(false);

        for (String label : CATEGORY_LABELS) {
            new TableItem(table, SWT.NONE).setText(label);
        }
        table.setSelection(0);

        table.addListener(SWT.Selection, e -> {
            int idx = table.getSelectionIndex();
            if (idx < 0)
                return;
            currentCategory = CATEGORIES[idx];
            refreshItemTable();
            if (onCategoryChanged != null)
                onCategoryChanged.accept(currentCategory);
        });

        return table;
    }

    // ── item table ────────────────────────────────────────────────────

    private Table buildItemTable(Composite parent) {
        Table table = new Table(parent, SWT.SINGLE | SWT.FULL_SELECTION);
        GridData gd = new GridData(SWT.FILL, SWT.FILL, true, true);
        gd.widthHint = 240;
        table.setLayoutData(gd);
        table.setHeaderVisible(false);
        table.setLinesVisible(false);

        // A single full-width column is required: without it the column
        // width is 0 and PaintItem has no area to draw into.
        TableColumn col = new TableColumn(table, SWT.NONE);
        col.setWidth(240);
        table.addControlListener(ControlListener.controlResizedAdapter(e -> col.setWidth(table.getClientArea().width)));

        table.addListener(SWT.MeasureItem, e -> e.height = ROW_HEIGHT);
        table.addListener(SWT.EraseItem, this::onEraseItem);
        table.addListener(SWT.PaintItem, this::onPaintItem);

        table.addListener(SWT.Selection, e -> {
            if (onSelectionChanged == null)
                return;
            int idx = table.getSelectionIndex();
            AbstractItemData item = idx >= 0 ? (AbstractItemData) table.getItem(idx).getData() : null;
            onSelectionChanged.accept(item);
        });

        return table;
    }

    private void refreshItemTable() {
        itemTable.removeAll();
        for (AbstractItemData item : filtered()) {
            TableItem ti = new TableItem(itemTable, SWT.NONE);
            ti.setData(item);
        }
    }

    // ── owner-draw ────────────────────────────────────────────────────

    /**
     * Suppress the default foreground text (we draw everything in PaintItem). When
     * the row is selected, draw our own highlight background and clear the
     * SWT.SELECTED flag so the system does not paint over it.
     */
    private void onEraseItem(Event e) {
        e.detail &= ~SWT.FOREGROUND;
        if ((e.detail & SWT.SELECTED) == 0)
            return;

        e.gc.setBackground(e.display.getSystemColor(SWT.COLOR_LIST_SELECTION));
        e.gc.fillRectangle(e.x, e.y, e.width, e.height);
        e.detail &= ~SWT.SELECTED;
    }

    /**
     * Draw one row: coloured icon square on the left, title above and subtitle
     * below on the right.
     *
     * ┌─────────────────────────────────────┐ │ [L] title │ ← TITLE_Y_OFF from row
     * top │ subtitle (dimmed) │ ← SUB_Y_OFF from row top
     * └─────────────────────────────────────┘
     */
    private void onPaintItem(Event e) {
        AbstractItemData item = (AbstractItemData) ((TableItem) e.item).getData();
        if (item == null)
            return;

        GC gc = e.gc;
        Display display = e.display;
        boolean selected = itemTable.getSelectionIndex() == itemTable.indexOf((TableItem) e.item);

        // ── icon: filled rectangle + letter ──────────────────────────
        int iconY = e.y + (ROW_HEIGHT - ICON_SIZE) / 2;
        Color iconBg = iconColor(item.item_type);
        Color savedBg = gc.getBackground();
        Color savedFg = gc.getForeground();

        gc.setBackground(iconBg);
        gc.fillRoundRectangle(e.x + ICON_LEFT, iconY, ICON_SIZE, ICON_SIZE, 8, 8);

        gc.setForeground(display.getSystemColor(SWT.COLOR_WHITE));
        String letter = iconLetter(item.item_type);
        Point sz = gc.textExtent(letter);
        gc.drawText(letter, e.x + ICON_LEFT + (ICON_SIZE - sz.x) / 2, iconY + (ICON_SIZE - sz.y) / 2, true);

        // ── title ─────────────────────────────────────────────────────
        gc.setForeground(display.getSystemColor(selected ? SWT.COLOR_LIST_SELECTION_TEXT : SWT.COLOR_LIST_FOREGROUND));
        gc.drawText(item.title(), e.x + TEXT_LEFT, e.y + TITLE_Y_OFF, true);

        // ── subtitle ──────────────────────────────────────────────────
        gc.setForeground(display.getSystemColor(selected ? SWT.COLOR_LIST_SELECTION_TEXT : SWT.COLOR_DARK_GRAY));
        gc.drawText(item.subtitle(), e.x + TEXT_LEFT, e.y + SUB_Y_OFF, true);

        gc.setBackground(savedBg);
        gc.setForeground(savedFg);
    }

    // ── helpers ───────────────────────────────────────────────────────

    private List<AbstractItemData> filtered() {
        return switch (currentCategory) {
        case ALL -> allItems.stream().filter(i -> !i.deleted).toList();
        case FAVORITES -> allItems.stream().filter(i -> !i.deleted && i.favorite).toList();
        case LOGINS -> allItems.stream().filter(i -> !i.deleted && i.item_type == ItemType.LOGIN).toList();
        case NOTES -> allItems.stream().filter(i -> !i.deleted && i.item_type == ItemType.NOTE).toList();
        case IDENTITIES -> allItems.stream().filter(i -> !i.deleted && i.item_type == ItemType.IDENTITY).toList();
        case TRASH -> allItems.stream().filter(i -> i.deleted).toList();
        };
    }

    private Color iconColor(int type) {
        return switch (type) {
        case ItemType.LOGIN -> loginColor;
        case ItemType.NOTE -> noteColor;
        case ItemType.IDENTITY -> identityColor;
        default -> loginColor; // FIXME
        };
    }

    private String iconLetter(int type) {
        return switch (type) {
        case ItemType.LOGIN -> "L";
        case ItemType.NOTE -> "N";
        case ItemType.IDENTITY -> "I";
        default -> "?";
        };
    }
}
