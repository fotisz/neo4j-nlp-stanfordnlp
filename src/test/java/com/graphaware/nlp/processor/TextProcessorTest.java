/*
 * Copyright (c) 2013-2016 GraphAware
 *
 * This file is part of the GraphAware Framework.
 *
 * GraphAware Framework is free software: you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details. You should have received a copy of
 * the GNU General Public License along with this program.  If not, see
 * <http://www.gnu.org/licenses/>.
 */
package com.graphaware.nlp.processor;

import com.graphaware.nlp.domain.AnnotatedText;
import com.graphaware.nlp.domain.Phrase;
import com.graphaware.nlp.domain.Sentence;
import com.graphaware.nlp.domain.Tag;
import com.graphaware.nlp.persistence.GraphPersistence;
import com.graphaware.nlp.persistence.LocalGraphDatabase;
import static com.graphaware.nlp.processor.stanford.StanfordTextProcessor.TOKENIZER;
import com.graphaware.nlp.util.ServiceLoader;
import com.graphaware.test.integration.EmbeddedDatabaseIntegrationTest;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;

import org.bouncycastle.jcajce.provider.digest.GOST3411;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.QueryExecutionException;
import org.neo4j.graphdb.ResourceIterator;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;

public class TextProcessorTest extends EmbeddedDatabaseIntegrationTest {

    @Test
    public void testAnnotatedText() {
        TextProcessor textProcessor = ServiceLoader.loadTextProcessor("com.graphaware.nlp.processor.stanford.StanfordTextProcessor");
        AnnotatedText annotateText = textProcessor.annotateText("On 8 May 2013, "
                + "one week before the Pakistani election, the third author, "
                + "in his keynote address at the Sentiment Analysis Symposium, "
                + "forecast the winner of the Pakistani election. The chart "
                + "in Figure 1 shows varying sentiment on the candidates for "
                + "prime minister of Pakistan in that election. The next day, "
                + "the BBC’s Owen Bennett Jones, reporting from Islamabad, wrote "
                + "an article titled “Pakistan Elections: Five Reasons Why the "
                + "Vote is Unpredictable,”1 in which he claimed that the election "
                + "was too close to call. It was not, and despite his being in Pakistan, "
                + "the outcome of the election was exactly as we predicted.", 1, 0, "en", false);

        assertEquals(4, annotateText.getSentences().size());
        assertEquals(15, annotateText.getSentences().get(0).getTags().size());
        assertEquals(11, annotateText.getSentences().get(1).getTags().size());
        assertEquals(24, annotateText.getSentences().get(2).getTags().size());
        assertEquals(9, annotateText.getSentences().get(3).getTags().size());

        GraphPersistence peristence = new LocalGraphDatabase(getDatabase());
        peristence.persistOnGraph(annotateText, false);
        checkLocation("Pakistan");
        checkVerb("show");

    }
    
    @Test
    public void testLemmaLowerCasing() {
        String testText = "Collibra’s Data Governance Innovation: Enabling Data as a Strategic Asset";
        
        TextProcessor textProcessor = ServiceLoader.loadTextProcessor("com.graphaware.nlp.processor.stanford.StanfordTextProcessor");
        AnnotatedText annotateText = textProcessor.annotateText(testText, 1, TOKENIZER, "en", false, null);

        assertEquals(1, annotateText.getSentences().size());
        assertEquals("governance", annotateText.getSentences().get(0).getTagOccurrence(16).getLemma());
        
        Map<String, Object> pipelineSpec = new HashMap<>();
        pipelineSpec.put("name", "tokenizeWithTrueCase");
        pipelineSpec.put("truecase", true);
        textProcessor.createPipeline(pipelineSpec);
        
        annotateText = textProcessor.annotateText(testText, 1, "tokenizeWithTrueCase", "en", false, null);

        assertEquals(1, annotateText.getSentences().size());
        assertEquals("governance", annotateText.getSentences().get(0).getTagOccurrence(16).getLemma());
        
    }
    
    @Test
    public void testLemmaSprittingByPunctuation() {
        String testText = "Ser Emmon Cuy, Ser Robar Royce, Ser Parmen Crane, they'd sworn as well.";
        
        TextProcessor textProcessor = ServiceLoader.loadTextProcessor("com.graphaware.nlp.processor.stanford.StanfordTextProcessor");
        AnnotatedText annotateText = textProcessor.annotateText(testText, 1, TOKENIZER, "en", false, null);

        assertEquals(1, annotateText.getSentences().size());
        assertEquals(6, annotateText.getSentences().get(0).getTags().size());
    }
    

    private void checkLocation(String location) throws QueryExecutionException {
        try (Transaction tx = getDatabase().beginTx()) {
            ResourceIterator<Object> rowIterator = getTagsIterator(location);
            Node pakistanNode = (Node) rowIterator.next();
            assertFalse(rowIterator.hasNext());
            String[] neList = (String[])pakistanNode.getProperty("ne");
            assertEquals(neList[0], "LOCATION");
            tx.success();
        }
    }

    private void checkVerb(String verb) throws QueryExecutionException {
        try (Transaction tx = getDatabase().beginTx()) {
            ResourceIterator<Object> rowIterator = getTagsIterator(verb);
            Node pakistanNode = (Node) rowIterator.next();
            assertFalse(rowIterator.hasNext());
            String[] posL = (String[])pakistanNode.getProperty("pos");
            assertEquals(posL[0], "VBZ");
            tx.success();
        }
    }

    private ResourceIterator<Object> getTagsIterator(String value) throws QueryExecutionException {
        Map<String, Object> params = new HashMap<>();
        params.put("value", value);
        Result pakistan = getDatabase().execute("MATCH (n:Tag {value: {value}}) return n", params);
        ResourceIterator<Object> rowIterator = pakistan.columnAs("n");
        return rowIterator;
    }

    @Test
    public void testAnnotatedTag() {
        TextProcessor textProcessor = ServiceLoader.loadTextProcessor("com.graphaware.nlp.processor.stanford.StanfordTextProcessor");
        Tag annotateTag = textProcessor.annotateTag("winners", "en");
        assertEquals(annotateTag.getLemma(), "winner");
    }

    @Test
    public void testAnnotationAndConcept() {
        // ConceptNet5Importer.Builder() - arguments need fixing
        /*TextProcessor textProcessor = ServiceLoader.loadTextProcessor("com.graphaware.nlp.processor.stanford.StanfordTextProcessor");
        ConceptNet5Importer conceptnet5Importer = new ConceptNet5Importer.Builder("http://conceptnet5.media.mit.edu/data/5.4", textProcessor)
                .build();
        String text = "Say hi to Christophe";
        AnnotatedText annotateText = textProcessor.annotateText(text, 1, 0, "en", false);
        List<Node> nodes = new ArrayList<>();
        try (Transaction beginTx = getDatabase().beginTx()) {
            Node annotatedNode = annotateText.storeOnGraph(getDatabase(), false);
            Map<String, Object> params = new HashMap<>();
            params.put("id", annotatedNode.getId());
            Result queryRes = getDatabase().execute("MATCH (n:AnnotatedText)-[*..2]->(t:Tag) where id(n) = {id} return t", params);
            ResourceIterator<Node> tags = queryRes.columnAs("t");
            while (tags.hasNext()) {
                Node tag = tags.next();
                nodes.add(tag);
                List<Tag> conceptTags = conceptnet5Importer.importHierarchy(Tag.createTag(tag), "en");
                conceptTags.stream().forEach((newTag) -> {
                    nodes.add(newTag.storeOnGraph(getDatabase(), false));
                });
            }
            beginTx.success();
        }*/
    }

    @Test
    public void testSentiment() {
        TextProcessor textProcessor = ServiceLoader.loadTextProcessor("com.graphaware.nlp.processor.stanford.StanfordTextProcessor");

        AnnotatedText annotateText = textProcessor.annotateText("I really hate to study at Stanford, it was a waste of time, I'll never be there again", 1, 1, "en", false);
        assertEquals(1, annotateText.getSentences().size());
        assertEquals(0, annotateText.getSentences().get(0).getSentiment());

        annotateText = textProcessor.annotateText("It was really horrible to study at Stanford", 1, 1, "en", false);
        assertEquals(1, annotateText.getSentences().size());
        assertEquals(1, annotateText.getSentences().get(0).getSentiment());

        annotateText = textProcessor.annotateText("I studied at Stanford", 1, 1, "en", false);
        assertEquals(1, annotateText.getSentences().size());
        assertEquals(2, annotateText.getSentences().get(0).getSentiment());

        annotateText = textProcessor.annotateText("I liked to study at Stanford", 1, 1, "en", false);
        assertEquals(1, annotateText.getSentences().size());
        assertEquals(3, annotateText.getSentences().get(0).getSentiment());

        annotateText = textProcessor.annotateText("I liked so much to study at Stanford, I enjoyed my time there, I would recommend every body", 1, 1, "en", false);
        assertEquals(1, annotateText.getSentences().size());
        assertEquals(4, annotateText.getSentences().get(0).getSentiment());
    }
    
    @Test
    public void testAnnotatedTextWithPosition() {
        TextProcessor textProcessor = ServiceLoader.loadTextProcessor("com.graphaware.nlp.processor.stanford.StanfordTextProcessor");
        Map<String, Object> pipelineSpec = new HashMap<>();
        pipelineSpec.put("name", "tokenizeWithTrueCase");
        pipelineSpec.put("truecase", true);
        pipelineSpec.put("sentiment", true);
        pipelineSpec.put("coref", true);
        pipelineSpec.put("relations", true);
        textProcessor.createPipeline(pipelineSpec);
        AnnotatedText annotateText = textProcessor.annotateText("On 8 May 2013, "
                + "one week before the Pakistani election, the third author, "
                + "in his keynote address at the Sentiment Analysis Symposium, "
                + "forecast the winner of the Pakistani election. The chart "
                + "in Figure 1 shows varying sentiment on the candidates for "
                + "prime minister of Pakistan in that election. The next day, "
                + "the BBC’s Owen Bennett Jones, reporting from Islamabad, wrote "
                + "an article titled “Pakistan Elections: Five Reasons Why the "
                + "Vote is Unpredictable,”1 in which he claimed that the election "
                + "was too close to call. It was not, and despite his being in Pakistan, "
                + "the outcome of the election was exactly as we predicted.", 1, "tokenizeWithTrueCase", "en", false, null);

        assertEquals(4, annotateText.getSentences().size());
        Sentence sentence1 = annotateText.getSentences().get(0);
        assertEquals(15, sentence1.getTags().size());
        
        assertNull(sentence1.getTagOccurrence(0));
        assertEquals("8 May 2013", sentence1.getTagOccurrence(3).getLemma());
        assertEquals("one week", sentence1.getTagOccurrence(15).getLemma());
        assertEquals("before", sentence1.getTagOccurrence(24).getLemma());
        assertEquals("third", sentence1.getTagOccurrence(59).getLemma());
        assertEquals("sentiment", sentence1.getTagOccurrence(103).getLemma());
        assertEquals("forecast", sentence1.getTagOccurrence(133).getLemma());
        assertNull(sentence1.getTagOccurrence(184));
        assertTrue(sentence1.getPhraseOccurrence(99).contains(new Phrase("the Sentiment Analysis Symposium")));
        assertTrue(sentence1.getPhraseOccurrence(103).contains(new Phrase("Sentiment")));
        assertTrue(sentence1.getPhraseOccurrence(113).contains(new Phrase("Analysis")));
        
        //his(76)-> the third author(54)
        assertTrue(sentence1.getPhraseOccurrence(55).get(1).getContent().equalsIgnoreCase("the third author"));
        Sentence sentence2 = annotateText.getSentences().get(1);
        assertEquals("chart", sentence2.getTagOccurrence(184).getLemma());
        assertEquals("figure", sentence2.getTagOccurrence(193).getLemma());
    }
    
    @Test
    public void testAnnotatedShortText() {
        TextProcessor textProcessor = ServiceLoader.loadTextProcessor("com.graphaware.nlp.processor.stanford.StanfordTextProcessor");
        AnnotatedText annotateText = textProcessor.annotateText("Fixing Batch Endpoint Logging Problem", 1, 1, "en", false);

        assertEquals(1, annotateText.getSentences().size());

        GraphPersistence peristence = new LocalGraphDatabase(getDatabase());
        peristence.persistOnGraph(annotateText, false);        

    }
    
    @Test
    public void testAnnotatedShortText2() {
        TextProcessor textProcessor = ServiceLoader.loadTextProcessor("com.graphaware.nlp.processor.stanford.StanfordTextProcessor");
        AnnotatedText annotateText = textProcessor.annotateText("Importing CSV data does nothing", 1, 1, "en", false);
        assertEquals(1, annotateText.getSentences().size());
        GraphPersistence peristence = new LocalGraphDatabase(getDatabase());
        peristence.persistOnGraph(annotateText, false);        
    }

    @Test
    public void testAnnotatedQuestionWithNoStopwords() {
        String text = "What is in front of the Notre Dame Main Building?";
        Map<String, Object> customPipeline = new HashMap<>();
        customPipeline.put("textProcessor", "com.graphaware.nlp.processor.stanford.StanfordTextProcessor");
        customPipeline.put("name", "custom");
        customPipeline.put("stopWords", "start,starts");
        customPipeline.put("dependency", true);
        TextProcessor textProcessor = ServiceLoader.loadTextProcessor("com.graphaware.nlp.processor.stanford.StanfordTextProcessor");
        textProcessor.createPipeline(customPipeline);
        AnnotatedText annotatedText = textProcessor.annotateText(text, 1, "custom", "en", false, null);

        assertEquals(1, annotatedText.getSentences().size());
        Sentence sentence = annotatedText.getSentences().get(0);
        assertEquals("be", sentence.getTagOccurrence(5).getLemma());
    }

    @Test
    public void testTypedDependenciesAreFoundAndStored() {
        TextProcessor textProcessor = ServiceLoader.loadTextProcessor("com.graphaware.nlp.processor.stanford.StanfordTextProcessor");
        AnnotatedText annotateText = textProcessor.annotateText("Donald Trump flew yesterday to New York City", 1, 3, "en", false);
        GraphPersistence peristence = new LocalGraphDatabase(getDatabase());
        peristence.persistOnGraph(annotateText, false);
    }
}
