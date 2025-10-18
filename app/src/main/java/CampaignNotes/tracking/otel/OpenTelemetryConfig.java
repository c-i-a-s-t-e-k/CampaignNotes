package CampaignNotes.tracking.otel;

import java.util.Base64;
import java.util.concurrent.TimeUnit;

import io.github.cdimascio.dotenv.Dotenv;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.semconv.ResourceAttributes;

/**
 * Configuration class for OpenTelemetry integration with Langfuse.
 * 
 * This class initializes OpenTelemetry SDK with OTLP exporter configured
 * to send traces to Langfuse OTel endpoint. It handles:
 * - Loading configuration from environment variables
 * - Setting up OTLP gRPC exporter with authentication
 * - Configuring batch span processor for optimal performance
 * - Registering shutdown hooks for graceful cleanup
 * 
 * Usage:
 * Call OpenTelemetryConfig.initialize() once at application startup.
 * Then use OpenTelemetryConfig.getTracer() to obtain tracer instance.
 */
public class OpenTelemetryConfig {
    
    private static final String SERVICE_NAME = "campaign-notes";
    public static final String SERVICE_VERSION = "1.0.0";
    
    private static OpenTelemetry openTelemetry;
    private static Tracer tracer;
    
    /**
     * Initializes OpenTelemetry SDK with Langfuse OTLP endpoint.
     * This method is idempotent - calling it multiple times has no effect.
     * 
     * Configuration is loaded from environment variables:
     * - LANGFUSE_HOST: Langfuse host URL (default: https://cloud.langfuse.com)
     * - LANGFUSE_PUBLIC_KEY: Public API key (required)
     * - LANGFUSE_SECRET_KEY: Secret API key (required)
     * 
     * @throws IllegalStateException if required environment variables are missing
     */
    public static synchronized void initialize() {
        if (openTelemetry != null) {
            return; // Already initialized
        }
        
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        
        String langfuseHost = dotenv.get("LANGFUSE_HOST", "https://cloud.langfuse.com");
        String publicKey = dotenv.get("LANGFUSE_PUBLIC_KEY");
        String secretKey = dotenv.get("LANGFUSE_SECRET_KEY");
        
        if (publicKey == null || secretKey == null) {
            throw new IllegalStateException("LANGFUSE_PUBLIC_KEY and LANGFUSE_SECRET_KEY must be set");
        }
        
        // OTel endpoint for Langfuse
        String otlpEndpoint = langfuseHost + "/api/public/otel";
        
        // Basic Auth header
        String credentials = publicKey + ":" + secretKey;
        String authHeader = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());
        
        // Resource - metadata about the service
        Resource resource = Resource.getDefault()
            .merge(Resource.create(Attributes.builder()
                .put(ResourceAttributes.SERVICE_NAME, SERVICE_NAME)
                .put(ResourceAttributes.SERVICE_VERSION, SERVICE_VERSION)
                .build()));
        
        // OTLP Exporter with authentication (HTTP/protobuf for Langfuse)
        OtlpHttpSpanExporter spanExporter = OtlpHttpSpanExporter.builder()
            .setEndpoint(otlpEndpoint)
            .addHeader("Authorization", authHeader)
            .setTimeout(30, TimeUnit.SECONDS)
            .build();
        
        // Batch Span Processor - handles batching and retry
        BatchSpanProcessor spanProcessor = BatchSpanProcessor.builder(spanExporter)
            .setMaxQueueSize(2048)
            .setMaxExportBatchSize(512)
            .setScheduleDelay(5, TimeUnit.SECONDS)
            .setExporterTimeout(30, TimeUnit.SECONDS)
            .build();
        
        // Tracer Provider
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(spanProcessor)
            .setResource(resource)
            .build();
        
        // OpenTelemetry SDK
        openTelemetry = OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .buildAndRegisterGlobal();
        
        // Create tracer
        tracer = openTelemetry.getTracer(SERVICE_NAME, SERVICE_VERSION);
        
        // Shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            tracerProvider.close();
        }));
    }
    
    /**
     * Returns the configured Tracer instance.
     * 
     * @return Tracer for creating spans
     * @throws IllegalStateException if OpenTelemetry is not initialized
     */
    public static Tracer getTracer() {
        if (tracer == null) {
            throw new IllegalStateException("OpenTelemetry not initialized. Call initialize() first.");
        }
        return tracer;
    }
    
    /**
     * Returns the OpenTelemetry instance.
     * 
     * @return OpenTelemetry SDK instance
     * @throws IllegalStateException if OpenTelemetry is not initialized
     */
    public static OpenTelemetry getOpenTelemetry() {
        if (openTelemetry == null) {
            throw new IllegalStateException("OpenTelemetry not initialized. Call initialize() first.");
        }
        return openTelemetry;
    }
}
