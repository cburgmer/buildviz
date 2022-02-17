// A hosted zone can be created using https://docs.aws.amazon.com/Route53/latest/DeveloperGuide/CreatingNewSubdomain.html
data "aws_route53_zone" "selected" {
  name = "buildviz.cburgmer.space."
}
