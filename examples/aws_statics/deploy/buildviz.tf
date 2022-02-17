resource "aws_s3_bucket" "statics" {
  bucket = "cburgmerbuildviz"
  acl    = "public-read"
}

data "aws_iam_policy_document" "statics" {
  statement {
    actions   = ["s3:GetObject"]
    resources = ["${aws_s3_bucket.statics.arn}/*"]

    principals {
      type        = "AWS"
      identifiers = ["*"]
    }
  }
}

resource "aws_s3_bucket_policy" "statics" {
  bucket = aws_s3_bucket.statics.id
  policy = data.aws_iam_policy_document.statics.json
}

locals {
  s3_origin_id = "myS3Origin"
}

resource "aws_cloudfront_distribution" "cf" {
  origin {
    domain_name = aws_s3_bucket.statics.bucket_regional_domain_name
    origin_id   = local.s3_origin_id
  }

  enabled             = true
  is_ipv6_enabled     = true
  default_root_object = "index.html"
  price_class         = "PriceClass_100"

  aliases = ["${data.aws_route53_zone.selected.name}"]

  default_cache_behavior {
    allowed_methods  = ["GET", "HEAD"]
    cached_methods   = ["GET", "HEAD"]
    target_origin_id = local.s3_origin_id

    forwarded_values {
      query_string = false

      cookies {
        forward = "none"
      }
    }

    min_ttl                = 0
    default_ttl            = 3600
    max_ttl                = 86400
    compress               = true
    viewer_protocol_policy = "redirect-to-https"
  }
  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }
  viewer_certificate {
    acm_certificate_arn      = aws_acm_certificate.cloudfront_acm.arn
    ssl_support_method       = "sni-only"
    minimum_protocol_version = "TLSv1.2_2019"
  }
}

resource "aws_route53_record" "dns_record" {
  zone_id = "${data.aws_route53_zone.selected.zone_id}"
  name    = "${data.aws_route53_zone.selected.name}"
  type    = "A"

  alias {
    name                   = "${aws_cloudfront_distribution.cf.domain_name}"
    zone_id                = "${aws_cloudfront_distribution.cf.hosted_zone_id}"
    evaluate_target_health = false
  }
}
