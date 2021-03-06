package org.lightgrok;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.spi.FileTypeDetector;
import java.util.Date;

import org.apache.log4j.Logger;
import org.apache.log4j.Level;
import org.apache.lucene.analysis.Analyzer;
//import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.core.SimpleAnalyzer;
import org.apache.lucene.document.LongField;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;


public class Indexer {
    private String mRoot = null;
    private Logger mLogger = Logger.getLogger("lightgrok");

    public static Indexer createIndexerWithRoot(String root) {
        Indexer n = new Indexer();
        n.mRoot = root;
        n.mLogger.setLevel(Level.ERROR);

        return n;
    }
    private Indexer() {}

    public void doIndex() {
        Date start = new Date();
        try {
            Path indexDir = PathProvider.rootIndexDirectory();
            final Path docDir = Paths.get(mRoot);

            indexDir = Paths.get(indexDir.toString(),
                                 PathProvider.hashSourcePath(docDir.toString()));

            //System.out.println("Indexing to directory '" + indexDir.toString() + "'...");
            mLogger.info("Indexing to directory '" + indexDir.toString() + "'...");

            Directory dir = FSDirectory.open(indexDir);
            //Analyzer analyzer = new StandardAnalyzer();
            Analyzer analyzer = new SimpleAnalyzer();
            IndexWriterConfig iwc = new IndexWriterConfig(analyzer);

            boolean create = true;

            if (create) {
                // Create a new index in the directory, removing any
                // previously indexed documents:
                iwc.setOpenMode(OpenMode.CREATE);
            } else {
                // Add new documents to an existing index:
                iwc.setOpenMode(OpenMode.CREATE_OR_APPEND);
            }

            // Optional: for better indexing performance, if you
            // are indexing many documents, increase the RAM
            // buffer.  But if you do this, increase the max heap
            // size to the JVM (eg add -Xmx512m or -Xmx1g):
            //
            // iwc.setRAMBufferSizeMB(256.0);

            IndexWriter writer = new IndexWriter(dir, iwc);
            indexDocs(writer, docDir);

            // NOTE: if you want to maximize search performance,
            // you can optionally call forceMerge here.  This can be
            // a terribly costly operation, so generally it's only
            // worth it when your index is relatively static (ie
            // you're done adding documents to it):
            //
            // writer.forceMerge(1);

            writer.close();

            Date end = new Date();
            // System.out.println(end.getTime() - start.getTime() + " total milliseconds");
            mLogger.info(end.getTime() - start.getTime() + " total milliseconds");

        } catch (IOException e) {
            // System.out.println(" caught a " + e.getClass() +
            //                    "\n with message: " + e.getMessage());
            mLogger.info(" caught a " + e.getClass() +
                    "\n with message: " + e.getMessage());
        }
    }


    void indexDocs(final IndexWriter writer, Path path) throws IOException {
        if (Files.isDirectory(path)) {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                    @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        try {
                            String fileType = Files.probeContentType(file);
                            // System.out.println(file.toString() + ":" + fileType);
                            mLogger.info(file.toString() + ":" + fileType);
                            if (!fileType.startsWith("text/")) {
                                return FileVisitResult.CONTINUE;
                            }
                            if (file.toString().indexOf(".git") != -1) {
                                return FileVisitResult.SKIP_SUBTREE;
                            }
                            if (file.toString().indexOf("LayoutTests") != -1) {
                                return FileVisitResult.SKIP_SUBTREE;
                            }
                            if (file.toString().indexOf("PerformanceTests") != -1) {
                                return FileVisitResult.SKIP_SUBTREE;
                            }
                            if (file.toString().indexOf("/.tars") != -1) {
                                return FileVisitResult.SKIP_SUBTREE;
                            }
                            if (file.toString().indexOf(".tgz") != -1) {
                                return FileVisitResult.CONTINUE;
                            }
                            if (file.toString().indexOf("/.svn") != -1) {
                                return FileVisitResult.SKIP_SUBTREE;
                            }
                            if (file.toString().indexOf("/android-sdk-linux/") != -1) {
                                return FileVisitResult.SKIP_SUBTREE;
                            }
                            if (file.toString().indexOf("/out/") != -1) {
                                return FileVisitResult.SKIP_SUBTREE;
                            }
                            if (file.toString().indexOf("/ucbrowser/OUT") != -1) {
                                return FileVisitResult.SKIP_SUBTREE;
                            }
                            if (file.toString().endsWith(".jar")) {
                                return FileVisitResult.CONTINUE;
                            }
                            if (file.toString().endsWith(".apk")) {
                                return FileVisitResult.CONTINUE;
                            }
                            indexDoc(writer, file, attrs.lastModifiedTime().toMillis());
                        } catch (IOException ignore) {
                            // don't index files that can't be read.
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
        } else {
            indexDoc(writer, path, Files.getLastModifiedTime(path).toMillis());
        }
    }

  void indexDoc(IndexWriter writer, Path file, long lastModified) throws IOException {
    try (InputStream stream = Files.newInputStream(file)) {
      // make a new, empty document
      Document doc = new Document();

      // Add the path of the file as a field named "path".  Use a
      // field that is indexed (i.e. searchable), but don't tokenize
      // the field into separate words and don't index term frequency
      // or positional information:
      Field pathField = new StringField("path", file.toString(), Field.Store.YES);
      doc.add(pathField);

      // Add the last modified date of the file a field named "modified".
      // Use a LongPoint that is indexed (i.e. efficiently filterable with
      // PointRangeQuery).  This indexes to milli-second resolution, which
      // is often too fine.  You could instead create a number based on
      // year/month/day/hour/minutes/seconds, down the resolution you require.
      // For example the long value 2011021714 would mean
      // February 17, 2011, 2-3 PM.
      doc.add(new LongField("modified", lastModified, Field.Store.NO));

      // Add the contents of the file to a field named "contents".  Specify a Reader,
      // so that the text of the file is tokenized and indexed, but not stored.
      // Note that FileReader expects the file to be in UTF-8 encoding.
      // If that's not the case searching for special characters will fail.
      doc.add(new TextField("contents", new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))));

      if (writer.getConfig().getOpenMode() == OpenMode.CREATE) {
          // New index, so we just add the document (no old document can be there):
          // System.out.println("adding " + file);
          mLogger.info("adding " + file);
          writer.addDocument(doc);
      } else {
          // Existing index (an old copy of this document may have been indexed) so
          // we use updateDocument instead to replace the old one matching the exact
          // path, if present:
          // System.out.println("updating " + file);
          mLogger.info("updating " + file);
          writer.updateDocument(new Term("path", file.toString()), doc);
      }
    }
  }
}
