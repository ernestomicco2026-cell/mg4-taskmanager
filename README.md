# MG4 Task Manager

Task manager per l'infotainment della **MG4** (Android Automotive OS 9): mostra le app aperte con la RAM usata e permette di chiuderle davvero (`am force-stop`), una alla volta o tutte insieme. Si apre dall'icona nel launcher o con un **tasto al volante**.

Funziona collegandosi all'**ADB dell'auto** in locale (`127.0.0.1:5555`), quindi non serve root.

## Funzioni

- Lista delle app aperte, con l'app in primo piano evidenziata
- **Chiudi** la singola app o **Chiudi tutto**
- **Escludi e nascondi le app di sistema** (radio, navigatore, app dell'auto): attivo di default
- **App escluse a scelta** da «Chiudi tutto» (es. DiPlay/CarPlay), dalle Impostazioni o tenendo premuta un'app
- **Tasto al volante** configurabile ("Imposta tasto…" e premi il tasto); opzione «solo pressione lunga»
- **Aggiornamenti automatici** dalle Release di questo repository, anche ad app chiusa

## Installazione (da chiavetta USB, senza PC)

1. Scarica `MG4-TaskManager.apk` dalle [Release](../../releases/latest) e copialo su una chiavetta USB
2. Inserisci la chiavetta nell'auto e installa l'APK come fai con le altre app
3. Apri **Task Manager** e segui la scheda «Completa la configurazione»:
   - **Accessibilità** → attiva «Task Manager» (serve per vedere e chiudere le app e per il tasto al volante)
   - **Accesso ai dati di utilizzo** → consenti a «Task Manager»

## Le due modalità

L'app sceglie da sola, a ogni apertura:

| | Modalità completa | Modalità base |
|---|---|---|
| Quando | l'ADB dell'auto risponde su `127.0.0.1:5555` | in tutti gli altri casi |
| Lista app | processi reali, con RAM usata | app aperte da questa accensione |
| Chiusura | istantanea (`am force-stop`) | l'app apre «Info app» e preme da sola «Forza interruzione» |
| Aggiornamenti | installati da soli, in silenzio | scaricati da soli, poi un tocco su «Installa» |
| Permessi | concessi in automatico | da attivare una volta nelle Impostazioni |

In modalità completa, alla prima connessione l'auto può chiedere «Consentire il debug?»: spunta «Consenti sempre».

## Aggiornamenti automatici

All'accensione dell'auto e poi ogni 6 ore (serve la connessione a Internet) l'app controlla l'ultima Release di questo repository, anche se non è aperta.

- **Con ADB:** scarica e installa l'aggiornamento da sola.
- **Senza ADB:** lo scarica e mostra la notifica «Task Manager vX pronto»; un tocco apre la schermata di Android con «Installa». La prima volta Android può chiedere di consentire l'installazione di app da «Task Manager».

In **Impostazioni → Aggiornamenti** si può disattivare il controllo, scegliere se installare da soli o forzare il controllo («Controlla ora»).

## Rilasci (per lo sviluppatore)

1. Scrivi le novità in `RELEASE_NOTES.md` e aggiorna `VERSION`
2. Crea e invia il tag: `git tag v1.0.1 && git push origin v1.0.1`
3. GitHub Actions compila, firma e pubblica l'APK nella Release: le auto lo ricevono al controllo successivo

Il numero di versione viene dal tag (`v1.2.3` → versionCode `10203`).

**Firma:** gli aggiornamenti si installano solo se firmati con la stessa chiave. La build di GitHub la prende dai secret del repository:

- `KEYSTORE_B64`: il file `tm.keystore` in base64 (`base64 -w0 tm.keystore`)
- `KEYSTORE_PASS`: la password del keystore

Non caricare mai `tm.keystore` nel repository e tienine una copia di sicurezza: se la perdi, per aggiornare dovrai disinstallare l'app.

## Compilare in locale

Non serve Android Studio: bastano JDK, Python 3 con Pillow, `android.jar` di API 28 e `d8` (build-tools).

```sh
ANDROID_JAR=$ANDROID_HOME/platforms/android-28/android.jar \
D8=$ANDROID_HOME/build-tools/34.0.0/d8 \
KEYSTORE=tm.keystore KEYSTORE_PASS=taskmanager \
sh build.sh
```

`build_apk.py` genera manifest e risorse in formato binario Android senza `aapt`.

## Struttura

| File | Cosa fa |
|---|---|
| `AdbClient.java` | Client del protocollo ADB (autenticazione RSA, comandi shell) |
| `Tasks.java` | Legge i processi (`ps`) e chiude le app (`am force-stop`) |
| `MainActivity.java` | Interfaccia e impostazioni |
| `KeyService.java` | Servizio di accessibilità per il tasto al volante |
| `KeyMonitorService.java` | Ascolto dei tasti grezzi (`getevent`) per i tasti non visibili ad Android |
| `Updater.java` | Aggiornamento automatico dalle Release di GitHub |
