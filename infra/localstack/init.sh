#!/bin/bash
# Runs inside LocalStack once it's ready. Provisions what Terraform/CDK would create in real AWS.
set -euo pipefail
REGION=ap-southeast-1
ACCOUNT=000000000000

# 1) Upload bucket. CORS lets the browser PUT directly to S3 with a pre-signed URL.
awslocal s3 mb s3://proxyvote-ingest
awslocal s3api put-bucket-cors --bucket proxyvote-ingest --cors-configuration '{
  "CORSRules": [{"AllowedOrigins": ["http://localhost:5174", "http://localhost:8091"],
                 "AllowedMethods": ["PUT"], "AllowedHeaders": ["*"], "MaxAgeSeconds": 3000}]}'

# 2) Queue + dead-letter queue. After 3 failed receives a message moves to the DLQ instead of looping forever.
awslocal sqs create-queue --queue-name meeting-events-dlq
DLQ_ARN=$(awslocal sqs get-queue-attributes --queue-url "$(awslocal sqs get-queue-url --queue-name meeting-events-dlq --query QueueUrl --output text)" \
  --attribute-names QueueArn --query Attributes.QueueArn --output text)
awslocal sqs create-queue --queue-name meeting-events --attributes "{
  \"VisibilityTimeout\": \"30\",
  \"RedrivePolicy\": \"{\\\"deadLetterTargetArn\\\":\\\"$DLQ_ARN\\\",\\\"maxReceiveCount\\\":\\\"3\\\"}\"}"
QUEUE_URL=$(awslocal sqs get-queue-url --queue-name meeting-events --query QueueUrl --output text)

# 3) The Lambda (jar built by: cd lambda && ./mvnw package)
awslocal lambda create-function --function-name meeting-ingest \
  --runtime java21 --handler dev.justine.ingest.MeetingIngestHandler::handleRequest \
  --zip-file fileb:///opt/lambda/meeting-ingest.jar \
  --role arn:aws:iam::$ACCOUNT:role/meeting-ingest --timeout 60 --memory-size 512 \
  --environment "Variables={QUEUE_URL=$QUEUE_URL}" > /dev/null
awslocal lambda wait function-active-v2 --function-name meeting-ingest

# 4) S3 -> Lambda trigger for .csv uploads
awslocal s3api put-bucket-notification-configuration --bucket proxyvote-ingest --notification-configuration "{
  \"LambdaFunctionConfigurations\": [{
    \"LambdaFunctionArn\": \"arn:aws:lambda:$REGION:$ACCOUNT:function:meeting-ingest\",
    \"Events\": [\"s3:ObjectCreated:*\"],
    \"Filter\": {\"Key\": {\"FilterRules\": [{\"Name\": \"suffix\", \"Value\": \".csv\"}]}}}]}"

touch /tmp/proxyvote-ready
echo "proxyvote: bucket, queues, lambda and trigger ready"
