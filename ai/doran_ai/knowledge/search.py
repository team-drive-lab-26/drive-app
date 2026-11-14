import re
from collections import Counter
from math import log

_NON_WORD = re.compile(r"[^0-9a-z가-힣]+")


def tokens(text: str) -> list[str]:
    compact = _NON_WORD.sub(" ", text.lower())
    grams: list[str] = []
    for word in compact.split():
        grams.extend(word[i : i + 2] for i in range(max(1, len(word) - 1)))
    return grams


class BigramIndex:
    """한국어 형태소 분석기 없이 쓰는 문자 2-gram BM25."""

    def __init__(self, docs: dict[str, str], k1: float = 1.2, b: float = 0.75):
        self.k1, self.b = k1, b
        self.terms = {doc_id: Counter(tokens(text)) for doc_id, text in docs.items()}
        self.lengths = {doc_id: sum(c.values()) for doc_id, c in self.terms.items()}
        self.avg_length = sum(self.lengths.values()) / max(1, len(self.lengths))
        df: Counter[str] = Counter()
        for counts in self.terms.values():
            df.update(counts.keys())
        n = len(self.terms)
        self.idf = {term: log(1 + (n - f + 0.5) / (f + 0.5)) for term, f in df.items()}

    def score(self, query: str, doc_id: str) -> float:
        counts = self.terms[doc_id]
        norm = self.k1 * (1 - self.b + self.b * self.lengths[doc_id] / (self.avg_length or 1))
        total = 0.0
        for term in set(tokens(query)):
            tf = counts.get(term, 0)
            if tf:
                total += self.idf[term] * tf * (self.k1 + 1) / (tf + norm)
        return total

    def rank(self, query: str, candidates: list[str]) -> list[tuple[str, float]]:
        scored = [(doc_id, self.score(query, doc_id)) for doc_id in candidates]
        return sorted((s for s in scored if s[1] > 0), key=lambda s: s[1], reverse=True)
