package CampaignNotes.tracking.trace.traces;

import CampaignNotes.tracking.trace.observations.Observation;

public abstract class Trace {

    public abstract boolean addObservation(Observation observation);
    public abstract boolean updateTraceInput(JsonObject input);
    public abstract boolean updateTraceOutput(JsonObject output);
}
