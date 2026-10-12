package de.winona.telegrambot.service;

import de.winona.telegrambot.i18n.Language;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LanguageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void everyoneStartsInEnglishUntilTheyChoose() {
        LanguageService service = new LanguageService(tempDir.resolve("languages.properties"));

        assertEquals(Language.ENGLISH, service.languageOf(1));
        assertFalse(Files.exists(tempDir.resolve("languages.properties")), "nothing is written before a choice");
    }

    @Test
    void theChoiceSurvivesARestartAndIsStoredAsACode() throws IOException {
        Path file = tempDir.resolve("data").resolve("languages.properties");
        LanguageService service = new LanguageService(file);
        service.setLanguage(1, Language.PERSIAN);
        service.setLanguage(2, Language.ENGLISH);
        service.setLanguage(1, Language.ENGLISH);
        service.setLanguage(1, Language.PERSIAN);

        LanguageService afterRestart = new LanguageService(file);

        assertEquals(Language.PERSIAN, afterRestart.languageOf(1));
        assertEquals(Language.ENGLISH, afterRestart.languageOf(2));
        assertEquals(Language.ENGLISH, afterRestart.languageOf(3), "players without a choice stay in English");
        assertTrue(Files.readString(file).contains("1=fa"));
    }

    @Test
    void anUnreadableFileDoesNotStopTheBot() throws IOException {
        Path file = tempDir.resolve("languages.properties");
        Files.writeString(file, "abc=fa\n7=fa\n8=klingon\n");

        LanguageService service = new LanguageService(file);

        assertEquals(Language.PERSIAN, service.languageOf(7));
        assertEquals(Language.ENGLISH, service.languageOf(8), "unknown codes mean the default language");
    }

    @Test
    void theInMemoryServiceWritesNoFile() {
        LanguageService service = LanguageService.inMemory();
        service.setLanguage(1, Language.PERSIAN);

        assertEquals(Language.PERSIAN, service.languageOf(1));
    }
}
