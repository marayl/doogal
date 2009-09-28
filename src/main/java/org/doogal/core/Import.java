package org.doogal.core;

import static org.doogal.core.Utility.getId;
import static org.doogal.core.Utility.ignore;
import static org.doogal.core.Utility.listFiles;
import static org.doogal.core.Utility.newId;
import static org.doogal.core.Utility.renameFile;
import static org.doogal.core.Utility.subdir;

import java.io.File;
import java.io.IOException;

import javax.mail.MessagingException;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.IndexWriter;

final class Import {

    private static File importFile(SharedState state, File from)
            throws IOException {

        File to = subdir(state.getData());
        final String id = newId();
        to = new File(to, id + ".txt");
        renameFile(from, to);
        return to;
    }

    static void exec(final SharedState state) throws Exception {
        final IndexWriter writer = new IndexWriter(state.getIndex(),
                new StandardAnalyzer(), false,
                IndexWriter.MaxFieldLength.LIMITED);
        try {
            listFiles(new File(state.getInbox()), new Predicate<File>() {
                public final boolean call(File file) throws IOException,
                        MessagingException {
                    if (ignore(file))
                        return true;
                    file = importFile(state, file);
                    final String id = getId(file);
                    final int lid = state.getLocal(getId(file));
                    state.log.info(String
                            .format("indexing document %d...", lid));
                    Rfc822.addDocument(writer, state.getData(), file);
                    state.addRecent(id);
                    return true;
                }
            });
        } finally {
            writer.optimize();
            writer.close();
        }
    }
}