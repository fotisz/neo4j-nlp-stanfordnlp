/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.graphaware.nlp.processor.stanford;

import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import java.util.Properties;

class PipelineBuilder {

    private static final String CUSTOM_STOP_WORD_LIST = "start,starts,period,periods,a,an,and,are,as,at,be,but,by,for,if,in,into,is,it,no,not,of,o,on,or,such,that,the,their,then,there,these,they,this,to,was,will,with";

    private final Properties properties = new Properties();
    private final StringBuilder annotators = new StringBuilder(); //basics annotators
    private int threadsNumber = 4;
    
    private final String name;

    public PipelineBuilder(String name) {
        this.name = name;
    }
    
    public PipelineBuilder tokenize() {
        checkForExistingAnnotators();
        annotators.append("tokenize, ssplit, pos, lemma, ner");
        return this;
    }
    
    public PipelineBuilder cleanxml() {
        checkForExistingAnnotators();
        annotators.append("cleanxml");
        properties.setProperty("clean.allowflawedxml", "true");
        return this;
    }
    
    public PipelineBuilder truecase() {
        checkForExistingAnnotators();
        annotators.append("truecase");
        properties.setProperty("truecase.overwriteText", "true");
        return this;
    }

    private void checkForExistingAnnotators() {
        if (annotators.toString().length() > 0) {
            annotators.append(", ");
        }
    }

    public PipelineBuilder extractSentiment() {
        checkForExistingAnnotators();
        annotators.append("parse, sentiment");
        //properties.setProperty("parse.model", "edu/stanford/nlp/models/srparser/englishSR.ser.gz");
        return this;
    }

    public PipelineBuilder extractRelations() {
        checkForExistingAnnotators();
        annotators.append("relation");
        return this;
    }

    public PipelineBuilder extractCoref() {
        checkForExistingAnnotators();
        annotators.append("mention, coref");
        properties.setProperty("coref.doClustering", "true");
        properties.setProperty("coref.md.type", "rule");
        properties.setProperty("coref.mode", "statistical");
        return this;
    }

    public PipelineBuilder defaultStopWordAnnotator() {
        checkForExistingAnnotators();
        annotators.append("stopword");
        properties.setProperty("customAnnotatorClass.stopword", StopwordAnnotator.class.getName());
        properties.setProperty(StopwordAnnotator.STOPWORDS_LIST, CUSTOM_STOP_WORD_LIST);
        return this;
    }

    public PipelineBuilder customStopWordAnnotator(String customStopWordList, boolean checkLemma) {
        checkForExistingAnnotators();
        String stopWordList;
        if (annotators.indexOf("stopword") >= 0) {
//            String alreadyexistingStopWordList = properties.getProperty(StopwordAnnotator.STOPWORDS_LIST);
//            stopWordList = alreadyexistingStopWordList + "," + customStopWordList;
            throw new RuntimeException("A standard stopword annotator already exist!");
        } else {
            String annoName = "stopword_" + name;
            annotators.append(annoName);
            properties.setProperty("customAnnotatorClass." + annoName, StopwordAnnotator.class.getName());
            if (customStopWordList.startsWith("+")) {
                stopWordList = CUSTOM_STOP_WORD_LIST + "," + customStopWordList.replace("+,", "").replace("+", "");
            } else {
                stopWordList = customStopWordList;
            }
        }
        properties.setProperty(StopwordAnnotator.STOPWORDS_LIST, stopWordList);
        properties.setProperty(StopwordAnnotator.CHECK_LEMMA, Boolean.toString(checkLemma));
        return this;
    }

    public PipelineBuilder stopWordAnnotator(Properties properties) {
        properties.entrySet().stream().forEach((entry) -> {
            this.properties.setProperty((String) entry.getKey(), (String) entry.getValue());
        });
        return this;
    }

    public PipelineBuilder threadNumber(int threads) {
        this.threadsNumber = threads;
        return this;
    }

    public StanfordCoreNLP build() {
        properties.setProperty("annotators", annotators.toString());
        properties.setProperty("threads", String.valueOf(threadsNumber));
        StanfordCoreNLP pipeline = new StanfordCoreNLP(properties);
        return pipeline;
    }
}
