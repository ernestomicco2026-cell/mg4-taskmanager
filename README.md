# MG4 Task Manager

**Italiano** · [English](README.en.md)

Task manager per l'infotainment della **MG4** (Android Automotive OS 9): mostra le app aperte con la RAM usata e permette di chiuderle davvero, una alla volta o tutte insieme. Si apre dall'icona nel launcher o con un **tasto al volante**.

Non serve root: l'edizione di sistema usa i permessi della firma platform dell'auto, quella normale si collega all'**ADB dell'auto** in locale (`127.0.0.1:5555`) oppure usa l'accessibilità.

## Funzioni

- Lista delle app aperte, con l'app in primo piano evidenziata e la RAM usata
- **Chiudi** la singola app o **Chiudi tutto**
- **Escludi e nascondi le app di sistema** (radio, navigatore, app dell'auto): attivo di default
- **App escluse a scelta** da «Chiudi tutto» (es. DiPlay/CarPlay), dalle Impostazioni o tenendo premuta un'app
- **Tasto al volante** configurabile ("Imposta tasto…" e premi il tasto); opzione «solo pressione lunga»
- **6 lingue:** italiano, inglese, spagnolo, portoghese, francese, tedesco (vedi sotto)
- **Aggiornamenti automatici** dalle Release di questo repository, anche ad app chiusa

## Lingue

Di default l'app segue la **lingua di sistema** dell'auto: italiano, English, español, português, français o deutsch; per qualsiasi altra lingua usa l'inglese.

Per sceglierne una diversa: **Impostazioni → Lingua**. Si può tornare in qualsiasi momento ad «Automatica (lingua di sistema)». La scelta vale per tutta l'app, notifiche comprese. Il nome del servizio nella pagina Accessibilità dell'auto segue invece sempre la lingua di sistema, perché lo mostra Android.

Anche le note degli aggiornamenti arrivano nella lingua scelta.

## Quale edizione scaricare

Nelle [Release](../../releases/latest) ci sono due file. **Installane uno solo.**

- **`MG4-TaskManager-system.apk` (consigliata):** chiusura istantanea e lista completa delle app **senza ADB e senza permessi da attivare**. Funziona perché l'infotainment MG4 è firmato con la chiave platform pubblica di AOSP, quindi questa edizione gira come **app di sistema**. Vedi sotto cosa comporta.
- **`MG4-TaskManager.apk`:** edizione normale, senza privilegi. Chiusura istantanea solo con ADB attivo, altrimenti modalità base via accessibilità.

Le due edizioni hanno firme diverse: per passare dall'una all'altra disinstalla prima quella che hai.

### Cosa comporta l'edizione di sistema

- Gira come **app di sistema** sull'infotainment: ha permessi ampi (chiudere a forza le app, leggere i processi). Installala solo se ti fidi della fonte, cioè di questo repository.
- È firmata con una chiave **pubblica**: chiunque può creare un APK firmato allo stesso modo. Perciò scarica l'APK **solo** da questa pagina Release ufficiale, mai da terzi.
- Gli aggiornamenti **richiedono sempre un tocco** su «Installa»: l'app non si installa mai da sola in modo silenzioso. (Sulla tua auto, se vuoi, puoi abilitare l'installazione silenziosa via ADB.)

## Installazione (da chiavetta USB, senza PC)

1. Scarica **una** delle due edizioni dalle [Release](../../releases/latest) e copiala su una chiavetta USB
2. Inserisci la chiavetta nell'auto e installa l'APK come fai con le altre app
3. Apri **Task Manager**. Con l'edizione di sistema è già tutto pronto. Con quella normale senza ADB, segui la scheda «Completa la configurazione» (Accessibilità + Accesso ai dati di utilizzo).

## Le tre modalità

L'app sceglie da sola, a ogni apertura:

| | Sistema | Completa (ADB) | Base |
|---|---|---|---|
| Quando | edizione firmata platform | ADB risponde su `127.0.0.1:5555` | gli altri casi |
| Lista app | processi reali, con RAM | processi reali, con RAM | app aperte da questa accensione |
| Chiusura | istantanea (`forceStopPackage`) | istantanea (`am force-stop`) | «Forza interruzione» via accessibilità |
| Permessi | automatici (firma) | automatici (ADB) | da attivare una volta |

Con l'edizione normale e ADB, alla prima connessione l'auto può chiedere «Consentire il debug?»: spunta «Consenti sempre».

## Velocità

- Alla riapertura compare subito l'ultima lista, che poi si aggiorna da sola
- In modalità sistema la lista arriva prima e la RAM un attimo dopo, letta solo per le app mostrate
- Icone, nomi e informazioni delle app restano in memoria: si leggono una volta sola
- Le app chiuse spariscono subito dalla lista, senza attendere il ricaricamento

## Aggiornamenti automatici

All'accensione dell'auto e poi ogni 6 ore (serve la connessione a Internet) l'app controlla l'ultima Release di questo repository, anche se non è aperta. Ogni edizione scarica **il proprio** APK (normale o `-system`), così la firma combacia sempre.

- **Con ADB:** scarica e installa l'aggiornamento da sola.
- **Senza ADB:** lo scarica e mostra la notifica «Task Manager vX pronto»; un tocco apre la schermata di Android con «Installa». La prima volta Android può chiedere di consentire l'installazione di app da «Task Manager».

In **Impostazioni → Aggiornamenti** si può disattivare il controllo, scegliere se installare da soli o forzare il controllo («Controlla ora»).

> **Da 1.2 a 1.2.1 con l'edizione normale:** la 1.2 scaricava sempre il primo APK della Release (quello `-system`), che con l'edizione normale non si installa. Se hai la 1.2 normale, installa la 1.2.1 a mano una volta (stessa procedura della chiavetta, sopra). Dalla 1.2.1 in poi gli aggiornamenti scelgono l'edizione giusta.

## Rilasci (per lo sviluppatore)

1. Carica le modifiche su `main` e aggiorna `VERSION` e `RELEASE_NOTES.md`. A ogni push la build di controllo (**Actions → Build**) compila e verifica le due edizioni; gli APK di prova sono tra gli *Artifacts* dell'esecuzione
2. Su GitHub: **Releases → Draft a new release**, crea il tag (es. `v1.2.1`) su `main`, titolo a piacere
3. **Descrizione:** lasciala vuota per usare automaticamente `RELEASE_NOTES.md`, oppure scrivila tu
4. Premi **Publish release**: GitHub Actions compila, verifica e firma le due edizioni e le allega alla Release in un paio di minuti. Le auto le ricevono al controllo successivo

Nella Release il numero di versione viene dal tag (`v1.2.3` → versionCode `10203`); `VERSION` serve alle build locali e di controllo, tienilo uguale al tag.

**Note multilingua:** in `RELEASE_NOTES.md` ogni lingua inizia con `<!-- lang:xx -->` (`it`, `en`, `es`, `pt`, `fr`, `de`). Su GitHub si vedono tutte; nell'app compare solo quella dell'utente (o l'inglese).

**Firma:** gli aggiornamenti si installano solo se firmati con la stessa chiave. La build di GitHub la prende dai secret del repository:

- `KEYSTORE_B64`: il file `tm.keystore` in base64 (`base64 -w0 tm.keystore`)
- `KEYSTORE_PASS`: la password del keystore

L'edizione di sistema è firmata con la chiave platform **pubblica** di AOSP, versionata in `signing/` (`platform.pk8` + `platform.x509.pem`): non è un segreto e la build di GitHub la usa direttamente.

Non caricare mai `tm.keystore` nel repository e tienine una copia di sicurezza: se la perdi, per aggiornare l'edizione normale dovrai disinstallarla.

## Compilare in locale

Non serve Android Studio: bastano JDK, Python 3 con Pillow, `android.jar` di API 28 e `d8` (build-tools).

```sh
ANDROID_JAR=$ANDROID_HOME/platforms/android-28/android.jar \
D8=$ANDROID_HOME/build-tools/34.0.0/d8 \
KEYSTORE=tm.keystore KEYSTORE_PASS=taskmanager \
sh build.sh

# facoltativo: controlla gli APK con aapt2 e apksigner
BUILD_TOOLS=$ANDROID_HOME/build-tools/34.0.0 sh verify.sh v1.2.1
```

`build_apk.py` genera manifest e risorse (comprese le stringhe tradotte) in formato binario Android senza `aapt`.

## Tradurre

Ogni testo dell'interfaccia è scritto nel codice con `Lang.tr(it, en, es, pt, fr, de)`: il compilatore segnala un errore se manca una lingua. I testi del servizio di accessibilità sono in `A11Y_STRINGS` dentro `build_apk.py`.

## Struttura

| File | Cosa fa |
|---|---|
| `MainActivity.java` | Interfaccia e impostazioni (lingua compresa) |
| `Lang.java` | Lingue: automatica o scelta, testi tradotti, note di rilascio per lingua |
| `Tasks.java` | Elenco e chiusura delle app, cache di icone/nomi/pacchetti |
| `SysPriv.java` | Modalità sistema: lista via ActivityManager, RAM in un secondo passaggio, `forceStopPackage` |
| `AdbClient.java` / `Adb.java` | Client del protocollo ADB (autenticazione RSA, comandi shell) e preferenze |
| `KeyService.java` | Servizio di accessibilità: tasto al volante e «Forza interruzione» in modalità base |
| `KeyMonitorService.java` / `KeyHub.java` | Tasti grezzi (`getevent`) e apprendimento del tasto |
| `Updater.java` / `UpdateJobService.java` | Aggiornamento automatico dalle Release di GitHub, per edizione |
| `build.sh` / `build_apk.py` / `verify.sh` | Compilazione, impacchettamento e verifica degli APK |
