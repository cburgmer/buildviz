resource "aws_vpc" "buildviz_vpc" {
  cidr_block           = "10.0.0.0/16"
  enable_dns_hostnames = true
}

resource "aws_subnet" "public_subnet" {
  vpc_id                  = "${aws_vpc.buildviz_vpc.id}"
  cidr_block              = "10.0.0.0/24"
  map_public_ip_on_launch = true
}

resource "aws_internet_gateway" "buildviz_internet_gateway" {
  vpc_id = "${aws_vpc.buildviz_vpc.id}"
}

resource "aws_route_table" "buildviz_main_route_table" {
  vpc_id = "${aws_vpc.buildviz_vpc.id}"

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = "${aws_internet_gateway.buildviz_internet_gateway.id}"
  }
}

resource "aws_main_route_table_association" "buildviz_main_route_table_association" {
  vpc_id         = "${aws_vpc.buildviz_vpc.id}"
  route_table_id = "${aws_route_table.buildviz_main_route_table.id}"
}
