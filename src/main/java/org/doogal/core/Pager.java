package org.doogal.core;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collection;

import javax.mail.MessagingException;

import org.apache.lucene.index.Term;

interface Pager extends Closeable {

    void setPage(String n) throws EvalException, IOException;

    void showPage() throws IOException;

    void nextPage() throws IOException;

    void prevPage() throws IOException;
    
    String peek(Term term) throws IOException, MessagingException;
    
    Collection<Term> terms() throws IOException;
}
