package org.doogal.core;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.lucene.index.Term;

final class ListSet implements DataSet {

    private final List<String> ls;

    ListSet(List<String> ls) {
        this.ls = ls;
    }

    public final void close() throws IOException {
    }

    public final String peek(Term term, PrintWriter out) {
        return null;
    }

    public final Collection<Term> getTerms() {
        return Collections.<Term> emptyList();
    }

    public final String get(int i) throws IOException {
        return ls.get(i);
    }

    public final int size() {
        return ls.size();
    }

    static final ListSet EMPTY = new ListSet(Collections.<String> emptyList());
}