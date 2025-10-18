# Przewodnik użycia OpenTelemetry w CampaignNotes

## Spis treści
1. [Podstawowe użycie](#podstawowe-użycie)
2. [Nowe funkcje trace'a](#nowe-funkcje-tracea)
3. [Linkowanie promptów](#linkowanie-promptów)
4. [Śledzenie kosztów i parametrów modelu](#śledzenie-kosztów-i-parametrów-modelu)
5. [Kompletny przykład](#kompletny-przykład)

## Podstawowe użycie

### Inicjalizacja (w App.java)

```java
// Wywołaj raz przy starcie aplikacji
OpenTelemetryConfig.initialize();
```

### Tworzenie trace'a

```java
OTelTraceManager traceManager = new OTelTraceManager();

try (OTelTrace trace = traceManager.createTrace(
    "note-embedding",           // nazwa workflow
    campaignId,                 // ID kampanii
    noteId,                     // ID notatki
    userId                      // ID użytkownika (opcjonalnie)
)) {
    // Twój kod workflow
    trace.setStatus(true, "Completed successfully");
}
```

## Nowe funkcje trace'a

### Dodawanie tagów

```java
try (OTelTrace trace = traceManager.createTrace(...)) {
    trace.addTag("production")
         .addTag("high-priority")
         .addTag("user-generated");
}
```

### Ustawianie metadanych (filtrowalne w Langfuse UI)

```java
try (OTelTrace trace = traceManager.createTrace(...)) {
    trace.setMetadata("campaign_type", "fantasy")
         .setMetadata("note_length", "long")
         .setMetadata("processing_priority", "high");
}
```

### Ustawianie sesji użytkownika

```java
try (OTelTrace trace = traceManager.createTrace(...)) {
    trace.setSessionId("session-abc-123");
}
```

### Ustawianie release i środowiska

```java
try (OTelTrace trace = traceManager.createTrace(...)) {
    trace.setRelease("v2.1.0")
         .setEnvironment("production");
}
```

### Publiczny dostęp do trace'a

```java
try (OTelTrace trace = traceManager.createTrace(...)) {
    trace.setPublic(true);  // Umożliwia udostępnienie przez URL
}
```

## Linkowanie promptów

### Powiązanie z wersjonowanym promptem z Langfuse

```java
try (OTelGenerationObservation obs = 
    new OTelGenerationObservation("nae-generation", trace.getContext())) {
    
    // Linkuj do promptu w Langfuse
    obs.withPrompt("NarrativeArtefactExtractorV2", 3)  // nazwa i wersja
       .withModel("gpt-4")
       .withPrompt(prompt)
       .withResponse(response)
       .withTokenUsage(inputTokens, outputTokens, totalTokens)
       .setSuccess();
}
```

## Śledzenie kosztów i parametrów modelu

### Dodawanie kosztu wywołania LLM

```java
try (OTelGenerationObservation obs = 
    new OTelGenerationObservation("llm-generation", trace.getContext())) {
    
    obs.withModel("gpt-4")
       .withCost(0.0156)  // koszt w USD
       .withTokenUsage(100, 200, 300)
       .setSuccess();
}
```

### Dodawanie parametrów modelu

```java
Map<String, Object> modelParams = new HashMap<>();
modelParams.put("temperature", 0.7);
modelParams.put("max_tokens", 2000);
modelParams.put("top_p", 0.95);
modelParams.put("frequency_penalty", 0.0);

try (OTelGenerationObservation obs = 
    new OTelGenerationObservation("llm-generation", trace.getContext())) {
    
    obs.withModel("gpt-4")
       .withModelParameters(modelParams)
       .withPrompt(prompt)
       .withResponse(response)
       .setSuccess();
}
```

### Dodawanie czasu rozpoczęcia completion

```java
Instant completionStartTime = Instant.now();
// ... oczekiwanie na pierwszą część odpowiedzi LLM ...

try (OTelGenerationObservation obs = 
    new OTelGenerationObservation("llm-generation", trace.getContext())) {
    
    obs.withModel("gpt-4")
       .withCompletionStartTime(completionStartTime)
       .setSuccess();
}
```

### Ustawianie rzeczywistego modelu z odpowiedzi

```java
try (OTelGenerationObservation obs = 
    new OTelGenerationObservation("llm-generation", trace.getContext())) {
    
    obs.withModel("gpt-4")  // model requested
       .withResponseModel("gpt-4-0125-preview")  // model actually used
       .setSuccess();
}
```

## Kompletny przykład

### Przykład z ArtifactGraphService

```java
public List<Artifact> extractArtifactsFromNote(String noteContent, 
                                                List<ArtifactCategory> categories,
                                                Note note, 
                                                Campain campaign, 
                                                OTelTrace trace) {
    
    // Dodaj kontekst do trace'a
    trace.setSessionId(getCurrentSessionId())
         .addTag("artifact-extraction")
         .addTag(campaign.getTheme())
         .setMetadata("campaign_name", campaign.getName())
         .setMetadata("note_type", note.getType());
    
    List<Artifact> artifacts = new ArrayList<>();
    
    // Utwórz observation dla NAE
    try (OTelGenerationObservation observation = 
        new OTelGenerationObservation("nae-generation", trace.getContext())) {
        
        observation.withComponent("nae")
                   .withStage("artifact-extraction");
        
        // Pobierz prompt z Langfuse
        Map<String, Object> promptVariables = new HashMap<>();
        promptVariables.put("CATEGORIES", formatCategoriesForPrompt(categories));
        promptVariables.put("TEXT", createNAEInputPrompt(noteContent));
        
        PromptContent promptContent = langfuseClient.getPromptContentWithVariables(
            "NarrativeArtefactExtractorV2", promptVariables);
        
        // Przygotuj wywołanie LLM
        String systemPrompt = extractSystemPrompt(promptContent);
        String inputPrompt = extractInputPrompt(promptContent);
        
        // Parametry modelu
        Map<String, Object> modelParams = new HashMap<>();
        modelParams.put("temperature", 0.7);
        modelParams.put("max_tokens", 4000);
        
        // Linkuj prompt z Langfuse
        observation.withPrompt("NarrativeArtefactExtractorV2", 
                              promptContent.getVersion())
                   .withModel(ARTIFACT_EXTRACTION_MODEL)
                   .withModelParameters(modelParams)
                   .withPrompt(systemPrompt + "\n\n" + inputPrompt);
        
        try {
            // Wywołaj LLM
            Instant completionStart = Instant.now();
            LLMResponse response = llmService.generate(systemPrompt, inputPrompt);
            
            observation.withResponseModel(response.getModelUsed())
                       .withCompletionStartTime(completionStart)
                       .withResponse(response.getContent())
                       .withTokenUsage(
                           response.getInputTokens(),
                           response.getOutputTokens(),
                           response.getTotalTokens()
                       )
                       .withCost(calculateCost(response));
            
            // Parsuj artefakty
            artifacts = parseArtifacts(response.getContent());
            
            observation.setSuccess();
            trace.setAttribute("artifacts.count", artifacts.size());
            
        } catch (Exception e) {
            observation.recordException(e);
            trace.setStatus(false, "Artifact extraction failed: " + e.getMessage());
            throw e;
        }
    }
    
    return artifacts;
}

private double calculateCost(LLMResponse response) {
    // Przykładowe ceny dla gpt-4
    double inputCostPer1k = 0.03;
    double outputCostPer1k = 0.06;
    
    return (response.getInputTokens() / 1000.0 * inputCostPer1k) +
           (response.getOutputTokens() / 1000.0 * outputCostPer1k);
}
```

## Najlepsze praktyki

1. **Zawsze używaj try-with-resources** - zapewnia automatyczne zamknięcie spanów
2. **Linkuj prompty** - jeśli używasz promptów z Langfuse, zawsze je linkuj przez `withPrompt()`
3. **Dodawaj koszty** - pomaga w monitorowaniu wydatków na LLM
4. **Używaj tagów** - ułatwia filtrowanie w Langfuse UI
5. **Metadane na najwyższym poziomie** - używaj `setMetadata()` dla wartości, po których chcesz filtrować
6. **Zawsze ustawiaj status** - używaj `setSuccess()` lub `setError()` na końcu obserwacji
7. **Rejestruj wyjątki** - używaj `recordException()` dla automatycznego śledzenia błędów

## Weryfikacja w Langfuse UI

Po wprowadzeniu zmian sprawdź w Langfuse:

1. **Traces** - czy trace'y się pojawiają z poprawnymi nazwami
2. **Sessions** - czy sesje są prawidłowo linkowane
3. **Tags** - czy tagi są widoczne i filtrowalne
4. **Prompts** - czy linkowanie promptów działa
5. **Costs** - czy koszty są prawidłowo obliczane i wyświetlane
6. **Metadata** - czy możesz filtrować po niestandardowych metadanych

