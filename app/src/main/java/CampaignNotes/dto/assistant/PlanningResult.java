package CampaignNotes.dto.assistant;

import java.util.Map;

public class PlanningResult {
    private ActionType action;
    private String reasoning;
    private Map<String, Object> parameters;

    // Getters and setters
    public ActionType getAction() {
        return action;
    }

    public void setAction(ActionType action) {
        this.action = action;
    }
    
    /**
     * Sets action from string value (for backward compatibility with LLM responses).
     * 
     * @param actionString the string value of the action
     */
    public void setAction(String actionString) {
        this.action = ActionType.fromString(actionString);
    }

    public String getReasoning() {
        return reasoning;
    }

    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }
}
