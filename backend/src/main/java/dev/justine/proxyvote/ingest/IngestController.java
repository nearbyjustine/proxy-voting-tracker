package dev.justine.proxyvote.ingest;

import dev.justine.proxyvote.config.AppProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * The browser uploads the CSV straight to S3 with a pre-signed PUT URL: the file never passes through
 * our API servers (no large request bodies, no memory pressure), and the URL only allows that one key.
 */
@RestController
@RequestMapping("/api/ingest")
public class IngestController {

    public record UploadRequest(@NotBlank @Pattern(regexp = "[\\w .-]{1,100}\\.csv", message = "must be a .csv file name") String fileName) {}

    public record UploadUrl(String url, String key, Instant expiresAt) {}

    private final S3Presigner presigner;
    private final AppProperties props;
    private final Clock clock;

    public IngestController(S3Presigner presigner, AppProperties props, Clock clock) {
        this.presigner = presigner;
        this.props = props;
        this.clock = clock;
    }

    @PostMapping("/upload-url")
    public UploadUrl uploadUrl(@Valid @RequestBody UploadRequest req) {
        String key = "uploads/" + UUID.randomUUID() + "-" + req.fileName().replace(' ', '_');
        var signed = presigner.presignPutObject(PutObjectPresignRequest.builder()
            .signatureDuration(props.aws().presignTtl())
            .putObjectRequest(PutObjectRequest.builder().bucket(props.aws().ingestBucket()).key(key).contentType("text/csv").build())
            .build());
        return new UploadUrl(signed.url().toString(), key, clock.instant().plus(props.aws().presignTtl()));
    }
}
