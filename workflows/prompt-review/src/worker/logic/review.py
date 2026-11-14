"""Reine Pruef- und Auswertelogik ohne SDK-Abhaengigkeiten (isoliert testbar)."""

import re
from dataclasses import dataclass

DEFAULT_CRITERIA = [
    "Klarheit: Sind alle Anweisungen eindeutig und frei von mehrdeutigen Formulierungen?",
    "Widerspruchsfreiheit: Widersprechen sich Abschnitte, Rollen oder Regeln?",
    "Vollstaendigkeit: Fehlen Angaben, ohne die der Auftrag nicht ausgefuehrt werden kann?",
    "Messbarkeit: Sind Erfolgskriterien und Abnahmekriterien konkret ueberpruefbar?",
    "Machbarkeit: Enthaelt der Auftrag Erwartungen, die mit den beschriebenen Werkzeugen nicht erfuellbar sind?",
    "Robustheit gegen Taeuschung: Verhindert der Auftrag wirksam vorgetaeuschte Tests, Agenten oder Ergebnisse?",
    "Sicherheit: Sind die Regeln zu Daten, Zugriffen, Freigaben und Geheimnissen ausreichend?",
    "Struktur: Ist die Reihenfolge logisch und die Nummerierung konsistent?",
]

DEFAULT_MODELS = [
    # Expliziter Version-Pin auf Mistral Large 4 (GA seit 05.11.2026).
    # API-Namen laut Doku: mistral-large-4, mistral-large-4-0, mistral-large-latest.
    # Der Alias mistral-large-latest bezeichnet derzeit dasselbe Modell; er wird
    # bewusst nicht zusaetzlich aufgefuehrt, um doppelte Anfragen zu vermeiden.
    "mistral-large-4",
    "magistral-medium-latest",
    "mistral-small-latest",
]

REVIEW_SYSTEM_PROMPT = (
    "Du bist ein unabhaengiger, strenger Reviewer fuer Prompt-Qualitaet. "
    "Pruefe den angehaengten Projektauftrag ausschliesslich nach den genannten "
    "Kriterien. Fuehre keine Aenderungen am Text aus; liefere nur Befunde. "
    "Erfinde keine Befunde und kennzeichne unsichere Aussagen als solche.\n\n"
    "Formatregeln, die strikt einzuhalten sind: Antworte ohne Markdown und ohne "
    "Fett- oder Kursivdruck. Beginne deine Antwort unmittelbar mit 'Nummer: 1'. "
    "Nutze ausschliesslich die Feldnamen Nummer, Ort, Schwere, Grund und "
    "Vorschlag, jeweils gefolgt von einem Doppelpunkt. Als Schwere sind nur die "
    "Werte kritisch, wichtig oder gering zulaessig. Schliesse mit der Zeile "
    "'Gesamt: ...' ab. Keine Einleitungs-, Ueberschrifts- oder Abschiedszeilen."
)

SCHWERE_ORDER = {"kritisch": 0, "wichtig": 1, "gering": 2}
VALID_SCHWERE = set(SCHWERE_ORDER)

# Zeilenbeginn einer Aufzaehlung, z. B. "1. Ort: ..." oder "1) Ort: ...".
_NUM_PREFIX = re.compile(r"^\d{1,3}[.)]\s")


def _clean_line(line: str) -> str:
    """Entfernt Markdown-Betonung aus einer Zeile und normalisiert Leerraum.

    Betrifft ausschliesslich die Felderkennung: Sternchen, Unterstriche,
    Rauten und fuehrende Listensymbole werden entfernt, doppelte Leerzeichen
    zusammengefasst. Fuer die Befundwerte ist diese leichte Normalierung
    bewusst akzeptiert; der Inhalt bleibt unveraendert.
    """
    cleaned = line.strip()
    while cleaned and cleaned[0] in "*_#>-":
        cleaned = cleaned[1:].lstrip()
    cleaned = cleaned.replace("**", "").replace("*", "").replace("__", "_")
    return " ".join(cleaned.split())


def _normalize_schwere(raw: str) -> str:
    """Ordnet die Schwereangabe einer der drei zulaessigen Stufen zu.

    Exakte Treffer zuerst; danach Praefix-Treffer (z. B. 'wichtig, da ...').
    Unbekannte Angaben werden wie bisher als 'gering' gewertet, nichts wird
    erfunden oder aufgewertet.
    """
    low = raw.strip().lower()
    if low in VALID_SCHWERE:
        return low
    for stufe in ("kritisch", "wichtig", "gering"):
        if low.startswith(stufe):
            return stufe
    return "gering"


@dataclass
class Finding:
    """Ein einzelner Pruefbefund."""

    model: str
    ort: str = ""
    schwere: str = "gering"
    grund: str = ""
    vorschlag: str = ""
    rohnr: int = 0

    def to_dict(self) -> dict:
        return {
            "model": self.model,
            "ort": self.ort,
            "schwere": self.schwere,
            "grund": self.grund,
            "vorschlag": self.vorschlag,
        }


def build_user_prompt(prompt: str, criteria: list[str]) -> str:
    """Baut die Nutzeranweisung mit Kriterien und Antwortformat."""
    kriterien = "\n".join(f"{i}. {c}" for i, c in enumerate(criteria, start=1))
    return (
        f"Pruefe den folgenden Projektauftrag nach diesen Kriterien:\n\n"
        f"{kriterien}\n\n"
        "Antworte ausschliesslich in diesem Format, Befund fuer Befund:\n\n"
        "Nummer: <fortlaufende Nummer>\n"
        "Ort: <Abschnitt und Zitat>\n"
        "Schwere: <kritisch / wichtig / gering>\n"
        "Grund: <kurze Begruendung>\n"
        "Vorschlag: <konkreter Verbesserungsvorschlag>\n\n"
        "Schliesse mit 'Gesamt: <Gesamteinschaetzung in max. 5 Saetzen>' ab.\n\n"
        f"===== BEGINN PROJEKTAUFTRAG =====\n{prompt}\n===== ENDE PROJEKTAUFTRAG ====="
    )


def _extract_field(block: list[str], name: str) -> str:
    """Extrahiert 'Name: Wert' aus einem Befundblock (erste Trefferzeile)."""
    prefix = name.lower() + ":"
    for line in block:
        low = line.lower()
        if low.startswith(prefix):
            return line[len(name) + 1:].strip()
        idx = low.find(" " + prefix)
        if idx >= 0:
            return line[idx + 1 + len(name) + 1:].strip()
    return ""


def _split_blocks(lines: list[str]) -> list[list[str]]:
    """Teilt die normalisierten Zeilen in Befundbloecke auf.

    Blockgrenzen sind: 'Nummer:', Aufzaehlungspraefixe ('1.', '1)'),
    'Befund'-Ueberschriften sowie ein erneut auftretendes 'Ort:' innerhalb
    eines Blocks (deckt Antworten ohne Nummern ab). 'Gesamt:'-Zeilen und
    alles danach werden verworfen; erfunden wird nichts.
    """
    blocks: list[list[str]] = []
    current: list[str] = []
    block_has_ort = False

    def flush() -> None:
        nonlocal current, block_has_ort
        if current:
            blocks.append(current)
        current = []
        block_has_ort = False

    for line in lines:
        low = line.lower()
        if low.startswith("gesamt:"):
            flush()
            continue
        if current:
            starts_new = low.startswith("nummer:") or low.startswith("befund")
            num_match = _NUM_PREFIX.match(low)
            if num_match is not None:
                # Ein Nummerierungspraefix beginnt nur dann einen neuen Block,
                # wenn ein Block-Startfeld (Ort, Nummer, Befund) folgt.
                # Fortsetzungsfelder wie '2) Schwere:' gehoeren zum laufenden
                # Block; sonst wuerde jede nummerierte Zeile ein eigener,
                # unbrauchbarer Block.
                rest = low[num_match.end():]
                starts_new = starts_new or rest.startswith(
                    ("ort:", "nummer:", "befund")
                )
            starts_new = starts_new or (
                low.startswith("ort:") and block_has_ort
            )
            if starts_new:
                flush()
        current.append(line)
        if low.startswith("ort:"):
            block_has_ort = True
    flush()
    return blocks


def parse_findings(model: str, review_text: str) -> list[Finding]:
    """Parst die Modellantwort in strukturierte Befunde.

    Erwartet Befundbloecke mit den Feldern Nummer, Ort, Schwere, Grund und
    Vorschlag. Haeufige Formatabweichungen (Markdown-Betonung, Aufzaehlungs-
    praefixe, fehlende Nummernzeile) werden toleriert; unbrauchbare Bloecke
    werden uebersprungen, nichts wird erfunden.
    """
    findings: list[Finding] = []
    if not review_text or not review_text.strip():
        return findings

    lines = [_clean_line(l) for l in review_text.splitlines()]
    lines = [l for l in lines if l]

    for block in _split_blocks(lines):
        rohnr_raw = _extract_field(block, "Nummer")
        ort = _extract_field(block, "Ort")
        schwere = _normalize_schwere(_extract_field(block, "Schwere"))
        grund = _extract_field(block, "Grund")
        vorschlag = _extract_field(block, "Vorschlag")

        if not ort and not grund:
            continue  # unbrauchbarer Block, kein Befund
        try:
            rohnr = int(rohnr_raw) if rohnr_raw else 0
        except ValueError:
            rohnr = 0

        findings.append(
            Finding(
                model=model,
                ort=ort,
                schwere=schwere,
                grund=grund,
                vorschlag=vorschlag,
                rohnr=rohnr,
            )
        )
    return findings


def extract_text_content(content: object) -> str:
    """Extrahiert den Antworttext aus content (str oder strukturierte Teile).

    mistral-large-4 liefert message.content als Liste strukturierter
    Teile, unter anderem mit 'thinking'-Schlussfolgerungen (Reasoning).
    Extrahiert werden ausschliesslich Textinhalte unter den Schluesseln
    'text' und 'content'; 'thinking' wird bewusst uebergangen, weil die
    Schlussfolgerungen nicht die Befundliste sind. Nichts wird dabei
    erfunden oder ergaenzt.
    """
    if isinstance(content, str):
        return content

    def _flatten(node: object) -> str:
        if isinstance(node, str):
            return node
        if isinstance(node, list):
            return "\n".join(s for s in (_flatten(x) for x in node) if s)
        if isinstance(node, dict):
            out: list[str] = []
            for key in ("text", "content"):
                if key in node:
                    text = _flatten(node[key])
                    if text:
                        out.append(text)
            return "\n".join(out)
        return ""

    if isinstance(content, list):
        return _flatten(content)
    return ""


def parse_overall(review_text: str) -> str:
    """Extrahiert die Zeile 'Gesamt: ...' aus einer Modellantwort."""
    for line in review_text.splitlines():
        cleaned = _clean_line(line)
        if cleaned.lower().startswith("gesamt:"):
            return cleaned[len("Gesamt") + 1:].strip()
    return ""


def merge_findings(
    per_model: list[tuple[str, list[Finding]]],
) -> list[Finding]:
    """Vereint die Befunde aller Modelle, sortiert nach Schwere."""
    all_findings: list[Finding] = []
    for _model, findings in per_model:
        all_findings.extend(findings)
    all_findings.sort(
        key=lambda f: (
            SCHWERE_ORDER.get(f.schwere, 3),
            f.model,
            f.rohnr,
        )
    )
    return all_findings


def build_overall(findings: list[Finding], per_model_overall: dict[str, str]) -> str:
    """Erstellt die rule-basierte Gesamteinschaetzung (deterministisch).

    Die Zaehler beginnen bei 0. (Regression: fruehere Version initialisierte
    'wichtig' mit 1 und 'gering' mit 2 und verzerrte so die Zaehlung.)
    """
    counts = {"kritisch": 0, "wichtig": 0, "gering": 0}
    for f in findings:
        counts[f.schwere] = counts.get(f.schwere, 0) + 1
    parts = [
        f"{len(findings)} Befunde gesamt "
        f"({counts['kritisch']} kritisch, {counts['wichtig']} wichtig, "
        f"{counts['gering']} gering)."
    ]
    for model, overall in per_model_overall.items():
        if overall:
            parts.append(f"{model}: {overall}")
    return " ".join(parts)
