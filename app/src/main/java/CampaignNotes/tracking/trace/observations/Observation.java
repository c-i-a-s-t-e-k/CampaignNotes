package CampaignNotes.tracking.trace.observations;

import CampaignNotes.tracking.trace.TraceManager;
import com.google.gson.JsonObject;


// abstrakcyjna klasa odnosząca się do wszytkich obserwacji jakie zajdą w obrębie pracy z modelami LLM (to obecnie generacja tekstu oraz embedingów)
public abstract class Observation {
    private JsonObject input;
    private JsonObject output;
    private TraceManager traceManager;


}
