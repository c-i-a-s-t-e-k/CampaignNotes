# Workflow Dodawania Notatki z Deduplikacją i Synchronizacją Embeddingów

## Cel dokumentu
Dokument opisuje proces dodawania nowej notatki do kampanii, uwzględniając mechanizmy deduplikacji artefaktów i relacji oraz synchronizację embeddingów w Qdrant.

## Workflow

1. **Frontend**: Użytkownik tworzy notatkę i wysyła żądanie `POST /api/campaigns/{campaignUuid}/notes`.

2. **Backend (`NoteController.createNote`)**:
   * Walidacja notatki i kampanii.
   * Generowanie embeddingu notatki i zapis w Qdrant.
   * Ekstrakcja artefaktów i relacji z treści notatki.
   * Proces deduplikacji przez `DeduplicationCoordinator`:
     * Porównanie z istniejącymi danymi w grafie.
     * Tworzenie propozycji merge (`MergeProposals`).
     * Klasyfikacja: auto-merge (wysoka pewność) lub wymagane potwierdzenie użytkownika (niska pewność).

3. **Backend (Obsługa wyników deduplikacji)**:
   * **Scenariusz 1: Wymagane potwierdzenie użytkownika**
     * Propozycje przechowywane w `DeduplicationSessionManager`.
     * Zwracana odpowiedź z `requiresUserConfirmation: true` i listą `artifactMergeProposals`.
   * **Scenariusz 2: Auto-merge**
     * Automatyczne łączenie duplikatów w Neo4j (`ArtifactGraphService.mergeArtifacts()` / `mergeRelationships()`).
     * W Qdrant: usunięcie embeddingów zmergowanych artefaktów/relacji, aktualizacja embeddingów istniejących.
     * Zapis niezduplikowanych artefaktów/relacji do Neo4j z embeddingami w Qdrant.
     * Remapping relacji wskazujących na zmergowane artefakty.
   * **Scenariusz 3: Brak duplikatów**
     * Zapis wszystkich artefaktów i relacji do Neo4j przez `ArtifactGraphService.saveToNeo4j(..., collectionName)`.
     * Automatyczny zapis embeddingów do Qdrant.

4. **Frontend (Obsługa odpowiedzi)**:
   * Jeśli `requiresUserConfirmation: true` → wyświetlenie `DeduplicationModal` do zatwierdzenia propozycji.
   * Jeśli `requiresUserConfirmation: false` → wyświetlenie `NoteCreateConfirmDialog` z podsumowaniem.

5. **Frontend (Potwierdzenie deduplikacji)**: Wysyłanie żądania `POST /api/campaigns/{campaignUuid}/notes/{noteId}/confirm-deduplication`.

6. **Backend (`NoteController.confirmDeduplication`)**:
   * Pobranie sesji z `DeduplicationSessionManager`.
   * Dla zatwierdzonych propozycji: merge (jak w Scenariuszu 2).
   * Dla odrzuconych propozycji: zapis jako nowe (jak w Scenariuszu 3).
   * Usunięcie sesji deduplikacji.
