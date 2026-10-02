package dev.justine.ingest;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;

/**
 * Triggered by S3 ObjectCreated on the ingest bucket. Reads the CSV and fans out one SQS message per meeting.
 *
 * Clients are created ONCE per container (static), not per invocation: Lambda reuses warm containers,
 * so this avoids rebuilding HTTP clients and TLS connections on every event.
 */
public class MeetingIngestHandler implements RequestHandler<S3Event, String> {

    private static final String ENDPOINT = System.getenv("AWS_ENDPOINT_URL");   // set by LocalStack; absent in real AWS
    private static final S3Client S3 = s3();
    private static final SqsClient SQS = sqs();
    private static final ObjectMapper JSON = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Override
    public String handleRequest(S3Event event, Context context) {
        String queueUrl = System.getenv("QUEUE_URL");
        int sent = 0;
        List<String> errors = new ArrayList<>();
        for (var record : event.getRecords()) {
            String bucket = record.getS3().getBucket().getName();
            // S3 event keys are URL-encoded ("my file.csv" arrives as "my+file.csv")
            String key = URLDecoder.decode(record.getS3().getObject().getKey(), StandardCharsets.UTF_8);
            String csv = S3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build()).asUtf8String();

            var result = MeetingCsvMapper.map(CsvParser.parse(csv));
            result.errors().forEach(e -> errors.add(key + " " + e));
            sent += send(queueUrl, key, result.meetings());
        }
        String summary = "sent=" + sent + " errors=" + errors.size() + (errors.isEmpty() ? "" : " " + errors);
        context.getLogger().log(summary);
        return summary;
    }

    /** SendMessageBatch takes at most 10 entries per call. */
    private int send(String queueUrl, String sourceKey, List<MeetingMessage> meetings) {
        int sent = 0;
        for (int i = 0; i < meetings.size(); i += 10) {
            List<SendMessageBatchRequestEntry> entries = new ArrayList<>();
            for (MeetingMessage m : meetings.subList(i, Math.min(i + 10, meetings.size()))) {
                entries.add(SendMessageBatchRequestEntry.builder()
                    .id(String.valueOf(entries.size()))
                    .messageBody(toJson(m))
                    .messageAttributes(java.util.Map.of("source",
                        MessageAttributeValue.builder().dataType("String").stringValue(sourceKey).build()))
                    .build());
            }
            var response = SQS.sendMessageBatch(SendMessageBatchRequest.builder().queueUrl(queueUrl).entries(entries).build());
            if (!response.failed().isEmpty()) {
                throw new IllegalStateException("SQS rejected " + response.failed().size() + " messages: " + response.failed());
            }
            sent += response.successful().size();
        }
        return sent;
    }

    private static String toJson(MeetingMessage m) {
        try {
            return JSON.writeValueAsString(m);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static S3Client s3() {
        var b = S3Client.builder().httpClient(UrlConnectionHttpClient.create());
        if (ENDPOINT != null) b.endpointOverride(URI.create(ENDPOINT)).forcePathStyle(true);
        return b.build();
    }

    private static SqsClient sqs() {
        var b = SqsClient.builder().httpClient(UrlConnectionHttpClient.create());
        if (ENDPOINT != null) b.endpointOverride(URI.create(ENDPOINT));
        return b.build();
    }
}
