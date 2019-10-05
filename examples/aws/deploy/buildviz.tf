data "aws_ami" "amazon_linux2_ami" {
  most_recent = true
  name_regex  = "^amzn2-ami-hvm-"
  owners      = ["137112412989"]
}

resource "aws_key_pair" "buildviz_keypair" {
  key_name   = "builviz_keypair"
  public_key = "${file("~/.ssh/id_rsa.pub")}"
}

resource "aws_security_group" "buildviz_instance_ssh_security_group" {
  name   = "buildviz_instance_ssh_group"
  vpc_id = "${aws_vpc.buildviz_vpc.id}"

  ingress {
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "buildviz_instance_http_security_group" {
  name   = "buildviz_instance_http_security_group"
  vpc_id = "${aws_vpc.buildviz_vpc.id}"

  ingress {
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

data "template_file" "script" {
  template = "${file("${path.module}/init.cfg.tpl")}"
}

data "template_cloudinit_config" "config" {
  part {
    filename     = "init.cfg"
    content_type = "text/cloud-config"
    content      = "${data.template_file.script.rendered}"
  }
}

resource "aws_instance" "buildviz_instance" {
  ami             = "${data.aws_ami.amazon_linux2_ami.id}"
  subnet_id       = "${aws_subnet.public_subnet.id}"
  instance_type   = "t3a.micro"
  key_name        = "${aws_key_pair.buildviz_keypair.id}"
  security_groups = ["${aws_security_group.buildviz_instance_ssh_security_group.id}", "${aws_security_group.buildviz_instance_http_security_group.id}"]

  user_data_base64 = "${data.template_cloudinit_config.config.rendered}"
}

output "instance_fqdn" {
  value = "${aws_instance.buildviz_instance.public_dns}"
}

// A hosted zone can be created using https://docs.aws.amazon.com/Route53/latest/DeveloperGuide/CreatingNewSubdomain.html
data "aws_route53_zone" "selected" {
  name         = "buildviz.cburgmer.space."
}

resource "aws_route53_record" "buildviz_dns_record" {
  zone_id = "${data.aws_route53_zone.selected.zone_id}"
  name    = "${data.aws_route53_zone.selected.name}"
  type    = "A"
  ttl     = "300"
  records = ["${aws_instance.buildviz_instance.public_ip}"]
}
