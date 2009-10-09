package org.doogal.swing;

import static org.doogal.core.Utility.printResource;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Event;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.mail.internet.ParseException;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.JTextComponent;
import javax.swing.text.Keymap;

import org.apache.commons.logging.Log;
import org.doogal.core.AsyncDoogal;
import org.doogal.core.Controller;
import org.doogal.core.Doogal;
import org.doogal.core.Environment;
import org.doogal.core.EvalException;
import org.doogal.core.ExitException;
import org.doogal.core.Shellwords;
import org.doogal.core.Size;
import org.doogal.core.StandardLog;
import org.doogal.core.SyncDoogal;
import org.doogal.core.table.DocumentTable;
import org.doogal.core.table.SummaryTable;
import org.doogal.core.table.Table;
import org.doogal.core.view.AbstractView;
import org.doogal.core.view.View;

public final class Scratch extends JPanel implements Doogal {

    private static final long serialVersionUID = 1L;
    private static final int SMALL_FONT = 12;
    private static final int BIG_FONT = 14;

    private final History history;
    private final JTextArea console;
    private final JTextField prompt;
    private final Doogal doogal;

    private final Map<String, Action> actions;

    private boolean closed = false;

    private static Frame getFrame(Container c) {
        while (c != null) {
            if (c instanceof Frame)
                return (Frame) c;
            c = c.getParent();
        }
        return null;
    }

    private static Component newScrollPane(Component view) {
        final JScrollPane scrollPane = new JScrollPane();
        scrollPane.setViewportView(view);
        return scrollPane;
    }

    private static TableModel newTableModel(Table table) throws IOException {
        if (null != table && table instanceof DocumentTable) {
            final DocumentTable from = (DocumentTable) table;
            final SummaryTable to = new SummaryTable();
            for (int i = 0; i < from.getRowCount(); ++i)
                to.add(from.getSummary(i));
            table = to;
        }
        return new TableAdapter(table);
    }

    private static void postWindowClosingEvent(Frame frame) {
        final WindowEvent windowClosingEvent = new WindowEvent(frame,
                WindowEvent.WINDOW_CLOSING);
        frame.getToolkit().getSystemEventQueue().postEvent(windowClosingEvent);
    }

    private final void setPrompt(boolean b) {
        prompt.setEnabled(b);
        prompt.setEditable(b);
        if (b)
            prompt.requestFocus();
    }

    private final void setEmacs() {

        final Map<String, Action> actions = new HashMap<String, Action>();
        final Action[] actionsArray = prompt.getActions();
        for (final Action a : actionsArray)
            actions.put(a.getValue(Action.NAME).toString(), a);

        final Keymap keymap = JTextComponent.addKeymap("emacs", prompt
                .getKeymap());

        Action action;
        KeyStroke key;

        action = actions.get(DefaultEditorKit.beginLineAction);
        key = KeyStroke.getKeyStroke(KeyEvent.VK_A, Event.CTRL_MASK);
        keymap.addActionForKeyStroke(key, action);

        action = actions.get(DefaultEditorKit.previousWordAction);
        key = KeyStroke.getKeyStroke(KeyEvent.VK_B, Event.ALT_MASK);
        keymap.addActionForKeyStroke(key, action);

        action = actions.get(DefaultEditorKit.backwardAction);
        key = KeyStroke.getKeyStroke(KeyEvent.VK_B, Event.CTRL_MASK);
        keymap.addActionForKeyStroke(key, action);

        key = KeyStroke.getKeyStroke(KeyEvent.VK_C, Event.CTRL_MASK);
        keymap.addActionForKeyStroke(key, new AbstractAction() {
            private static final long serialVersionUID = 1L;

            public final void actionPerformed(ActionEvent e) {
                prompt.setText("");
            }
        });

        action = actions.get(DefaultEditorKit.endLineAction);
        key = KeyStroke.getKeyStroke(KeyEvent.VK_E, Event.CTRL_MASK);
        keymap.addActionForKeyStroke(key, action);

        action = actions.get(DefaultEditorKit.nextWordAction);
        key = KeyStroke.getKeyStroke(KeyEvent.VK_F, Event.ALT_MASK);
        keymap.addActionForKeyStroke(key, action);

        action = actions.get(DefaultEditorKit.forwardAction);
        key = KeyStroke.getKeyStroke(KeyEvent.VK_F, Event.CTRL_MASK);
        keymap.addActionForKeyStroke(key, action);

        action = new AbstractAction() {
            private static final long serialVersionUID = 1L;

            public final void actionPerformed(ActionEvent e) {
                prompt.setText(history.next());
            }
        };

        key = KeyStroke.getKeyStroke(KeyEvent.VK_N, Event.CTRL_MASK);
        keymap.addActionForKeyStroke(key, action);
        key = KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0);
        keymap.addActionForKeyStroke(key, action);

        action = new AbstractAction() {
            private static final long serialVersionUID = 1L;

            public final void actionPerformed(ActionEvent e) {
                prompt.setText(history.prev());
            }
        };

        key = KeyStroke.getKeyStroke(KeyEvent.VK_P, Event.CTRL_MASK);
        keymap.addActionForKeyStroke(key, action);
        key = KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0);
        keymap.addActionForKeyStroke(key, action);

        prompt.setKeymap(keymap);
    }

    Scratch() throws Exception {
        super(new BorderLayout());

        history = new History();

        final JTable jtable = new JTable(new TableAdapter());
        jtable.setDefaultRenderer(Size.class, new DefaultTableCellRenderer() {

            private static final long serialVersionUID = 1L;

            {
                setHorizontalAlignment(SwingConstants.RIGHT);
            }

            @Override
            protected final void setValue(Object value) {
                super.setValue(value.toString());
            }
        });

        jtable.getSelectionModel().addListSelectionListener(
                new ListSelectionListener() {
                    public final void valueChanged(ListSelectionEvent e) {
                        if (closed)
                            return;
                        final ListSelectionModel model = (ListSelectionModel) e
                                .getSource();
                        if (model.isSelectionEmpty())
                            setArgs();
                        else {
                            final int[] rows = jtable.getSelectedRows();
                            final Object[] args = new Object[rows.length];
                            for (int i = 0; i < rows.length; ++i)
                                args[i] = jtable.getValueAt(rows[i], 0);
                            setArgs(args);
                        }
                    }
                });

        jtable.addMouseListener(new MouseAdapter() {
            private final void setSelection(int index) {
                final ListSelectionModel selectionModel = jtable
                        .getSelectionModel();
                if (!selectionModel.isSelectedIndex(index))
                    selectionModel.setSelectionInterval(index, index);
            }

            private final JPopupMenu newMenu() {
                final TableAdapter model = (TableAdapter) jtable.getModel();
                final String[] names = model.getActions();
                if (null == names)
                    return null;
                final JPopupMenu menu = new JPopupMenu();
                for (int i = 0; i < names.length; ++i)
                    menu.add(new JMenuItem(actions.get(names[i])));
                return menu;
            }

            private final void showPopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    final Point p = e.getPoint();
                    final int index = jtable.rowAtPoint(p);
                    if (-1 != index) {
                        setSelection(index);
                        final JPopupMenu menu = newMenu();
                        if (null != menu)
                            menu.show(e.getComponent(), e.getX(), e.getY());
                    }
                }
            }

            public final void mouseClicked(MouseEvent ev) {
                if (SwingUtilities.isLeftMouseButton(ev)
                        && 2 == ev.getClickCount()) {
                    final Point p = ev.getPoint();
                    final int index = jtable.rowAtPoint(p);
                    if (-1 != index) {
                        try {
                            eval("peek");
                        } catch (final EvalException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }

            public final void mousePressed(MouseEvent e) {
                showPopup(e);
            }

            public final void mouseReleased(MouseEvent e) {
                showPopup(e);
            }
        });

        console = new JTextArea();
        console.setMargin(new Insets(5, 5, 5, 5));
        console.setFont(new Font("Monospaced", Font.PLAIN, SMALL_FONT));

        console.setEditable(false);
        console.setFocusable(false);
        console.setLineWrap(true);

        prompt = new JTextField();
        prompt.setMargin(new Insets(5, 5, 5, 5));
        prompt.setFont(new Font("Monospaced", Font.PLAIN, BIG_FONT));

        setPrompt(false);
        setEmacs();

        final JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                newScrollPane(jtable), newScrollPane(console));
        splitPane.setOneTouchExpandable(true);
        splitPane.setDividerLocation(getToolkit().getScreenSize().height / 4);

        add(prompt, BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);

        final Environment env = new Environment();
        final PrintWriter out = new PrintWriter(new TextAreaStream(console),
                true);
        final Log log = new StandardLog(out, out);
        final View view = new AbstractView(out, log) {

            @Override
            public final void setTable(final Table table) throws IOException {
                super.setTable(table);
                final TableModel model = newTableModel(table);
                EventQueue.invokeLater(new Runnable() {
                    public final void run() {
                        jtable.setRowSorter(new TableRowSorter<TableModel>(model));
                        jtable.setModel(model);
                    }
                });
            }

            public final void setPage(String n) throws EvalException,
                    IOException {
            }

            public final void showPage() throws EvalException, IOException {
            }

            public final void nextPage() throws EvalException, IOException {
            }

            public final void prevPage() throws EvalException, IOException {
            }
        };
        final Controller controller = new Controller() {
            public final void exit(boolean interact) throws ExitException {
                if (!interact)
                    throw new ExitException();
                EventQueue.invokeLater(new Runnable() {
                    public final void run() {
                        log.info("exiting...");
                        postWindowClosingEvent(getFrame(Scratch.this));
                    }
                });
            }

            public final void ready() {
                EventQueue.invokeLater(new Runnable() {
                    public final void run() {
                        setPrompt(true);
                    }
                });
            }
        };
        doogal = new AsyncDoogal(log, SyncDoogal.newInstance(env, view,
                controller));

        prompt.addActionListener(new ActionListener() {
            public final void actionPerformed(ActionEvent ev) {
                final String s = prompt.getText();
                history.add(s);
                final Reader reader = new StringReader(s);
                prompt.setText("");
                setPrompt(false);
                try {
                    Shellwords.parse(reader, doogal);
                } catch (final EvalException e) {
                    log.error(e.getLocalizedMessage());
                } catch (final IOException e) {
                    log.error(e.getLocalizedMessage());
                } catch (final ParseException e) {
                    log.error(e.getLocalizedMessage());
                }
            }
        });

        printResource("motd.txt", out);

        actions = new HashMap<String, Action>();
        final Map<String, String> builtins = doogal.getBuiltins();
        for (final Entry<String, String> entry : builtins.entrySet())
            actions.put(entry.getKey(), new AbstractAction() {
                private static final long serialVersionUID = 1L;

                {
                    final String key = entry.getKey();
                    final String name = ""
                            + Character.toUpperCase(key.charAt(0))
                            + key.substring(1);
                    putValue(Action.NAME, name);
                }

                public final void actionPerformed(ActionEvent ev) {
                    try {
                        doogal.eval(entry.getKey());
                    } catch (final EvalException e) {
                        e.printStackTrace();
                    }
                }
            });
    }

    public final void close() throws IOException {
        // Avoid multiple close from multiple clicks.
        if (!closed) {
            closed = true;
            doogal.close();
        }
    }

    public final void eval(String cmd, Object... args) throws EvalException {
        doogal.eval(cmd, args);
    }

    public final void eval() throws EvalException {
        doogal.eval();
    }

    public final void batch(Reader reader) throws EvalException, IOException,
            ParseException {
        doogal.batch(reader);
    }

    public final void batch(File file) throws EvalException, IOException,
            ParseException {
        doogal.batch(file);
    }

    public final void config() throws EvalException, IOException,
            ParseException {
        doogal.config();
    }

    public final void setArgs(Object... args) {
        doogal.setArgs(args);
    }

    public final Map<String, String> getBuiltins() {
        return doogal.getBuiltins();
    }

    final Map<String, Action> getActions() {
        return actions;
    }

    private static void run() throws Exception {
        final JFrame f = new JFrame("Doogal");
        final Scratch m = new Scratch();
        f.addWindowListener(new WindowAdapter() {
            @Override
            public final void windowClosing(WindowEvent ev) {
                try {
                    m.close();
                } catch (final IOException e) {
                    e.printStackTrace();
                }
            }
        });
        f.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        final Map<String, Action> actions = m.getActions();

        final JMenu file = new JMenu("File");
        file.setMnemonic(KeyEvent.VK_F);
        file.add(new JMenuItem(actions.get("new")));
        file.add(new JMenuItem(actions.get("open")));
        file.add(new JMenuItem(actions.get("import")));
        file.add(new JMenuItem(actions.get("exit")));

        final JMenu edit = new JMenu("Edit");
        edit.setMnemonic(KeyEvent.VK_E);
        edit.add(new JMenuItem(actions.get("delete")));
        edit.add(new JMenuItem(actions.get("peek")));
        edit.add(new JMenuItem(actions.get("publish")));
        edit.add(new JMenuItem(actions.get("tidy")));

        final JMenu search = new JMenu("Search");
        search.setMnemonic(KeyEvent.VK_S);
        search.add(new JMenuItem(actions.get("browse")));
        search.add(new JMenuItem(actions.get("recent")));
        search.add(new JMenuItem(actions.get("more")));
        search.add(new JMenuItem(actions.get("names")));
        search.add(new JMenuItem(actions.get("search")));

        final JMenu tools = new JMenu("Tools");
        tools.setMnemonic(KeyEvent.VK_T);
        tools.add(new JMenuItem(actions.get("alias")));
        tools.add(new JMenuItem(actions.get("archive")));
        tools.add(new JMenuItem(actions.get("index")));
        tools.add(new JMenuItem(actions.get("set")));

        final JMenu help = new JMenu("Help");
        help.setMnemonic(KeyEvent.VK_H);
        help.add(new JMenuItem(actions.get("help")));
        
        final JMenuBar mb = new JMenuBar();
        mb.add(file);
        mb.add(edit);
        mb.add(search);
        mb.add(tools);
        mb.add(help);

        f.setJMenuBar(mb);

        f.setLayout(new BorderLayout());
        f.add(m, BorderLayout.CENTER);
        f.pack();

        final Dimension d = f.getToolkit().getScreenSize();
        f.setSize(d.width / 2, d.height / 2);
        f.setVisible(true);
        m.config();
    }

    public static void main(String[] args) {

        System.setProperty("line.separator", "\n");
        EventQueue.invokeLater(new Runnable() {

            public final void run() {
                try {
                    Scratch.run();
                } catch (final Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }
}
