package org.doogal;

import java.io.File;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.IndexWriter;

import static org.doogal.Utility.*;

final class Index {

	private static void indexRepo(final Repo repo, final IndexWriter writer)
			throws Exception {

		listFiles(repo.getData(), new Predicate<File>() {
			public final boolean call(File file) {
				if (ignore(file))
					return true;
				try {
					Rfc822.addDocument(writer, repo.getData(), file);
				} catch (final Exception e) {
					System.err.println("Error: " + file + ": " + e);
				}
				return true;
			}
		});
	}

	static void exec(Repo repo) throws Exception {
		final IndexWriter writer = new IndexWriter(repo.getIndex(),
				new StandardAnalyzer(), true,
				IndexWriter.MaxFieldLength.LIMITED);
		try {
			System.out.println("indexing...");
			indexRepo(repo, writer);
		} finally {
			writer.optimize();
			writer.close();
		}
	}
}
