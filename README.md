# MG4 Task Manager

**Italiano** · [English](README.en.md)

Task manager per l'infotainment della **MG4** (Android Automotive OS 9): mostra le app aperte con la RAM usata e permette di chiuderle davvero (`am force-stop`), una alla volta o tutte insieme. Si apre dall'icona nel launcher o con un **tasto al volante**.

Funziona collegandosi all'**ADB dell'auto** in locale (`127.0.0.1:5555`), quindi non serve root.

## Funzioni

- Lista delle app aperte, con l'app in primo piano evidenziata
- **Chiudi** la singola app o **Chiudi tutto**
- **Escludi e nascondi le app di sistema** (radio, navigatore, app dell'auto): attivo di default
- **App escluse a scelta** da «Chiudi tutto» (es. DiPlay/CarPlay), dalle Impostazioni o tenendo premuta un'app
- **Tasto al volante** configurabile ("Imposta tasto…" e premi il tasto); opzione «solo pressione lunga»
- **Aggiornamenti automatici** dalle Release di questo repository, anche ad app chiusa

## Quale edizione scaricare

Nelle [Release](../../releases/latest) ci sono due file. **Installane uno solo.**

- **`MG4-TaskManager-system.apk` (consigliata):** chiusura istantanea e lista completa delle app **senza ADB e senza permessi da attivare**. Funziona perché l'infotainment MG4 è firmato con la chiave platform pubblica di AOSP, quindi questa edizione gira come **app di sistema**. Vedi sotto cosa comporta.
- **`MG4-TaskManager.apk`:** edizione normale, senza privilegi. Chiusura istantanea solo con ADB attivo, altrimenti modalità base via accessibilità.

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

## Aggiornamenti automatici

All'accensione dell'auto e poi ogni 6 ore (serve la connessione a Internet) l'app controlla l'ultima Release di questo repository, anche se non è aperta.

- **Con ADB:** scarica e installa l'aggiornamento da sola.
- **Senza ADB:** lo scarica e mostra la notifica «Task Manager vX pronto»; un tocco apre la schermata di Android con «Installa». La prima volta Android può chiedere di consentire l'installazione di app da «Task Manager».

In **Impostazioni → Aggiornamenti** si può disattivare il controllo, scegliere se installare da soli o forzare il controllo («Controlla ora»).

## Rilasci (per lo sviluppatore)

1. Carica le modifiche su `main` (aggiorna anche `VERSION`)
2. Su GitHub: **Releases → Draft a new release**, crea il tag (es. `v1.0.1`), scrivi le novità e premi **Publish release**
3. GitHub Actions compila e firma l'APK e lo allega alla Release in un paio di minuti: le auto lo ricevono al controllo successivo

Il numero di versione viene dal tag (`v1.2.3` → versionCode `10203`).

**Firma:** gli aggiornamenti si installano solo se firmati con la stessa chiave. La build di GitHub la prende dai secret del repository:

- `KEYSTORE_B64`: il file `tm.keystore` in base64 (`base64 -w0 tm.keystore`)
- `KEYSTORE_PASS`: la password del keystore

L'edizione di sistema è firmata con la chiave platform **pubblica** di AOSP, versionata in `signing/` (`platform.pk8` + `platform.x509.pem`): non è un segreto e la build di GitHub la usa direttamente.

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
