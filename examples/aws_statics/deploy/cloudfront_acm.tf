resource "aws_acm_certificate" "cloudfront_acm" {
  domain_name       = "${data.aws_route53_zone.selected.name}"
  validation_method = "DNS"
}

resource "aws_acm_certificate_validation" "cloudfront_acm" {
  certificate_arn = aws_acm_certificate.cloudfront_acm.arn

  validation_record_fqdns = [aws_route53_record.cloudfront_acm.fqdn]

  timeouts {
    create = "10m"
  }
}

resource "aws_route53_record" "cloudfront_acm" {
  name            = aws_acm_certificate.cloudfront_acm.domain_validation_options.*.resource_record_name[0]
  records         = [aws_acm_certificate.cloudfront_acm.domain_validation_options.*.resource_record_value[0]]
  type            = aws_acm_certificate.cloudfront_acm.domain_validation_options.*.resource_record_type[0]
  allow_overwrite = true
  zone_id         = data.aws_route53_zone.selected.zone_id
  ttl             = 60
}
