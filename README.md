# Craftplay-Rubbellose

Minecraft/Purpur-Plugin fuer Rubellose mit Vault-Economy, SQLite/MySQL, GUI-Shop, Actionbar-Ladebalken, Gewinnvorschau, Spielerprogression und persistenter Ergebnis-Sicherung.

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
target/Craftplay-Rubbellose-0.3.0.jar
```

## Installation

1. Server stoppen.
2. `Craftplay-Rubbellose-0.3.0.jar` in den `plugins`-Ordner kopieren.
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
| `/rubbellos claim` | Offenes Rubellos fortsetzen |
| `/rubbellos daily` | Taegliches Gratis-Los abholen |
| `/rubbellos history` | Eigene Gewinn-Historie anzeigen |
| `/rubbellos series` | Rubellos-Serien anzeigen |
| `/rubbellos pass` | Rubellos-Pass anzeigen |
| `/rubbellos quests` | Taegliche Auftraege anzeigen |
| `/rubbellos board` | Jackpot-, Pass- und Ziel-Board oeffnen |
| `/rubbellos risk` | Letzten Geldgewinn riskieren |
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
- Kauf-, Oeffnungs- und Besitzlimits ueber `config.yml`.
- Gewinnvorschau mit Seltenheiten und effektiven Chancen.
- Lucky Hour mit konfigurierbarem Gewinnbonus.
- Streak-System fuer regelmaessiges Oeffnen.
- Rubellos-Serien mit Sammelfortschritt und Abschlussbelohnung.
- Eventlose mit optionalem Ablaufdatum.
- Serverziel und Gruppenziele mit Belohnungen fuer alle.
- Rubellos-Pass mit XP, Leveln und Belohnungen.
- Taegliche Quests fuer Spieler.
- Mystery-Multiplikator fuer Geldgewinne.
- Pity-System gegen lange Pechstrassen.
- Risiko-Spiel fuer den letzten Geldgewinn.
- Spieler koennen eigene Lose per `/rubbellos gift` verschenken.

## Sicherheitslogik

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
