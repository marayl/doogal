package org.doogal.notes.core;

import static org.doogal.notes.core.Utility.copyTempFile;
import static org.doogal.notes.core.Utility.edit;
import static org.doogal.notes.core.Utility.firstFile;
import static org.doogal.notes.core.Utility.getId;

import java.io.File;
import java.io.IOException;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.doogal.notes.util.EvalException;
import org.doogal.notes.view.View;

final class Open {

    static boolean exec(View view, SharedState state, Term term)
            throws InterruptedException, IOException {

        final IndexReader reader = state.getIndexReader();
        final File file = firstFile(reader, state.getData(), term);
        if (null == file)
            throw new EvalException("no such document");

        final String id = getId(file);
        final File tmp = copyTempFile(file, state.getTmp());
        try {

            if (!edit(state.getEditor(), tmp, view.getOut())) {
                view.getLog().info("no change...");
                return false;
            }

            view.getLog().info(
                    String.format("indexing document %d...\n", state
                            .getLocal(id)));
            final IndexWriter writer = new IndexWriter(state.getIndex(),
                    new StandardAnalyzer(), false,
                    IndexWriter.MaxFieldLength.LIMITED);
            try {
                if (file.exists()) {
                    Tidy.tidy(tmp, file);
                    Rfc822.updateDocument(writer, state.getData(), file);
                } else
                    writer.deleteDocuments(new Term("id", id));
            } finally {
                writer.optimize();
                writer.close();
            }

        } finally {
            tmp.delete();
            state.addRecent(id);
        }
        return true;
    }
}
