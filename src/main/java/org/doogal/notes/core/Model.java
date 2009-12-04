package org.doogal.notes.core;

import static org.doogal.notes.core.Utility.join;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermDocs;
import org.apache.lucene.index.IndexReader.FieldOption;
import org.apache.lucene.queryParser.MultiFieldQueryParser;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.HitCollector;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.similar.MoreLikeThis;
import org.doogal.core.util.Destroyable;
import org.doogal.core.util.UnaryPredicate;
import org.doogal.notes.command.AbstractBuiltin;
import org.doogal.notes.command.Builtin;
import org.doogal.notes.command.Command;
import org.doogal.notes.domain.Constants;
import org.doogal.notes.domain.Summary;
import org.doogal.notes.table.ArrayTable;
import org.doogal.notes.table.DocumentTable;
import org.doogal.notes.table.SummaryTable;
import org.doogal.notes.table.Table;
import org.doogal.notes.table.TableType;
import org.doogal.notes.util.EvalException;
import org.doogal.notes.util.HtmlPage;
import org.doogal.notes.util.WriteOnce;
import org.doogal.notes.view.RefreshView;

final class Model implements Destroyable {
    private static final class WrapException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        WrapException(Throwable t) {
            super(t);
        }
    }

    private static final String[] FIELDS = { "subject", "title", "contents" };
    private static final Random RAND = new Random();

    private final Environment env;
    private final RefreshView view;
    private final Repo repo;
    private final IdentityMap identityMap;
    private final Recent recent;
    private SharedState state;
    private Selection select;

    private final Term getTerm(String value) throws IOException,
            NumberFormatException {
        return Character.isDigit(value.charAt(0)) ? identityMap.getTerm(value)
                : new Term("name", value);
    }

    private final Term getTerm() {
        final String id = recent.top();
        if (null == id)
            throw new EvalException("no such identifier");
        return new Term("id", id);
    }

    private final DocumentTable browse() throws IOException {

        final int max = state.maxDoc();
        final int n = Math.min(state.numDocs(), Constants.MAX_RESULTS);

        final SummaryTable table = new SummaryTable();
        int i = Math.abs(RAND.nextInt(max));
        while (table.getRowCount() < n) {
            final int j = i++ % max;
            if (!state.isDeleted(j)) {
                final Document doc = state.doc(j);
                final String id = doc.get("id");
                final int lid = state.getLocal(id);
                table.add(new Summary(lid, doc));
            }
        }
        return table;
    }

    private final DocumentTable more(Term term) throws IOException {
        final TermDocs docs = state.getIndexReader().termDocs(term);
        if (!docs.next())
            throw new EvalException("no such document");
        final MoreLikeThis mlt = new MoreLikeThis(state.getIndexReader());
        mlt.setFieldNames(FIELDS);
        mlt.setMinDocFreq(1);
        mlt.setMinTermFreq(1);
        final Query query = mlt.like(docs.doc());
        return new SearchTable(view, state, query);
    }

    @SuppressWarnings("unchecked")
    private final Table names() throws ParseException {
        final Collection<String> names = state.getIndexReader().getFieldNames(
                FieldOption.ALL);
        final String[] ls = new String[names.size()];
        int i = 0;
        for (final String name : names)
            ls[i++] = name;
        Arrays.sort(ls);
        return new ArrayTable(TableType.FIELD_NAME, "name", ls);
    }

    private final DocumentTable search(String s) throws IOException,
            ParseException {
        final QueryParser parser = new MultiFieldQueryParser(FIELDS,
                new StandardAnalyzer());
        parser.setAllowLeadingWildcard(true);
        final Query query = parser.parse(s);
        return new SearchTable(view, state, query);
    }

    private final Table values(final String s) throws IOException,
            ParseException {
        final QueryParser parser = new QueryParser(s, new StandardAnalyzer());
        parser.setAllowLeadingWildcard(true);
        final Query query = parser.parse("*");
        final Set<String> values = new TreeSet<String>();
        try {
            state.search(query, new HitCollector() {
                @Override
                public final void collect(int doc, float score) {
                    try {
                        final Document d = state.doc(doc);
                        final Field[] fs = d.getFields(s);
                        for (int i = 0; i < fs.length; ++i)
                            values.add(fs[i].stringValue());
                    } catch (final IOException e) {
                        throw new WrapException(e);
                    }
                }
            });
        } catch (final WrapException e) {
            throw (IOException) e.getCause();
        }
        return new ArrayTable(TableType.FIELD_VALUE, "value", values
                .toArray(new String[values.size()]));
    }

    Model(Environment env, RefreshView view, Repo repo) {
        this.env = env;
        this.view = view;
        this.repo = repo;
        identityMap = new IdentityMap();
        recent = new Recent();
        state = null;
        select = null;
    }

    public final void destroy() {
        view.destroy();
        if (null != state) {
            try {
                state.release();
            } catch (final IOException e) {
                view.getLog().error(e.getLocalizedMessage());
            }
            state = null;
        }
    }

    final void update() throws IOException {
        if (null != state && !state.isCurrent()) {
            state.release();
            state = null;
        }
        if (null == state)
            state = new SharedState(env, repo, identityMap, recent);
    }

    final void setSelection(Selection select) {
        this.select = select;
    }

    final Selection getSelection() {
        return select;
    }

    final File getConfig() {
        return new File(repo.getEtc(), "doogal.conf");
    }

    @Builtin("archive")
    public final Command newArchive() {
        return new AbstractBuiltin() {
            public final String getDescription() {
                return "create a backup archive";
            }

            @SuppressWarnings("unused")
            @Synopsis("archive")
            public final void exec() throws IOException {
                view.getLog().info("archiving...");
                Archive.exec(repo);
            }
        };
    }

    @Builtin("browse")
    public final Command newBrowse() {
        return new AbstractBuiltin() {
            public final String getDescription() {
                return "browse random selection";
            }

            @Override
            public final String getLargeIcon() {
                return "/Find24.gif";
            }

            @Override
            public final String getSmallIcon() {
                return "/Find16.gif";
            }

            @SuppressWarnings("unused")
            @Synopsis("browse")
            public final void exec() throws IOException {
                view.setTable(browse());
                view.refresh();
            }
        };
    }

    @Builtin("delete")
    public final Command newDelete() {
        return new AbstractBuiltin() {
            public final String getDescription() {
                return "delete a document";
            }

            @Override
            public final String getLargeIcon() {
                return "/Delete24.gif";
            }

            @Override
            public final String getSmallIcon() {
                return "/Delete16.gif";
            }

            @SuppressWarnings("unused")
            public final void exec() throws IOException {
                if (TableType.DOCUMENT == view.getType())
                    if (null != select) {
                        exec(select.getArgs());
                        return;
                    }
                // Only when context arguments are set.
                throw new EvalException("unknown command");
            }

            @SuppressWarnings("unused")
            public final void exec(String s) throws IOException {
                view.getLog().info("deleting...");
                if ("*".equals(s))
                    view.whileSummary(new UnaryPredicate<Summary>() {
                        public final boolean call(Summary arg)
                                throws IOException {
                            Delete
                                    .exec(state, identityMap.getTerm(arg
                                            .getId()));
                            return true;
                        }
                    });
                else
                    Delete.exec(state, getTerm(s));
                view.refresh(TableType.DOCUMENT);
            }

            @Synopsis("delete doc...")
            public final void exec(Object... args) throws IOException {
                view.getLog().info("deleting...");
                boolean glob = false;
                for (final Object arg : args) {
                    final String s = arg.toString();
                    if ("*".equals(s))
                        glob = true;
                    else
                        Delete.exec(state, getTerm(arg.toString()));
                }
                if (glob)
                    view.whileSummary(new UnaryPredicate<Summary>() {
                        public final boolean call(Summary arg)
                                throws IOException {
                            Delete
                                    .exec(state, identityMap.getTerm(arg
                                            .getId()));
                            return true;
                        }
                    });
                view.refresh(TableType.DOCUMENT);
            }
        };
    }

    @Builtin("goto")
    public final Command newGoto() {
        return new AbstractBuiltin() {
            public final String getDescription() {
                return "jump to page";
            }

            @SuppressWarnings("unused")
            @Synopsis("goto n")
            public final void exec(String n) throws IOException {
                view.setPage(Integer.valueOf(n));
                view.refresh();
            }
        };
    }

    @Builtin("import")
    public final Command newImport() {
        return new AbstractBuiltin() {
            public final String getDescription() {
                return "import from inbox";
            }

            @Override
            public final String getLargeIcon() {
                return "/Import24.gif";
            }

            @Override
            public final String getSmallIcon() {
                return "/Import16.gif";
            }

            @SuppressWarnings("unused")
            @Synopsis("import")
            public final void exec() throws IOException {
                view.getLog().info("importing...");
                Import.exec(view, state);
            }
        };
    }

    @Builtin("index")
    public final Command newIndex() {
        return new AbstractBuiltin() {
            public final String getDescription() {
                return "generate search index";
            }

            @Override
            public final String getLargeIcon() {
                return "/History24.gif";
            }

            @Override
            public final String getSmallIcon() {
                return "/History16.gif";
            }

            @SuppressWarnings("unused")
            @Synopsis("index")
            public final void exec() throws IOException {
                view.getLog().info("indexing...");
                Index.exec(repo);
            }
        };
    }

    @Builtin("list")
    public final Command newList() {
        return new AbstractBuiltin() {
            public final String getDescription() {
                return "show current page";
            }

            @SuppressWarnings("unused")
            @Synopsis("list")
            public final void exec() throws IOException {
                view.refresh();
            }
        };
    }

    @Builtin("more")
    public final Command newMore() {
        return new AbstractBuiltin() {

            public final String getDescription() {
                return "more like this";
            }

            @Override
            public final String getLargeIcon() {
                return "/FindAgain24.gif";
            }

            @Override
            public final String getSmallIcon() {
                return "/FindAgain16.gif";
            }

            @SuppressWarnings("unused")
            public final void exec() throws IOException {
                if (TableType.DOCUMENT == view.getType())
                    if (null != select) {
                        exec(select.getArg().toString());
                        return;
                    }
                view.setTable(more(getTerm()));
                view.refresh();
            }

            @Synopsis("more [doc]")
            public final void exec(String s) throws IOException,
                    NumberFormatException {
                view.setTable(more(getTerm(s)));
                view.refresh();
            }
        };
    }

    @Builtin("names")
    public final Command newNames() {
        return new AbstractBuiltin() {
            public final String getDescription() {
                return "field names";
            }

            @SuppressWarnings("unused")
            @Synopsis("names")
            public final void exec() throws IOException, ParseException {
                view.setTable(names());
                view.refresh();
            }
        };
    }

    @Builtin("new")
    public final Command newNew() {
        return new AbstractBuiltin() {
            public final String getDescription() {
                return "create a new document";
            }

            @Override
            public final String getLargeIcon() {
                return "/New24.gif";
            }

            @Override
            public final String getSmallIcon() {
                return "/New16.gif";
            }

            @SuppressWarnings("unused")
            public final void exec() throws InterruptedException, IOException {
                view.getLog().info("new document...");
                if (New.exec(view, state))
                    view.refresh(TableType.DOCUMENT);
            }

            @SuppressWarnings("unused")
            @Synopsis("new [template]")
            public final void exec(String s) throws InterruptedException,
                    IOException {
                view.getLog().info("new document...");
                if (New.exec(view, state, s))
                    view.refresh(TableType.DOCUMENT);
            }
        };
    }

    @Builtin("next")
    public final Command newNext() {
        return new AbstractBuiltin() {
            public final String getDescription() {
                return "move to next page";
            }

            @SuppressWarnings("unused")
            @Synopsis("next")
            public final void exec() throws IOException {
                view.nextPage();
                view.refresh();
            }
        };
    }

    @Builtin("open")
    public final Command newOpen() {
        return new AbstractBuiltin() {
            public final String getDescription() {
                return "open existing document";
            }

            @Override
            public final String getLargeIcon() {
                return "/Open24.gif";
            }

            @Override
            public final String getSmallIcon() {
                return "/Open16.gif";
            }

            @SuppressWarnings("unused")
            public final void exec() throws InterruptedException, IOException {
                if (TableType.DOCUMENT == view.getType())
                    if (null != select) {
                        exec(select.getArg().toString());
                        return;
                    }
                view.getLog().info("opening...");
                if (Open.exec(view, state, getTerm()))
                    view.refresh(TableType.DOCUMENT);
            }

            @Synopsis("open [doc]")
            public final void exec(String s) throws InterruptedException,
                    IOException {
                view.getLog().info("opening...");
                if (Open.exec(view, state, getTerm(s)))
                    view.refresh(TableType.DOCUMENT);
            }
        };
    }

    @Builtin("peek")
    public final Command newPeek() {
        return new AbstractBuiltin() {
            public final String getDescription() {
                return "peek inside document";
            }

            @Override
            public final String getLargeIcon() {
                return "/Zoom24.gif";
            }

            @Override
            public final String getSmallIcon() {
                return "/Zoom16.gif";
            }

            @SuppressWarnings("unused")
            public final void exec() throws IOException, NumberFormatException {
                if (TableType.DOCUMENT == view.getType())
                    if (null != select) {
                        exec(select.getArgs());
                        return;
                    }
                Peek.exec(view, state, getTerm());
            }

            @SuppressWarnings("unused")
            public final void exec(String s) throws IOException {
                if ("*".equals(s))
                    view.whileSummary(new UnaryPredicate<Summary>() {
                        public final boolean call(Summary arg)
                                throws IOException {
                            Peek.exec(view, state, identityMap.getTerm(arg
                                    .getId()));
                            return true;
                        }
                    });
                else
                    Peek.exec(view, state, getTerm(s));
            }

            @Synopsis("peek [doc...]")
            public final void exec(Object... args) throws IOException,
                    NumberFormatException {
                boolean glob = false;
                for (final Object arg : args) {
                    final String s = arg.toString();
                    if ("*".equals(s))
                        glob = true;
                    else
                        Peek.exec(view, state, getTerm(arg.toString()));
                }
                if (glob)
                    view.whileSummary(new UnaryPredicate<Summary>() {
                        public final boolean call(Summary arg)
                                throws IOException {
                            Peek.exec(view, state, identityMap.getTerm(arg
                                    .getId()));
                            return true;
                        }
                    });
            }
        };
    }

    @Builtin("previous")
    public final Command newPrev() {
        return new AbstractBuiltin() {
            public final String getDescription() {
                return "move to previous page";
            }

            @SuppressWarnings("unused")
            @Synopsis("previous")
            public final void exec() throws IOException {
                view.prevPage();
                view.refresh();
            }
        };
    }

    @Builtin("publish")
    public final Command newPublish() {
        return new AbstractBuiltin() {
            public final String getDescription() {
                return "publish to html";
            }

            @Override
            public final String getLargeIcon() {
                return "/WebComponent24.gif";
            }

            @Override
            public final String getSmallIcon() {
                return "/WebComponent16.gif";
            }

            @SuppressWarnings("unused")
            public final void exec() throws IOException, NumberFormatException {
                if (TableType.DOCUMENT == view.getType())
                    if (null != select) {
                        exec(select.getArgs());
                        return;
                    }
                view.getLog().info("publishing...");
                view.setHtml(Publish.exec(view, state, getTerm()));
            }

            @SuppressWarnings("unused")
            public final void exec(String s) throws IOException {
                final WriteOnce<HtmlPage> once = new WriteOnce<HtmlPage>();
                view.getLog().info("publishing...");

                if ("*".equals(s))
                    try {
                        view.whileSummary(new UnaryPredicate<Summary>() {
                            public final boolean call(Summary arg)
                                    throws IOException {
                                once.set(Publish.exec(view, state, identityMap
                                        .getTerm(arg.getId())));
                                return true;
                            }
                        });
                    } catch (final Exception e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                else
                    once.set(Publish.exec(view, state, getTerm(s)));

                if (!once.isEmpty())
                    view.setHtml(once.get());
            }

            @Synopsis("publish [doc...]")
            public final void exec(Object... args) throws IOException,
                    NumberFormatException {
                final WriteOnce<HtmlPage> once = new WriteOnce<HtmlPage>();
                view.getLog().info("publishing...");

                boolean glob = false;
                for (final Object arg : args) {
                    final String s = arg.toString();
                    if (!"*".equals(s))
                        once.set(Publish.exec(view, state, getTerm(arg
                                .toString())));
                    else
                        // Glob once.
                        glob = true;
                }
                if (glob)
                    view.whileSummary(new UnaryPredicate<Summary>() {
                        public final boolean call(Summary arg)
                                throws IOException {
                            once.set(Publish.exec(view, state, identityMap
                                    .getTerm(arg.getId())));
                            return true;
                        }
                    });
                if (!once.isEmpty())
                    view.setHtml(once.get());
            }
        };
    }

    @Builtin("recent")
    public final Command newRecent() {
        return new AbstractBuiltin() {
            public final String getDescription() {
                return "recently visited";
            }

            @Override
            public final String getLargeIcon() {
                return "/Bookmarks24.gif";
            }

            @Override
            public final String getSmallIcon() {
                return "/Bookmarks16.gif";
            }

            @SuppressWarnings("unused")
            @Synopsis("recent")
            public final void exec() throws IOException {
                view.setTable(recent.asTable(state));
                view.refresh();
            }
        };
    }

    @Builtin("search")
    public final Command newSearch() {
        return new AbstractBuiltin() {
            private String last = "doogal";

            public final String getDescription() {
                return "search repository";
            }

            @Override
            public final String getLargeIcon() {
                return "/Find24.gif";
            }

            @Override
            public final String getSmallIcon() {
                return "/Find16.gif";
            }

            @SuppressWarnings("unused")
            public final void exec() throws IOException, ParseException {
                view.getLog().info(
                        String.format("searching for '%s'...\n", last));
                view.setTable(search(last));
                view.refresh();
            }

            public final void exec(String s) throws IOException, ParseException {
                view.setTable(search(s));
                view.refresh();
                last = s;
            }

            @SuppressWarnings("unused")
            @Synopsis("search [expr...]")
            public final void exec(Object... args) throws IOException,
                    ParseException {
                exec(join(args));
            }
        };
    }

    @Builtin("set")
    public final Command newEnv() {
        return new AbstractBuiltin() {
            public final String getDescription() {
                return "environment configuration";
            }

            @Override
            public final String getLargeIcon() {
                return "/Preferences24.gif";
            }

            @Override
            public final String getSmallIcon() {
                return "/Preferences16.gif";
            }

            @SuppressWarnings("unused")
            @Synopsis("set")
            public final void exec() throws IOException {
                view.setTable(env.asTable());
                view.refresh();
            }

            @SuppressWarnings("unused")
            @Synopsis("set name")
            public final void exec(String name) {
                env.reset(name);
                view.refresh(TableType.ENVIRONMENT);
            }

            @SuppressWarnings("unused")
            @Synopsis("set name value")
            public final void exec(String name, String value) {
                env.set(name, value);
                view.refresh(TableType.ENVIRONMENT);
            }
        };
    }

    @Builtin("tidy")
    public final Command newTidy() {
        return new AbstractBuiltin() {
            public final String getDescription() {
                return "clean ascii and trim";
            }

            @SuppressWarnings("unused")
            public final void exec() throws IOException {
                if (TableType.DOCUMENT == view.getType())
                    if (null != select) {
                        exec(select.getArgs());
                        return;
                    }
                view.getLog().info("tidying...");
                Tidy.exec(view, state, getTerm());
            }

            @SuppressWarnings("unused")
            public final void exec(String s) throws IOException {
                view.getLog().info("tidying...");
                if ("*".equals(s))
                    view.whileSummary(new UnaryPredicate<Summary>() {
                        public final boolean call(Summary arg)
                                throws IOException {
                            Tidy.exec(view, state, identityMap.getTerm(arg
                                    .getId()));
                            return true;
                        }
                    });
                else
                    Tidy.exec(view, state, getTerm(s));
            }

            @Synopsis("tidy [doc...]")
            public final void exec(Object... args) throws IOException,
                    NumberFormatException {
                view.getLog().info("tidying...");
                boolean glob = false;
                for (final Object arg : args) {
                    final String s = arg.toString();
                    if ("*".equals(s))
                        glob = true;
                    else
                        Tidy.exec(view, state, getTerm(arg.toString()));
                }
                if (glob)
                    view.whileSummary(new UnaryPredicate<Summary>() {
                        public final boolean call(Summary arg)
                                throws IOException {
                            Tidy.exec(view, state, identityMap.getTerm(arg
                                    .getId()));
                            return true;
                        }
                    });
            }
        };
    }

    @Builtin("values")
    public final Command newValues() {
        return new AbstractBuiltin() {
            public final String getDescription() {
                return "field values";
            }

            @SuppressWarnings("unused")
            @Synopsis("values name")
            public final void exec(String s) throws IOException, ParseException {
                view.setTable(values(s));
                view.refresh();
            }
        };
    }
}
