package org.doogal.notes.core;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.IndexWriter;

public final class Repo {
    private final File root;
    private final File data;
    private final File etc;
    private final File index;
    private final File tmp;
    private final File trash;

    private void initConfig() throws FileNotFoundException {
        final File file = new File(etc, "doogal.conf");
        if (!file.exists()) {
            final PrintWriter out = new PrintWriter(file);
            try {
                out.println("# Doogle config file.");
                out.println("alias ls list");
                out.println("alias p peek");
                out.println("alias s search");
                out.println("index");
            } finally {
                out.close();
            }
        }
    }

    private void initType(File dir, String type) throws FileNotFoundException {
        final File file = new File(dir, type + ".txt");
        if (!file.exists()) {
            final PrintWriter out = new PrintWriter(file);
            try {
                out.println("Title: ");
                out.println("Label: ");
                out.println("Content-Type: text/" + type);
                out.println();
            } finally {
                out.close();
            }
        }
    }

    private void initMail(File dir) throws FileNotFoundException {
        final File file = new File(dir, "mail.txt");
        if (!file.exists()) {
            final PrintWriter out = new PrintWriter(file);
            try {
                out.println("From: ");
                out.println("To: ");
                out.println("Subject: ");
                out.println();
            } finally {
                out.close();
            }
        }
    }

    public Repo(String path) {
        root = new File(path);
        data = new File(root, "data");
        etc = new File(root, "etc");
        index = new File(root, "index");
        tmp = new File(root, "tmp");
        trash = new File(root, "trash");
    }

    public final void init() throws IOException {

        final boolean create = !index.exists();

        root.mkdir();
        data.mkdir();
        etc.mkdir();
        index.mkdir();
        tmp.mkdir();
        trash.mkdir();

        initConfig();

        new File(root, "html").mkdir();
        new File(root, "inbox").mkdir();

        final File dir = new File(root, "template");
        dir.mkdir();
        initType(dir, "plain");
        initMail(dir);

        if (create) {
            final IndexWriter writer = new IndexWriter(index,
                    new StandardAnalyzer(), true,
                    IndexWriter.MaxFieldLength.LIMITED);
            writer.close();
        }
    }

    final File getData() {
        return data;
    }

    final File getEtc() {
        return etc;
    }

    final File getIndex() {
        return index;
    }

    final File getTmp() {
        return tmp;
    }

    final File getTrash() {
        return trash;
    }
}
