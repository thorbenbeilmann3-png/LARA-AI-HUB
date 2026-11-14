"""Reine Pruef- und Auswertelogik ohne SDK-Abhaengigkeiten (isoliert testbar)."""

from dataclasses import dataclass, field

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
    "mistral-large-latest",
    "magistral-medium-latest",
    "mistral-small-latest",
]

REVIEW_SYSTEM_PROMPT = (
    "Du bist ein unabhaengiger, strenger Reviewer fuer Prompt-Qualitaet. "
    "Pruefe den angehaengten Projektauftrag ausschliesslich nach den genannten "
    "Kriterien. Fuehre keine Aenderungen am Text aus; liefere nur Befunde. "
    "Erfinde keine Befunde und kennzeichne unsichere Aussagen als solche."
)

SCHWERE_ORDER = {"kritisch": 0, "wichtig": 1, "gering": 2}
VALID_SCHWERE = set(SCHWERE_ORDER)


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


def _extract_field(block: str, name: str) -> str:
    """Extrahiert 'Name: Wert' aus einem Befundblock (erste Zeile reicht)."""
    for line in block.splitlines():
        stripped = line.strip()
        if stripped.lower().startswith(name.lower() + ":"):
            return stripped[len(name) + 1:].strip()
    return ""


def parse_findings(model: str, review_text: str) -> list[Finding]:
    """Parst die Modellantwort in strukturierte Befunde.

    Erwartet Bloecke der Form 'Nummer: ...' bis 'Vorschlag: ...'.
    Fehlerhafte Bloecke werden uebersprungen, nicht erfunden.
    """
    findings: list[Finding] = []
    if not review_text or not review_text.strip():
        return findings

    blocks: list[str] = []
    current: list[str] = []
    for line in review_text.splitlines():
        if line.strip().lower().startswith("nummer:"):
            if current:
                blocks.append("\n".join(current))
                current = []
        current.append(line)
    if current:
        blocks.append("\n".join(current))

    for block in blocks:
        if block.strip().lower().startswith("gesamt:"):
            continue
        rohnr_raw = _extract_field(block, "Nummer")
        ort = _extract_field(block, "Ort")
        schwere = _extract_field(block, "Schwere").lower()
        grund = _extract_field(block, "Grund")
        vorschlag = _extract_field(block, "Vorschlag")

        if not ort and not grund:
            continue  # unbrauchbarer Block, kein Befund
        if schwere not in VALID_SCHWERE:
            schwere = "gering"
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


def parse_overall(review_text: str) -> str:
    """Extrahiert die Zeile 'Gesamt: ...' aus einer Modellantwort."""
    for line in review_text.splitlines():
        stripped = line.strip()
        if stripped.lower().startswith("gesamt:"):
            return stripped[len("Gesamt") + 1:].strip()
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
    """Erstellt die rule-basierte Gesamteinschaetzung (deterministisch)."""
    counts = {"kritisch": 0, "wichtig": 1, "gering": 2}
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
