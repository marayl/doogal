package org.doogal.core;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;

public final class Repo {
    private final File root;
    private final File data;
    private final File etc;
    private final File index;
    private final File trash;

    private void initConfig() throws FileNotFoundException {
        final File file = new File(etc, "doogal.conf");
        if (!file.exists()) {
            final PrintWriter out = new PrintWriter(file);
            try {
                out.println("# Doogle config file.");
                out.println("alias ls list");
                out.println("alias p publish");
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
        trash = new File(root, "trash");
    }

    public final void init() throws FileNotFoundException {

        root.mkdir();
        data.mkdir();
        etc.mkdir();
        index.mkdir();
        trash.mkdir();

        initConfig();

        new File(root, "html").mkdir();
        new File(root, "inbox").mkdir();

        final File dir = new File(root, "template");
        dir.mkdir();
        initType(dir, "plain");
        initMail(dir);
    }

    final File getData() {
        return data;
    }

    public final File getEtc() {
        return etc;
    }

    final File getIndex() {
        return index;
    }

    final File getTrash() {
        return trash;
    }
}