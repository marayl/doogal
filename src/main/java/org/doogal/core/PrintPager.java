package org.doogal.core;

import static org.doogal.core.Constants.PAGE_SIZE;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;

import javax.mail.MessagingException;

import org.apache.lucene.index.Term;

final class PrintPager implements Pager {
    private final Results results;
    private final PrintWriter out;
    private int start;
    private int end;

    PrintPager(Results results, PrintWriter out) throws IOException {
        this.results = results;
        this.out = out;
        start = 0;
        end = Math.min(results.size(), start + PAGE_SIZE);
    }

    public final void close() throws IOException {
        results.close();
    }
    
    public final void setPage(String n) throws EvalException, IOException {
        final int i = Math.max(Integer.valueOf(n) - 1, 0) * PAGE_SIZE;
        if (results.size() <= i)
            throw new EvalException("no such page");
        start = i;
    }

    public final void showPage() throws IOException {

        end = Math.min(results.size(), start + PAGE_SIZE);

        String prompt = null;
        if (0 < results.size()) {
            final int page = 1 + start / PAGE_SIZE;
            final int total = 1 + (results.size() - 1) / PAGE_SIZE;
            out.println("page " + page + " of " + total + ":");
            if (page < total)
                prompt = "more...";
        } else
            out.println("no results");

        for (int i = start; i < end; i++) {

            final String s = results.get(i);
            if (0 == s.length())
                out.println();
            else
                out.println(" " + s);
        }

        if (null != prompt)
            out.println(prompt);
    }

    public final void nextPage() throws IOException {
        if (start + PAGE_SIZE < results.size())
            start += PAGE_SIZE;
    }

    public final void prevPage() throws IOException {
        start = Math.max(0, start - PAGE_SIZE);
    }

    public final String peek(Term term) throws IOException, MessagingException {
        return results.peek(term, out);
    }

    public final Collection<Term> terms() throws IOException {
        return results.getTerms();
    }
}
