resource "aws_s3_bucket" "statics" {
  bucket = "cburgmerbuildviz"
  acl    = "private"
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

    viewer_protocol_policy = "allow-all"
    min_ttl                = 0
    default_ttl            = 3600
    max_ttl                = 86400
  }
  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }
  viewer_certificate {
    cloudfront_default_certificate = true
  }
}

// A hosted zone can be created using https://docs.aws.amazon.com/Route53/latest/DeveloperGuide/CreatingNewSubdomain.html
data "aws_route53_zone" "selected" {
  name = "buildviz.cburgmer.space."
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
