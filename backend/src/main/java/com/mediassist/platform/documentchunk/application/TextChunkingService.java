package com.mediassist.platform.documentchunk.application;

import java.util.List;

public interface TextChunkingService {

    List<String> splitIntoChunks(String text);
}
