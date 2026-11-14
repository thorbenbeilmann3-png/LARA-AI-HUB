"""Tests fuer die reine Pruef- und Auswertelogik (keine SDK-Abhaengigkeiten).

Ausfuehrung: python3 -m unittest discover -s tests -v
(fuer pytest: pytest, siehe pyproject.toml)
"""

import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "src"))

import unittest

from worker.logic.review import (
    DEFAULT_CRITERIA,
    DEFAULT_MODELS,
    build_overall,
    build_user_prompt,
    extract_text_content,
    merge_findings,
    parse_findings,
    parse_overall,
)


BEISPIEL_ANTWORT = """Nummer: 1
Ort: Abschnitt 2, Budgetvorgabe
Schwere: kritisch
Grund: Die Stromkosten fehlen.
Vorschlag: Stromkosten als festen Posten aufnehmen.

Nummer: 2
Ort: Abschnitt 5, Schritt 3
Schwere: wichtig
Grund: BLOCKIERT ist nicht definiert.
Vorschlag: Zustandsweise ergaenzen.

Gesamt: Der Auftrag ist insgesamt tragfaehig, hat aber zwei Luecken."""

# Antwortstil, wie ihn groessere Modelle haeufig liefern: Markdown-Betonung
# und Aufzaehlungspraefixe statt der 'Nummer:'-Zeilen.
MARKDOWN_ANTWORT = """Hier meine Pruefung:

### Befund 1
- **Ort:** Abschnitt 7, Hardwareinventur
- **Schwere:** kritisch
- **Grund:** Die Hardwareangaben sind unbestaetigt.
- **Vorschlag:** CPU-Generation zuerst pruefen.

### Befund 2
1) Ort: Abschnitt 13, Sicherheit
2) Schwere: wichtig, da Secrets ungeschuetzt sind
3) Grund: Keine Speicherregeln fuer Secrets.
4) Vorschlag: Verschluesselte Ablage vorschreiben.

**Gesamt:** Der Auftrag ist gut, hat aber zwei Luecken."""

# Befunde ohne jegliche Nummerierung, nur durch 'Ort:'-Zeilen getrennt.
OHNE_NUMMERN_ANTWORT = """Ort: Abschnitt 2.1, Budget
Schwere: wichtig
Grund: Kein Messgeraet definiert.

Ort: Abschnitt 12, Dashboard
Schwere: gering
Grund: 'dauerhaft' ist nicht definiert.

Gesamt: Zwei kleinere Luecken."""

LEERE_ANTWORT = ""
UNBRAUCHBARE_ANTWORT = "Hier steht alles Moegliche, aber kein Format."


class TestDefaultModels(unittest.TestCase):
    def test_mistral_large_4_enthalten(self):
        # Anforderung des Nutzers: Mistral Large 4 explizit als Pruefmodell.
        self.assertIn("mistral-large-4", DEFAULT_MODELS)
        # Keine doppelte Anfrage an dasselbe Modell:
        self.assertNotIn("mistral-large-latest", DEFAULT_MODELS)

    def test_drei_modelle(self):
        self.assertEqual(len(DEFAULT_MODELS), 3)


class TestBuildUserPrompt(unittest.TestCase):
    def test_enthaelt_kriterien_und_format(self):
        text = build_user_prompt("Mein Auftrag", DEFAULT_CRITERIA)
        self.assertIn("Mein Auftrag", text)
        self.assertIn("1. Klarheit", text)
        self.assertIn("Nummer:", text)
        self.assertIn("Schwere:", text)
        self.assertIn("BEGINN PROJEKTAUFTRAG", text)


class TestParseFindings(unittest.TestCase):
    def test_vollstaendige_antwort(self):
        findings = parse_findings("mistral-large-4", BEISPIEL_ANTWORT)
        self.assertEqual(len(findings), 2)
        self.assertEqual(findings[0].schwere, "kritisch")
        self.assertEqual(findings[1].schwere, "wichtig")
        self.assertEqual(findings[0].model, "mistral-large-4")
        self.assertIn("Stromkosten", findings[0].grund)
        self.assertNotIn("Gesamt", findings[0].ort)

    def test_markdown_antwort_wird_erkannt(self):
        findings = parse_findings("mistral-large-4", MARKDOWN_ANTWORT)
        self.assertEqual(len(findings), 2)
        self.assertEqual(findings[0].schwere, "kritisch")
        self.assertEqual(findings[1].schwere, "wichtig")
        self.assertIn("Hardwareinventur", findings[0].ort)
        self.assertIn("verschluesselte ablage", findings[1].vorschlag.lower())
        self.assertNotIn("Gesamt", findings[0].ort)

    def test_antwort_ohne_nummern_wird_erkannt(self):
        findings = parse_findings("m", OHNE_NUMMERN_ANTWORT)
        self.assertEqual(len(findings), 2)
        self.assertEqual(findings[0].schwere, "wichtig")
        self.assertEqual(findings[1].schwere, "gering")

    def test_leere_antwort_liefert_keine_befunde(self):
        self.assertEqual(parse_findings("m", LEERE_ANTWORT), [])

    def test_unbrauchbare_antwort_erfindet_nichts(self):
        self.assertEqual(parse_findings("m", UNBRAUCHBARE_ANTWORT), [])

    def test_ungueltige_schwere_wird_gingeordnet(self):
        text = "Nummer: 1\nOrt: X\nSchwere: mega-schlimm\nGrund: G\nVorschlag: V"
        findings = parse_findings("m", text)
        self.assertEqual(findings[0].schwere, "gering")

    def test_schwere_mit_zusatz_wird_erkannt(self):
        text = (
            "Nummer: 1\nOrt: X\nSchwere: Wichtig, da nicht messbar\n"
            "Grund: G\nVorschlag: V"
        )
        findings = parse_findings("m", text)
        self.assertEqual(findings[0].schwere, "wichtig")


class TestExtractTextContent(unittest.TestCase):
    """Extraktion des Antworttexts aus strukturiertem content (mistral-large-4)."""

    def test_einfacher_text(self):
        self.assertEqual(extract_text_content("Antwort"), "Antwort")

    def test_strukturierte_teile_mit_thinking(self):
        content = [
            {"thinking": [{"text": "Ich ueberlege ..."}]},
            {"text": [{"text": "Nummer: 1\nOrt: X"}]},
        ]
        self.assertEqual(extract_text_content(content), "Nummer: 1\nOrt: X")

    def test_text_als_string_teil(self):
        content = [
            {"thinking": [{"text": "Ueberlegung"}]},
            {"text": "Nummer: 1"},
        ]
        self.assertEqual(extract_text_content(content), "Nummer: 1")

    def test_thinking_allein_liefert_leer(self):
        # Thinking-Anteile duerfen nicht als Befunde missverstanden werden.
        self.assertEqual(
            extract_text_content([{"thinking": [{"text": "nur Gedanke"}]}]), ""
        )

    def test_unbekannter_inhalt_liefert_leer(self):
        self.assertEqual(extract_text_content(42), "")


class TestParseOverall(unittest.TestCase):
    def test_gesamt_wird_extrahiert(self):
        self.assertIn("tragfaehig", parse_overall(BEISPIEL_ANTWORT))

    def test_gesamt_mit_markdown_wird_extrahiert(self):
        self.assertIn("zwei Luecken", parse_overall(MARKDOWN_ANTWORT))

    def test_fehlende_gesamtzeile(self):
        self.assertEqual(parse_overall("ohne gesamt"), "")


class TestMergeFindings(unittest.TestCase):
    def test_sortierung_nach_schwere(self):
        von_m1 = parse_findings("modell-a", BEISPIEL_ANTWORT)
        von_m2 = parse_findings(
            "modell-b",
            "Nummer: 1\nOrt: Y\nSchwere: gering\nGrund: G\nVorschlag: V",
        )
        merged = merge_findings([("modell-a", von_m1), ("modell-b", von_m2)])
        self.assertEqual(len(merged), 3)
        self.assertEqual(merged[0].schwere, "kritisch")
        self.assertEqual(merged[-1].schwere, "gering")


class TestBuildOverall(unittest.TestCase):
    def test_zaehlung_und_modelleinschaetzungen(self):
        von_m1 = parse_findings("modell-a", BEISPIEL_ANTWORT)
        findings = merge_findings([("modell-a", von_m1)])
        overall = build_overall(
            findings, {"modell-a": parse_overall(BEISPIEL_ANTWORT)}
        )
        self.assertIn("2 Befunde", overall)
        self.assertIn("1 kritisch", overall)
        self.assertIn("tragfaehig", overall)

    def test_ohne_befunde(self):
        overall = build_overall([], {})
        self.assertIn("0 Befunde", overall)

    def test_zaehlung_beginnt_bei_null(self):
        # Regressionstest: Zaehler muessen bei 0 beginnen, nicht bei der
        # Sortierreihenfolge (frueherer Fehler: 1/2 statt 0/0).
        findings = merge_findings([
            ("m", parse_findings(
                "m",
                "Nummer: 1\nOrt: A\nSchwere: kritisch\nGrund: G\nVorschlag: V\n\n"
                "Nummer: 2\nOrt: B\nSchwere: kritisch\nGrund: G\nVorschlag: V\n\n"
                "Nummer: 3\nOrt: C\nSchwere: wichtig\nGrund: G\nVorschlag: V\n\n"
                "Nummer: 4\nOrt: D\nSchwere: wichtig\nGrund: G\nVorschlag: V\n\n"
                "Nummer: 5\nOrt: E\nSchwere: gering\nGrund: G\nVorschlag: V\n\n"
                "Nummer: 6\nOrt: F\nSchwere: gering\nGrund: G\nVorschlag: V",
            )),
        ])
        overall = build_overall(findings, {})
        self.assertIn("6 Befunde gesamt", overall)
        self.assertIn("(2 kritisch, 2 wichtig, 2 gering)", overall)
        self.assertNotIn("(2 kritisch, 3 wichtig", overall)


if __name__ == "__main__":
    unittest.main()
