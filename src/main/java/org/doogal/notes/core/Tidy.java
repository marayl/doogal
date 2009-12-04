package org.doogal.notes.core;

import static org.doogal.notes.core.Utility.copyTempFile;
import static org.doogal.notes.core.Utility.firstFile;
import static org.doogal.notes.core.Utility.getId;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.doogal.notes.util.EvalException;
import org.doogal.notes.view.View;

final class Tidy {
    private static final String tidy(String s) {
        s = s.replace('\u2013', '-');
        s = s.replace('\u2018', '\'');
        s = s.replace('\u2019', '\'');
        s = s.replace('\u201C', '"');
        s = s.replace('\u201D', '"');
        s = s.replace('\u2022', '*');
        s = s.replace("\u2026", "...");
        // Trim trailing.
        return s.replaceFirst("\\s+$", "");
    }

    private static void tidy(BufferedReader in, PrintWriter out)
            throws IOException {
        for (;;) {
            final String line = in.readLine();
            if (null == line)
                break;
            out.println(tidy(line));
        }
    }

    static void tidy(File from, File to) throws IOException {
        final BufferedReader in = new BufferedReader(new InputStreamReader(
                new FileInputStream(from), "UTF-8"));
        try {
            final PrintWriter out = new PrintWriter(to, "UTF-8");
            try {
                tidy(in, out);
            } finally {
                out.close();
            }
        } finally {
            in.close();
        }
    }

    static void exec(File file, File dir) throws IOException {
        final File tmp = copyTempFile(file, dir);
        try {
            tidy(tmp, file);
        } finally {
            tmp.delete();
        }
    }

    static void exec(View view, final SharedState state, Term term)
            throws IOException {

        final IndexReader reader = state.getIndexReader();
        final File file = firstFile(reader, state.getData(), term);
        if (null == file)
            throw new EvalException("no such document");

        exec(file, state.getTmp());

        final String id = getId(file);
        view.getLog().info("indexing document...");
        final IndexWriter writer = new IndexWriter(state.getIndex(),
                new StandardAnalyzer(), false,
                IndexWriter.MaxFieldLength.LIMITED);
        try {
            if (file.exists())
                Rfc822.updateDocument(writer, state.getData(), file);
            else
                writer.deleteDocuments(new Term("id", id));
        } finally {
            writer.optimize();
            writer.close();
        }
        state.addRecent(id);
    }

    public static void main(String[] args) throws IOException {
        System.setProperty("line.separator", "\n");
        tidy(new File("C:\\tmp\\in.txt"), new File("C:\\tmp\\out.txt"));
    }
}
