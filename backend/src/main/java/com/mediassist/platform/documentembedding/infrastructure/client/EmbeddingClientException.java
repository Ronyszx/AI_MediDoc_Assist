package com.mediassist.platform.documentembedding.infrastructure.client;

//public class EmbeddingClientException extends RuntimeException {
//
////    public EmbeddingClientException(String message) {
////        super(message);
////    }
////}

    public class EmbeddingClientException extends RuntimeException {

        public EmbeddingClientException(String message) {
            super(message);
        }

        public EmbeddingClientException(String message, Throwable cause) {
            super(message, cause);
        }
    }
