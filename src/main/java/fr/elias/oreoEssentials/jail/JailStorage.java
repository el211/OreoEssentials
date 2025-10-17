package fr.elias.oreoEssentials.jail;

import java.util.*;

public interface JailStorage {
    Map<String, JailModels.Jail> loadJails();
    void saveJails(Map<String, JailModels.Jail> all);

    Map<UUID, JailModels.Sentence> loadSentences();
    void saveSentences(Map<UUID, JailModels.Sentence> sentences);

    void close();
}
