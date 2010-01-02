package org.doogal.notes.table;

public enum TableType {
    ALIAS(null, new String[] { "unalias" }), BUILTIN("help",
            new String[] { "help" }), DOCUMENT("peek", new String[] { "peek",
            "open", "publish", "delete", "tidy", "more" }), ENVIRONMENT, FIELD_NAME, FIELD_VALUE;
    private final String action;
    private final String[] actions;

    private TableType(String action, String[] actions) {
        this.action = action;
        this.actions = actions;
    }

    private TableType(String action) {
        this(action, new String[] {});
    }

    private TableType() {
        this(null);
    }

    public final String getAction() {
        return action;
    }

    public final String[] getActions() {
        return actions;
    }
}
