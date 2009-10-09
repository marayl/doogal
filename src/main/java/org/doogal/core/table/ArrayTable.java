package org.doogal.core.table;

import java.io.IOException;

public final class ArrayTable implements Table {
    private final String columnName;
    private final String[] list;

    public ArrayTable(String columnName, String[] list) {
        this.columnName = columnName;
        this.list = list;
    }

    public void close() throws IOException {

    }

    public final int getRowCount() {
        return list.length;
    }

    public final int getColumnCount() {
        return 1;
    }

    public final String getColumnName(int columnIndex) {
        return columnName;
    }

    public final Class<?> getColumnClass(int columnIndex) {
        return String.class;
    }

    public final Object getValueAt(int rowIndex, int columnIndex) {
        return list[rowIndex];
    }

    public final String[] getActions() {
        return new String[] { "open" };
    }
}
