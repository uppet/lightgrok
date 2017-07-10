package org.lightgrok;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;

import org.apache.log4j.Logger;
import org.apache.log4j.Level;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;

/** Simple command-line based search demo. */
public class Searcher {

    private Searcher() {}

    private String mRoot = null;
	private Logger mLogger = Logger.getLogger("lightgrok");
    public static Searcher createSearcherWithRoot(String root) {
        Searcher n = new Searcher();
        n.mRoot = root;
		n.mLogger.setLevel(Level.ERROR);
        return n;
    }

    public void doSearch(String search) throws Exception {
        String fuzzySearch = "*" + search + "*";
        Path indexDir = PathProvider.rootIndexDirectory();
        final Path docDir = Paths.get(mRoot);

        indexDir = Paths.get(indexDir.toString(),
                             PathProvider.hashSourcePath(docDir.toString()));

        System.out.println("Index of directory '" + indexDir.toString() + "'...");

        String field = "contents";
        int repeat = 0;
        boolean raw = false;
        String queryString = fuzzySearch;

        IndexReader reader = DirectoryReader.open(FSDirectory.open(indexDir));
        IndexSearcher searcher = new IndexSearcher(reader);
        Analyzer analyzer = new StandardAnalyzer();

        QueryParser parser = new QueryParser(field, analyzer);
        parser.setAllowLeadingWildcard(true);
        while (true) {
            String line = queryString;

            if (line == null || line.length() == -1) {
                break;
            }

            line = line.trim();
            if (line.length() == 0) {
                break;
            }

            Query query = parser.parse(line);
            // System.out.println("Searching for: " + query.toString(field));
			mLogger.info("Searching for: " + query.toString(field));

            doSearchInternal(searcher, query, raw, search);

            if (queryString != null) {
                break;
            }
        }
        reader.close();
    }

    public void doSearchInternal(IndexSearcher searcher, Query query,
                                        boolean raw, String rawQuery) throws IOException {

        // Collect enough docs to show 5 pages
        TopDocs results = searcher.search(query, 5000);
        ScoreDoc[] hits = results.scoreDocs;

        int numTotalHits = results.totalHits;
        mLogger.info(numTotalHits + " total matching documents");

        int start = 0;
        int end = Math.min(numTotalHits, 5000);

        end = Math.min(hits.length, start + 5000);

        boolean grep_report = true;

        for (int i = start; i < end; i++) {
            if (raw) { // output raw format
                mLogger.info("doc="+hits[i].doc+" score="+hits[i].score);
                // continue;
                Document doc = searcher.doc(hits[i].doc);
                String path = doc.get("path");
                if (path != null) {
                    mLogger.info((i+1) + ". " + path);
                    String title = doc.get("title");
                    if (title != null) {
                        mLogger.info("   Title: " + doc.get("title"));
                    }
                } else {
                    mLogger.info((i+1) + ". " + "No path for this document");
                }
            }

            if (grep_report) {
                // System.out.println("try report " + query.toString());
                Document doc = searcher.doc(hits[i].doc);
                String path = doc.get("path");
                final Path docPath = Paths.get(path);

                try (InputStream stream = Files.newInputStream(docPath)) {
                    BufferedReader br = new BufferedReader(
                            new InputStreamReader(stream,
                                                  StandardCharsets.UTF_8));
                    String line;
                    int lineCount = 0;
                    // process the line.
                    while ((line = br.readLine()) != null) {
                        ++lineCount;
                        String icLine = line.toLowerCase();
                        if (icLine.indexOf(rawQuery.toLowerCase()) != -1) {
                            if (line.length() > 200) {
                                line = line.substring(0, 200);
                            }
                            String reportPath = path;
                            if (PathProvider.getStripRootLead()
                                && path.startsWith(mRoot)) {
                                reportPath = "./" + path.substring(mRoot.length());
                            }
                            System.out.print(reportPath + ":" + lineCount + ":\t" + line);
                            System.out.println();
                        }
                    }
                } catch (IOException e) {
                    System.out.println("io exception for: " + path);
                }
            }
        }
    }
}
