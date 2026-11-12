#!/bin/bash
# Prod only. Creates the monitoring described in documentation/architecture/deployment.md: log metric filters, alarms and an
# outside health check. Safe to run again: every call updates what exists. Run it where an admin AWS profile is signed in:
#   ALERT_EMAIL=you@example.com AWS_PROFILE=<admin profile> ./alarms.sh
# The address is not in the repository on purpose. SNS sends each new address a confirmation message, and nothing is
# delivered until its link is clicked.
set -euo pipefail

: "${ALERT_EMAIL:?set ALERT_EMAIL to the address that should receive alerts}"
REGION=us-west-2
ACCOUNT=$EVEASSIST_ACCOUNT_ID
INSTANCE=$EVEASSIST_INSTANCE_ID
LOG_GROUP=/eveassist/prod
NS=EveAssist/Prod
TAGS=(Key=Project,Value=eveassist Key=Env,Value=prod)
export AWS_PAGER=""

echo "1. notification topic in $REGION"
TOPIC=$(aws sns create-topic --region $REGION --name eveassist-alerts --tags "${TAGS[@]}" --query TopicArn --output text)
aws sns subscribe --region $REGION --topic-arn "$TOPIC" --protocol email --notification-endpoint "$ALERT_EMAIL" > /dev/null
# Route 53 health metrics exist only in us-east-1, so that alarm needs a topic there: the one the billing alarm uses.
# Subscribing again resends the confirmation if the first one was never clicked.
US_EAST_TOPIC=arn:aws:sns:us-east-1:$ACCOUNT:billing-alarm-eve-assist
aws sns subscribe --region us-east-1 --topic-arn "$US_EAST_TOPIC" --protocol email --notification-endpoint "$ALERT_EMAIL" > /dev/null

echo "2. log metric filters on $LOG_GROUP"
filter() { # name, pattern, metric
    aws logs put-metric-filter --region $REGION --log-group-name $LOG_GROUP --filter-name "$1" --filter-pattern "$2" \
        --metric-transformations "metricName=$3,metricNamespace=$NS,metricValue=1,defaultValue=0"
}
# the app and Keycloak write " ERROR " at that level; Caddy writes JSON with a lower-case level
filter eveassist-prod-errors '" ERROR "' AppErrors
# the app logs a WARN with "failed" when an ESI fetch fails (it does not log the HTTP status, so 420 and 429 are not counted apart)
filter eveassist-prod-esi-failures '"WARN" "failed"' EsiFetchFailures
# Caddy could not reach the app or Keycloak: connection refused, or the name did not resolve
filter eveassist-prod-upstream-unreachable '{ $.logger = "http.log.error" && $.msg = "*dial tcp*" }' UpstreamUnreachable

echo "3. alarms in $REGION"
alarm() { # name, description, then the rest of the put-metric-alarm arguments
    local name=$1 description=$2; shift 2
    aws cloudwatch put-metric-alarm --region $REGION --alarm-name "$name" --alarm-description "$description" \
        --alarm-actions "$TOPIC" --ok-actions "$TOPIC" --tags "${TAGS[@]}" "$@"
}
alarm eveassist-prod-errors "An ERROR line in the app or Keycloak log in the last 5 minutes" \
    --namespace $NS --metric-name AppErrors --statistic Sum --period 300 --evaluation-periods 1 \
    --threshold 1 --comparison-operator GreaterThanOrEqualToThreshold --treat-missing-data notBreaching
alarm eveassist-prod-esi-failures "5 or more failed ESI fetches in 15 minutes" \
    --namespace $NS --metric-name EsiFetchFailures --statistic Sum --period 900 --evaluation-periods 1 \
    --threshold 5 --comparison-operator GreaterThanOrEqualToThreshold --treat-missing-data notBreaching
alarm eveassist-prod-upstream-unreachable "Caddy could not reach the app or Keycloak, 5 or more times in 5 minutes" \
    --namespace $NS --metric-name UpstreamUnreachable --statistic Sum --period 300 --evaluation-periods 1 \
    --threshold 5 --comparison-operator GreaterThanOrEqualToThreshold --treat-missing-data notBreaching
alarm eveassist-prod-instance-status "The instance failed an EC2 status check" \
    --namespace AWS/EC2 --metric-name StatusCheckFailed --dimensions Name=InstanceId,Value=$INSTANCE \
    --statistic Maximum --period 60 --evaluation-periods 3 --datapoints-to-alarm 2 \
    --threshold 1 --comparison-operator GreaterThanOrEqualToThreshold --treat-missing-data breaching
alarm eveassist-prod-cpu-credits "The instance's CPU credit balance is below 20 (a t4g runs slow or bills surplus credits when it is empty)" \
    --namespace AWS/EC2 --metric-name CPUCreditBalance --dimensions Name=InstanceId,Value=$INSTANCE \
    --statistic Minimum --period 300 --evaluation-periods 3 \
    --threshold 20 --comparison-operator LessThanThreshold --treat-missing-data notBreaching

echo "4. outside health check of the public site (Route 53, in us-east-1)"
HC=$(aws route53 list-health-checks --query "HealthChecks[?CallerReference=='eveassist-prod-site'].Id | [0]" --output text)
if [ "$HC" = "None" ] || [ -z "$HC" ]; then
    HC=$(aws route53 create-health-check --caller-reference eveassist-prod-site \
        --health-check-config 'Type=HTTPS,FullyQualifiedDomainName=eveassist.net,Port=443,ResourcePath=/privacy,RequestInterval=30,FailureThreshold=3,EnableSNI=true' \
        --query HealthCheck.Id --output text)
    aws route53 change-tags-for-resource --resource-type healthcheck --resource-id "$HC" --add-tags Key=Name,Value=eveassist-prod-site Key=Project,Value=eveassist
fi
aws cloudwatch put-metric-alarm --region us-east-1 --alarm-name eveassist-prod-site-down \
    --alarm-description "https://eveassist.net/privacy fails the Route 53 health check from outside" \
    --alarm-actions "$US_EAST_TOPIC" --ok-actions "$US_EAST_TOPIC" \
    --namespace AWS/Route53 --metric-name HealthCheckStatus --dimensions Name=HealthCheckId,Value="$HC" \
    --statistic Minimum --period 60 --evaluation-periods 2 --threshold 1 --comparison-operator LessThanThreshold \
    --treat-missing-data breaching

echo "done. Confirm the subscription links that SNS emailed to $ALERT_EMAIL (one per region); alerts reach nobody until then."
