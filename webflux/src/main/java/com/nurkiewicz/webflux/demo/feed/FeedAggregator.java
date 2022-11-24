package com.nurkiewicz.webflux.demo.feed;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

import com.rometools.opml.feed.opml.Outline;
import com.rometools.rome.feed.synd.SyndEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.stereotype.Component;


@Component
public class FeedAggregator {

    private static final Logger log = LoggerFactory.getLogger(FeedAggregator.class);

    private final BlogsReader blogsReader;
    private final ArticlesReader articlesReader;

    public FeedAggregator(BlogsReader blogsReader, ArticlesReader articlesReader) {
        this.blogsReader = blogsReader;
        this.articlesReader = articlesReader;
    }

    public Flux<Article> articles() {
        return blogsReader
                .allBlogsStream()
                .flatMap(this::fetchPeriodically);
    }

    private Flux<Article> fetchPeriodically(Outline outline) {
        Duration randDuration = Duration.ofMillis(ThreadLocalRandom.current().nextInt(10_000, 30_000));
        return Flux
                .interval(randDuration)
                .flatMap(i -> fetchEntries(outline.getXmlUrl()))
                .flatMap(this::toArticle);
    }

    private Mono<Article> toArticle(SyndEntry entry) {
        if (entry.getPublishedDate() == null) {
            return Mono.empty();
        }
        return Mono
                .fromCallable(() ->
                        new Article(URI.create(entry.getLink().trim()), entry.getPublishedDate().toInstant(), entry.getTitle())
                )
                .doOnError(e -> log.warn("Unable to create article from {}", entry, e))
                .onErrorComplete();
    }

    private Flux<SyndEntry> fetchEntries(String url) {
        return articlesReader
                .fetch(url)
                .doOnSubscribe(s -> log.info("Fetching entries from {}", url))
                .doOnError(e -> log.warn("Failed to fetch {}", url, e))
                .onErrorComplete();
    }
}
