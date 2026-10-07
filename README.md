# Craftplay-Rubbellose

Minecraft/Purpur-Plugin fuer Rubellose mit Vault-Economy, SQLite/MySQL, GUI-Shop, Actionbar-Ladebalken, Gewinnvorschau und persistenter Ergebnis-Sicherung.

## Anforderungen

- Purpur/Paper 1.21.10
- Java 21
- Vault
- Ein Vault-kompatibles Economy-Plugin
- Optional: PlaceholderAPI

## Build

```bash
mvn package
```

Die fertige Plugin-Datei liegt danach hier:

```text
target/Craftplay-Rubbellose-0.3.6.jar
```

## Installation

1. Server stoppen.
2. `Craftplay-Rubbellose-0.3.6.jar` in den `plugins`-Ordner kopieren.
3. Vault und ein Economy-Plugin installieren, falls noch nicht vorhanden.
4. Server starten.
5. Dateien in `plugins/Craftplay-Rubbellose/` anpassen.
6. `/rubbellos reload` ausfuehren oder Server neu starten.

## Wichtige Dateien

- `config.yml`: Sprache, Datenbank, Limits, Cooldowns, Ladebalken.
- `gui.yml`: Shop-GUI, Rubbel-GUI, Jackpot-Historie und Item-Anzeigen.
- `rewards.yml`: Rubellos-Typen, Preise, Chancen und Gewinne.
- `language_de.yml`: Deutsche Nachrichten.
- `language_en.yml`: Englische Nachrichten.

## Befehle

| Befehl | Beschreibung |
| --- | --- |
| `/rubbellos` | Shop oeffnen |
| `/rubbellos shop` | Shop oeffnen |
| `/rubbellos buy <typ> <anzahl>` | Mehrere Lose desselben Typs kaufen |
| `/rubbellos claim` | Offenes Rubellos fortsetzen |
| `/rubbellos daily` | Taegliches Gratis-Los abholen |
| `/rubbellos history` | Eigene Gewinn-Historie anzeigen |
| `/rubbellos board` | Jackpot- und Lucky-Hour-Board öffnen |
| `/rubbellos gift <spieler> <typ> <anzahl>` | Eigene Lose verschenken |
| `/rubbellos jackpots` | Jackpot-Historie oeffnen |
| `/rubbellos stats` | Serverstatistik anzeigen |
| `/rubbellos info <spieler>` | Spielerstatistik anzeigen |
| `/rubbellos list` | Geladene Rubellos-Typen anzeigen |
| `/rubbellos debug` | Diagnosewerte anzeigen |
| `/rubbellos resetpending <spieler>` | Offenes Rubellos eines Spielers entfernen |
| `/rubbellos give <spieler> <typ> <anzahl>` | Rubellose geben |
| `/rubbellos simulate <typ> <anzahl>` | Auszahlungen simulieren |
| `/rubbellos reload` | Config, GUI, Sprache und Rewards neu laden |
| `/cpscratchdiag` | Zeigt, welche Plugin-Version die Commands besitzt |

`/scratchcard` ist ebenfalls registriert.

## Permissions

| Permission | Zweck |
| --- | --- |
| `craftplay.scratchcards.use` | Rubellose nutzen |
| `craftplay.scratchcards.shop` | Shop oeffnen |
| `craftplay.scratchcards.buy` | Rubellose kaufen |
| `craftplay.scratchcards.stats` | Statistiken anzeigen |
| `craftplay.scratchcards.give` | Rubellose geben |
| `craftplay.scratchcards.reload` | Plugin neu laden |
| `craftplay.scratchcards.admin` | Admin-Funktionen |

## PlaceholderAPI

Wenn PlaceholderAPI installiert ist, werden diese Platzhalter registriert:

- `%cpsc_opened%`
- `%cpsc_bought%`
- `%cpsc_won_money%`
- `%cpsc_jackpots%`
- `%cpsc_best_win%`

## Spielerfeatures

- Daily-Los mit taeglichem Reset nach Rootserver-/JVM-Zeit.
- Nur Käufe haben ein konfigurierbares Tageslimit; kein Besitz- oder Öffnungslimit.
- Mehrfachkauf mit Mengenauswahl und Gesamtpreis im Shop.
- Gewinnvorschau mit Seltenheiten und effektiven Chancen.
- Lucky Hour mit konfigurierbarem Gewinnbonus.
- Eventlose mit optionalem Ablaufdatum.
- Mystery-Multiplikator fuer Geldgewinne.
- Spieler koennen eigene Lose per `/rubbellos gift` verschenken.

## Bedrock/Geyser

Bedrock-Spieler nutzen dieselben Befehle wie Java-Spieler. `/rubbellos` wird zusaetzlich ueber Papers Brigadier-Command-System registriert, damit Geyser den Command sauber an Bedrock-Clients ausliefern kann. Es gibt kein festes Menue-Item im Inventar; nur gekaufte oder erhaltene Rubellose landen dort.

## Mehrere Lose kaufen

Im Shop zuerst die Menge (standardmäßig 1, 5 oder 10) auswählen, dann den gewünschten Lostyp anklicken. Der Lostyp zeigt Einzelpreis, ausgewählte Menge und Gesamtpreis an. Alternativ kauft `/rubbellos buy small 5` direkt fünf kleine Lose. Die bisherigen Standardbuttons für 25 und 64 Lose werden beim Start oder Reload auch aus vorhandenen GUI-Dateien entfernt; dein eingestelltes Tageskauflimit bleibt unverändert.

`purchases.max_amount_per_purchase` in `config.yml` begrenzt die Menge pro Kauf (Standard: 64). Mengen, Slots und Anzeigen stehen in `gui.yml` unter `shop.quantity_selector`; die Platzhalter `%amount%`, `%unit_price%` und `%total_price%` stehen fuer Kaufmenge, Einzelpreis und Gesamtpreis. `shop.quantity_selector.enabled: false` deaktiviert die Mengenauswahl im GUI, nicht den Kaufbefehl.

Geld, Inventarplatz und Tageskauflimit müssen für die gesamte Menge reichen. Sonst wird nichts gekauft oder abgebucht. Der Standard erlaubt 25 gekaufte Lose pro Tag, nicht 25 Kaufvorgänge. Dein bereits eingestelltes Limit, etwa 10, bleibt beim Update erhalten. Jedes Los zählt einzeln für das Kauflimit und die normalen Statistiken. Es gibt keine XP oder Fortschrittsbelohnungen. Bei einem Speicherfehler wird das Inventar zurückgesetzt und eine Erstattung versucht; fehlgeschlagene Erstattungen werden protokolliert.

Das bisherige `limits.max_opens_per_day` wird beim Start oder `/rubbellos reload` automatisch entfernt. Alle vorhandenen Lose koennen ohne Tages-Oeffnungslimit geoeffnet werden; Cooldown und Schutz vor mehreren gleichzeitig laufenden Losen bleiben erhalten. Bestehende eigene Einstellungen bleiben erhalten, neue Kaufoptionen werden automatisch ergaenzt.

## Sicherheitslogik

Risiko-Spiel, Rubellos-Pass, XP, Quests, Serien, Streaks, Gruppenziele und Pity-Zähler sind entfernt. Es gibt keine Sammelfortschritte oder zusätzliche Erfolgsbelohnungen. Das frühere Serverziel mit Online-Bonus bleibt ebenfalls entfernt. Alte Funktions- und Besitzlimit-Einträge werden beim Start oder Reload automatisch aus Konfiguration, GUI und Sprachdateien entfernt. Preise, Gewinnchancen, Kauflimit und eigene übrige Einstellungen bleiben erhalten. Alte Datenbanktabellen werden nicht gelöscht, aber nicht mehr verwendet; ausgezahlte Coins, Gewinnhistorie und offene Lose bleiben erhalten.

- Rubellose werden ueber den `PersistentDataContainer` markiert.
- Umbenanntes Papier wird nicht akzeptiert.
- Das Rubellos-Item wird direkt beim Start entfernt.
- Der Gewinn wird direkt beim Start berechnet.
- Der Ladebalken ist nur Animation.
- Das Kauflimit ist ein Tageslimit und wird um Mitternacht nach Rootserver-/JVM-Zeit zurueckgesetzt.
- Pro Spieler ist nur ein offenes Rubellos erlaubt.
- Offene Rubellose werden in der Datenbank gespeichert.
- Nach Disconnect oder Restart kann der Spieler mit `/rubbellos claim` fortfahren.
- Admins koennen fehlerhafte offene Lose mit `/rubbellos resetpending <spieler>` entfernen.

## Standardtypen

- `small`
- `medium`
- `premium`
- `event`

Die Typen koennen in `rewards.yml` angepasst oder erweitert werden. Die Gewinnchancen stehen pro Reward unter `chance` und muessen nicht exakt 100 ergeben; sie werden automatisch gewichtet. Der Shop zeigt die effektiven Prozentwerte aus diesen Gewichten an.

## Empfohlener Testablauf

1. `/rubbellos debug`
2. `/rubbellos list`
3. `/rubbellos give <deinName> small 1`
4. Rubellos per Rechtsklick oeffnen.
5. Ladebalken pruefen.
6. Alle Felder im GUI freirubbeln.
7. Auszahlung und Datenbankeintrag pruefen.
8. `/rubbellos stats`
9. `/rubbellos info <deinName>`

## Fehlerbehebung

Wenn Kaufen nicht moeglich ist, pruefe zuerst `/rubbellos debug`. Bei `Vault-Economy: nicht gefunden` ist Vault zwar geladen, aber kein Economy-Plugin als Vault-Provider registriert. Installiere oder pruefe dann dein Economy-Plugin und starte den Server neu.

Wenn der Serverlog bei `/rubellos` noch `Craftplay-Rubbellose v0.1.0 - plugin is disabled` meldet, fuehrt der Server noch den alten Legacy-Command aus. Ab Version 0.3.0 nutzt das Plugin `/rubbellos` und entfernt alte/deaktivierte Legacy-Mappings fuer `/rubellos`. Ein kompletter Serverneustart ist stabiler als PlugManX.

## Debug-Datei

In `config.yml` kann eine Diagnose-Datei aktiviert werden:

```yaml
debug:
  enabled: true
  file: "debug-errors.txt"
  write_info_messages: true
```

Wenn aktiv, schreibt das Plugin Fehler mit Zeitstempel und Stacktrace nach `plugins/Craftplay-Rubbellose/debug-errors.txt`. Mit `/rubbellos debug` wird angezeigt, ob die Datei aktiv ist und wo sie liegt.

## MySQL

In `config.yml`:

```yaml
database:
  use_mysql: true
```

Danach Zugangsdaten im `mysql`-Block setzen und Server neu starten.
