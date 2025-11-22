package CampaignNotes.dto.assistant;

/**
 * Enum representing the types of actions that can be decided by the PlanningService.
 */
public enum ActionType {
    /**
     * User query requires clarification before processing.
     */
    CLARIFICATION_NEEDED("clarification_needed"),
    
    /**
     * Query is out of scope of the assistant's capabilities.
     */
    OUT_OF_SCOPE("out_of_scope"),
    
    /**
     * Search for notes using semantic search.
     */
    SEARCH_NOTES("search_notes"),
    
    /**
     * Search for artifacts first, then query graph for related data.
     */
    SEARCH_ARTIFACTS_THEN_GRAPH("search_artifacts_then_graph"),
    
    /**
     * Search for relations first, then query graph for related data.
     */
    SEARCH_RELATIONS_THEN_GRAPH("search_relations_then_graph"),
    
    /**
     * Combined search across multiple sources (notes, artifacts, relations).
     */
    COMBINED_SEARCH("combined_search");
    
    private final String value;
    
    ActionType(String value) {
        this.value = value;
    }
    
    /**
     * Returns the string value of the action type.
     * 
     * @return the string representation
     */
    public String getValue() {
        return value;
    }
    
    /**
     * Converts a string value to the corresponding ActionType enum.
     * 
     * @param value the string value to convert
     * @return the corresponding ActionType, or SEARCH_NOTES as default if not found
     */
    public static ActionType fromString(String value) {
        if (value == null) {
            return SEARCH_NOTES;
        }
        
        for (ActionType actionType : ActionType.values()) {
            if (actionType.value.equals(value)) {
                return actionType;
            }
        }
        
        // Default fallback
        return SEARCH_NOTES;
    }
    
    /**
     * Checks if this action requires a graph query.
     * 
     * @return true if the action requires graph query execution
     */
    public boolean requiresGraphQuery() {
        return this == SEARCH_ARTIFACTS_THEN_GRAPH || 
               this == SEARCH_RELATIONS_THEN_GRAPH;
    }
}

