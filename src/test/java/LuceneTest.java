/*
 * This Java source file was generated by the Gradle 'init' task.
 */
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TopDocs;
import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;

import java.io.IOException;

public class LuceneTest {
    @Test public void testLuceneTermQuery() throws IOException {
        // For learning purpose
        Directory dir = new RAMDirectory();
        Analyzer analyzer = new StandardAnalyzer();
        IndexWriterConfig iwc = new IndexWriterConfig(analyzer);

        IndexWriter writer = new IndexWriter(dir, iwc);

        Document doc = new Document();


        Field pathField = new StringField("path", "inside memory", Field.Store.YES);
        doc.add(pathField);

        doc.add(new TextField("contents",  "a fox jump over the bron dog", Field.Store.YES));
        writer.addDocument(doc);

        writer.close();

        IndexReader reader = DirectoryReader.open(dir);
        IndexSearcher searcher = new IndexSearcher(reader);

        TermQuery tq = null;
        TopDocs results = null;

        tq = new TermQuery(new Term("contents", "jump"));
        results = searcher.search(tq, 100);
        assertEquals(results.totalHits, 1);

        tq = new TermQuery(new Term("contents", "bron"));
        results = searcher.search(tq, 100);
        assertEquals(results.totalHits, 1);

        tq = new TermQuery(new Term("contents", "www"));
        results = searcher.search(tq, 100);
        assertEquals(results.totalHits, 0);

        reader.close();
    }
}
