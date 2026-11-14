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

    def test_leere_antwort_liefert_keine_befunde(self):
        self.assertEqual(parse_findings("m", LEERE_ANTWORT), [])

    def test_unbrauchbare_antwort_erfindet_nichts(self):
        self.assertEqual(parse_findings("m", UNBRAUCHBARE_ANTWORT), [])

    def test_ungueltige_schwere_wird_gingeordnet(self):
        text = "Nummer: 1\nOrt: X\nSchwere: mega-schlimm\nGrund: G\nVorschlag: V"
        findings = parse_findings("m", text)
        self.assertEqual(findings[0].schwere, "gering")


class TestParseOverall(unittest.TestCase):
    def test_gesamt_wird_extrahiert(self):
        self.assertIn("tragfaehig", parse_overall(BEISPIEL_ANTWORT))

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
