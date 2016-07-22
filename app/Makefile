default:
	protoc vatest_proto.proto --java_out=src/main/java
fetch:
	rm vatest_proto.proto || true
	wget https://raw.githubusercontent.com/zblassman/vatest-proto/master/vatest_proto.proto
