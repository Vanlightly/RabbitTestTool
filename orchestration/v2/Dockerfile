FROM maven:3.6-jdk-8 as orchestrator_builder

COPY . /workspace

WORKDIR /workspace

RUN mvn package

FROM ubuntu:18.04

RUN apt-get update

# install Java
RUN apt install openjdk-11-jre-headless

# install ansible and ec2 dynamic inventory
RUN apt install software-properties-common
RUN apt-add-repository --yes --update ppa:ansible/ansible
RUN apt install ansible
COPY dependencies/ansible.cfg /etc/ansible/ansible.cfg
COPY dependencies/ec2.ini /etc/ansible/ec2.ini
COPY dependencies/ec2.py /etc/ansible/ec2.py

# Install Cloud CLIs

# AWS CLI
RUN apt-get install awscli -y
COPY $AWS_CONFIG ~/.aws/config
COPY $AWS_CREDENTIALS ~/.aws/credentials
COPY $AWS_PEM ~/.ssh/benchmarking.pem
RUN aws --version

# EKS CLI
curl --silent --location "https://github.com/weaveworks/eksctl/releases/latest/download/eksctl_$(uname -s)_amd64.tar.gz" | tar xz -C /tmp
sudo mv /tmp/eksctl /usr/local/bin
RUN eksctl version

# GCLOUD CLI
RUN apt-get install apt-transport-https ca-certificates gnupg
RUN curl https://packages.cloud.google.com/apt/doc/apt-key.gpg | apt-key --keyring /usr/share/keyrings/cloud.google.gpg add -
RUN apt-get update && sudo apt-get install google-cloud-sdk

COPY $GCLOUD_KEY_FILE gcloud_key_file
RUN gcloud auth activate-service-account --key-file=gcloud_key_file
RUN gcloud config set project $GCLOUD_PROJECT


# prepare orchestrator
WORKDIR /orchestrator

COPY --from=orchestrator_builder \
    /workspace/target/orchestrator-0.1-SNAPSHOT-jar-with-dependencies.jar \
    orchestrator.jar


COPY ../benchmark/target/rabbittesttool-1.1-SNAPSHOT-jar-with-dependencies.jar .
COPY configuration .
COPY deploy .
COPY run .
mkdir logs

# run orchestrator
ENTRYPOINT ["java", "-jar", "orchestrator.jar"]