package org.doogal.core;

import static org.doogal.core.Utility.getId;
import static org.doogal.core.Utility.listFiles;
import static org.doogal.core.Utility.renameFile;

import java.io.File;
import java.io.IOException;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;

final class Delete {

    static void exec(final SharedState state, Term term) throws Exception {
        final IndexReader reader = state.getIndexReader();
        listFiles(reader, state.getData(), term, new Predicate<File>() {
            public final boolean call(File file) throws EvalException,
                    IOException {
                final File trash = new File(state.getTrash(), file.getName());
                trash.delete();
                renameFile(file, trash);
                state.removeRecent(getId(file));
                return true;
            }
        });

        final IndexWriter writer = new IndexWriter(state.getIndex(),
                new StandardAnalyzer(), false,
                IndexWriter.MaxFieldLength.LIMITED);
        try {
            writer.deleteDocuments(term);
        } finally {
            writer.optimize();
            writer.close();
        }
    }
}