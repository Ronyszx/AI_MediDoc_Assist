package com.mediassist.platform.config;

import com.mediassist.platform.documentqa.infrastructure.client.DocumentQaProperties;
import com.mediassist.platform.documentqa.infrastructure.retrieval.DocumentQaRetrievalProperties;
import com.mediassist.platform.documentqa.infrastructure.retrieval.FacetRetrievalProperties;
import com.mediassist.platform.documentqa.infrastructure.evidence.DocumentEvidenceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({DocumentQaProperties.class, DocumentQaRetrievalProperties.class,
    FacetRetrievalProperties.class, DocumentEvidenceProperties.class})
public class DocumentQaConfiguration {

    @Bean
    public RestClient documentQaRestClient(RestClient.Builder builder, DocumentQaProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getReadTimeout());

        return builder.requestFactory(requestFactory).build();
    }
}
