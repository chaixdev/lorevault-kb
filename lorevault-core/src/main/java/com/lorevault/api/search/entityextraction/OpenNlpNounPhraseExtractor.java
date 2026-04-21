package com.lorevault.api.search.entityextraction;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import opennlp.tools.chunker.ChunkerME;
import opennlp.tools.chunker.ChunkerModel;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.Span;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * OpenNLP-based noun phrase extractor (Strategy B).
 *
 * <p>Runs a tokenize → POS → chunk pipeline to discover noun phrases that are
 * not already in the known-entity trie. Works on fictional names because the
 * POS tagger and chunker operate on grammar context, not entity training data.</p>
 *
 * <p>All three model files are optional: if any model resource is missing or
 * fails to load, this extractor silently disables itself. Strategy A
 * (Aho-Corasick) still runs in that case.</p>
 *
 * <p>Model paths are configured via {@code application.yml}:</p>
 * <pre>
 * lorevault:
 *   nlp:
 *     token-model: classpath:nlp/en-token.bin
 *     pos-model:   classpath:nlp/en-pos-maxent.bin
 *     chunk-model: classpath:nlp/en-chunker.bin
 * </pre>
 */
@Component
@Slf4j
class OpenNlpNounPhraseExtractor {

    @Value("${lorevault.nlp.token-model:classpath:nlp/en-token.bin}")
    private Resource tokenModelResource;

    @Value("${lorevault.nlp.pos-model:classpath:nlp/en-pos-maxent.bin}")
    private Resource posModelResource;

    @Value("${lorevault.nlp.chunk-model:classpath:nlp/en-chunker.bin}")
    private Resource chunkModelResource;

    // Thread-safe stateless wrappers; null when unavailable
    private volatile TokenizerME  tokenizer;
    private volatile POSTaggerME  posTagger;
    private volatile ChunkerME    chunker;
    private volatile boolean      available = false;

    @PostConstruct
    void initialize() {
        try {
            tokenizer = loadTokenizer(tokenModelResource);
            posTagger = loadPosTagger(posModelResource);
            chunker   = loadChunker(chunkModelResource);
            available = true;
            log.info("OpenNlpNounPhraseExtractor: models loaded — NP discovery enabled");
        } catch (MissingModelException e) {
            log.info("OpenNlpNounPhraseExtractor: {} — NP discovery disabled (Strategy A still active)",
                    e.getMessage());
        } catch (Exception e) {
            log.warn("OpenNlpNounPhraseExtractor: failed to load models — NP discovery disabled: {}",
                    e.getMessage());
        }
    }

    /**
     * Extracts noun phrases from {@code query}, excluding any already in {@code alreadyMatched}.
     *
     * @param query          user's question string
     * @param alreadyMatched names already found by Strategy A (case-insensitive dedup)
     * @return discovered noun phrases (multi-word tokens, min 2 chars, title-cased heuristic)
     */
    List<String> extractNounPhrases(String query, java.util.Set<String> alreadyMatched) {
        if (!available || query == null || query.isBlank()) {
            return List.of();
        }

        try {
            String[] tokens = tokenizer.tokenize(query);
            if (tokens.length == 0) return List.of();

            String[] tags   = posTagger.tag(tokens);
            Span[]   chunks = chunker.chunkAsSpans(tokens, tags);

            List<String> nounPhrases = new ArrayList<>();
            for (Span span : chunks) {
                if (!"NP".equals(span.getType())) continue;

                // Reconstruct the noun phrase text
                StringBuilder sb = new StringBuilder();
                for (int i = span.getStart(); i < span.getEnd(); i++) {
                    if (i > span.getStart()) sb.append(' ');
                    sb.append(tokens[i]);
                }
                String phrase = sb.toString().trim();

                if (phrase.length() < 2) continue;
                if (alreadyMatched.contains(phrase)) continue;
                if (isStopPhrase(phrase)) continue;

                nounPhrases.add(phrase);
            }
            return nounPhrases;
        } catch (Exception e) {
            log.debug("OpenNLP extraction failed for query '{}': {}", query, e.getMessage());
            return List.of();
        }
    }

    boolean isAvailable() {
        return available;
    }

    // --- loaders ---

    private TokenizerME loadTokenizer(Resource resource) throws Exception {
        if (!resource.exists()) throw new MissingModelException("token model not found: " + resource);
        try (InputStream in = resource.getInputStream()) {
            return new TokenizerME(new TokenizerModel(in));
        }
    }

    private POSTaggerME loadPosTagger(Resource resource) throws Exception {
        if (!resource.exists()) throw new MissingModelException("POS model not found: " + resource);
        try (InputStream in = resource.getInputStream()) {
            return new POSTaggerME(new POSModel(in));
        }
    }

    private ChunkerME loadChunker(Resource resource) throws Exception {
        if (!resource.exists()) throw new MissingModelException("chunker model not found: " + resource);
        try (InputStream in = resource.getInputStream()) {
            return new ChunkerME(new ChunkerModel(in));
        }
    }

    /** Very short or purely functional words that are syntactically NPs but not entity candidates. */
    private boolean isStopPhrase(String phrase) {
        return switch (phrase.toLowerCase()) {
            case "i", "you", "he", "she", "it", "we", "they",
                 "me", "him", "her", "us", "them",
                 "this", "that", "these", "those",
                 "what", "who", "which", "whose",
                 "anyone", "someone", "everyone", "no one",
                 "anything", "something", "everything",
                 "a", "an", "the" -> true;
            default -> false;
        };
    }

    private static class MissingModelException extends Exception {
        MissingModelException(String msg) { super(msg); }
    }
}
