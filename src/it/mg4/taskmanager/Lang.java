package it.mg4.taskmanager;

import java.util.Locale;

/**
 * Bilingue: italiano se la lingua di sistema è l'italiano, altrimenti inglese.
 * Ogni testo visibile si scrive inline con Lang.tr("italiano", "english").
 */
public final class Lang {

    public static final boolean IT = "it".equals(Locale.getDefault().getLanguage());

    private Lang() {
    }

    public static String tr(String it, String en) {
        return IT ? it : en;
    }
}
