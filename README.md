# DungeonQuest (Spigot 26.2)

Dungeons mit Bossen, Elite-Mobs, 5 Schwierigkeitsstufen und Belohnungen.

## Plugin bauen (ohne Programmierkenntnisse)

1. Kostenloses Konto auf https://github.com anlegen.
2. Neues Repository erstellen (Button "New", Name z. B. `DungeonQuest`, Privat ist okay).
3. Diesen Ordner entpacken und **alles** (inklusive des versteckten Ordners `.github`) per Drag & Drop in das Repository ziehen ("Add file" -> "Upload files"), dann "Commit changes".
4. Oben auf den Reiter **Actions** klicken. Der Build "Plugin bauen" startet automatisch (ca. 1-2 Minuten).
5. Wenn er grün ist: den Lauf anklicken, unten bei **Artifacts** `DungeonQuest` herunterladen. Darin liegt `DungeonQuest.jar`.
6. Ist der Lauf rot: Fehlermeldung (Schritt "Plugin bauen") kopieren und an Claude schicken.

## Auf den Nitrado-Server

1. Im Nitrado-Panel prüfen, dass Spigot 26.2 und die dazu passende Java-Version (vermutlich Java 25) eingestellt sind.
2. Server stoppen, `DungeonQuest.jar` per Dateibrowser/FTP in den Ordner `plugins` hochladen.
3. Server starten. Beim allerersten Start werden nach ca. 5 Sekunden automatisch Dungeons in der Welt erzeugt (das dauert einige Minuten und kann kurz ruckeln, am besten ohne Spieler online).
4. Die Server-Schwierigkeit darf nicht auf "Friedlich" stehen, sonst verschwinden alle Gegner.

## Befehle

- `/dungeon locate` - nächsten Dungeon suchen (Kompass zeigt auf ihn)
- Admin (OP): `list`, `create <stufe> [turm]`, `generate <anzahl> [stufe] [turm|tief]`, `tp <id>`, `reset <id>`, `remove <id>`, `item <name>`, `reload`

## So funktioniert es

- **Unterirdische Dungeons:** Der Eingang ist ein kleiner Turm an der Oberfläche mit einem Schacht (Leiter oder einfach reinspringen, unten wartet Wasser).
- **Türme an der Oberfläche:** Freistehende Türme mit Etagen, die man per Leiter nach oben erklimmt. Je höher der Turm, desto schwerer (Leicht = 5 Etagen, Inferno = 9 Etagen). Oben wartet der Boss.
- Je weiter vom Spawn entfernt, desto härter der Dungeon.
- Räume füllen sich mit Gegnern, sobald man sie betritt. Zum Schluss wartet ein Boss mit 3 Phasen.
- Mehr Spieler = mehr Gegner, mehr Boss-Leben und mehr Schaden. Alle Werte stehen in `plugins/DungeonQuest/config.yml`.
- Jeder Spieler im Dungeon bekommt beim Boss-Sieg eigene Belohnungen, auf höheren Stufen auch legendäre Items.

## Seltene Items

Zusätzlich zur normalen Beute gibt es auf den schwersten Stufen **pro Spieler** eine Chance auf ein seltenes Spezial-Item:
Inferno 12 %, Albtraum 6 %, Schwer 2 % (in `config.yml` unter `seltene-items` änderbar). Mögliche Items: Windstiefel, Schwingen des Dungeons (Elytra), Sturmdreizack, Titanenhammer, Ewige Spitzhacke, Aegis-Schild, Drachenei. Ein Fund wird allen im Chat angekündigt.

Zum Testen: `/dungeon item <name>` (OP).

## Hinweis für bestehende Server

Wurden Dungeons schon erzeugt, entstehen die Türme beim nächsten Start nachträglich automatisch. Alternativ: `/dungeon generate 5 inferno turm`.
