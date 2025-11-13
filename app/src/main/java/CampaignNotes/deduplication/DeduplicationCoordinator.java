package CampaignNotes.deduplication;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import CampaignNotes.GraphEmbeddingService;
import CampaignNotes.config.DeduplicationConfig;
import CampaignNotes.dto.deduplication.ArtifactCandidate;
import CampaignNotes.dto.deduplication.RelationshipCandidate;
import CampaignNotes.tracking.otel.OTelTraceManager;
import CampaignNotes.tracking.otel.OTelTraceManager.OTelTrace;
import model.Artifact;
import model.Campain;
import model.DeduplicationDecision;
import model.DeduplicationResult;
import model.EmbeddingResult;
import model.Note;
import model.Relationship;

/**
 * Coordinator for the complete hybrid deduplication process (Phase 1 + Phase 2).
 * Orchestrates:
 * - Phase 1: Generation of embeddings and ANN search for candidate detection
 * - Phase 2: LLM-based reasoning to verify potential duplicates
 * 
 * Ensures that artifacts and relationships are properly de-duplicated
 * before being added to the knowledge graph.
 */
public class DeduplicationCoordinator {
    
    private final CandidateFinder candidateFinder;
    private final DeduplicationLLMService dedupLLMService;
    private final GraphEmbeddingService graphEmbeddingService;
    private final DeduplicationConfig config;
    private final OTelTraceManager traceManager;
    
    /**
     * Constructor with dependency injection.
     * 
     * @param candidateFinder service for Phase 1 (ANN search)
     * @param dedupLLMService service for Phase 2 (LLM reasoning)
     * @param graphEmbeddingService service for embedding generation
     * @param config deduplication configuration
     */
    public DeduplicationCoordinator(CandidateFinder candidateFinder,
                                   DeduplicationLLMService dedupLLMService,
                                   GraphEmbeddingService graphEmbeddingService,
                                   DeduplicationConfig config) {
        this.candidateFinder = candidateFinder;
        this.dedupLLMService = dedupLLMService;
        this.graphEmbeddingService = graphEmbeddingService;
        this.config = config;
        this.traceManager = OTelTraceManager.getInstance();
    }
    
    /**
     * Processes a list of newly extracted artifacts for deduplication.
     * Performs both Phase 1 (ANN search) and Phase 2 (LLM reasoning).
     * 
     * @param artifacts newly extracted artifacts from the note
     * @param sourceNote the source note
     * @param campaign the campaign
     * @param campaignCollectionName the Qdrant collection name
     * @param trace OpenTelemetry trace for tracking
     * @return DeduplicationResult with all decisions and findings
     */
    public DeduplicationResult processArtifacts(List<Artifact> artifacts, Note sourceNote, 
                                               Campain campaign, String campaignCollectionName,
                                               OTelTrace trace) {
        long startTime = System.currentTimeMillis();
        DeduplicationResult result = new DeduplicationResult(sourceNote.getId(), campaign.getUuid());
        
        if (artifacts == null || artifacts.isEmpty()) {
            result.setTotalDurationMs(System.currentTimeMillis() - startTime);
            return result;
        }
        
        trace.addEvent("artifact_deduplication_started");
        
        // Phase 1: Embedding generation and ANN search
        long phase1Start = System.currentTimeMillis();
        Map<String, List<ArtifactCandidate>> allCandidates = new HashMap<>();
        Map<String, EmbeddingResult> artifactEmbeddings = new HashMap<>();
        
        for (Artifact artifact : artifacts) {
            try {
                // Generate embedding for the artifact
                EmbeddingResult embeddingResult = graphEmbeddingService.generateArtifactEmbedding(artifact);
                artifactEmbeddings.put(artifact.getId(), embeddingResult);
                result.addTokensUsed(embeddingResult.getTokensUsed());
                
                // Phase 1: Find candidates
                List<ArtifactCandidate> candidates = candidateFinder.findSimilarArtifacts(
                    embeddingResult.getEmbedding(), campaign.getUuid(), campaignCollectionName);
                
                if (!candidates.isEmpty()) {
                    allCandidates.put(artifact.getId(), candidates);
                    trace.addEvent("artifact_candidates_found",
                        Map.of("artifact_id", artifact.getId(), "candidate_count", String.valueOf(candidates.size())));
                }
                
            } catch (Exception e) {
                System.err.println("Error processing artifact " + artifact.getId() + ": " + e.getMessage());
                trace.recordException(e);
            }
        }
        
        long phase1Duration = System.currentTimeMillis() - phase1Start;
        result.setPhase1DurationMs(phase1Duration);
        
        // Phase 2: LLM reasoning
        long phase2Start = System.currentTimeMillis();
        
        for (Artifact artifact : artifacts) {
            List<ArtifactCandidate> candidates = allCandidates.getOrDefault(artifact.getId(), new ArrayList<>());
            
            if (candidates.isEmpty()) {
                // No candidates found, this is a new artifact
                result.addNewArtifact(artifact);
                continue;
            }
            
            // Analyze each candidate
            List<DeduplicationDecision> decisions = new ArrayList<>();
            boolean hasMerge = false;
            
            for (ArtifactCandidate candidate : candidates) {
                DeduplicationDecision decision = dedupLLMService.analyzeArtifactSimilarity(
                    artifact, candidate, sourceNote, campaign, trace);
                
                if (decision.isSame()) {
                    hasMerge = true;
                    decisions.add(decision);
                    trace.addEvent("artifact_merge_candidate_found",
                        Map.of("new_artifact_id", artifact.getId(),
                               "candidate_id", candidate.getArtifactId(),
                               "confidence", String.valueOf(decision.getConfidence())));
                }
            }
            
            // Store decisions
            if (!decisions.isEmpty()) {
                result.addArtifactDecision(artifact.getId(), decisions.get(0));  // Store primary decision
            }
            
            // If no merge candidates, add as new artifact
            if (!hasMerge) {
                result.addNewArtifact(artifact);
            }
        }
        
        long phase2Duration = System.currentTimeMillis() - phase2Start;
        result.setPhase2DurationMs(phase2Duration);
        
        long totalDuration = System.currentTimeMillis() - startTime;
        result.setTotalDurationMs(totalDuration);
        
        trace.setAttribute("dedup.artifacts.processed", artifacts.size());
        trace.setAttribute("dedup.artifacts.new", result.getNewArtifacts().size());
        trace.setAttribute("dedup.merge_candidates", result.getPositiveMergeDecisions());
        trace.setAttribute("dedup.phase1_duration_ms", phase1Duration);
        trace.setAttribute("dedup.phase2_duration_ms", phase2Duration);
        trace.addEvent("artifact_deduplication_completed");
        
        return result;
    }
    
    /**
     * Processes a list of newly extracted relationships for deduplication.
     * Performs both Phase 1 (ANN search) and Phase 2 (LLM reasoning).
     * 
     * @param relationships newly extracted relationships from the note
     * @param sourceNote the source note
     * @param campaign the campaign
     * @param campaignCollectionName the Qdrant collection name
     * @param trace OpenTelemetry trace for tracking
     * @return DeduplicationResult with all decisions and findings
     */
    public DeduplicationResult processRelationships(List<Relationship> relationships, Note sourceNote,
                                                   Campain campaign, String campaignCollectionName,
                                                   OTelTrace trace) {
        long startTime = System.currentTimeMillis();
        DeduplicationResult result = new DeduplicationResult(sourceNote.getId(), campaign.getUuid());
        
        if (relationships == null || relationships.isEmpty()) {
            result.setTotalDurationMs(System.currentTimeMillis() - startTime);
            return result;
        }
        
        trace.addEvent("relationship_deduplication_started");
        
        // Phase 1: Embedding generation and ANN search
        long phase1Start = System.currentTimeMillis();
        Map<String, List<RelationshipCandidate>> allCandidates = new HashMap<>();
        Map<String, EmbeddingResult> relationshipEmbeddings = new HashMap<>();
        
        for (Relationship relationship : relationships) {
            try {
                // Generate embedding for the relationship
                EmbeddingResult embeddingResult = graphEmbeddingService.generateRelationshipEmbedding(relationship);
                relationshipEmbeddings.put(relationship.getId(), embeddingResult);
                result.addTokensUsed(embeddingResult.getTokensUsed());
                
                // Phase 1: Find candidates
                List<RelationshipCandidate> candidates = candidateFinder.findSimilarRelationships(
                    embeddingResult.getEmbedding(), campaign.getUuid(), campaignCollectionName);
                
                if (!candidates.isEmpty()) {
                    allCandidates.put(relationship.getId(), candidates);
                    trace.addEvent("relationship_candidates_found",
                        Map.of("relationship_id", relationship.getId(), "candidate_count", String.valueOf(candidates.size())));
                }
                
            } catch (Exception e) {
                System.err.println("Error processing relationship " + relationship.getId() + ": " + e.getMessage());
                trace.recordException(e);
            }
        }
        
        long phase1Duration = System.currentTimeMillis() - phase1Start;
        result.setPhase1DurationMs(phase1Duration);
        
        // Phase 2: LLM reasoning
        long phase2Start = System.currentTimeMillis();
        
        for (Relationship relationship : relationships) {
            List<RelationshipCandidate> candidates = allCandidates.getOrDefault(relationship.getId(), new ArrayList<>());
            
            if (candidates.isEmpty()) {
                // No candidates found, this is a new relationship
                result.addNewRelationship(relationship);
                continue;
            }
            
            // Analyze each candidate
            List<DeduplicationDecision> decisions = new ArrayList<>();
            boolean hasMerge = false;
            
            for (RelationshipCandidate candidate : candidates) {
                DeduplicationDecision decision = dedupLLMService.analyzeRelationshipSimilarity(
                    relationship, candidate, sourceNote, campaign, trace);
                
                if (decision.isSame()) {
                    hasMerge = true;
                    decisions.add(decision);
                    trace.addEvent("relationship_merge_candidate_found",
                        Map.of("new_relationship_id", relationship.getId(),
                               "candidate_id", candidate.getRelationshipId(),
                               "confidence", String.valueOf(decision.getConfidence())));
                }
            }
            
            // Store decisions
            if (!decisions.isEmpty()) {
                result.addRelationshipDecision(relationship.getId(), decisions.get(0));
            }
            
            // If no merge candidates, add as new relationship
            if (!hasMerge) {
                result.addNewRelationship(relationship);
            }
        }
        
        long phase2Duration = System.currentTimeMillis() - phase2Start;
        result.setPhase2DurationMs(phase2Duration);
        
        long totalDuration = System.currentTimeMillis() - startTime;
        result.setTotalDurationMs(totalDuration);
        
        trace.setAttribute("dedup.relationships.processed", relationships.size());
        trace.setAttribute("dedup.relationships.new", result.getNewRelationships().size());
        trace.setAttribute("dedup.merge_candidates", result.getPositiveMergeDecisions());
        trace.setAttribute("dedup.phase1_duration_ms", phase1Duration);
        trace.setAttribute("dedup.phase2_duration_ms", phase2Duration);
        trace.addEvent("relationship_deduplication_completed");
        
        return result;
    }
}

