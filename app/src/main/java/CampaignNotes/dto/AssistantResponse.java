package CampaignNotes.dto;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class AssistantResponse {
    private String responseType;
    private String errorType;
    private String textResponse;
    private GraphDTO graphData;
    private List<SourceReference> sources;
    private List<String> executedActions;
    private Map<String, Object> debugInfo;

    // Private constructor to force use of builders
    private AssistantResponse() {}

    // Getters
    public String getResponseType() { return responseType; }
    public String getErrorType() { return errorType; }
    public String getTextResponse() { return textResponse; }
    public GraphDTO getGraphData() { return graphData; }
    public List<SourceReference> getSources() { return sources; }
    public List<String> getExecutedActions() { return executedActions; }
    public Map<String, Object> getDebugInfo() { return debugInfo; }

    // Builder for text response
    public static AssistantResponse text(String response, List<SourceReference> sources, List<String> actions) {
        AssistantResponse ar = new AssistantResponse();
        ar.responseType = "text";
        ar.textResponse = response;
        ar.sources = sources;
        ar.executedActions = actions;
        return ar;
    }

    // Builder for text + graph
    public static AssistantResponse textAndGraph(String response, GraphDTO graph, List<SourceReference> sources, List<String> actions) {
        AssistantResponse ar = new AssistantResponse();
        ar.responseType = "text_and_graph";
        ar.textResponse = response;
        ar.graphData = graph;
        ar.sources = sources;
        ar.executedActions = actions;
        return ar;
    }

    // Builder for errors
    public static AssistantResponse error(String errorType, String message, Map<String, Object> debug) {
        AssistantResponse ar = new AssistantResponse();
        ar.responseType = "error";
        ar.errorType = errorType;
        ar.textResponse = message;
        ar.sources = Collections.emptyList();
        ar.executedActions = Collections.emptyList();
        ar.debugInfo = debug;
        return ar;
    }

    // Builder for clarification needed
    public static AssistantResponse clarificationNeeded(String message) {
        AssistantResponse ar = new AssistantResponse();
        ar.responseType = "clarification_needed";
        ar.textResponse = message;
        ar.sources = Collections.emptyList();
        ar.executedActions = List.of("planning");
        return ar;
    }

    // Builder for out of scope
    public static AssistantResponse outOfScope() {
        AssistantResponse ar = new AssistantResponse();
        ar.responseType = "out_of_scope";
        ar.errorType = "out_of_scope";
        ar.textResponse = "Przepraszam, ale to pytanie wykracza poza moje kompetencje. Mogę pomóc Ci tylko w zakresie danych z tej kampanii (postacie, lokacje, przedmioty, wydarzenia i ich relacje).";
        ar.sources = Collections.emptyList();
        ar.executedActions = List.of("planning");
        return ar;
    }
}
