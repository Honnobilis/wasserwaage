# Wasserwaage

Einfache Android-App (Kotlin, ohne Zusatzbibliotheken), die den Beschleunigungssensor als Wasserwaage nutzt.

## Modi

**Wand** – Handy mit dem Rücken an die Wand halten, z. B. auf die Oberkante eines Bilderrahmens.
- Zeigt die Abweichung der nächstliegenden Gerätekante von waagrecht bzw. senkrecht in Grad und mm/m, dazu, welche Seite höher ist.
- Grafisch: farbige Horizontlinie (bleibt immer waagrecht) und Röhrenlibelle.
- Die Anzeige dreht sich in 90°-Schritten mit, damit sie hoch- und querkant lesbar ist.

**Fläche** – Handy mit dem Display nach oben auf die Fläche legen, z. B. eine Tischplatte.
- Zeigt die Neigung in Längs- und Querrichtung in Grad und mm/m.
- Grafisch: Dosenlibelle für beide Richtungen sowie je eine Röhrenlibelle für längs und quer.

Farben: grün < 0,2°, gelb < 1°, sonst orange.

## Kalibrieren

Viele Handys haben einen kleinen Sensor-Versatz. Über „Kalibrieren“ → „Nullpunkt setzen“ wird die aktuelle Lage als Null gespeichert (auf einer sicher ebenen Fläche bzw. an einer sicher geraden Kante). Im Wand-Modus gilt das je Ausrichtung (hochkant, quer, …). „Zurücksetzen“ löscht die Kalibrierung des aktuellen Modus.

## Bauen

```
./gradlew assembleDebug
```

Die APK liegt dann unter `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions baut bei jedem Push automatisch eine Debug-APK und stellt sie als Artefakt `wasserwaage-debug-apk` bereit.
