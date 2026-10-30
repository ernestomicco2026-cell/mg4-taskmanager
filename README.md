# MG4 Task Manager

**Italiano** · [English](README.en.md)

Task manager per l'infotainment della **MG4** (Android Automotive OS 9): mostra le app aperte con la RAM usata e permette di chiuderle davvero, una alla volta o tutte insieme. Si apre dall'icona nel launcher o con un **tasto al volante**.

Non serve root.

[![Offrimi un caffè con PayPal](https://img.shields.io/badge/Offrimi%20un%20caff%C3%A8-PayPal-0070BA?style=for-the-badge&logo=paypal&logoColor=white)](https://paypal.me/ErnestoMicco?locale.x=it_IT&country.x=IT)

## Video

[![Guarda il video su YouTube](https://img.youtube.com/vi/fYtzuTELNVg/hqdefault.jpg)](https://youtu.be/fYtzuTELNVg)

▶️ [Guarda il video su YouTube](https://youtu.be/fYtzuTELNVg)

## Funzioni

- Lista delle app aperte, con l'app in primo piano evidenziata e la RAM usata
- **Chiudi** la singola app o **Chiudi tutto**
- **Disinstalla** le app che hai installato tu (mai quelle di sistema), vedi sotto
- **Escludi e nascondi le app di sistema** (radio, navigatore, app dell'auto): attivo di default
- **App escluse a scelta** da «Chiudi tutto» (es. DiPlay/CarPlay), dalle Impostazioni o tenendo premuta un'app
- **Tasto al volante** configurabile ("Imposta tasto…" e premi il tasto); opzione «solo pressione lunga»
- **6 lingue:** italiano, inglese, spagnolo, portoghese, francese, tedesco
- **Veloce:** alla riapertura la lista compare subito e le app chiuse spariscono all'istante
- **Aggiornamenti automatici**, anche ad app chiusa

## Lingue

L'app usa la **lingua di sistema** dell'auto (italiano, English, español, português, français o deutsch; per le altre lingue l'inglese). Per sceglierne una diversa: **Impostazioni → Lingua**. Si può tornare in qualsiasi momento ad «Automatica».

## Disinstallare un'app

Sulle schede delle app installate da te c'è il pulsante **🗑**; in **Impostazioni → Disinstalla app** trovi invece l'elenco di tutte le app installate da te, anche quelle chiuse. Prima di rimuovere un'app il Task Manager chiede sempre conferma. Le app di sistema non compaiono e non si possono disinstallare.

- **Edizione di sistema o con ADB:** l'app viene rimossa subito dopo la conferma.
- **Edizione normale senza ADB:** si apre la schermata di Android, dove confermi con «OK».

## Quale edizione scaricare

Nelle [Release](../../releases/latest) ci sono due file. **Installane uno solo.**

- **`MG4-TaskManager-system.apk` (consigliata):** chiusura istantanea e lista completa delle app, **senza permessi da attivare**. Gira come **app di sistema** dell'infotainment.
- **`MG4-TaskManager.apk`:** edizione normale, senza privilegi. Chiusura istantanea solo con ADB attivo, altrimenti tramite accessibilità.

Per passare da un'edizione all'altra, disinstalla prima quella che hai.

### Cosa comporta l'edizione di sistema

- Ha permessi ampi (chiudere a forza le app, leggere i processi): installala solo se ti fidi della fonte, cioè di questo repository.
- Scarica l'APK **solo** da questa pagina Release ufficiale, mai da terzi.
- Gli aggiornamenti **richiedono sempre un tocco** su «Installa»: l'app non si installa mai da sola.

## Installazione (da chiavetta USB, senza PC)

1. Scarica **una** delle due edizioni dalle [Release](../../releases/latest) e copiala su una chiavetta USB
2. Inserisci la chiavetta nell'auto e installa l'APK come fai con le altre app
3. Apri **Task Manager**. Con l'edizione di sistema è già tutto pronto. Con quella normale, se serve, segui la scheda «Completa la configurazione» (Accessibilità + Accesso ai dati di utilizzo).

## Le tre modalità

L'app sceglie da sola, a ogni apertura:

| | Sistema | Completa (ADB) | Base |
|---|---|---|---|
| Quando | edizione di sistema | edizione normale con ADB attivo | edizione normale senza ADB |
| Lista app | tutte le app aperte, con RAM | tutte le app aperte, con RAM | app aperte da questa accensione |
| Chiusura | istantanea | istantanea | premendo «Forza interruzione» in automatico |
| Permessi | nessuno da attivare | nessuno da attivare | da attivare una volta |

Con l'edizione normale e ADB, la prima volta l'auto può chiedere «Consentire il debug?»: spunta «Consenti sempre».

## Aggiornamenti automatici

All'accensione dell'auto e poi ogni 6 ore (serve Internet) l'app controlla se c'è una nuova versione, anche se non è aperta. Ogni edizione scarica il proprio aggiornamento.

- **Con ADB:** lo scarica e lo installa da sola.
- **Senza ADB:** lo scarica e mostra la notifica «Task Manager vX pronto»; un tocco apre la schermata di Android con «Installa».

In **Impostazioni → Aggiornamenti** si può disattivare il controllo, scegliere se installare da soli o controllare subito («Controlla ora»).

> **Hai la 1.2 normale?** Installa la 1.2.1 a mano una volta, con la chiavetta. Dalla 1.2.1 in poi gli aggiornamenti sono automatici.

## Sostieni il progetto

MG4 Task Manager è e resterà **gratuito**. Se ti è utile e ti va di contribuire alle ore passate a svilupparlo e provarlo
in auto, puoi lasciare un'offerta: è del tutto **volontaria** e **non c'è un importo minimo**; anche una stella al repository o una
segnalazione di un problema sono un aiuto prezioso. Grazie!

Anche solo **2 €** sono un aiuto prezioso per continuare a sviluppare e provare in auto le nuove versioni.

[![Offrimi un caffè con PayPal](https://img.shields.io/badge/Offrimi%20un%20caff%C3%A8-PayPal-0070BA?style=for-the-badge&logo=paypal&logoColor=white)](https://paypal.me/ErnestoMicco?locale.x=it_IT&country.x=IT)
