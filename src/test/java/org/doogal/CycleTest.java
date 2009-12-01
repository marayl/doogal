package org.doogal;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import jdepend.framework.JDepend;
import jdepend.framework.JavaPackage;
import junit.framework.TestCase;

public final class CycleTest extends TestCase {

    @SuppressWarnings("unchecked")
    private static void printCycles(JDepend jdepend, PrintWriter out) {
        final Collection<JavaPackage> packages = jdepend.getPackages();
        for (final JavaPackage p : packages)
            if (p.containsCycle()) {
                out.println(p.getName() + " contains cycles:");
                final List<JavaPackage> all = new ArrayList<JavaPackage>();
                p.collectAllCycles(all);
                for (final JavaPackage q : all)
                    out.println("  " + q.getName());
            }
    }

    private static void printCycles(JDepend jdepend, OutputStream out) {
        printCycles(jdepend, new PrintWriter(out));
    }

    @SuppressWarnings("unchecked")
    private static void printScript(JDepend jdepend, PrintWriter out) {
        final Collection<JavaPackage> packages = jdepend.getPackages();
        out.println("#!/bin/sh");
        out.println("cat <<EOD | tred | dot -Tgif >doogal.gif");
        out.println("digraph G {");
        out.println("  label=\"Doogal\";");
        out.println("  edge [style=dashed];");
        out.println("  graph [rankdir=BT];");
        out.println("  node [shape=box];");
        for (final JavaPackage p : packages) {
            final Collection<JavaPackage> efferents = p.getEfferents();
            for (final JavaPackage q : efferents)
                if (q.getName().startsWith("org.doogal")) {
                    final String from = p.getName().replaceAll("[.]", "_");
                    final String to = q.getName().replaceAll("[.]", "_");
                    out.println("  " + from + " -> " + to + ";");
                }
        }
        out.println("}");
        out.println("EOD");
    }

    private static void printScript(JDepend jdepend, File file)
            throws FileNotFoundException {
        final PrintWriter out = new PrintWriter(file);
        try {
            printScript(jdepend, out);
        } finally {
            out.close();
        }
        file.setExecutable(true);
    }

    @Override
    protected final void setUp() throws IOException {
        System.setProperty("line.separator", "\n");
    }

    public final void testAllPackages() throws IOException {
        // Do not remove this test.
        // Use the Ant jdepend task to identify cycles.
        final JDepend jdepend = new JDepend();
        jdepend.addDirectory("target/classes");
        jdepend.analyze();
        printCycles(jdepend, System.out);
        // Create Graphviz script.
        final File file = new File("target/jdepend.sh");
        printScript(jdepend, file);
        // Fail if cycles exist.
        assertEquals("cycles exist", false, jdepend.containsCycles());
    }
}
