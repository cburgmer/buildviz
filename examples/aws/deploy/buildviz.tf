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
    from_port   = 8080
    to_port     = 8080
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

resource "aws_instance" "buildviz_instance" {
  ami             = "${data.aws_ami.amazon_linux2_ami.id}"
  subnet_id       = "${aws_subnet.public_subnet.id}"
  instance_type   = "t2.micro"
  key_name        = "${aws_key_pair.buildviz_keypair.id}"
  security_groups = ["${aws_security_group.buildviz_instance_ssh_security_group.id}", "${aws_security_group.buildviz_instance_http_security_group.id}"]

  provisioner "file" {
    source      = "../docker-compose.yml"
    destination = "docker-compose.yml"

    connection {
      user = "ec2-user"
    }
  }

  provisioner "remote-exec" {
    inline = [
      "sudo yum update -y",
      "sudo yum install -y docker",
      "sudo curl -L https://github.com/docker/compose/releases/download/1.21.2/docker-compose-$(uname -s)-$(uname -m) -o /usr/local/bin/docker-compose",
      "sudo chmod +x /usr/local/bin/docker-compose",
      "sudo service docker start",
      "sudo usermod -a -G docker ec2-user", # needs a re-login to take effect
    ]

    connection {
      user = "ec2-user"
    }
  }

  provisioner "remote-exec" {
    inline = [
      "mkdir buildviz nginx", # Work around https://github.com/docker/compose/issues/3391
      "docker-compose pull",
      "mkdir data", # create mount volume ourselves, so it has the correct owner, might be https://github.com/docker/compose/issues/3270
      "docker-compose up --no-build -d",
    ]

    connection {
      user = "ec2-user"
    }
  }
}

output "instance_fqdn" {
  value = "${aws_instance.buildviz_instance.public_dns}"
}
