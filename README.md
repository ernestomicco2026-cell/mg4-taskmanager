# MG4 Task Manager

<p align="center">
<b>Task manager per l'infotainment MG4: vedi le app aperte e le chiudi davvero</b><br>
<i>Task manager for the MG4 infotainment: see the open apps and really close them</i>
</p>

<p align="center">
<a href="https://github.com/ernestomicco2026-cell/mg4-taskmanager/releases/latest/download/MG4-TaskManager-system.apk"><img alt="Scarica / Download 1.4" src="https://img.shields.io/badge/Scarica%20%2F%20Download-1.4-2ea44f?style=for-the-badge&logo=android&logoColor=white"></a>
<a href="https://paypal.me/ErnestoMicco?locale.x=it_IT&country.x=IT"><img alt="Sostieni il progetto con PayPal · Support the project with PayPal" src="https://img.shields.io/badge/Sostieni%20%2F%20Support-PayPal-0070BA?style=for-the-badge&logo=paypal&logoColor=white"></a>
</p>

<p align="center">
🇮🇹 <a href="#italiano">Italiano</a> · 🇬🇧 <a href="#english">English</a> ·
<a href="#quale-edizione-scaricare--which-edition-should-i-download">Quale edizione? / Which edition?</a> ·
<a href="#versioni">Novità</a> / <a href="#changelog">What's new</a>
</p>

> ⚠️ **Compatibilità / Compatibility:** infotainment MG4 (Android Automotive 9) con software **SWI68**, **SWI69**,
> **SWI132** e **SWI133** · MG4 infotainment (Android Automotive 9) with **SWI68**, **SWI69**, **SWI132** and **SWI133**
> software. Non serve root · no root needed.

---

## Video

<a href="https://youtu.be/fYtzuTELNVg"><img width="640" alt="Video di MG4 Task Manager su YouTube · MG4 Task Manager video on YouTube" src="https://img.youtube.com/vi/fYtzuTELNVg/hqdefault.jpg" /></a>

Guarda il [video su YouTube](https://youtu.be/fYtzuTELNVg) · Watch the [video on YouTube](https://youtu.be/fYtzuTELNVg).

---

## Quale edizione scaricare? / Which edition should I download?

Nelle [Release](../../releases/latest) ci sono **due file: installane uno solo**.

| Edizione | File | Per chi |
|---|---|---|
| **Di sistema (consigliata)** | `MG4-TaskManager-system.apk` | tutti: chiusura istantanea e lista completa, senza permessi da attivare |
| Normale | `MG4-TaskManager.apk` | chi preferisce un'app senza privilegi: chiusura istantanea solo con ADB attivo |

**SWI68 R67, R71, R73:** qui MG ha bloccato i menu di Android, quindi installa l'**edizione di sistema**, che si attiva
da sola. Per passare da un'edizione all'altra disinstalla prima quella che hai.

The [Releases](../../releases/latest) contain **two files: install only one**. The **system edition**
(`MG4-TaskManager-system.apk`, recommended) closes apps instantly and shows the full list with no permissions to
enable. The **normal edition** (`MG4-TaskManager.apk`) has no privileges and closes apps instantly only with ADB on.
On **SWI68 R67, R71, R73** the Android menus are locked: install the system edition. To switch edition, uninstall the
one you have first.

---

## Italiano

Task manager per l'infotainment della **MG4**: mostra le app aperte con la RAM usata e permette di chiuderle davvero,
una alla volta o tutte insieme. Si apre dall'icona nel launcher o con un **tasto al volante**.

**Indice:** [Cosa fa](#cosa-fa) · [Installazione](#installazione) · [Aggiornamenti](#aggiornamenti) · [MG4 Store](#mg4-store) · [Togliere il Task Manager](#togliere-il-task-manager) · [Comandi utili](#comandi-utili) · [Segnalare un problema](#segnalare-un-problema) · [Sostieni il progetto](#sostieni-il-progetto) · [Versioni](#versioni) · [Cosa arriva](#cosa-arriva)

### Cosa fa

- Lista delle app aperte, con l'app in primo piano evidenziata e la RAM usata.
- **Chiudi** la singola app o **Chiudi tutto**.
- **Disinstalla** le app che hai installato tu (mai quelle di sistema), vedi sotto.
- **Escludi e nascondi le app di sistema** (radio, navigatore, app dell'auto): attivo di serie.
- **App escluse a scelta** da «Chiudi tutto» (per esempio DiPlay/CarPlay), dalle Impostazioni o tenendo premuta un'app.
- **Tasto al volante** configurabile ("Imposta tasto…" e premi il tasto); opzione «solo pressione lunga».
- **Veloce:** alla riapertura la lista compare subito e le app chiuse spariscono all'istante.
- **Aggiornamenti automatici**, anche ad app chiusa.
- **Lingue:** italiano, inglese, spagnolo, portoghese, francese e tedesco. L'app segue la **lingua di sistema**
  dell'auto (per le altre lingue l'inglese); per sceglierne una diversa: **Impostazioni → Lingua**. Si può tornare in
  qualsiasi momento ad «Automatica».

#### Disinstallare un'app

Sulle schede delle app installate da te c'è il pulsante **🗑**; in **Impostazioni → Disinstalla app** trovi invece
l'elenco di tutte le app installate da te, anche quelle chiuse. Prima di rimuovere un'app il Task Manager chiede
sempre conferma. Le app di sistema non compaiono e non si possono disinstallare.

- **Edizione di sistema o con ADB:** l'app viene rimossa subito dopo la conferma.
- **Edizione normale senza ADB:** si apre la schermata di Android, dove confermi con «OK».

#### Le tre modalità

L'app sceglie da sola, a ogni apertura:

| | Sistema | Completa (ADB) | Base |
|---|---|---|---|
| Quando | edizione di sistema | edizione normale con ADB attivo | edizione normale senza ADB |
| Lista app | tutte le app aperte, con RAM | tutte le app aperte, con RAM | app aperte da questa accensione |
| Chiusura | istantanea | istantanea | premendo «Forza interruzione» in automatico |
| Permessi | nessuno da attivare | nessuno da attivare | da attivare una volta |

Con l'edizione normale e ADB, la prima volta l'auto può chiedere «Consentire il debug?»: spunta «Consenti sempre».
In **Impostazioni → Modalità** vedi quale edizione è installata e se l'accessibilità è attiva; il pulsante **Attiva**
la accende (nell'edizione di sistema subito, da sola).

### Installazione

Scarica **una** delle due edizioni dalle [Release](../../releases/latest) (vedi
[Quale edizione scaricare?](#quale-edizione-scaricare--which-edition-should-i-download)).

#### Metodo 1 — Da chiavetta USB (senza computer)

1. Copia l'APK su una chiavetta USB formattata **FAT32** o **exFAT**.
2. Inserisci la chiavetta nell'auto, apri il file manager e tocca l'APK.
3. Se Android avvisa che non può installare app da questa fonte: tocca **Impostazioni**, attiva
   **Consenti da questa fonte** per il file manager e torna indietro.
4. Tocca **Installa**, poi apri **Task Manager**. Con l'edizione di sistema è già tutto pronto. Con quella normale, se
   serve, segui la scheda «Completa la configurazione» (Accessibilità + Accesso ai dati di utilizzo).

#### Metodo 2 — Con adb da computer

```bash
adb install -r -f MG4-TaskManager-system.apk
```

#### Cosa comporta l'edizione di sistema

- Ha permessi ampi (chiudere a forza le app, leggere i processi): installala solo se ti fidi della fonte, cioè di
  questo repository.
- Scarica l'APK **solo** da questa pagina Release ufficiale, mai da terzi.
- Gli aggiornamenti **richiedono sempre un tocco** su «Installa»: l'app non si installa mai da sola.

### Aggiornamenti

All'accensione dell'auto e poi ogni 6 ore (serve internet) l'app controlla se c'è una nuova versione, anche se non è
aperta. Ogni edizione scarica il proprio aggiornamento.

- **Con ADB:** lo scarica e lo installa da sola.
- **Senza ADB:** lo scarica e mostra la notifica «Task Manager vX pronto»; un tocco apre la schermata di Android con
  «Installa».

In **Impostazioni → Aggiornamenti** si può disattivare il controllo, scegliere se installare da soli o controllare
subito («Controlla ora»).

> **Hai la 1.2 normale?** Installa la 1.2.1 a mano una volta, con la chiavetta. Dalla 1.2.1 in poi gli aggiornamenti
> sono automatici.

### MG4 Store

Ora c'è **MG4 Store** per scaricare e aggiornare tutte le nostre app MG4: il launcher, MG4 Clima, MG4 Task Manager,
MG4 Browser, MG4 Games e quelle che arriveranno. Si trova anche nel launcher MG4 (icona **Store**).
Repository: [ernestomicco2026-cell/store](https://github.com/ernestomicco2026-cell/store).

### Togliere il Task Manager

Si disinstalla come una normale app, dalle impostazioni di Android o da MG4 Store (cestino). Le app chiuse si
riaprono normalmente, e niente dell'auto viene modificato. Per passare all'altra edizione, disinstalla prima questa.

### Comandi utili

```bash
# installare o aggiornare da computer
adb install -r -f MG4-TaskManager-system.apk

# vedere quali app sono aperte (lo stesso dato che legge il Task Manager)
adb shell dumpsys activity recents | grep "Recent #"
```

### Segnalare un problema

Le segnalazioni si aprono nella sezione [Issues](../../issues). Scrivi il software dell'auto (per esempio SWI68 R69),
l'edizione installata e la modalità che vedi in **Impostazioni → Modalità**: bastano per capire cosa succede.

### Sostieni il progetto

MG4 Task Manager è e resterà **gratuito**. Se ti è utile e ti va di contribuire alle ore passate a svilupparlo e
provarlo in auto, puoi lasciare un'offerta: è del tutto **volontaria** e **non c'è un importo minimo**, puoi donare
quello che vuoi. Anche una stella al repository o una segnalazione di un problema sono un aiuto prezioso. Grazie!

Anche solo **2 €** sono un aiuto prezioso per continuare a sviluppare e provare in auto le nuove versioni.

[![Offrimi un caffè con PayPal](https://img.shields.io/badge/Offrimi%20un%20caff%C3%A8-PayPal-0070BA?style=for-the-badge&logo=paypal&logoColor=white)](https://paypal.me/ErnestoMicco?locale.x=it_IT&country.x=IT)

### Versioni

#### 1.4
- **Edizione di sistema:** attiva da sola l'accessibilità, l'accesso ai dati di utilizzo e l'installazione degli
  aggiornamenti, senza menu e senza ADB. Funziona anche sulle SWI68 R67, R71 e R73, dove i menu di Android sono bloccati.
- Gli altri servizi di accessibilità già attivi (launcher, altre app) restano come sono.
- **Impostazioni → Modalità:** mostra l'edizione installata e se l'accessibilità è attiva, con il pulsante **Attiva**.
- **Edizione normale:** se il menu Accessibilità non è raggiungibile e ADB non c'è, consiglia l'edizione di sistema.

#### 1.3
- **Disinstalla le app** che hai installato tu: pulsante 🗑 sulla scheda dell'app oppure **Impostazioni → Disinstalla
  app**, con conferma. Le app di sistema non si possono disinstallare.

#### 1.2.1
- **6 lingue:** italiano, inglese, spagnolo, portoghese, francese e tedesco, con scelta in **Impostazioni → Lingua**.
- Più veloce alla riapertura; aggiornamenti automatici anche per l'edizione normale.

#### 1.2
- App in italiano e inglese, in base alla lingua di sistema.
- Più veloce: icone ed etichette tenute in memoria, RAM letta in una sola volta.

#### 1.1
- Due edizioni: **normale** e **di sistema** (chiusura istantanea e lista completa senza ADB). Se ne installa una sola.

#### 1.0
- Prima versione: lista delle app aperte, chiusura singola e «Chiudi tutto», installazione da chiavetta, app di
  sistema escluse, tasto al volante, aggiornamenti automatici.

### Cosa arriva

- **Passo 1 — prossima versione:** **polacco** e **turco**, così il Task Manager parla le stesse 8 lingue delle altre
  app MG4.

Molte idee arrivano dalle segnalazioni (issue) degli utenti: grazie!

---

## English

Task manager for the **MG4** infotainment: it shows the open apps with their RAM usage and lets you really close them,
one at a time or all at once. It opens from the launcher icon or with a **steering-wheel button**.

**Contents:** [Features](#features) · [Installation](#installation) · [Updates](#updates) · [MG4 Store](#mg4-store-1) · [Removing Task Manager](#removing-task-manager) · [Useful commands](#useful-commands) · [Reporting a problem](#reporting-a-problem) · [Support the project](#support-the-project) · [Changelog](#changelog) · [What's coming](#whats-coming)

### Features

- List of open apps, with the foreground app highlighted and RAM usage.
- **Close** a single app or **Close all**.
- **Uninstall** the apps you installed (never system apps), see below.
- **Exclude and hide system apps** (radio, navigation, car apps): on by default.
- **Apps excluded by choice** from "Close all" (e.g. DiPlay/CarPlay), from Settings or by long-pressing an app.
- Configurable **steering-wheel button** ("Set button…" then press the button); "long-press only" option.
- **Fast:** when reopened the list shows up at once, and closed apps disappear instantly.
- **Automatic updates**, even when the app is closed.
- **Languages:** Italian, English, Spanish, Portuguese, French and German. The app follows the car's **system
  language** (English for any other language); to pick a different one: **Settings → Language**. You can go back to
  "Automatic" at any time.

#### Uninstalling an app

Cards of apps you installed have a **🗑** button; **Settings → Uninstall apps** lists all the apps you installed,
closed ones included. Task Manager always asks for confirmation before removing an app. System apps aren't listed and
can't be uninstalled.

- **System edition or with ADB:** the app is removed right after you confirm.
- **Normal edition without ADB:** Android's screen opens, where you confirm with "OK".

#### The three modes

The app picks by itself every time it opens:

| | System | Full (ADB) | Basic |
|---|---|---|---|
| When | system edition | normal edition with ADB active | normal edition without ADB |
| App list | all open apps, with RAM | all open apps, with RAM | apps opened since power-on |
| Closing | instant | instant | by tapping "Force stop" automatically |
| Permissions | none to enable | none to enable | enable once |

With the normal edition and ADB, the first time the car may ask "Allow debugging?": check "Always allow".
In **Settings → Mode** you can see which edition is installed and whether accessibility is on; the **Enable** button
turns it on (in the system edition at once, by itself).

### Installation

Download **one** of the two editions from the [Releases](../../releases/latest) (see
[Which edition should I download?](#quale-edizione-scaricare--which-edition-should-i-download)).

#### Method 1 — From a USB stick (no computer)

1. Copy the APK to a USB stick formatted **FAT32** or **exFAT**.
2. Plug the stick into the car, open the file manager and tap the APK.
3. If Android says it can't install apps from this source: tap **Settings**, enable **Allow from this source**
   for the file manager and go back.
4. Tap **Install**, then open **Task Manager**. With the system edition everything is ready. With the normal one, if
   needed, follow the "Finish setup" card (Accessibility + Usage access).

#### Method 2 — With adb from a computer

```bash
adb install -r -f MG4-TaskManager-system.apk
```

#### What the system edition means

- It has broad permissions (force-stop apps, read processes): install it only if you trust the source, i.e. this
  repository.
- Download the APK **only** from this official Releases page, never from third parties.
- Updates **always require a tap** on "Install": the app never installs itself.

### Updates

At power-on and then every 6 hours (internet needed) the app checks for a new version, even when it isn't open. Each
edition downloads its own update.

- **With ADB:** it downloads and installs it by itself.
- **Without ADB:** it downloads it and shows the notification "Task Manager vX ready"; a tap opens Android's screen
  with "Install".

In **Settings → Updates** you can turn checking off, choose whether to install automatically, or check right away
("Check now").

> **Have the normal 1.2?** Install 1.2.1 by hand once, with the USB stick. From 1.2.1 on, updates are automatic.

### MG4 Store

There is now **MG4 Store** to download and update all our MG4 apps: the launcher, MG4 Clima, MG4 Task Manager,
MG4 Browser, MG4 Games and the ones still to come. You also find it in the MG4 launcher (**Store** icon).
Repository: [ernestomicco2026-cell/store](https://github.com/ernestomicco2026-cell/store).

### Removing Task Manager

It uninstalls like a normal app, from the Android settings or from MG4 Store (bin). Closed apps reopen normally, and
nothing in the car is changed. To switch to the other edition, uninstall this one first.

### Useful commands

```bash
# install or update from a computer
adb install -r -f MG4-TaskManager-system.apk

# see which apps are open (the same data Task Manager reads)
adb shell dumpsys activity recents | grep "Recent #"
```

### Reporting a problem

Reports go in the [Issues](../../issues) section. Write the car's software (for example SWI68 R69), the installed
edition and the mode shown in **Settings → Mode**: that's enough to understand what's going on.

### Support the project

MG4 Task Manager is and will stay **free**. If you find it useful and would like to contribute to the hours spent
developing it and testing it in the car, you can leave a donation: it's completely **voluntary** and **there is no
minimum amount**, you can give whatever you like. A star on the repository or a bug report help a lot too. Thank you!

Even just **€2** is a great help to keep developing and testing new versions in the car.

[![Buy me a coffee with PayPal](https://img.shields.io/badge/Buy%20me%20a%20coffee-PayPal-0070BA?style=for-the-badge&logo=paypal&logoColor=white)](https://paypal.me/ErnestoMicco?locale.x=it_IT&country.x=IT)

### Changelog

#### 1.4
- **System edition:** turns on accessibility, usage access and update installs by itself, with no menus and no ADB.
  Works on SWI68 R67, R71 and R73 too, where the Android menus are locked.
- Other accessibility services already on (launcher, other apps) stay as they are.
- **Settings → Mode:** shows the installed edition and whether accessibility is on, with an **Enable** button.
- **Normal edition:** if the Accessibility menu can't be reached and there's no ADB, it suggests the system edition.

#### 1.3
- **Uninstall the apps** you installed: 🗑 button on the app card or **Settings → Uninstall apps**, with confirmation.
  System apps can't be uninstalled.

#### 1.2.1
- **6 languages:** Italian, English, Spanish, Portuguese, French and German, selectable in **Settings → Language**.
- Faster when reopened; automatic updates for the normal edition too.

#### 1.2
- App in Italian and English, following the system language.
- Faster: icons and labels kept in memory, RAM read in one go.

#### 1.1
- Two editions: **normal** and **system** (instant close and full list without ADB). Install only one.

#### 1.0
- First version: list of open apps, single close and "Close all", install from a USB stick, system apps excluded,
  steering-wheel button, automatic updates.

### What's coming

- **Step 1 — next version:** **Polish** and **Turkish**, so Task Manager speaks the same 8 languages as the other MG4
  apps.

Many ideas come from users' reports (issues): thank you!
