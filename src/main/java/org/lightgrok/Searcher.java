/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
    public static Searcher createSearcherWithRoot(String root) {
        Searcher n = new Searcher();
        n.mRoot = root;
        return n;
    }
  
    public void doSearch(String search) throws Exception {
        final String indexDir = "/tmp/lightgrok/index";

        String field = "contents";
        int repeat = 0;
        boolean raw = false;
        String queries = null;
        String queryString = search;
    
        IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexDir)));
        IndexSearcher searcher = new IndexSearcher(reader);
        Analyzer analyzer = new StandardAnalyzer();

        QueryParser parser = new QueryParser(field, analyzer);
        while (true) {
            if (queries == null && queryString == null) {                        // prompt the user
                System.out.println("Enter query: ");
            }

            String line = queryString;// queryString != null ? queryString : in.readLine();

            if (line == null || line.length() == -1) {
                break;
            }

            line = line.trim();
            if (line.length() == 0) {
                break;
            }
      
            Query query = parser.parse(line);
            System.out.println("Searching for: " + query.toString(field));

            doSearchInternal(searcher, query, raw, search);

            if (queryString != null) {
                break;
            }
        }
        reader.close();
    }

    public static void doSearchInternal(IndexSearcher searcher, Query query, 
                                        boolean raw, String rawQuery) throws IOException {
 
        // Collect enough docs to show 5 pages
        TopDocs results = searcher.search(query, 5000);
        ScoreDoc[] hits = results.scoreDocs;
    
        int numTotalHits = results.totalHits;
        System.out.println(numTotalHits + " total matching documents");

        int start = 0;
        int end = Math.min(numTotalHits, 5000);
        
        end = Math.min(hits.length, start + 5000);

        boolean grep_report = true;
      
        for (int i = start; i < end; i++) {
            if (raw) { // output raw format
                System.out.println("doc="+hits[i].doc+" score="+hits[i].score);
                // continue;
                Document doc = searcher.doc(hits[i].doc);
                String path = doc.get("path");
                if (path != null) {
                    System.out.println((i+1) + ". " + path);
                    String title = doc.get("title");
                    if (title != null) {
                        System.out.println("   Title: " + doc.get("title"));
                    }
                } else {
                    System.out.println((i+1) + ". " + "No path for this document");
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
                        if (line.indexOf(rawQuery) != -1) {
                            if (line.length() > 200) {
                                line = line.substring(0, 200);
                            }
                            System.out.print(path + ":" + lineCount + ":\t" + line);
                            System.out.println();
                        }
                    }
                }
            }
        }
    }
}
